package net.minecraft.launchwrapper;

import java.io.File;
import java.util.Map;

@Deprecated(since = "0.5")
public class Launch {

    public static LaunchClassLoader classLoader;
    public static File minecraftHome;
    public static File assetsDir;
    public static Map<String, Object> blackboard;

}
