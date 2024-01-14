package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.tweaker.Launcher;
import com.cleanroommc.bouncepad.api.tweaker.Tweaker;
import joptsimple.OptionParser;
import joptsimple.util.PathConverter;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.imaginebreaker.NativeImagineBreaker;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// TODO: service-fy tweakers, loaders and transformers
public class Bouncepad {

    private static BouncepadClassLoader classLoader;
    private static Logger logger;
    private static InternalBlackboard blackboard;
    private static Path minecraftHome;
    private static Path assetsDirectory;

    public static void main(String[] args) {
        Thread.currentThread().setContextClassLoader(new AdamClassLoader());
        var backing = new HashMap<String, Object>();
        Launch.blackboard = backing;

        classLoader = new BouncepadClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        classLoader.init();

        Launch.classLoader = classLoader;
        logger = LogManager.getLogger("Bouncepad");
        blackboard = new InternalBlackboard(backing);

        runImagineBreaker(); // TODO: not to run unless its Java 9 or above
        launch(args);
    }

    public static BouncepadClassLoader classLoader() {
        return classLoader;
    }

    public static Logger logger() {
        return logger;
    }

    public static Path minecraftHome() {
        return minecraftHome;
    }

    public static Path assetsDirectory() {
        return assetsDirectory;
    }

