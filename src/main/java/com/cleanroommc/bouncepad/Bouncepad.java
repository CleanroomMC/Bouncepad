package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.Blackboard;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.imaginebreaker.NativeImagineBreaker;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Bouncepad {

    public static final ClassLoader BOOTSTRAP_CLASSLOADER = Bouncepad.class.getClassLoader();
    public static final Logger LOGGER = LogManager.getLogger("Bouncepad");

    private static final InternalBlackboard BLACKBOARD = InternalBlackboard.INSTANCE;

    public static LaunchClassLoader classLoader;

    public static File minecraftHome;
    public static File assetsDir;

    public static void main(String[] args) {
        Launch.blackboard = InternalBlackboard.INSTANCE.map;
        classLoader = new BouncepadClassLoader(getClassPathURLs());
        Launch.classLoader = classLoader;
        Thread.currentThread().setContextClassLoader(classLoader);
        runImagineBreaker();
        launch(args);
    }

    private static List<URL> getClassPathURLs() {
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
        OptionParser parser = new OptionParser();

        parser.allowsUnrecognizedOptions();

        OptionSpec<String> profileOption = parser.accepts("version", "The version we launched with").withRequiredArg();
        OptionSpec<File> gameDirOption = parser.accepts("gameDir", "Alternative game directory").withRequiredArg().ofType(File.class);
        OptionSpec<File> assetsDirOption = parser.accepts("assetsDir", "Assets directory").withRequiredArg().ofType(File.class);
        OptionSpec<String> tweakClassOption = parser.accepts("tweakClass", "Tweak class(es) to load").withRequiredArg();
        OptionSpec<String> nonOption = parser.nonOptions();

        OptionSet options = parser.parse(args);

        String profileName = options.valueOf(profileOption);
        minecraftHome = options.valueOf(gameDirOption);
        assetsDir = options.valueOf(assetsDirOption);
        List<String> tweakClassNames = new ArrayList<>(options.valuesOf(tweakClassOption));
        BLACKBOARD.map.put("TweakClasses", tweakClassNames);

        List<String> argumentList = new ArrayList<>();
        BLACKBOARD.map.put("ArgumentList", argumentList);

        Set<String> dupeChecker = new HashSet<>();

        List<ITweaker> allTweakers = new ArrayList<>();

        try {
            final List<ITweaker> tweakers = new ArrayList<>(tweakClassNames.size() + 1);
            BLACKBOARD.map.put("Tweaks", tweakers);

            ITweaker firstTweaker = null;

            do {
                Iterator<String> tweakerNamesIterator = tweakClassNames.iterator();
                while (tweakerNamesIterator.hasNext()) {
                    String tweakerName = tweakerNamesIterator.next();
                    tweakerNamesIterator.remove();
                    if (!dupeChecker.add(tweakerName)) {
                        LOGGER.warn("Tweak class name {} has already been visited -- skipping", tweakerName);
                        continue;
                    }
                    LOGGER.info("Loading tweak class name {}", tweakerName);
                    classLoader.addClassLoaderExclusion(tweakerName.substring(0, tweakerName.lastIndexOf('.')));
                    Constructor<?> tweakerCtor = Class.forName(tweakerName, true, classLoader).getDeclaredConstructor();
                    final ITweaker tweaker = (ITweaker) tweakerCtor.newInstance();
                    tweakers.add(tweaker);
                    if (firstTweaker == null) {
                        LOGGER.info("Using primary tweak class name {}", tweakerName);
                        firstTweaker = tweaker;
                    }
                }
                Iterator<ITweaker> tweakersIterator = tweakers.iterator();
                while (tweakersIterator.hasNext()) {
                    ITweaker tweaker = tweakersIterator.next();
                    LOGGER.info("Calling tweak class {}", tweaker.getClass().getName());
                    tweaker.acceptOptions(options.valuesOf(nonOption), minecraftHome, assetsDir, profileName);
                    tweaker.injectIntoClassLoader(classLoader);
                    allTweakers.add(tweaker);
                    tweakersIterator.remove();
                }
            } while (!tweakClassNames.isEmpty());

            for (ITweaker tweaker : allTweakers) {
                argumentList.addAll(Arrays.asList(tweaker.getLaunchArguments()));
            }

            if (firstTweaker == null) {
                LOGGER.fatal("Unable to launch, there are no valid launch targets found within provided tweakers.");
                System.exit(1);
            }

            String launchTarget = firstTweaker.getLaunchTarget();
            Class<?> clazz = Class.forName(launchTarget, false, classLoader);
            Method mainMethod = clazz.getMethod("main", String[].class);

            LOGGER.info("Launching wrapped minecraft [{}]", launchTarget);
            mainMethod.invoke(null, (Object) argumentList.toArray(new String[0]));

        } catch (Throwable t) {
            LOGGER.fatal("Unable to launch", t);
            System.exit(1);
        }
    }

}
