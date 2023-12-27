package net.minecraft.launchwrapper;

import java.io.*;
import java.net.*;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import com.cleanroommc.bouncepad.Bouncepad;
import com.cleanroommc.bouncepad.DebugOption;
import org.apache.logging.log4j.Level;

@Deprecated(since = "0.5")
public abstract class LaunchClassLoader extends URLClassLoader {

    public static final int BUFFER_SIZE = 1 << 12;

    protected final List<URL> sources;
    private final ThreadLocal<byte[]> loadBuffer = new ThreadLocal<>();

    protected List<IClassTransformer> transformers = new ArrayList<>(2);
    private Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private Set<String> invalidClasses = new HashSet<>(1000);

    private Set<String> classLoaderExceptions = new HashSet<>();
    private Set<String> transformerExceptions = new HashSet<>();
    private Map<String,byte[]> resourceCache = new ConcurrentHashMap<>(1000);
    private Set<String> negativeResourceCache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected IClassNameTransformer renameTransformer;

    private static final String[] RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
            "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

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

    // TODO: Removal
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

    private byte[] runTransformers(final String name, final String transformedName, byte[] basicClass) {
        if (DebugOption.SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS.isOn()) {
            saveClass(basicClass, transformedName, new File(Bouncepad.minecraftHome, "save_transformations" + File.separator + "before_all"));
        }
        if (DebugOption.EXPLICIT_LOGGING.isOn()) {
            LogWrapper.finest("Beginning transform of {%s (%s)} Start Length: %d", name, transformedName, (basicClass == null ? 0 : basicClass.length));
            for (final IClassTransformer transformer : transformers) {
                final String transName = transformer.getClass().getName();
                LogWrapper.finest("Before transformer {%s (%s)} %s: %d", name, transformedName, transName, (basicClass == null ? 0 : basicClass.length));
                basicClass = transformer.transform(name, transformedName, basicClass);
                if (DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn()) {
                    saveClass(basicClass, transformedName, new File(Bouncepad.minecraftHome,
                            "save_transformations" + File.separator + "after_each" + File.separator + transformer.getClass().getName().replace('.', File.separatorChar)));
                }
                LogWrapper.finest("After transformer {%s (%s)} %s: %d", name, transformedName, transName, (basicClass == null ? 0 : basicClass.length));
            }
            LogWrapper.finest("Ending transform of {%s (%s)} Start Length: %d", name, transformedName, (basicClass == null ? 0 : basicClass.length));
        } else if (DebugOption.SAVE_CLASS_AFTER_EACH_TRANSFORMATION.isOn()) {
            for (final IClassTransformer transformer : transformers) {
                basicClass = transformer.transform(name, transformedName, basicClass);
                saveClass(basicClass, transformedName, new File(Bouncepad.minecraftHome,
                        "save_transformations" + File.separator + "after_each" + File.separator + transformer.getClass().getName().replace('.', File.separatorChar)));
            }
        } else {
            for (final IClassTransformer transformer : transformers) {
                basicClass = transformer.transform(name, transformedName, basicClass);
            }
        }
        if (DebugOption.SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS.isOn()) {
            saveClass(basicClass, transformedName, new File(Bouncepad.minecraftHome, "save_transformations" + File.separator + "after_all"));
        }
        return basicClass;
    }

    public List<URL> getSources() {
        return sources;
    }

    private byte[] readFully(InputStream stream) {
        try {
            byte[] buffer = getOrCreateBuffer();

            int read;
            int totalLength = 0;
            while ((read = stream.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
                totalLength += read;

                // Extend our buffer
                if (totalLength >= buffer.length - 1) {
                    byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }
            }

            final byte[] result = new byte[totalLength];
            System.arraycopy(buffer, 0, result, 0, totalLength);
            return result;
        } catch (Throwable t) {
            LogWrapper.log(Level.WARN, t, "Problem loading class");
            return new byte[0];
        }
    }

    private byte[] getOrCreateBuffer() {
        byte[] buffer = loadBuffer.get();
        if (buffer == null) {
            loadBuffer.set(new byte[BUFFER_SIZE]);
            buffer = loadBuffer.get();
        }
        return buffer;
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
        if (negativeResourceCache.contains(name)) {
            return null;
        } else if (resourceCache.containsKey(name)) {
            return resourceCache.get(name);
        }
        if (name.indexOf('.') == -1) {
            for (final String reservedName : RESERVED_NAMES) {
                if (name.toUpperCase(Locale.ENGLISH).startsWith(reservedName)) {
                    final byte[] data = getClassBytes("_" + name);
                    if (data != null) {
                        resourceCache.put(name, data);
                        return data;
                    }
                }
            }
        }

        InputStream classStream = null;
        try {
            final String resourcePath = name.replace('.', '/').concat(".class");
            final URL classResource = findResource(resourcePath);

            if (classResource == null) {
                if (DebugOption.EXPLICIT_LOGGING.isOn()) {
                    LogWrapper.finest("Failed to find class resource %s", resourcePath);
                }
                negativeResourceCache.add(name);
                return null;
            }
            classStream = classResource.openStream();

            if (DebugOption.EXPLICIT_LOGGING.isOn()) {
                LogWrapper.finest("Loading class %s from resource %s", name, classResource.toString());
            }
            final byte[] data = readFully(classStream);
            resourceCache.put(name, data);
            return data;
        } finally {
            closeSilently(classStream);
        }
    }

    private static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void clearNegativeEntries(Set<String> entriesToClear) {
        negativeResourceCache.removeAll(entriesToClear);
    }

}
