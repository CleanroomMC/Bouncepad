package com.cleanroommc.bouncepad.api.tweaker;

import com.cleanroommc.bouncepad.BouncepadClassLoader;

import java.nio.file.Path;
import java.util.List;

public interface Tweaker {

    default void acceptOptions(List<String> arguments, Path gameDirectory, Path assetDirectory) {

    }

    default void acceptClassLoader(BouncepadClassLoader classLoader) {

    }

    default void supplyArguments(List<String> arguments) {

    }

}
