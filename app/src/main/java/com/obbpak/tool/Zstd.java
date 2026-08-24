package com.obbpak.tool;

import java.io.IOException;

public final class Zstd {

    private Zstd() {}

    public static byte[] decompress(byte[] src) throws IOException {
        return decompress(src, null);
    }

    public static byte[] decompress(byte[] src, byte[] dict) throws IOException {
        if (dict != null && dict.length > 0) return nativeDecompressDict(src, dict);
        return nativeDecompress(src);
    }

    public static byte[] compress(byte[] src, int level) throws IOException {
        return compress(src, level, null);
    }

    public static byte[] compress(byte[] src, int level, byte[] dict) throws IOException {
        if (dict != null && dict.length > 0) return nativeCompressDict(src, level, dict);
        return nativeCompress(src, level);
    }

    private static native byte[] nativeDecompress(byte[] src);

    private static native byte[] nativeDecompressDict(byte[] src, byte[] dict);

    private static native byte[] nativeCompress(byte[] src, int level);

    private static native byte[] nativeCompressDict(byte[] src, int level, byte[] dict);
}
