package com.cleanroommc.bouncepad.test.bumpasmapi;

import com.cleanroommc.bouncepad.asm.impl.BumpASMAPITransformer;
import com.cleanroommc.bouncepad.test.util.TestClassVisitor;
import com.cleanroommc.bouncepad.test.util.TransformingClassLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class BumpASMAPITest {

    private TransformingClassLoader transformatingClassLoader;

    @BeforeEach
    private void beforeEach() {
        this.transformatingClassLoader = new TransformingClassLoader(new BumpASMAPITransformer());
        this.transformatingClassLoader.addClassForTransform("com.cleanroommc.bouncepad.test.util.TestClassVisitor");
    }

    @Test
    public void test() throws ReflectiveOperationException {
        var clazz = this.transformatingClassLoader.findClass("com.cleanroommc.bouncepad.test.util.TestClassVisitor");
        Assertions.assertNotNull(clazz);

        var firstCtor = clazz.getDeclaredConstructor(int.class);
        Assertions.assertNotNull(firstCtor);
        var object = (TestClassVisitor) firstCtor.newInstance(Opcodes.ASM5);
        Assertions.assertNotNull(object);
        Assertions.assertEquals(object.getApi(), Opcodes.ASM9);

        var secondCtor = clazz.getDeclaredConstructor(int.class, ClassVisitor.class);
        Assertions.assertNotNull(secondCtor);
        object = (TestClassVisitor) secondCtor.newInstance(Opcodes.ASM5, null);
        Assertions.assertEquals(object.getApi(), Opcodes.ASM9);
    }

}
