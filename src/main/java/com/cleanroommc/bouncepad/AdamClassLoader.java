package com.cleanroommc.bouncepad;

import java.net.URL;
import java.net.URLClassLoader;

public class AdamClassLoader extends URLClassLoader {

    public AdamClassLoader() {
        super(new URL[0], AdamClassLoader.class.getClassLoader());
    }

}
