package com.obbpak.tool;

public final class LuaCompiler {

    private LuaCompiler() {}

    public static byte[] compile(byte[] source, String chunkName, boolean strip) {
        return nativeCompile(source, chunkName, strip);
    }

    /** sizeof(size_t) of the native build that produced our dumps. */
    public static int sizeT() {
        return nativeSizeT();
    }

    private static native byte[] nativeCompile(byte[] source, String chunkName, boolean strip);

    private static native int nativeSizeT();

    static {
        System.loadLibrary("luajni");
    }
}
