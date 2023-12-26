package com.cleanroommc.bouncepad;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.LogWrapper;

import java.io.File;

public class BouncepadClassLoader extends LaunchClassLoader {

    BouncepadClassLoader() {
        this(BouncepadClassLoader.class.getClassLoader());
    }

    public BouncepadClassLoader(ClassLoader parentClassLoader) {
        super(parentClassLoader);
        this.prepareDebugFolders();
    }

    // TODO: implement these, but w/ checks,
    /*
    @Override
    public void addURL(URL url) {

    }
     */

    private void prepareDebugFolders() {
        if (DebugOption.SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS.isOn() ||
                DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn() ||
                DebugOption.SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS.isOn()) {
            File saveTransformationFolder = new File(Bouncepad.minecraftHome, "save_transformations");
            saveTransformationFolder.mkdirs();
            LogWrapper.info("Transformation related debug options enabled, saving classes to \"%s\"",
                    saveTransformationFolder.getAbsolutePath().replace('\\', '/'));
            File beforeFolder = new File(saveTransformationFolder, "before_all");
            beforeFolder.mkdirs();
            File afterEachFolder = new File(saveTransformationFolder, "after_each");
            afterEachFolder.mkdirs();
            File afterAllFolder = new File(saveTransformationFolder, "after_all");
            afterAllFolder.mkdirs();
        }
    }

}
