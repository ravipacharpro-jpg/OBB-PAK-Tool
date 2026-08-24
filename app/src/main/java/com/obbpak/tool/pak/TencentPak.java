package com.obbpak.tool.pak;

import com.obbpak.tool.PakKeys;
import com.obbpak.tool.PakKeysHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reader for Tencent/UE-style PAK archives as used by PUBG Mobile.
 * Streams via RandomAccessFile so huge paks never fully load into RAM.
 */
public class TencentPak {

    public static final int CM_NONE = 0, CM_ZLIB = 1, CM_ZSTD = 6, CM_ZSTD_DICT = 8;

    public static final class Entry {
        public long offset;
        public long uncompressedSize;
        public int compressionMethod;
        public long size;
        public final List<long[]> blocks = new ArrayList<>();
        public int compressionBlockSize;
        public boolean encrypted;
        public int encryptionMethod;
        public String dir;
        public String name;
    }

    public static class PakInfo {
        public boolean indexEncrypted;
        public int version;
        public byte[] indexHash = new byte[0];
        public long indexSize;
        public long indexOffset;
        public byte[] packedKey = new byte[0];
        public byte[] packedIv = new byte[0];
    }

    protected final RandomReader reader;
    protected final long fileLen;
    protected final PakKeys keys;
    public PakInfo info = new PakInfo();
    public String mountPoint = "";
    public final List<Entry> files = new ArrayList<>();
    public final Map<String, Map<String, Entry>> dirs = new LinkedHashMap<>();
    public byte[] zstdDict = null;

    private static final int FOOTER_MAGIC = 0x5A6F12C1;

    public TencentPak(RandomReader reader, String pakName) throws IOException {
        this.reader = reader;
        this.fileLen = reader.length();
        this.keys = PakKeysHolder.get();
        readPakInfo();
        loadIndex();
    }

    // ---------------- little-endian helpers ----------------

