package com.obbpak.tool.pak;

import com.obbpak.tool.PakKeys;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Tencent SM4 variant (custom S-BOX, FK, CK) + all pak block ciphers.
 * Pure Java port of the reference implementation.
 */
public final class Sm4 {

    private static final int[] S_BOX = {
            0x34, 0x66, 0x25, 0x74, 0x89, 0x78, 0xE4, 0xA9, 0x5A, 0x41, 0xBC, 0x7A, 0xD6, 0x16, 0x21, 0x23,
            0x4D, 0x61, 0xDA, 0x94, 0x9B, 0xDF, 0x13, 0x3C, 0x69, 0x3A, 0x31, 0x0A, 0x5F, 0xD7, 0x99, 0x95,
            0xF1, 0xAE, 0x72, 0x3D, 0x07, 0x60, 0x24, 0xB6, 0x98, 0xEE, 0xC4, 0xA2, 0x2D, 0x88, 0xDD, 0x8D,
            0x04, 0xEA, 0xBB, 0x11, 0xCA, 0x3E, 0x5D, 0xA1, 0xF6, 0x3F, 0xB0, 0x97, 0x80, 0x47, 0x2B, 0xA6,
            0xE6, 0xF7, 0xD9, 0xB1, 0x59, 0xC0, 0x7C, 0xBE, 0x54, 0x28, 0xB7, 0x7E, 0x4F, 0xF8, 0x43, 0x6E,
            0xA0, 0x50, 0x0E, 0xF5, 0x90, 0xB8, 0xFB, 0xA3, 0x7B, 0x62, 0x19, 0x46, 0x03, 0x2A, 0xB9, 0x8F,
            0x9F, 0x77, 0xB4, 0x5B, 0x83, 0x87, 0x08, 0xEB, 0xE2, 0x1E, 0x42, 0xF0, 0x0F, 0xE8, 0x71, 0x6A,
            0x75, 0xAD, 0x55, 0x1F, 0xB5, 0xAB, 0x33, 0xFA, 0x7F, 0x15, 0xBD, 0x85, 0xD8, 0x06, 0x68, 0xB3,
            0x52, 0x30, 0x48, 0x0B, 0x00, 0xED, 0xEF, 0xB2, 0x57, 0x8E, 0xE7, 0x6C, 0xD5, 0xE5, 0x2E, 0x53,
            0x82, 0x05, 0xF9, 0x81, 0xF4, 0x56, 0xBF, 0x8C, 0x4B, 0xE3, 0xDB, 0x4A, 0x91, 0x4C, 0x2C, 0xD3,
            0x40, 0x29, 0x4E, 0x20, 0x14, 0x36, 0x79, 0x09, 0x6F, 0xD1, 0x37, 0xE0, 0x39, 0x0C, 0x8A, 0x92,
            0x38, 0x12, 0x35, 0x6D, 0xE1, 0xFD, 0x93, 0x9A, 0x17, 0xD4, 0xC9, 0x9C, 0x6B, 0x84, 0x26, 0x9D,
            0xAF, 0x76, 0xC1, 0x9E, 0xD0, 0x96, 0xC5, 0xCB, 0xE9, 0x73, 0x49, 0xD2, 0xCD, 0x64, 0xC3, 0xC7,
            0x01, 0x7D, 0xF3, 0xAC, 0xFC, 0xDE, 0xA4, 0x44, 0x32, 0x1B, 0xC2, 0xBA, 0x1C, 0x02, 0xC6, 0x27,
            0x45, 0x8B, 0xF2, 0x18, 0xA7, 0x10, 0x51, 0x1D, 0xC8, 0xCF, 0x63, 0xFF, 0x2F, 0x0D, 0x58, 0xCE,
            0x65, 0xA5, 0xDC, 0x1A, 0x3B, 0x86, 0xFE, 0x22, 0x5C, 0xA8, 0x5E, 0x67, 0xAA, 0xEC, 0x70, 0xCC
    };

    private static final int[] FK = {0x46970E9C, 0x4BC0685E, 0x59056186, 0xBCA2491E};

    private static final int[] CK = {
            0x000EB92B, 0x3A0AE783, 0x9E3B5C67, 0xADDBDABF, 0x7B7484CB, 0x49156C63, 0xC79AB5E7, 0x79EC9CFF,
            0x1725BEAB, 0x2FB89CA3, 0x24808AD7, 0xDDD28B1F, 0x4740DA4B, 0xBBC3EA73, 0x247B30E7, 0x91BE385F,
            0x0401248B, 0x45FCD3A3, 0x530B4CE7, 0xC68DD35F, 0xE3D16C2B, 0x4F698C13, 0x6B92C747, 0x769EFB1F,
            0x4C73BE9B, 0xC942B193, 0xAD80D827, 0x372FB33F, 0x13CB6AAB, 0x2BDC0AA3, 0x17A4A247, 0xD5E96CAF
    };

