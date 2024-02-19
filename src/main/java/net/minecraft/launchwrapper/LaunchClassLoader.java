package net.minecraft.launchwrapper;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;

@Deprecated(since = "0.5")
public class LaunchClassLoader extends URLClassLoader {

    public LaunchClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    /**
     * Lifted visibility of {@link URLClassLoader#addURL(URL)}
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    public List<URL> getSources() {
        return List.of(this.getURLs());
    }

    // Keep binary compatibility
    public void registerTransformer(String transformerName) {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers a standard transformation chain.");
    }

    // Keep binary compatibility
    public List<IClassTransformer> getTransformers() {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers a standard transformation chain.");
    }

    // Keep binary compatibility
    public void addClassLoaderExclusion(String toExclude) {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers a classloader exclusions.");
    }

    // Keep binary compatibility
    public void addTransformerExclusion(String toExclude) {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers a transformer exclusions.");
    }

    // Keep binary compatibility
    public byte[] getClassBytes(String name) {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers class to bytes helper.");
    }

    // Keep binary compatibility
    public void clearNegativeEntries(Set<String> entriesToClear) {
        throw new UnsupportedOperationException("LaunchClassLoader no longer offers negative entries.");
    }

}
