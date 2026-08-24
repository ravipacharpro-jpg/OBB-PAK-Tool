package com.obbpak.tool;

public final class XorTools {

    private XorTools() {}

    public static byte[] xor(byte[] data, byte[] key) {
        if (key == null || key.length == 0) return data;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }

    public static byte[] parseKey(String input) throws IllegalArgumentException {
        String s = input.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Key is empty");
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.matches("([0-9A-Fa-f]{2}[ ,:]?)+")) {
            StringBuilder hex = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (Character.isLetterOrDigit(c)) hex.append(c);
            }
            try {
                int len = hex.length() / 2;
                byte[] key = new byte[len];
                for (int i = 0; i < len; i++) {
                    key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return key;
            } catch (Exception ignored) {}
        }
        return s.getBytes();
    }
}
