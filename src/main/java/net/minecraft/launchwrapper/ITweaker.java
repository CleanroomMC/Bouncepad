package net.minecraft.launchwrapper;

import java.io.File;
import java.util.List;

@Deprecated(since = "0.5")
public interface ITweaker {

    void acceptOptions(List<String> args, File gameDirectory, File assetDirectory, String profile);

    void injectIntoClassLoader(LaunchClassLoader classLoader);

    String getLaunchTarget();

    String[] getLaunchArguments();

}
