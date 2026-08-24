package com.obbpak.tool;

public final class PakKeysHolder {

    private static volatile PakKeys instance = new PakKeys();

    private PakKeysHolder() {}

    public static PakKeys get() {
        return instance;
    }

    public static void set(PakKeys keys) {
        if (keys != null) instance = keys;
    }
}