    static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    static long le64(byte[] b, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[off + i] & 0xFFL);
        return v;
    }

    // ---------------- footer ----------------

    private static int memSize(int version) {
        return 45 + (version >= 7 ? 32 : 0) + (version >= 8 ? 768 : 0)
                + (version >= 9 ? 8 : 0) + (version >= 12 ? 20 : 0);
    }

    private void readPakInfo() throws IOException {
        long[] ks = keys.keystream();

        int maxTotal = memSize(12);
        if (fileLen < maxTotal + 64) throw new IOException("PAK too small / corrupted footer");

        byte[] tail = new byte[maxTotal];
        reader.readFully(fileLen - maxTotal, tail, 0, maxTotal);

        // Probe every known layout (with/without tencent extras) and accept the
        // one whose decoded version + index bounds are plausible. Handles both
        // old v8-v12 paks (RSA blobs) and newer v13/v14 base-only footers.
        int bestStart = -1;
        int bestSize = -1;
        int bestVersion = -1;
        long bestIsz = -1, bestIoff = -1;
        int[] layouts = {45, memSize(7), memSize(8), memSize(9), memSize(12)};
        for (int size : layouts) {
            int p = maxTotal - size;
            if (p < 0) continue;
            try {
                int off = p;
                if (size >= memSize(7)) off += 32;
                if (size >= memSize(8)) off += 768;
                if (size >= memSize(9)) off += 8;
                int verOff = off + 5;
                if (verOff + 4 > maxTotal) continue;
                int ver = le32(tail, verOff);
                if (ver < 1 || ver > 20) continue;
                int hEnd = verOff + 4 + (ver >= 6 ? 20 : 0);
                long iszRaw = le64(tail, hEnd);
                long ioffRaw = le64(tail, hEnd + 8);
                long isz = iszRaw ^ ((ks[10] << 32) | (ks[11] & 0xFFFFFFFFL));
                long ioff = ioffRaw ^ ((ks[0] << 32) | (ks[1] & 0xFFFFFFFFL));
                long footerStart = fileLen - size;
                if (ioff < 1 || isz < 16) continue;
                if (ioff + isz > footerStart) continue;
                if (footerStart - (ioff + isz) > 65536) continue;
                bestStart = p;
                bestSize = size;
                bestVersion = ver;
                bestIsz = isz;
                bestIoff = ioff;
                break;
            } catch (RuntimeException ignored) {
            }
        }
        if (bestStart < 0) throw new IOException("Unsupported or corrupted PAK footer");

        int start = bestStart;
        int p = start;

        boolean hasExtras = bestSize >= memSize(7);
        if (hasExtras && bestVersion >= 7) p += 32;
        if (hasExtras && bestVersion >= 8) {
            info.packedKey = Arrays.copyOfRange(tail, p, p + 256); p += 256;
            info.packedIv = Arrays.copyOfRange(tail, p, p + 256); p += 256;
            p += 256; // packed index hash
        }
        if (hasExtras && bestVersion >= 9) p += 8;   // stem hash + unk

        info.version = bestVersion;
        int encFlag = tail[p] ^ (int) (ks[3] & 0xFF);
        info.indexEncrypted = (encFlag & 0xFF) == 1;
        p += 1;
        if (info.version >= 6) {
            p += 4; // skip xored magic (magic constant varies per game build)
            byte[] h = Arrays.copyOfRange(tail, p, p + 20); p += 20;
            info.indexHash = xorKey(h, ks, 4, 5);
        } else {
            p += 4;
        }
        info.indexSize = bestIsz;
        info.indexOffset = bestIoff;

        if (info.version <= 3) info.indexEncrypted = false;
    }

    private static byte[] xorKey(byte[] data, long[] ks, int ksOff, int nDwords) {
        byte[] key = new byte[nDwords * 4];
        for (int i = 0; i < nDwords; i++) {
            Sm4.putLe(key, i * 4, (int) ks[ksOff + i]);
        }
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key[i % key.length]);
        return out;
    }

    // ---------------- index ----------------

    private void loadIndex() throws IOException {
        if (info.indexOffset < 0 || info.indexSize < 0
                || info.indexOffset > fileLen || info.indexSize > fileLen - info.indexOffset) {
            throw new IOException("Index offset/size out of bounds — wrong keys?");
        }
        byte[] idx = new byte[(int) info.indexSize];
        reader.readFully(info.indexOffset, idx, 0, idx.length);

        if (info.indexEncrypted) idx = decryptIndex(idx);

        Reader ir = new Reader(idx);
        mountPoint = cleanMount(ir.str());
        int nFiles = ir.u32();
        files.clear();
        for (int i = 0; i < nFiles; i++) {
            Entry e = new Entry();
            ir.skip(20); // content hash
            e.offset = ir.u64();
            e.uncompressedSize = ir.u64();
            e.compressionMethod = ir.u32() & 0xF;
            e.size = ir.u64();
            if (info.version >= 5) { ir.u8(); ir.skip(20); }
            if (e.compressionMethod != CM_NONE && info.version >= 3) {
                int nb = ir.u32();
                for (int b = 0; b < nb; b++) {
                    long s = ir.u64(), en = ir.u64();
                    e.blocks.add(new long[]{s, en});
                }
            } else {
                e.compressionMethod = e.compressionMethod == CM_NONE ? CM_NONE : e.compressionMethod;
            }
            if (info.version >= 4) e.compressionBlockSize = ir.u32();
            if (info.version >= 4) e.encrypted = ir.u8() == 1;
            if (info.version >= 12) e.encryptionMethod = ir.u32();
            if (info.version >= 12) ir.u32(); // index_new_sep
            files.add(e);
        }
        long nDirs = ir.u64();
        dirs.clear();
        zstdDict = null;
        for (long d = 0; d < nDirs; d++) {
            String dirPath = cleanMount(ir.str());
            int nEnts = (int) ir.u64();
            Map<String, Entry> m = new LinkedHashMap<>();
            for (int k = 0; k < nEnts; k++) {
                String fname = ir.str();
                int ref = ir.i32();
                int realIdx = files.size() - ref - 1;
                if (realIdx < 0 || realIdx >= files.size()) continue;
                Entry e = files.get(realIdx);
                e.dir = dirPath;
                e.name = fname;
                m.put(fname, e);
            }
            if ("zstddic".equals(lastSegment(dirPath)) && !m.isEmpty()) {
                try {
                    loadZstdDict(m.values().iterator().next());
                } catch (Exception ignored) {
                }
            } else {
                dirs.put(dirPath, m);
            }
        }
    }

    private void loadZstdDict(Entry e) throws IOException {
        if (e.encrypted || e.compressionMethod != CM_NONE) return;
        long sz = Math.min(e.size, 64L * 1024 * 1024);
        byte[] raw = new byte[(int) sz];
        reader.readFully(e.offset, raw, 0, raw.length);
        int p = 0;
        long dictSize = le64(raw, p); p += 8;
        p += 4;
        int declared = le32(raw, p); p += 4;
        if (declared != (int) dictSize || dictSize <= 0 || dictSize > raw.length - p) return;
        zstdDict = Arrays.copyOfRange(raw, p, p + (int) dictSize);
    }

    private static String lastSegment(String path) {
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String cleanMount(String mp) {
        StringBuilder sb = new StringBuilder();
        for (String part : mp.replace('\\', '/').split("/")) {
            if (part.isEmpty() || "..".equals(part)) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(part);
        }
        return sb.toString();
    }

    private byte[] decryptIndex(byte[] ct) throws IOException {
        if (info.version > 7 && info.packedKey.length == 256 && info.packedIv.length == 256) {
            byte[] key = Sm4.rsaExtract(info.packedKey, keys.rsaMod1());
            byte[] ivFull = Sm4.rsaExtract(info.packedIv, keys.rsaMod1());
            if (key.length < 16 || ivFull.length < 16) throw new IOException("RSA index key extraction failed");
            try {
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(ivFull, 0, 16));
                return c.doFinal(ct);
            } catch (Exception ex) {
                throw new IOException("AES index decrypt failed: " + ex.getMessage());
            }
        }
        // v13/v14 base-only paks carry no RSA blobs; SIMPLE1 is the fallback
        return Sm4.decryptSimple1(ct, keys);
    }

    // ---------------- content ----------------

    /** Reads, decrypts and decompresses one entry fully. */
    public byte[] readEntry(Entry e) throws IOException {
        String stem = fileStem(e.name);
        int encM = e.encryptionMethod;
        if (e.compressionMethod == CM_NONE) {
            int need = alignEncrypted((int) Math.min(e.size, Integer.MAX_VALUE), encM, e.encrypted);
            byte[] data = readAt(e.offset, (int) Math.min(e.size, Integer.MAX_VALUE));
            if (e.encrypted) data = decryptBlock(data, stem, encM);
            return trim(data, (int) Math.min(e.uncompressedSize, data.length));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                (int) Math.min(Math.max(e.uncompressedSize, 1), 256L * 1024 * 1024));
        int n = e.blocks.size();
        for (int x : blockIndices(n, encM, e.encrypted)) {
            long[] blk = e.blocks.get(x);
            int slot = (int) (blk[1] - blk[0]);
            byte[] data = readAt(blk[0], alignEncrypted(slot, encM, e.encrypted));
            if (e.encrypted) data = decryptBlock(data, stem, encM);
            out.write(decompressByMethod(data, e.compressionMethod));
        }
        return out.toByteArray();
    }

    byte[] decompressByMethod(byte[] data, int method) throws IOException {
        switch (method) {
            case CM_ZLIB:
                java.util.zip.Inflater inf = new java.util.zip.Inflater();
                try {
                    inf.setInput(data);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4);
                    byte[] buf = new byte[1 << 16];
                    while (!inf.finished()) {
                        int n = inf.inflate(buf);
                        if (n == 0 && inf.needsInput()) break;
                        bos.write(buf, 0, n);
                    }
                    return bos.toByteArray();
                } catch (Exception ex) {
                    throw new IOException("zlib: " + ex.getMessage());
                } finally {
                    inf.end();
                }
            case CM_ZSTD:
                return com.obbpak.tool.Zstd.decompress(data, null);
            case CM_ZSTD_DICT:
                return com.obbpak.tool.Zstd.decompress(data, zstdDict);
            default:
                throw new IOException("Unsupported compression method " + method);
        }
    }

    private byte[] readAt(long off, int len) throws IOException {
        if (len <= 0) return new byte[0];
        if (off < 0 || len > fileLen - off) throw new IOException("Read out of bounds");
        byte[] b = new byte[len];
        reader.readFully(off, b, 0, len);
        return b;
    }

    private static byte[] trim(byte[] data, int len) {
        if (len >= data.length) return data;
        return Arrays.copyOf(data, len);
    }

    // ---------------- crypto dispatch ----------------

    public static boolean isSm4Method(int m) {
        return m == 2 || m == 4 || (m >= 31 && m < 63);
    }

    public static int alignEncrypted(int n, int encM, boolean encrypted) {
        if (!encrypted) return n;
        if (encM == 16) return alignUp(n, 16);
        if (isSm4Method(encM)) return alignUp(n, 16);
        return n;
    }

    static int alignUp(int x, int n) {
        return (x + n - 1) / n * n;
    }

    public byte[] decryptBlock(byte[] ct, String stem, int encM) {
        if (encM == 17) return ct;
        if (encM == 1) return Sm4.decryptSimple1(ct, keys);
        if (encM == 16) return Sm4.decryptSimple2(ct, keys);
        if (isSm4Method(encM)) return Sm4.decryptSm4(ct, stem, encM, keys);
        throw new RuntimeException("Unsupported encryption method " + encM);
    }

    public byte[] encryptBlock(byte[] pt, String stem, int encM) {
        if (encM == 17) return pt;
        if (encM == 1) return Sm4.encryptSimple1(pt, keys);
        if (encM == 16) return Sm4.encryptSimple2(pt, keys);
        if (isSm4Method(encM)) return Sm4.encryptSm4(pt, stem, encM, keys);
        throw new RuntimeException("Unsupported encryption method " + encM);
    }

    /** Block permutation for SM4-encrypted multi-block files. */
    public static int[] blockIndices(int n, int encM, boolean encrypted) {
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i;
        if (!encrypted || !isSm4Method(encM) || n <= 1) return identity;
        boolean[] seen = new boolean[n];
        int[] perm = new int[n];
        int count = 0;
        Lcg lcg = new Lcg(n);
        while (count < n) {
            int v = (int) (lcg.next() % n);
            if (!seen[v]) { seen[v] = true; perm[count++] = v; }
        }
        int[] inv = new int[n];
        for (int i = 0; i < n; i++) inv[perm[i]] = i;
        return inv;
    }

    static final class Lcg {
        private long state;

        Lcg(long seed) { state = seed; }

        private static long wrap(long x) {
            x &= 0xFFFFFFFFL;
            if ((x & 0x80000000L) != 0) return ((x + 0x80000000L) & 0xFFFFFFFFL) - 0x80000000L;
            return x;
        }

        long next() {
            long x1 = wrap(1103515245L * state);
            state = wrap(x1 + 12345);
            long x2 = state < 0 ? wrap(x1 + 77880) : state;
            return ((x2 >> 16) & 0xFFFFFFFFL) % 32767;
        }
    }

    static String fileStem(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---------------- tiny index reader ----------------

    static final class Reader {
        final byte[] b;
        int p;

        Reader(byte[] b) { this.b = b; }

        int u8() { return b[p++] & 0xFF; }

        int u32() {
            int v = le32(b, p);
            p += 4;
            return v;
        }

        int i32() { return u32(); }

        long u64() {
            long v = le64(b, p);
            p += 8;
            return v;
        }

        void skip(int n) { p += n; }

        String str() {
            int len = u32();
            if (len <= 0) return "";
            String s = new String(b, p, len - 1, java.nio.charset.StandardCharsets.UTF_8);
            p += len;
            return s;
        }
    }
}
