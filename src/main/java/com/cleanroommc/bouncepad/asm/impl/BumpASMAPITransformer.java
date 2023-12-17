package com.cleanroommc.bouncepad.asm.impl;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import static org.objectweb.asm.ClassReader.*;

public class BumpASMAPITransformer implements IClassTransformer, Opcodes {

    private static final String INTERNAL_TYPE_NAME = BumpASMAPITransformer.class.getName().replace('.', '/');

    public static class AutoBumpClassVisitor extends ClassVisitor {

        protected AutoBumpClassVisitor(int api) {
            super(bumpOpcode(api));
        }

        protected AutoBumpClassVisitor(int api, ClassVisitor classVisitor) {
            super(bumpOpcode(api), classVisitor);
        }

    }

    public static class AutoBumpFieldVisitor extends FieldVisitor {

        protected AutoBumpFieldVisitor(int api) {
            super(bumpOpcode(api));
        }

        protected AutoBumpFieldVisitor(int api, FieldVisitor fieldVisitor) {
            super(bumpOpcode(api), fieldVisitor);
        }

    }

    public static class AutoBumpMethodVisitor extends MethodVisitor {

        protected AutoBumpMethodVisitor(int api) {
            super(bumpOpcode(api));
        }

        protected AutoBumpMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(bumpOpcode(api), methodVisitor);
        }

    }

    public static int bumpOpcode(int originalOpcode) {
        return ASM9;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != 0) {
            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = null;
            switch (classReader.getSuperName()) {
                case "org/objectweb/asm/ClassVisitor","org/objectweb/asm/MethodVisitor", "org/objectweb/asm/FieldVisitor" ->
                        classReader.accept(new VisitToBump(classReader.getSuperName(), classWriter = new ClassWriter(classReader, 0)), SKIP_DEBUG | SKIP_FRAMES);
            }
            if (classWriter != null) {
                return classWriter.toByteArray();
            }
        }
        return bytes;
    }

    private static class VisitToBump extends ClassVisitor {

        private final String superClass;
        private final String newSuperClass;

        private VisitToBump(String superClass, ClassVisitor classVisitor) {
            super(ASM9, classVisitor);
            this.superClass = superClass;
            this.newSuperClass = BumpASMAPITransformer.INTERNAL_TYPE_NAME + "$AutoBump" + superClass.substring(superClass.lastIndexOf('/') + 1);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, this.newSuperClass, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return "<init>".equals(name) ? new APIHijacker(this.superClass, this.newSuperClass, methodVisitor) : methodVisitor;
        }

    }

    private static class APIHijacker extends MethodVisitor {

        private final String superClass;
        private final String newSuperClass;

        private APIHijacker(String superClass, String newSuperClass, MethodVisitor methodVisitor) {
            super(ASM9, methodVisitor);
            this.superClass = superClass;
            this.newSuperClass = newSuperClass;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESPECIAL && this.superClass.equals(owner) && "<init>".equals(name)) {
                super.visitMethodInsn(INVOKESPECIAL, this.newSuperClass, name, descriptor, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

    }

}