    public static final int BLOCK = 16;

    private final int[] rkey = new int[32];

    public Sm4(byte[] key) {
        if (key.length != 16) throw new IllegalArgumentException("SM4 key must be 16 bytes");
        int k0 = i32(key, 0) ^ FK[0];
        int k1 = i32(key, 4) ^ FK[1];
        int k2 = i32(key, 8) ^ FK[2];
        int k3 = i32(key, 12) ^ FK[3];
        for (int i = 0; i < 32; i += 4) {
            k0 ^= t1(k1 ^ k2 ^ k3 ^ CK[i]);
            rkey[i] = k0;
            k1 ^= t1(k2 ^ k3 ^ k0 ^ CK[i + 1]);
            rkey[i + 1] = k1;
            k2 ^= t1(k3 ^ k0 ^ k1 ^ CK[i + 2]);
            rkey[i + 2] = k2;
            k3 ^= t1(k0 ^ k1 ^ k2 ^ CK[i + 3]);
            rkey[i + 3] = k3;
        }
    }

    private static int bs(int x) {
        return ((S_BOX[(x >>> 24) & 0xff] << 24)
                | (S_BOX[(x >>> 16) & 0xff] << 16)
                | (S_BOX[(x >>> 8) & 0xff] << 8)
                | S_BOX[x & 0xff]);
    }

    private static int rol(int x, int n) {
        return Integer.rotateLeft(x, n);
    }

    private static int t0(int x) {
        x = bs(x);
        return x ^ rol(x, 2) ^ rol(x, 10) ^ rol(x, 18) ^ rol(x, 24);
    }

    private static int t1(int x) {
        x = bs(x);
        return x ^ rol(x, 13) ^ rol(x, 23);
    }

