package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.asm.generator.ClassGenerator;
import com.cleanroommc.bouncepad.impl.asm.BumpASMAPITransformer;
import jdk.internal.access.SharedSecrets;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

public class BouncepadClassLoader extends LaunchClassLoader {

    private static final Path BEFORE_ALL_TRANSFORMATIONS_SAVE_DIRECTORY = Bouncepad.minecraftHome().resolve("save_transformations/before_all/");
    private static final Path AFTER_ALL_TRANSFORMATIONS_SAVE_DIRECTORY = Bouncepad.minecraftHome().resolve("save_transformations/after_all/");

    protected static Path getAfterEachTransformationSaveDirectory(Class<?> clazz) {
        return Bouncepad.minecraftHome().resolve("save_transformations/after_each/").resolve(clazz.getName().replace('.', '/'));
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(4096);
    private final List<ClassGenerator> classGenerators = new ArrayList<>();

    BouncepadClassLoader() {
        this(BouncepadClassLoader.class.getClassLoader());
    }

    public BouncepadClassLoader(ClassLoader parentClassLoader) {
        super(parentClassLoader);
    }

    public void addClassGenerator(ClassGenerator generator) {
        this.classGenerators.add(generator);
    }

    public boolean isClassLoaded(String name) {
        return this.loadedClasses.containsKey(name.replace('/', '.'));
    }

    void init() {
        // TODO: Mixin Staging
        this.registerTransformer(BumpASMAPITransformer.class.getName());
    }

    @Override
    public Class<?> findClass(final String name) throws ClassNotFoundException {
        var clazz = this.loadedClasses.get(name);
        if (clazz != null) {
            return clazz;
        }
        var path = name.replace('.', '/').concat(".class");
        // TODO: Find out if prioritizing our own classloader first is troublesome
        var resource = this.findResource(path);
        // var startTime = System.nanoTime(); TODO: used for internal performance tracking
        boolean runTransformers = true;
        byte[] classData = null; // TODO: make internal caching byte array?
        if (resource == null) {
            resource = this.getResource(path);
            if (resource == null) {
                // Check if any class generators accept this name
                for (var classGenerator : this.classGenerators) {
                    if (classGenerator.accept(name)) {
                        try {
                            classData = classGenerator.generateClass(Opcodes.ASM9, name);
                            runTransformers = classGenerator.acceptTransformers(name);
                            Bouncepad.logger().info("Class generator [{}] generated class [{}]", classGenerator.getClass().getName(), name);
                            break;
                        } catch (Throwable t) {
                            Bouncepad.logger().fatal("Class generator [{}] was unable to complete for [{}]", classGenerator.getClass().getName(), name);
                        }
                    }
                }
                if (classData == null) {
                    // TODO: should we cache the results to avoid duplicate resource checking calls?
                    if (this.renameTransformer == null) {
                        throw new ClassNotFoundException(name);
                    }
                    var transformedName = this.renameTransformer.remapClassName(name);
                    if (transformedName.equals(name)) {
                        throw new ClassNotFoundException(name);
                    } else {
                        return this.findClass(transformedName);
                    }
                }
            }
        }
        Manifest manifest = null;
        CodeSigner[] codeSigners = null;
        if (classData == null) {
            classData = new byte[4];
            try {
                var conn = resource.openConnection();
                try (var is = conn.getInputStream()) {
                    var buffer = new ByteArrayOutputStream();
                    int read;
                    while ((read = is.readNBytes(classData, 0, classData.length)) != 0) {
                        buffer.write(classData, 0, read);
                    }
                    classData = buffer.toByteArray();
                    // TODO: provide a way of providing mock jar information for classes?
                    if (conn instanceof JarURLConnection jarConnection) {
                        manifest = jarConnection.getManifest();
                        // Note: JarFile should NOT be null here
                        codeSigners = jarConnection.getJarFile().getJarEntry(path).getCodeSigners();
                    }
                }
            } catch (IOException e) {
                // TODO: Demote to logging + return null array?
                throw new ClassNotFoundException("Unable to establish connection to jar", e);
            }
        }
        var lastDivider = name.lastIndexOf('.');
        if (lastDivider != -1) {
            var pkgName = name.substring(0, lastDivider);
            // TODO: Package sealing, respect it to what extent?
            if (this.getAndVerifyPackage(pkgName, manifest, resource) == null) {
                try {
                    if (manifest != null && !name.startsWith("net.minecraft.")) {
                        this.definePackage(pkgName, manifest, resource);
                    } else {
                        this.definePackage(pkgName, null, null, null, null, null, null, null);
                    }
                } catch (IllegalArgumentException e) {
                    // We are a parallel-capable classloader - we have to verify for race-conditions
                    if (this.getAndVerifyPackage(pkgName, manifest, resource) == null) {
                        throw new AssertionError("Cannot find package " + pkgName);
                    }
                }
            }
        }
        // TODO: implement custom bytecode processing chain
        if (runTransformers) {
            classData = this.transformClassData(name, classData);
        }

        var codeSource = codeSigners == null ? null : new CodeSource(resource, codeSigners);
        clazz = this.defineClass(name, classData, 0, classData.length, codeSource);

        this.loadedClasses.put(name, clazz);
        return clazz;
    }

    protected byte[] transformClassData(String name, byte[] classData) {
        for (var exclusion : this.transformerExceptions) {
            if (name.startsWith(exclusion)) {
                if (DebugOption.EXPLICIT_LOGGING.isOn()) {
                    Bouncepad.logger().debug("Skip transforming [{}] due to it being excluded explicitly.", name);
                }
                return classData;
            }
        }
        if (DebugOption.SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS.isOn()) {
            this.saveClassToDisk(classData, name, BEFORE_ALL_TRANSFORMATIONS_SAVE_DIRECTORY);
        }
        if (DebugOption.EXPLICIT_LOGGING.isOn()) {
            var logger = Bouncepad.logger();
            logger.debug("Begin transformation of class: [{}] with [{}]-length byte array", name, classData == null ? "null" : classData.length);
            for (IClassTransformer transformer : this.transformers) {
                final var transformerName = transformer.getClass().getName();
                classData = transformer.transform(name, name, classData);
                logger.debug("[Transformer: {}]: After transformation of [{]], now results in a [{}]-length byte array", transformerName, name, classData == null ? "null" : classData.length);
                if (DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn()) {
                    this.saveClassToDisk(classData, name, getAfterEachTransformationSaveDirectory(transformer.getClass()));
                }
            }
        } else if (DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn()) {
            for (IClassTransformer transformer : this.transformers) {
                classData = transformer.transform(name, name, classData);
                this.saveClassToDisk(classData, name, getAfterEachTransformationSaveDirectory(transformer.getClass()));
            }
        } else {
            for (IClassTransformer transformer : this.transformers) {
                classData = transformer.transform(name, name, classData);
            }
        }
        if (DebugOption.SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS.isOn()) {
            this.saveClassToDisk(classData, name, AFTER_ALL_TRANSFORMATIONS_SAVE_DIRECTORY);
        }
        return classData;
    }

    // Copied from URLClassLoader#getAndVerifyPackage
    protected Package getAndVerifyPackage(String pkgName, Manifest manifest, URL resource) {
        var pkg = this.getDefinedPackage(pkgName);
        if (pkg.isSealed()) {
            // Verify that code source URL is the same
            if (!pkg.isSealed(resource)) {
                throw new SecurityException("sealing violation: package " + pkgName + " is sealed");
            }
        } else {
            // Make sure we are not attempting to seal the package at this code source URL
            if (manifest != null && this.isSealed(pkgName, manifest)) {
                throw new SecurityException("sealing violation: can't seal package " + pkgName + ": already loaded");
            }
        }
        return pkg;
    }

    // Also copied from URLClassLoader
    private boolean isSealed(String name, Manifest manifest) {
        var attr = SharedSecrets.javaUtilJarAccess().getTrustedAttributes(manifest, name.replace('.', '/').concat("/"));
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = manifest.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    private void saveClassToDisk(byte[] classData, String name, Path path) {
        if (DebugOption.EXPLICIT_LOGGING.isOn()) {
            Bouncepad.logger().debug("Saving transformed class [{}] to [{}]", name, path.toAbsolutePath().toString());
        }
        try (var os = Files.newOutputStream(path.resolve(name.replace('.', '/') + ".class"))) {
            os.write(classData);
        } catch (IOException e) {
            Bouncepad.logger().warn("Could not save transformed class [{}]", name, e);
        }
    }

}
