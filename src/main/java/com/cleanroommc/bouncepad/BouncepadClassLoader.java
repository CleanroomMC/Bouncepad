package com.cleanroommc.bouncepad;

import net.minecraft.launchwrapper.LaunchClassLoader;

import java.net.URL;

public class BouncepadClassLoader extends LaunchClassLoader {

    public BouncepadClassLoader(URL[] urls) {
        super(urls);
    }

}
