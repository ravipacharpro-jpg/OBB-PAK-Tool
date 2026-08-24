package com.obbpak.tool;

public final class LuaCompiler {

    private LuaCompiler() {}

    public static byte[] compile(byte[] source, String chunkName, boolean strip) {
        return nativeCompile(source, chunkName, strip);
    }

    private static native byte[] nativeCompile(byte[] source, String chunkName, boolean strip);

    static {
        System.loadLibrary("luajni");
    }
}
