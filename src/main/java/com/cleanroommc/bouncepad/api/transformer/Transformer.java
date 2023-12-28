package com.cleanroommc.bouncepad.api.transformer;

// TODO: more expressive?
public interface Transformer {

    boolean allow(String className);

    void transform(String className, TransformationContext context);

}

