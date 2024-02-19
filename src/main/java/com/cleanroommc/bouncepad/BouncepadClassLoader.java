package com.cleanroommc.bouncepad;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.IClassTransformer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class BouncepadClassLoader extends LaunchClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public BouncepadClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public void registerTransformer(String transformerName) {
        super.registerTransformer(transformerName);
    }

    @Override
    public List<IClassTransformer> getTransformers() {
        return super.getTransformers();
    }

    @Override
    public void addClassLoaderExclusion(String toExclude) {
        super.addClassLoaderExclusion(toExclude);
    }

    @Override
    public void addTransformerExclusion(String toExclude) {
        super.addTransformerExclusion(toExclude);
    }

    @Override
    public byte[] getClassBytes(String name) {
        return super.getClassBytes(name);
    }

    /**
     * Required for Java Agents to work on HotSpot
     * @param path The file path added to the classpath
     */
    @SuppressWarnings("unused")
    public void appendToClassPathForInstrumentation(String path) {
        try {
            this.addURL(new File(path).toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
