package net.minecraft.launchwrapper;

@Deprecated(since = "0.5")
public interface IClassNameTransformer {

    String unmapClassName(String name);

    String remapClassName(String name);

}
