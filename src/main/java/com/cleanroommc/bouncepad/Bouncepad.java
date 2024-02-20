package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.Blackboard;
import com.cleanroommc.bouncepad.debug.DebugOption;
import joptsimple.OptionParser;
import joptsimple.util.PathConverter;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.imaginebreaker.ImagineBreaker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public final class Bouncepad {

    private static BouncepadClassLoader classLoader;
    private static Logger logger;
    private static Blackboard globalBlackboard;
    private static Path minecraftHome, assetsDirectory;

    public static void main(String[] args) {
        if (ClassLoader.getSystemClassLoader().getClass() != BouncepadClassLoader.class) {
            throw new RuntimeException("java.system.class.loader property should be pointed towards com.cleanroommc.bouncepad.BouncepadClassLoader.");
        }

        classLoader = (BouncepadClassLoader) ClassLoader.getSystemClassLoader();

        // ImagineBreaker components cannot be statically instantiated in BouncepadClassLoader
        // as MethodHandles rely on SystemClassLoader being instantiated first
        ImagineBreaker.openBootModules();
        ImagineBreaker.wipeFieldFilters();
        ImagineBreaker.wipeMethodFilters();

        initLogger();
        logger.info("Initializing Bouncepad");
        initBlackboard();
        logger.info("Initializing Default Blackboard");
        logger.info("Processing Starting Arguments");

        if (DebugOption.DO_NOT_PROCESS_ARGUMENTS.isOff()) {
            processArgs(args);
        }
    }

    public static BouncepadClassLoader mainClassLoader() {
        return classLoader;
    }

    public static ClassLoader platformClassLoader() {
        return ClassLoader.getPlatformClassLoader();
    }

    public static ClassLoader appClassLoader() {
        return classLoader.getParent();
    }

    public static Logger logger() {
        return logger;
    }

    public static Blackboard globalBlackboard() {
        return globalBlackboard;
    }

    public static Path minecraftHome() {
        return minecraftHome;
    }

    public static Path assetsDirectory() {
        return assetsDirectory;
    }

    private static void initLogger() {
        logger = LogManager.getLogger("Bouncepad");
    }

    private static void initBlackboard() {
        var map = new HashMap<String, Object>();
        Bouncepad.globalBlackboard = new Blackboard(map);
        Launch.blackboard = map;
    }

    private static void processArgs(String[] args) {
        var parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        var profileOption = parser.accepts("version", "The version we launched with").withRequiredArg();
        var gameDirOption = parser.accepts("gameDir", "Game Directory").withRequiredArg().withValuesConvertedBy(new PathConverter());
        var assetsDirOption = parser.accepts("assetsDir", "Assets Directory").withRequiredArg().withValuesConvertedBy(new PathConverter());
        var tweakClassOption = parser.accepts("tweakClass", "Tweak class(es) to load").withRequiredArg();
        var nonOption = parser.nonOptions();

        var options = parser.parse(args);

        minecraftHome = options.valueOf(gameDirOption);
        assetsDirectory = options.valueOf(assetsDirOption);
        Launch.minecraftHome = minecraftHome.toFile();
        Launch.assetsDir = assetsDirectory.toFile();

        var profileName = options.valueOf(profileOption);
        var tweakClassNames = new ArrayList<>(options.valuesOf(tweakClassOption));

    }

}
