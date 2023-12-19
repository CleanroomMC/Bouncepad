package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.Launcher;
import com.cleanroommc.bouncepad.api.Tweaker;
import joptsimple.OptionParser;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.imaginebreaker.NativeImagineBreaker;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// TODO: service-fy tweakers, loaders and transformers
public class Bouncepad {

    public static final Logger LOGGER = LogManager.getLogger("Bouncepad");

    private static final InternalBlackboard BLACKBOARD = InternalBlackboard.INSTANCE;

    public static BouncepadClassLoader classLoader;

    public static File minecraftHome;
    public static File assetsDir;

    public static void main(String[] args) {
        Launch.blackboard = InternalBlackboard.INSTANCE.map;

        classLoader = new BouncepadClassLoader(getClassPathURLs());
        Launch.classLoader = classLoader;
        Thread.currentThread().setContextClassLoader(classLoader);

        runImagineBreaker(); // TODO: not to run unless its Java 9 or above

        launch(args);
    }

    private static List<URL> getClassPathURLs() {
        // Same classpaths present in AppClassLoader
        String[] classpaths = System.getProperty("java.class.path").split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        try {
            for (String classpath : classpaths) {
                urls.add(new File(classpath).toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return urls;
    }

    private static void runImagineBreaker() {
        String imagineBreakerLibraryName = System.mapLibraryName("imaginebreaker");
        URL imagineBreakerLibraryUrl = NativeImagineBreaker.class.getClassLoader().getResource(imagineBreakerLibraryName);
        if (imagineBreakerLibraryUrl == null) {
            LOGGER.fatal("Unable to launch, {} cannot be found.", imagineBreakerLibraryName);
            System.exit(1);
        } else {
            try {
                if ("jar".equals(imagineBreakerLibraryUrl.getProtocol())) {
                    // Extract the native to a temporary file if it resides in a jar (non-dev)
                    Path tempDir = Files.createTempDirectory("bouncepad");
                    tempDir.toFile().deleteOnExit();
                    Path tempFile = tempDir.resolve(imagineBreakerLibraryName);
                    try (InputStream is = NativeImagineBreaker.class.getClassLoader().getResourceAsStream(imagineBreakerLibraryName)) {
                        Files.copy(is, tempFile);
                    }
                    tempFile.toFile().deleteOnExit();
                    System.load(tempFile.toAbsolutePath().toString());
                } else {
                    // Load as-is if it is outside a jar (dev)
                    System.load(new File(imagineBreakerLibraryUrl.toURI()).getAbsolutePath());
                }
                NativeImagineBreaker.openBaseModules();
                NativeImagineBreaker.removeAllReflectionFilters();
            } catch (Throwable t) {
                LOGGER.fatal("Unable to launch, error loading natives", t);
                System.exit(1);
            }
        }
    }

    private static void launch(String[] args) {
        var parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        // var profileOption = parser.accepts("version", "The version we launched with").withRequiredArg();
        var gameDirOption = parser.accepts("gameDir", "Alternative game directory").withRequiredArg().ofType(File.class);
        var assetsDirOption = parser.accepts("assetsDir", "Assets directory").withRequiredArg().ofType(File.class);
        var tweakClassOption = parser.accepts("tweakClass", "Tweak class(es) to load").withOptionalArg();
        var tweakerOption = parser.accepts("tweakers", "Tweakers to load").withOptionalArg();
        var launcherOption = parser.accepts("launcher", "Launcher to load").withOptionalArg();
        var nonOption = parser.nonOptions();

        var options = parser.parse(args);

        // var profileName = options.valueOf(profileOption);
        minecraftHome = options.valueOf(gameDirOption);
        assetsDir = options.valueOf(assetsDirOption);

        var oldTeakClassNames = new ArrayList<>(options.valuesOf(tweakClassOption));
        BLACKBOARD.internalPut("TweakClasses", oldTeakClassNames);
        BLACKBOARD.put("bouncepad:oldtweakers", oldTeakClassNames);
        var newTweakClassNames = new ArrayList<>(options.valuesOf(tweakerOption));
        BLACKBOARD.put("bouncepad:tweakers", newTweakClassNames);

        var argumentList = new ArrayList<>(options.valuesOf(nonOption));
        BLACKBOARD.internalPut("ArgumentList", argumentList);
        BLACKBOARD.put("bouncepad:arguments", argumentList);

        Launcher mainLauncher = null;

        Set<String> calledTweakers = new HashSet<>();
        List<ITweaker> allOldTweakers = new ArrayList<>(oldTeakClassNames.size() + 1);
        List<Tweaker> allNewTweakers = new ArrayList<>(newTweakClassNames.size() + 1);

        try {
            var launcherName = options.valueOf(launcherOption);
            if (options.valueOf(launcherOption) != null) {
                BLACKBOARD.put("bouncepad:launcher", launcherName);
                var ctor = Class.forName(launcherName, true, classLoader).getDeclaredConstructor();
                mainLauncher = (Launcher) ctor.newInstance();
            }

            var tweakerNamesIterator = newTweakClassNames.iterator();
            // Processing tweakers
            while (tweakerNamesIterator.hasNext()) {
                var tweakerName = tweakerNamesIterator.next();
                tweakerNamesIterator.remove();
                if (!calledTweakers.add(tweakerName)) {
                    LOGGER.warn("{} tweaker has already been visited, skipping!", tweakerName);
                    continue;
                }
                LOGGER.info("Loading {} tweaker", tweakerName);
                var ctor = Class.forName(tweakerName, true, classLoader).getDeclaredConstructor();
                var tweaker = (Tweaker) ctor.newInstance();
                allNewTweakers.add(tweaker);
                if (mainLauncher == null && tweaker instanceof Launcher launcher) {
                    mainLauncher = launcher;
                }
            }
            tweakerNamesIterator = oldTeakClassNames.iterator();
            // Processing old tweakers
            while (tweakerNamesIterator.hasNext()) {
                var tweakerName = tweakerNamesIterator.next();
                tweakerNamesIterator.remove();
                if (!calledTweakers.add(tweakerName)) {
                    LOGGER.warn("{} tweaker has already been visited, skipping!", tweakerName);
                    continue;
                }
                LOGGER.info("Loading {} tweaker", tweakerName);
                var ctor = Class.forName(tweakerName, true, classLoader).getDeclaredConstructor();
                var tweaker = (ITweaker) ctor.newInstance();
                allOldTweakers.add(tweaker);
                if (mainLauncher == null) {
                    mainLauncher = tweaker;
                }
            }
            for (var tweaker : allNewTweakers) {
                LOGGER.info("Calling tweak class {}", tweaker.getClass().getName());
                tweaker.acceptOptions(argumentList, minecraftHome, assetsDir);
                tweaker.acceptClassLoader(classLoader);
            }
            for (var tweaker : allOldTweakers) {
                LOGGER.info("Calling tweak class {}", tweaker.getClass().getName());
                tweaker.acceptOptions(argumentList, minecraftHome, assetsDir);
                tweaker.acceptClassLoader(classLoader);
            }
            if (mainLauncher == null) {
                LOGGER.fatal("Unable to launch, a valid launcher has not been provided.");
                System.exit(1);
            }
            LOGGER.info("Launching wrapped minecraft [{}]", mainLauncher.getClass().getName());
            mainLauncher.launch(argumentList);
        } catch (Throwable t) {
            LOGGER.fatal("Unable to launch", t);
            System.exit(1);
        }

    }

}
