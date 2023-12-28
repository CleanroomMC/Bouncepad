package com.cleanroommc.bouncepad.api.transformer;

import com.cleanroommc.bouncepad.api.asm.cp.ConstantPool;
import org.objectweb.asm.tree.ClassNode;

import java.util.function.Supplier;

public record TransformationContext(ConstantPool pool, Supplier<ClassNode> nodeGetter) {

}
