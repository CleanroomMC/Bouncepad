package com.cleanroommc.bouncepad.api.transformer;

import org.objectweb.asm.ClassVisitor;

public record TransformationContext(ClassVisitor classVisitor) {

}
