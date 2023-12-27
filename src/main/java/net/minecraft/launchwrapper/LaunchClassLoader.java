package net.minecraft.launchwrapper;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Level;

@Deprecated(since = "0.5")
public abstract class LaunchClassLoader extends URLClassLoader {

    protected final List<URL> sources;
    protected final List<IClassTransformer> transformers = new ArrayList<>(2);

    @Deprecated(forRemoval = true)
    protected Set<String> classLoaderExceptions = new HashSet<>();
    @Deprecated(forRemoval = true)
    protected Set<String> transformerExceptions = new HashSet<>();

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

    @Deprecated(forRemoval = true)
    public void registerTransformer(String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).newInstance();
            transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && renameTransformer == null) {
                renameTransformer = (IClassNameTransformer) transformer;
            }
        } catch (Exception e) {
            LogWrapper.log(Level.ERROR, e, "A critical problem occurred registering the ASM transformer class %s", transformerClassName);
        }
    }

    public List<URL> getSources() {
        return sources;
    }

    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(transformers);
    }

    public void addClassLoaderExclusion(String toExclude) {
        classLoaderExceptions.add(toExclude);
    }

    public void addTransformerExclusion(String toExclude) {
        transformerExceptions.add(toExclude);
    }

    public byte[] getClassBytes(String name) throws IOException {
        throw new UnsupportedOperationException("");
    }

}