    private static void runImagineBreaker() {
        var imagineBreakerLibraryName = System.mapLibraryName("imaginebreaker");
        var osVersion = System.getProperty("os.version");
        if(osVersion != null && osVersion.toLowerCase().contains("android")) {
            imagineBreakerLibraryName = "libimaginebreaker_android.so";
        }
        var imagineBreakerLibraryUrl = classLoader.getResource(imagineBreakerLibraryName);
        if (imagineBreakerLibraryUrl == null) {
            logger.fatal("Unable to launch, {} cannot be found.", imagineBreakerLibraryName);
            System.exit(1);
        } else {
            try {
                if ("jar".equals(imagineBreakerLibraryUrl.getProtocol())) {
                    // Extract the native to a temporary file if it resides in a jar (non-dev)
                    var tempDir = Files.createTempDirectory("bouncepad");
                    tempDir.toFile().deleteOnExit();
                    var tempFile = tempDir.resolve(imagineBreakerLibraryName);
                    try (InputStream is = classLoader.getResourceAsStream(imagineBreakerLibraryName)) {
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
                logger.fatal("Unable to launch, error loading natives", t);
                System.exit(1);
            }
        }
    }

    private static void launch(String[] args) {
        var parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        var gameDirOption = parser.accepts("gameDir", "Alternative game directory")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter());
        var assetsDirOption = parser.accepts("assetsDir", "Assets directory")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter());
        var tweakClassOption = parser.accepts("tweakClass", "Tweak class(es) to load").withOptionalArg();
        var tweakerOption = parser.accepts("tweakers", "Tweakers to load").withOptionalArg();
        var launcherOption = parser.accepts("launcher", "Launcher to load").withOptionalArg();
        var nonOption = parser.nonOptions();

        var options = parser.parse(args);

        // var profileName = options.valueOf(profileOption);
        minecraftHome = options.valueOf(gameDirOption);
        Launch.minecraftHome = minecraftHome.toFile();
        assetsDirectory = options.valueOf(assetsDirOption);
        Launch.assetsDir = assetsDirectory.toFile();

        var oldTweakClassNames = new ArrayList<>(options.valuesOf(tweakClassOption));
        blackboard.internalPut("TweakClasses", oldTweakClassNames);
        blackboard.put("bouncepad:oldtweakers", oldTweakClassNames);
        var newTweakClassNames = new ArrayList<>(options.valuesOf(tweakerOption));
        blackboard.put("bouncepad:newtweakers", newTweakClassNames);

        var extraArgs = options.valuesOf(nonOption);
        var argumentList = new ArrayList<String>();
        blackboard.internalPut("ArgumentList", argumentList);
        blackboard.put("bouncepad:arguments", argumentList);

        Launcher mainLauncher = null;

        Set<String> calledTweakers = new HashSet<>();
        List<Tweaker> allOldTweakers = new ArrayList<>(oldTweakClassNames.size() + 1);
        List<Tweaker> allNewTweakers = new ArrayList<>(newTweakClassNames.size() + 1);

        try {
            var launcherName = options.valueOf(launcherOption);
            if (options.valueOf(launcherOption) != null) {
                blackboard.put("bouncepad:launcher", launcherName);
                var ctor = Class.forName(launcherName, true, classLoader).getDeclaredConstructor();
                mainLauncher = (Launcher) ctor.newInstance();
            }

            var tweakers = new ArrayList<Tweaker>();
            blackboard.internalPut("Tweaks", tweakers);
            blackboard.put("bouncepad:tweakers", tweakers);

            Iterator<String> tweakerNamesIterator;

            do {
                tweakerNamesIterator = newTweakClassNames.iterator();
                // Processing tweakers
                while (tweakerNamesIterator.hasNext()) {
                    var tweakerName = tweakerNamesIterator.next();
                    tweakerNamesIterator.remove();
                    if (!calledTweakers.add(tweakerName)) {
                        logger.warn("{} tweaker has already been visited, skipping!", tweakerName);
                        continue;
                    }
                    logger.info("Loading {} tweaker", tweakerName);
                    var ctor = Class.forName(tweakerName, true, classLoader).getDeclaredConstructor();
                    var tweaker = (Tweaker) ctor.newInstance();
                    tweakers.add(tweaker);
                    if (mainLauncher == null && tweaker instanceof Launcher launcher) {
                        mainLauncher = launcher;
                    }
                }
                for (var iter = tweakers.iterator(); iter.hasNext();) {
                    var tweaker = iter.next();
                    logger.info("Calling tweak class {}", tweaker.getClass().getName());
                    tweaker.acceptOptions(extraArgs, minecraftHome, assetsDirectory);
                    tweaker.acceptClassLoader(classLoader);
                    allNewTweakers.add(tweaker);
                    iter.remove();
                }
            } while (!newTweakClassNames.isEmpty());

            do {
                tweakerNamesIterator = oldTweakClassNames.iterator();
                // Processing old tweakers
                while (tweakerNamesIterator.hasNext()) {
                    var tweakerName = tweakerNamesIterator.next();
                    tweakerNamesIterator.remove();
                    if (!calledTweakers.add(tweakerName)) {
                        logger.warn("{} tweaker has already been visited, skipping!", tweakerName);
                        continue;
                    }
                    logger.info("Loading {} tweaker", tweakerName);
                    var ctor = Class.forName(tweakerName, true, classLoader).getDeclaredConstructor();
                    var tweaker = (ITweaker) ctor.newInstance();
                    tweakers.add(tweaker);
                    if (mainLauncher == null) {
                        mainLauncher = tweaker;
                    }
                }

                for (var iter = tweakers.iterator(); iter.hasNext();) {
                    var tweaker = iter.next();
                    logger.info("Calling tweak class {}", tweaker.getClass().getName());
                    tweaker.acceptOptions(extraArgs, minecraftHome, assetsDirectory);
                    tweaker.acceptClassLoader(classLoader);
                    allOldTweakers.add(tweaker);
                    iter.remove();
                }
            } while (!oldTweakClassNames.isEmpty());

            if (mainLauncher == null) {
                logger.fatal("Unable to launch, a valid launcher has not been provided.");
                System.exit(1);
            }
            for (var tweaker : allNewTweakers) {
                tweaker.supplyArguments(argumentList);
            }
            for (var tweaker : allOldTweakers) {
                tweaker.supplyArguments(argumentList);
            }
            logger.info("Launching wrapped minecraft [{}]", mainLauncher.getClass().getName());
            mainLauncher.launch(argumentList);
        } catch (Throwable t) {
            logger.fatal("Unable to launch", t);
            System.exit(1);
        }

    }

}
