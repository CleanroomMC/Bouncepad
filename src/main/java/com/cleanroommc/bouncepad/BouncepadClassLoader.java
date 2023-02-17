package com.cleanroommc.bouncepad;

import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

public class BouncepadClassLoader extends LaunchClassLoader {

    public BouncepadClassLoader(URL[] urls) {
        super(urls);
    }

    @Override
    public byte[] getClassBytes(String name) throws IOException {
        return new byte[0];
    }

    @Override
    public void clearNegativeEntries(Set<String> entriesToClear) {

    }

}
