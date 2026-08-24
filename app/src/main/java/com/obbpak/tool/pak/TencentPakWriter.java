package com.obbpak.tool.pak;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * In-place block patcher for PUBG PAK repacking.
 * Copies the original pak, then rewrites only the slots of modded files.
 */
public final class TencentPakWriter {

    private TencentPakWriter() {}

    public interface Progress {
        void onStep(String message);
    }

    /** A compiled+converted payload ready to be written into pak slots. */
    public static final class Payload {
        public byte[] t24;
        public String name;      // file name in pak
        public String relPath;   // relative path from input folder
    }

    /**
     * Writes payloads into a copy of the original pak.
     *
     * @param original opened source pak (read-only use)
     * @param out      seekable channel over the destination copy
     */
    public static int writeModFiles(TencentPak original, ChannelWriter out,
                                    List<Payload> payloads, Progress progress) throws IOException {
        int patched = 0;
        for (Payload pl : payloads) {
            List<TencentPak.Entry> matches = findEntries(original, pl.name, pl.relPath);
            if (matches.isEmpty()) {
                if (progress != null) progress.onStep("No slot found: " + pl.name);
                continue;
            }
            boolean any = false;
            for (TencentPak.Entry e : matches) {
                if (e.encrypted && e.encryptionMethod == 17) continue;
                try {
                    writeIntoEntry(original, out, e, pl.t24);
                    any = true;
                } catch (IOException ex) {
                    if (progress != null) progress.onStep("Slot fail " + pl.name + ": " + ex.getMessage());
                }
            }
            if (any) {
                patched++;
                if (progress != null) progress.onStep("Patched: " + pl.name);
            }
        }
        return patched;
    }

