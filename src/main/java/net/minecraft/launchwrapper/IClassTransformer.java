package net.minecraft.launchwrapper;

@Deprecated(since = "0.5")
public interface IClassTransformer {

    byte[] transform(String name, String transformedName, byte[] classBytes);

}
