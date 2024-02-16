package net.minecraft.launchwrapper;

import java.io.*;
import java.net.*;
import java.util.*;

import com.cleanroommc.bouncepad.Bouncepad;
import com.cleanroommc.bouncepad.debug.DebugOption;

@Deprecated(since = "0.5")
public abstract class LaunchClassLoader extends URLClassLoader {

    protected final List<URL> sources;
    protected final List<IClassTransformer> transformers = new ArrayList<>(2);
    protected final Set<String> transformerExceptions = new HashSet<>();

    protected IClassNameTransformer renameTransformer;

    private static List<URL> getOriginClassPathURLs() {
        // Same classpaths present in AppClassLoader
        String[] classpaths = System.getProperty("java.class.path").split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        try {
            for (String classpath : classpaths) {
                urls.add(new File(classpath).toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return urls;
    }

    protected LaunchClassLoader(ClassLoader parentClassLoader) {
        super(new URL[0], parentClassLoader);
        this.sources = getOriginClassPathURLs();
    }

    @Deprecated
    public LaunchClassLoader(URL[] sources) {
        super(sources, null);
        this.sources = new ArrayList<>(List.of(sources));

        // classloader exclusions
        addClassLoaderExclusion("java.");
        addClassLoaderExclusion("javax."); // Scripting Module
        addClassLoaderExclusion("jdk.dynalink."); // Nashorn
        addClassLoaderExclusion("sun.");
        addClassLoaderExclusion("org.lwjgl.");
        addClassLoaderExclusion("org.apache.logging.");
        addClassLoaderExclusion("net.minecraft.launchwrapper.");

        // transformer exclusions
        addTransformerExclusion("javax.");
        addTransformerExclusion("argo.");
        addTransformerExclusion("org.objectweb.asm.");
        addTransformerExclusion("com.google.common.");
        addTransformerExclusion("org.bouncycastle.");
        addTransformerExclusion("net.minecraft.launchwrapper.injector.");

    }

    public void registerTransformer(String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).newInstance();
            this.transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && renameTransformer == null) {
                this.renameTransformer = (IClassNameTransformer) transformer;
            }
        } catch (Exception e) {
            Bouncepad.logger().error("Critical problem occurred when registering transfomer [{}]", transformerClassName, e);
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        this.sources.add(url);
    }

    public List<URL> getSources() {
        return sources;
    }

    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(transformers);
    }

    @Deprecated
    public void addClassLoaderExclusion(String toExclude) {
        Bouncepad.logger().warn("LaunchClassLoader#addClassLoaderExclusion is deprecated, calling addTransformerExclusion.");
        this.transformerExceptions.add(toExclude);
    }

    @Deprecated
    public void addTransformerExclusion(String toExclude) {
        this.transformerExceptions.add(toExclude);
        Bouncepad.logger().warn("Added [{}] to transformer exclusions.", toExclude);
    }

    public byte[] getClassBytes(String name) throws IOException {
        var path = name.replace('.', '/').concat(".class");
        var resource = this.findResource(path);
        if (resource == null) {
            resource = this.getResource(path);
            if (resource == null) {
                if (DebugOption.EXPLICIT_LOGGING.isOn()) {
                    Bouncepad.logger().debug("Cannot find resource of class: [{}]", name);
                }
                if (this.renameTransformer == null) {
                    return null;
                }
                var transformedName = this.renameTransformer.remapClassName(name);
                if (transformedName.equals(name)) {
                    return null;
                } else {
                    return this.getClassBytes(transformedName);
                }
            }
        }
        var classData = new byte[4];
        var conn = resource.openConnection();
        try (var is = conn.getInputStream()) {
            var buffer = new ByteArrayOutputStream();
            int read;
            while ((read = is.readNBytes(classData, 0, classData.length)) != 0) {
                buffer.write(classData, 0, read);
            }
            classData = buffer.toByteArray();
        }
        if (DebugOption.EXPLICIT_LOGGING.isOn()) {
            Bouncepad.logger().debug("Loading [{}]'s byte array from resource: [{}]", name, resource);
        }
        return classData;
    }

    @Deprecated(since = "0.6")
    public void clearNegativeEntries(Set<String> entriesToClear) {
        Bouncepad.logger().warn("LaunchClassLoader#clearNegativeEntries is deprecated and has no effect as of Bouncepad 0.6.");
    }

}
