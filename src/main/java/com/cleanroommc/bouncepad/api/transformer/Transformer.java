package com.cleanroommc.bouncepad.api.transformer;

public interface Transformer extends Comparable<Transformer> {

    boolean allow(String className);

    void transform(String className, TransformationContext context);

    @Override
    default int compareTo(Transformer other) {
        return 0;
    }

}

