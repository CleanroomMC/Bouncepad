package com.cleanroommc.bouncepad.test.util;

import net.minecraft.launchwrapper.IClassTransformer;
import org.junit.platform.commons.util.ClassLoaderUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;

/**
 * Used for testing transformations in a more sandboxed manner, it is/(can be made) a lot more controlled here
 */
public class TransformingClassLoader extends ClassLoader {

    private final IClassTransformer transformer;
    private final ClassLoader parent;
    private final Set<String> transformFor = new HashSet<>();

    public TransformingClassLoader(IClassTransformer transformer) {
        this.transformer = transformer;
        this.parent = ClassLoaderUtils.getDefaultClassLoader();
    }

    public void addClassForTransform(String className) {
        this.transformFor.add(className);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        if (this.transformFor.contains(name)) {
            try (InputStream is = this.parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                var buffer = new ByteArrayOutputStream();
                int read;
                byte[] data = new byte[4];
                while ((read = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, read);
                }
                buffer.flush();
                byte[] classBytes = buffer.toByteArray();
                classBytes = this.transformer.transform(name, name, classBytes);
                // TODO: evaluate whether class that requested for this CL will always be the right one
                return MethodHandles.lookup().defineClass(classBytes);
            } catch (IOException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return super.findClass(name);
    }

}
