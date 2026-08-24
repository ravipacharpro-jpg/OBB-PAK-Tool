package com.obbpak.tool;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipTools {

    private ZipTools() {}

    private static String sanitize(String name) {
        String cleaned = name.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        StringBuilder sb = new StringBuilder();
        for (String part : cleaned.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(part);
        }
        return sb.toString();
    }

    public static int extract(Context ctx, Uri zipUri, DocumentFile destDir,
                              FileHelper.ProgressListener listener) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        InputStream zinStream = cr.openInputStream(zipUri);
        if (zinStream == null) throw new IOException("Cannot open archive");
        int count = 0;
        ZipInputStream zin = new ZipInputStream(zinStream);
        try {
            ZipEntry entry;
            byte[] buf = new byte[1 << 16];
            while ((entry = zin.getNextEntry()) != null) {
                String safePath = sanitize(entry.getName());
                if (safePath.isEmpty()) continue;
                if (entry.isDirectory()) {
                    mkdirs(destDir, safePath);
                    continue;
                }
                int slash = safePath.lastIndexOf('/');
                DocumentFile parent = slash >= 0 ? mkdirs(destDir, safePath.substring(0, slash)) : destDir;
                String fileName = slash >= 0 ? safePath.substring(slash + 1) : safePath;

                DocumentFile outFile = parent.findFile(fileName);
                if (outFile == null || !outFile.isFile()) {
                    String ext = "";
                    int dot = fileName.lastIndexOf('.');
                    if (dot >= 0 && dot < fileName.length() - 1) ext = fileName.substring(dot + 1);
                    outFile = parent.createFile("application/octet-stream", fileName);
                }
                if (outFile == null) throw new IOException("Cannot create: " + safePath);

                OutputStream os = cr.openOutputStream(outFile.getUri(), "wt");
                if (os == null) throw new IOException("Cannot open output: " + safePath);
                long written = 0;
                try {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        written += n;
                        if (listener != null) listener.onProgress(-1, written);
                    }
                    os.flush();
                } finally {
                    try { os.close(); } catch (IOException ignored) {}
                }
                count++;
            }
        } finally {
            try { zin.close(); } catch (IOException ignored) {}
        }
        return count;
    }

    private static DocumentFile mkdirs(DocumentFile root, String path) throws IOException {
        DocumentFile cur = root;
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            DocumentFile next = cur.findFile(part);
            if (next == null || !next.isDirectory()) {
                next = cur.createDirectory(part);
                if (next == null) throw new IOException("Cannot create folder: " + part);
            }
            cur = next;
        }
        return cur;
    }

    public interface RepackProgress {
        void onFile(int index, String name);
    }

    public static int repack(Context ctx, DocumentFile srcDir, OutputStream outStream,
                             RepackProgress progress) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(outStream);
        zos.setLevel(java.util.zip.Deflater.BEST_SPEED);
        int[] counter = {0};
        try {
            walkAndWrite(ctx, srcDir, "", zos, counter, progress);
            zos.finish();
        } finally {
            try { zos.close(); } catch (IOException ignored) {}
        }
        return counter[0];
    }

    private static void walkAndWrite(Context ctx, DocumentFile dir, String prefix,
                                     ZipOutputStream zos, int[] counter,
                                     RepackProgress progress) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        for (DocumentFile f : dir.listFiles()) {
            String rel = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            if (rel == null) continue;
            if (f.isDirectory()) {
                zos.putNextEntry(new ZipEntry(rel + "/"));
                zos.closeEntry();
                walkAndWrite(ctx, f, rel, zos, counter, progress);
            } else if (f.isFile()) {
                counter[0]++;
                if (progress != null) progress.onFile(counter[0], rel);
                zos.putNextEntry(new ZipEntry(rel));
                InputStream is = cr.openInputStream(f.getUri());
                if (is == null) throw new IOException("Cannot read: " + rel);
                try {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                } finally {
                    try { is.close(); } catch (IOException ignored) {}
                }
                zos.closeEntry();
            }
        }
    }
}
