package com.cleanroommc.bouncepad.api.tweaker;

import com.cleanroommc.bouncepad.BouncepadClassLoader;

import java.io.File;
import java.util.List;

public interface Tweaker {

    default void acceptOptions(List<String> arguments, File gameDirectory, File assetDirectory) {

    }

    default void acceptClassLoader(BouncepadClassLoader classLoader) {

    }

    default void supplyArguments(List<String> arguments) {

    }

}
