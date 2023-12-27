package com.cleanroommc.bouncepad;

import jdk.internal.access.SharedSecrets;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.LogWrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

// TODO: implement logging w/ DebugOptions
public class BouncepadClassLoader extends LaunchClassLoader {

    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(4096);

    BouncepadClassLoader() {
        this(BouncepadClassLoader.class.getClassLoader());
    }

    public BouncepadClassLoader(ClassLoader parentClassLoader) {
        super(parentClassLoader);
        this.prepareDebugFolders();
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        this.sources.add(url);
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
        if (resource == null) {
            resource = this.getResource(path);
            if (resource == null) {
                // TODO: should we cache the results to avoid duplicate resource checking calls?
                throw new ClassNotFoundException(name);
            }
        }
        // var startTime = System.nanoTime(); TODO: used for internal performance tracking
        var classData = new byte[4]; // TODO: make internal caching byte array?
        Manifest manifest = null;
        CodeSigner[] codeSigners = null;
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
        var lastDivider = name.lastIndexOf('.');
        if (lastDivider != -1) {
            var pkgName = name.substring(0, lastDivider);
            // TODO: Package sealing, respect it to what extent?
            if (this.getAndVerifyPackage(pkgName, manifest, resource) == null) {
                try {
                    if (manifest != null) {
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
        classData = this.transformClassData(name, classData);

        var codeSource = codeSigners == null ? null : new CodeSource(resource, codeSigners);
        clazz = this.defineClass(name, classData, 0, classData.length, codeSource);

        this.loadedClasses.put(name, clazz);
        return clazz;
    }

    protected byte[] transformClassData(String name, byte[] classData) {
        for (IClassTransformer transformer : this.transformers) {
            classData = transformer.transform(name, name, classData);
        }
        return classData;
    }

    // Copied from URLClassLoader#getAndVerifyPackage
    protected Package getAndVerifyPackage(String pkgName, Manifest manifest, URL url) {
        var pkg = this.getDefinedPackage(pkgName);
        if (pkg.isSealed()) {
            // Verify that code source URL is the same.
            if (!pkg.isSealed(url)) {
                throw new SecurityException("sealing violation: package " + pkgName + " is sealed");
            }
        } else {
            // Make sure we are not attempting to seal the package
            // at this code source URL.
            if (manifest != null && this.isSealed(pkgName, manifest)) {
                throw new SecurityException("sealing violation: can't seal package " + pkgName + ": already loaded");
            }
        }
        return pkg;
    }

    private void prepareDebugFolders() {
        if (DebugOption.SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS.isOn() ||
                DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn() ||
                DebugOption.SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS.isOn()) {
            File saveTransformationFolder = new File(Bouncepad.minecraftHome, "save_transformations");
            saveTransformationFolder.mkdirs();
            LogWrapper.info("Transformation related debug options enabled, saving classes to \"%s\"",
                    saveTransformationFolder.getAbsolutePath().replace('\\', '/'));
            File beforeFolder = new File(saveTransformationFolder, "before_all");
            beforeFolder.mkdirs();
            File afterEachFolder = new File(saveTransformationFolder, "after_each");
            afterEachFolder.mkdirs();
            File afterAllFolder = new File(saveTransformationFolder, "after_all");
            afterAllFolder.mkdirs();
        }
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

}