    private static int i32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    public void decryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        int x0 = i32(in, inOff);
        int x1 = i32(in, inOff + 4);
        int x2 = i32(in, inOff + 8);
        int x3 = i32(in, inOff + 12);
        for (int i = 0; i < 32; i += 4) {
            int rk = rkey[31 - i];
            int nx0 = x0 ^ t0(x1 ^ x2 ^ x3 ^ rk);
            int rk1 = rkey[30 - i];
            int nx1 = x1 ^ t0(nx0 ^ x2 ^ x3 ^ rk1);
            int rk2 = rkey[29 - i];
            int nx2 = x2 ^ t0(nx0 ^ nx1 ^ x3 ^ rk2);
            int rk3 = rkey[28 - i];
            int nx3 = x3 ^ t0(nx0 ^ nx1 ^ nx2 ^ rk3);
            x0 = nx0; x1 = nx1; x2 = nx2; x3 = nx3;
        }
        put(out, outOff, x3);
        put(out, outOff + 4, x2);
        put(out, outOff + 8, x1);
        put(out, outOff + 12, x0);
    }

    public void encryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        int x0 = i32(in, inOff);
        int x1 = i32(in, inOff + 4);
        int x2 = i32(in, inOff + 8);
        int x3 = i32(in, inOff + 12);
        for (int i = 0; i < 32; i += 4) {
            int nx0 = x0 ^ t0(x1 ^ x2 ^ x3 ^ rkey[i]);
            int nx1 = x1 ^ t0(nx0 ^ x2 ^ x3 ^ rkey[i + 1]);
            int nx2 = x2 ^ t0(nx0 ^ nx1 ^ x3 ^ rkey[i + 2]);
            int nx3 = x3 ^ t0(nx0 ^ nx1 ^ nx2 ^ rkey[i + 3]);
            x0 = nx0; x1 = nx1; x2 = nx2; x3 = nx3;
        }
        put(out, outOff, x3);
        put(out, outOff + 4, x2);
        put(out, outOff + 8, x1);
        put(out, outOff + 12, x0);
    }

    private static void put(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    // ================= pak-level crypto =================

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** EM_SIMPLE1: single-byte XOR. */
    public static byte[] decryptSimple1(byte[] ct, PakKeys keys) {
        byte[] out = new byte[ct.length];
        for (int i = 0; i < ct.length; i++) out[i] = (byte) (ct[i] ^ keys.simple1Key);
        return out;
    }

    public static byte[] encryptSimple1(byte[] pt, PakKeys keys) {
        return decryptSimple1(pt, keys);
    }

    /** EM_SIMPLE2: chained word XOR with 4-byte key. */
    public static byte[] decryptSimple2(byte[] ct, PakKeys keys) {
        byte[] key = keys.simple2Key();
        int ik = le32(key, 0);
        long state = ik & 0xFFFFFFFFL;
        byte[] out = new byte[ct.length];
        for (int i = 0; i + 4 <= ct.length; i += 4) {
            int w = le32(ct, i);
            state = (state ^ (w & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            putLe(out, i, (int) state);
        }
        return out;
    }

    public static byte[] encryptSimple2(byte[] pt, PakKeys keys) {
        byte[] key = keys.simple2Key();
        int ik = le32(key, 0);
        long ks = ik & 0xFFFFFFFFL;
        byte[] out = new byte[pt.length];
        for (int i = 0; i + 4 <= pt.length; i += 4) {
            int w = le32(pt, i);
            int cw = (int) (((w & 0xFFFFFFFFL) ^ ks) & 0xFFFFFFFFL);
            putLe(out, i, cw);
            ks = w & 0xFFFFFFFFL;
        }
        return out;
    }

    /** Derives SM4 file key from lowercase filename stem + per-method secret. */
    public static byte[] deriveSm4Key(String fileNameNoExt, int encMethod, PakKeys keys) {
        String secret = keys.sm4SecretFor(encMethod);
        String input = fileNameNoExt.toLowerCase() + secret;
        byte[] d = sha1(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Arrays.copyOf(d, 16);
    }

    public static byte[] decryptSm4(byte[] ct, String nameStem, int encMethod, PakKeys keys) {
        Sm4 sm4 = new Sm4(deriveSm4Key(nameStem, encMethod, keys));
        byte[] out = new byte[ct.length];
        for (int i = 0; i + BLOCK <= ct.length; i += BLOCK) {
            sm4.decryptBlock(ct, i, out, i);
        }
        return out;
    }

    public static byte[] encryptSm4(byte[] pt, String nameStem, int encMethod, PakKeys keys) {
        Sm4 sm4 = new Sm4(deriveSm4Key(nameStem, encMethod, keys));
        byte[] out = new byte[pt.length];
        for (int i = 0; i + BLOCK <= pt.length; i += BLOCK) {
            sm4.encryptBlock(pt, i, out, i);
        }
        return out;
    }

    /** RSA public-key extraction used to recover index AES key/iv/hash-check value. */
    public static byte[] rsaExtract(byte[] signature, byte[] modulus) {
        java.math.BigInteger c = new java.math.BigInteger(1, reverse(signature));
        java.math.BigInteger n = new java.math.BigInteger(1, reverse(modulus));
        byte[] m = c.modPow(java.math.BigInteger.valueOf(65537), n).toByteArray();
        byte[] fixed = new byte[256];
        // value -> 256-byte little-endian
        for (int i = 0; i < m.length && i < 256; i++) {
            fixed[i] = m[m.length - 1 - i];
        }
        // strip trailing zero bytes (high end in LE), like python rstrip(b'\x00')
        int len = fixed.length;
        while (len > 0 && fixed[len - 1] == 0) len--;
        byte[] stripped = Arrays.copyOf(fixed, len);
        stripped = padToN(stripped, 4);
        return meowmeow(stripped);
    }

    private static byte[] padToN(byte[] data, int n) {
        int padding = n - data.length % n;
        if (padding == n) return data;
        return Arrays.copyOf(data, data.length + padding);
    }

    private static byte[] xorRepeat(byte[] buf, byte[] key) {
        byte[] out = new byte[buf.length];
        for (int i = 0; i < buf.length; i++) out[i] = (byte) (buf[i] ^ key[i % key.length]);
        return out;
    }

    private static byte[] hashHash(byte[] buf, int n) {
        byte[] result = new byte[Math.max(n, 0)];
        int filled = 0;
        while (filled < n) {
            byte[] h = sha1(buf);
            int copy = Math.min(h.length, n - filled);
            System.arraycopy(h, 0, result, filled, copy);
            filled += copy;
        }
        return result;
    }

    private static byte[] meowmeow(byte[] buffer) {
        if (buffer.length < 43) return new byte[0];
        byte[] x1 = slice(buffer, 1, 1 + 20);
        byte[] x2 = slice(buffer, 21, buffer.length);
        x1 = xorRepeat(x1, hashHash(x2, x1.length));
        x2 = xorRepeat(x2, hashHash(x1, x2.length));
        byte[] part1 = slice(x2, 0, 20);
        byte[] m = slice(x2, 20, x2.length);
        if (!java.util.Arrays.equals(part1, sha1(new byte[20]))) return new byte[0];
        int idx = -1;
        for (int i = 0; i < m.length; i++) {
            if (m[i] != 0) { idx = i; break; }
        }
        if (idx < 0) return new byte[0];
        return slice(m, 1 + idx, m.length);
    }

    private static byte[] slice(byte[] b, int start, int end) {
        return Arrays.copyOfRange(b, Math.min(start, b.length), Math.min(end, b.length));
    }

    static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    static void putLe(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    static byte[] reverse(byte[] a) {
        byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i];
        return r;
    }
}
