package com.cleanroommc.bouncepad;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class Bouncepad {

    public static final ClassLoader PARENT_CLASSLOADER = Bouncepad.class.getClassLoader();
    public static final Logger LOGGER = LogManager.getLogger("Bouncepad");
    public static final Map<String, Object> BLACKBOARD = new HashMap<>();

    public static LaunchClassLoader classLoader;

    public static File minecraftHome;
    public static File assetsDir;

    public static void main(String[] args) {
        Launch.blackboard = BLACKBOARD;
        classLoader = new BouncepadClassLoader(getClassPathURLs());
        Launch.classLoader = classLoader;
        Thread.currentThread().setContextClassLoader(classLoader);
        launch(args);
    }

    private static URL[] getClassPathURLs() {
        if (PARENT_CLASSLOADER instanceof URLClassLoader) {
            return ((URLClassLoader) PARENT_CLASSLOADER).getURLs();
        }
        int javaVersion = Integer.parseInt(System.getProperty("java.specification.version"));
        Class<?> classWithUcpField = javaVersion >= 16 ? PARENT_CLASSLOADER.getClass().getSuperclass() : PARENT_CLASSLOADER.getClass();
        try {
            Field urlClassPathField = classWithUcpField.getDeclaredField("ucp");
            urlClassPathField.setAccessible(true);
            Object urlClassPath = urlClassPathField.get(PARENT_CLASSLOADER);
            Method getURLsMethod = urlClassPath.getClass().getDeclaredMethod("getURLs");
            getURLsMethod.setAccessible(true);
            return (URL[]) getURLsMethod.invoke(urlClassPath);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        return new URL[0];
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
        BLACKBOARD.put("TweakClasses", tweakClassNames);

        List<String> argumentList = new ArrayList<>();
        BLACKBOARD.put("ArgumentList", argumentList);

        Set<String> dupeChecker = new HashSet<>();

        List<ITweaker> allTweakers = new ArrayList<>();

        try {
            final List<ITweaker> tweakers = new ArrayList<>(tweakClassNames.size() + 1);
            BLACKBOARD.put("Tweaks", tweakers);

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