    public static List<TencentPak.Entry> findEntries(TencentPak pak, String name, String relPath) {
        List<TencentPak.Entry> out = new ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Map<String, TencentPak.Entry>> d : pak.dirs.entrySet()) {
            for (java.util.Map.Entry<String, TencentPak.Entry> f : d.getValue().entrySet()) {
                boolean sameName = f.getKey().equals(name);
                boolean sameRel = relPath != null
                        && (d.getKey() + "/" + f.getKey()).replace('\\', '/').endsWith(relPath.replace('\\', '/'));
                if (sameName || sameRel) out.add(f.getValue());
            }
        }
        return out;
    }

    private static void writeIntoEntry(TencentPak pak, ChannelWriter out,
                                       TencentPak.Entry e, byte[] data) throws IOException {
        String stem = TencentPak.fileStem(e.name);
        int encM = e.encryptionMethod;

        if (e.blocks.isEmpty()) {
            long slot = e.size;
            byte[] payload = fitOrForce(pak, data, e.compressionMethod, (int) slot, encM, e.encrypted, stem, e);
            out.seekWrite(e.offset, payload);
            return;
        }

        if (e.blocks.size() == 1) {
            long[] blk = e.blocks.get(0);
            int slot = (int) (blk[1] - blk[0]);
            int usable = TencentPak.alignEncrypted(slot, encM, e.encrypted);
            byte[] comp = compressToFit(pak, data, e.compressionMethod, usable, e);
            if (comp == null) comp = forceCompress(pak, data, e.compressionMethod, e);
            byte[] payload = finalizeForSlot(comp, slot, usable, encM, e.encrypted, stem, pak);
            out.seekWrite(blk[0], payload);
            return;
        }

        // multi-block: chunk by compressionBlockSize and fill permuted blocks
        int bsz = e.compressionBlockSize > 0 ? e.compressionBlockSize : (int) e.uncompressedSize;
        int nBlocks = e.blocks.size();
        List<byte[]> chunks = new ArrayList<>();
        for (int off = 0; off < data.length; off += bsz) {
            int end = Math.min(off + bsz, data.length);
            chunks.add(java.util.Arrays.copyOfRange(data, off, end));
        }
        if (chunks.size() > nBlocks) {
            throw new IOException("Too many chunks (" + chunks.size() + " > " + nBlocks + ")");
        }

        int[] order = firstFitOrder(pak, chunks, e);
        for (int idx = 0; idx < chunks.size(); idx++) {
            int si = order[idx];
            long[] blk = e.blocks.get(si);
            int slot = (int) (blk[1] - blk[0]);
            int usable = TencentPak.alignEncrypted(slot, encM, e.encrypted);
            byte[] comp = compressToFit(pak, chunks.get(idx), e.compressionMethod, usable, e);
            if (comp == null) throw new IOException("Block " + idx + " does not fit");
            byte[] payload = finalizeForSlot(comp, slot, usable, encM, e.encrypted, stem, pak);
            out.seekWrite(blk[0], payload);
        }
    }

    /** Picks the permutation of chunk->block that fits best (identity first, then LCG inverse). */
    private static int[] firstFitOrder(TencentPak pak, List<byte[]> chunks,
                                       TencentPak.Entry e) {
        int n = e.blocks.size();
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i;
        if (!e.encrypted || !TencentPak.isSm4Method(e.encryptionMethod)) return identity;

        int[] inv = TencentPak.blockIndices(n, e.encryptionMethod, true);
        // verify identity works for all chunks; else use lcg-inverse mapping
        for (int i = 0; i < chunks.size(); i++) {
            long[] blk = e.blocks.get(identity[i]);
            int usable = TencentPak.alignEncrypted((int) (blk[1] - blk[0]), e.encryptionMethod, true);
            if (compressToFitQuiet(pak, chunks.get(i), e.compressionMethod, usable, e) == null) {
                return inv;
            }
        }
        return identity;
    }

    private static byte[] fitOrForce(TencentPak pak, byte[] data, int cm, long slot,
                                     int encM, boolean encrypted, String stem, TencentPak.Entry e) throws IOException {
        int s = (int) slot;
        int usable = TencentPak.alignEncrypted(s, encM, encrypted);
        byte[] body = data;
        if (cm != TencentPak.CM_NONE) {
            body = compressToFit(pak, data, cm, usable, e);
            if (body == null) body = forceCompress(pak, data, cm, e);
        }
        return finalizeForSlot(body, s, usable, encM, encrypted, stem, pak);
    }

    private static byte[] finalizeForSlot(byte[] comp, int slot, int usable,
                                          int encM, boolean encrypted, String stem,
                                          TencentPak pak) throws IOException {
        if (encrypted) {
            if (comp.length > usable) throw new IOException("payload exceeds slot after encryption");
            byte[] padded = new byte[usable];
            System.arraycopy(comp, 0, padded, 0, comp.length);
            byte[] cipher = pak.encryptBlock(padded, stem, encM);
            if (cipher.length < slot) {
                byte[] outp = new byte[slot];
                System.arraycopy(cipher, 0, outp, 0, cipher.length);
                return outp;
            }
            return java.util.Arrays.copyOf(cipher, slot);
        }
        if (comp.length > slot) throw new IOException("payload exceeds slot");
        byte[] outp = new byte[slot];
        System.arraycopy(comp, 0, outp, 0, comp.length);
        return outp;
    }

    private static byte[] compressToFit(TencentPak pak, byte[] data, int method, int target,
                                        TencentPak.Entry e) {
        if (method == TencentPak.CM_NONE) return data.length <= target ? data : null;
        for (int lvl = maxLevel(method); lvl >= 1; lvl--) {
            byte[] c = tryCompress(pak, data, method, lvl, e);
            if (c != null && c.length <= target) return c;
        }
        return null;
    }

    private static byte[] compressToFitQuiet(TencentPak pak, byte[] data, int method, int target,
                                             TencentPak.Entry e) {
        try {
            return compressToFit(pak, data, method, target, e);
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] forceCompress(TencentPak pak, byte[] data, int method, TencentPak.Entry e) {
        if (method == TencentPak.CM_NONE) return data;
        byte[] c = tryCompress(pak, data, method, maxLevel(method), e);
        return c != null ? c : data;
    }

    private static int maxLevel(int method) {
        return method == TencentPak.CM_ZLIB ? 9 : 22;
    }

    private static byte[] tryCompress(TencentPak pak, byte[] data, int method, int level,
                                      TencentPak.Entry e) {
        try {
            switch (method) {
                case TencentPak.CM_ZLIB: {
                    java.util.zip.Deflater def = new java.util.zip.Deflater(level, false);
                    try {
                        def.setInput(data);
                        def.finish();
                        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2 + 64);
                        byte[] buf = new byte[1 << 16];
                        while (!def.finished()) bos.write(buf, 0, def.deflate(buf));
                        return bos.toByteArray();
                    } finally {
                        def.end();
                    }
                }
                case TencentPak.CM_ZSTD:
                    return com.obbpak.tool.Zstd.compress(data, level, null);
                case TencentPak.CM_ZSTD_DICT:
                    return com.obbpak.tool.Zstd.compress(data, level, pak.zstdDict);
                default:
                    return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }
}
