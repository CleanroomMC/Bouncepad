package net.minecraft.launchwrapper;

import com.cleanroommc.bouncepad.BouncepadClassLoader;
import com.cleanroommc.bouncepad.api.tweaker.Launcher;
import com.cleanroommc.bouncepad.api.tweaker.Tweaker;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

@Deprecated(since = "0.5")
public interface ITweaker extends Launcher, Tweaker {

    void acceptOptions(List<String> args, File gameDirectory, File assetDirectory, String profile);

    void injectIntoClassLoader(LaunchClassLoader classLoader);

    String getLaunchTarget();

    String[] getLaunchArguments();

    @Override
    default void launch(List<String> arguments) throws Exception {
        Class<?> clazz = Class.forName(getLaunchTarget(), false, Thread.currentThread().getContextClassLoader());
        Method mainMethod = clazz.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) arguments.toArray(String[]::new));
    }

    @Override
    default void acceptOptions(List<String> arguments, File gameDirectory, File assetDirectory) {
        acceptOptions(arguments, gameDirectory, assetDirectory, "1.12.2");
    }

    @Override
    default void acceptClassLoader(BouncepadClassLoader classLoader) {
        injectIntoClassLoader(classLoader);
    }

    @Override
    default void supplyArguments(List<String> arguments) {
        arguments.addAll(List.of(getLaunchArguments()));
    }

}
