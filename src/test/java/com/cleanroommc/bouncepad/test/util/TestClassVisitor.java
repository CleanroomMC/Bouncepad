package com.cleanroommc.bouncepad.test.util;

import org.objectweb.asm.ClassVisitor;

public class TestClassVisitor extends ClassVisitor {

    public TestClassVisitor(int api) {
        super(api);
    }

    public TestClassVisitor(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    public int getApi() {
        return api;
    }

}
