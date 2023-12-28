package com.cleanroommc.bouncepad.api.asm.generator;

public interface ClassGenerator {

    boolean accept(String className);

    boolean acceptTransformers(String className);

    byte[] generateClass(int asmApi, String className) throws Throwable;

}
