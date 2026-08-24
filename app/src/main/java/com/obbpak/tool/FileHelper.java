package com.obbpak.tool;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class FileHelper {

    private FileHelper() {}

    public interface ProgressListener {
        void onProgress(int fileCount, long bytes);
    }

    public static byte[] readAll(Context ctx, Uri uri) throws IOException {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        if (is == null) throw new IOException("Cannot open input file");
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    public static void writeAll(Context ctx, Uri uri, byte[] data) throws IOException {
        OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wt");
        if (os == null) throw new IOException("Cannot open output file");
        try {
            os.write(data);
            os.flush();
        } finally {
            try { os.close(); } catch (IOException ignored) {}
        }
    }

    public static String displayName(Context ctx, Uri uri) {
        String name = "file";
        try (android.database.Cursor c = ctx.getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null || name.isEmpty()) {
            String path = uri.getLastPathSegment();
            if (path != null) name = path.substring(Math.max(0, path.lastIndexOf('/') + 1));
        }
        return name;
    }

    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
