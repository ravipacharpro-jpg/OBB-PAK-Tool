package com.obbpak.tool;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_EXTRACT_SRC = 100;
    private static final int REQ_EXTRACT_DEST = 101;
    private static final int REQ_REPACK_SRC = 102;
    private static final int REQ_REPACK_OUT = 103;
    private static final int REQ_DECOMPILE_SRC = 104;
    private static final int REQ_DECOMPILE_OUT = 105;
    private static final int REQ_COMPILE_SRC = 106;
    private static final int REQ_COMPILE_OUT = 107;
    private static final int REQ_XOR_SRC = 108;
    private static final int REQ_XOR_OUT = 109;
    private static final int REQ_PUBG_SRC = 110;
    private static final int REQ_PUBG_DEST = 111;
    private static final int REQ_PUBGR_PAK = 112;
    private static final int REQ_PUBGR_DIR = 113;
    private static final int REQ_PUBGR_OUT = 114;
    private static final int REQ_T24_SRC = 115;
    private static final int REQ_T24_OUT = 116;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AlertDialog progressDialog;

    private Uri pendingExtractSrc;
    private Uri pendingRepackSrc;
    private Uri pendingToolSrc;
    private Uri pendingPubgPak;
    private String pendingXorKey;

    private TextView statusStorage, statusExtract, statusRepack,
            statusDecompile, statusCompile, statusXor, statusPubg, statusPubgR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusStorage = findViewById(R.id.status_storage);
        statusExtract = findViewById(R.id.status_extract);
        statusRepack = findViewById(R.id.status_repack);
        statusDecompile = findViewById(R.id.status_decompile);
        statusCompile = findViewById(R.id.status_compile);
        statusXor = findViewById(R.id.status_xor);
        statusPubg = findViewById(R.id.status_pubg_unpack);
        statusPubgR = findViewById(R.id.status_pubg_repack);

        Button btnStorage = findViewById(R.id.btn_storage);
        Button btnExtract = findViewById(R.id.btn_extract);
        Button btnRepack = findViewById(R.id.btn_repack);
        Button btnDecompile = findViewById(R.id.btn_decompile);
        Button btnCompile = findViewById(R.id.btn_compile);
        Button btnXor = findViewById(R.id.btn_xor);

        btnStorage.setOnClickListener(v -> requestStorageAccess());
        btnExtract.setOnClickListener(v -> pickFile(REQ_EXTRACT_SRC, "*/*"));
        btnRepack.setOnClickListener(v -> pickFolder(REQ_REPACK_SRC));
        btnDecompile.setOnClickListener(v -> pickFile(REQ_DECOMPILE_SRC, "*/*"));
        btnCompile.setOnClickListener(v -> pickFile(REQ_COMPILE_SRC, "*/*"));
        Button btnKeys = findViewById(R.id.btn_keys);
        btnKeys.setOnClickListener(v -> startActivity(new Intent(this, KeysActivity.class)));
        Button btnPubg = findViewById(R.id.btn_pubg_unpack);
        btnPubg.setOnClickListener(v -> pickFile(REQ_PUBG_SRC, "*/*"));
        Button btnPubgR = findViewById(R.id.btn_pubg_repack);
        btnPubgR.setOnClickListener(v -> pickFile(REQ_PUBGR_PAK, "*/*"));
        Button btnT24 = findViewById(R.id.btn_compile_t24);
        btnT24.setOnClickListener(v -> pickFile(REQ_T24_SRC, "*/*"));
        btnXor.setOnClickListener(v -> {
            TextView keyView = findViewById(R.id.xor_key);
            String key = keyView.getText() != null ? keyView.getText().toString() : "";
            try {
                XorTools.parseKey(key);
                pendingXorKey = key;
                pickFile(REQ_XOR_SRC, "*/*");
            } catch (IllegalArgumentException e) {
                statusXor.setText("Bad key: " + e.getMessage());
            }
        });

        updateStorageStatus();
    }

    private void updateStorageStatus() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            boolean granted = android.os.Environment.isExternalStorageManager();
            statusStorage.setText(granted
                    ? "All files access: GRANTED"
                    : "Not granted — some folders may not be writable");
        } else {
            statusStorage.setText("Legacy storage mode active");
        }
    }

    private void requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                    Toast.makeText(this, "Open settings manually", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStorageStatus();
    }

    private void pickFile(int requestCode, String mime) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        startActivityForResult(i, requestCode);
    }

    private void pickFolder(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(i, requestCode);
    }

    private void createOutput(int requestCode, String suggestedName) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, suggestedName);
        startActivityForResult(i, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        switch (requestCode) {
            case REQ_EXTRACT_SRC:
                pendingExtractSrc = uri;
                statusExtract.setText("Selected: " + FileHelper.displayName(this, uri)
                        + "\nNow choose destination folder…");
                pickFolder(REQ_EXTRACT_DEST);
                break;

            case REQ_EXTRACT_DEST:
                runAsync(statusExtract, () -> doExtract(uri));
                break;

            case REQ_REPACK_SRC:
                pendingRepackSrc = uri;
                statusRepack.setText("Folder selected.\nNow choose output file name…");
                createOutput(REQ_REPACK_OUT, "repacked.pak");
                break;

            case REQ_REPACK_OUT:
                runAsync(statusRepack, () -> doRepack(pendingRepackSrc, uri));
                break;

            case REQ_DECOMPILE_SRC:
                pendingToolSrc = uri;
                createOutput(REQ_DECOMPILE_OUT,
                        FileHelper.baseName(FileHelper.displayName(this, uri)) + ".lua");
                break;

            case REQ_DECOMPILE_OUT:
                runAsync(statusDecompile, () -> doDecompile(pendingToolSrc, uri));
                break;

            case REQ_COMPILE_SRC:
                pendingToolSrc = uri;
                createOutput(REQ_COMPILE_OUT,
                        FileHelper.baseName(FileHelper.displayName(this, uri)) + ".luac");
                break;

            case REQ_COMPILE_OUT:
                runAsync(statusCompile, () -> doCompile(pendingToolSrc, uri));
                break;

            case REQ_XOR_SRC:
                pendingToolSrc = uri;
                createOutput(REQ_XOR_OUT,
                        FileHelper.baseName(FileHelper.displayName(this, uri)) + "_xor."
                                + (FileHelper.extension(FileHelper.displayName(this, uri)).isEmpty()
                                ? "bin" : FileHelper.extension(FileHelper.displayName(this, uri))));
                break;

            case REQ_XOR_OUT:
                runAsync(statusXor, () -> doXor(pendingToolSrc, uri));
                break;

            case REQ_PUBG_SRC:
                pendingPubgPak = uri;
                statusPubg.setText("Selected: " + FileHelper.displayName(this, uri)
                        + "\nNow choose destination folder…");
                pickFolder(REQ_PUBG_DEST);
                break;

            case REQ_PUBG_DEST:
                runAsync(statusPubg, () -> doPubgUnpack(pendingPubgPak, uri));
                break;

            case REQ_PUBGR_PAK:
                pendingPubgPak = uri;
                statusPubgR.setText("Original PAK selected.\nNow choose folder with edited .lua files…");
                pickFolder(REQ_PUBGR_DIR);
                break;

            case REQ_PUBGR_DIR:
                pendingRepackSrc = uri;
                statusPubgR.setText("Edit folder selected.\nNow choose output .pak name…");
                createOutput(REQ_PUBGR_OUT, "mod_" + System.currentTimeMillis() + ".pak");
                break;

            case REQ_PUBGR_OUT:
                final Uri pakUri = pendingPubgPak;
                final Uri dirUri = pendingRepackSrc;
                runAsync(statusPubgR, () -> doPubgRepack(pakUri, dirUri, uri));
                break;

            case REQ_T24_SRC:
                pendingToolSrc = uri;
                createOutput(REQ_T24_OUT,
                        FileHelper.baseName(FileHelper.displayName(this, uri)) + "_t24.luac");
                break;

            case REQ_T24_OUT:
                runAsync(statusCompile, () -> doCompileT24(pendingToolSrc, uri));
                break;
        }
    }

    private interface Job {
        String run() throws Exception;
    }

    private void runAsync(final TextView status, final Job job) {
        setBusy(true, status, null);
        executor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                String result = job.run();
                long ms = System.currentTimeMillis() - start;
                onUi(() -> {
                    status.setText(result + " (" + (ms / 1000.0) + "s)");
                    Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
                    setBusy(false, status, null);
                });
            } catch (final Exception e) {
                onUi(() -> {
                    status.setText("ERROR: " + e.getMessage());
                    setBusy(false, status, null);
                });
            }
        });
    }

    private void setBusy(boolean busy, TextView activeStatus, String msg) {
        if (busy && !isFinishing()) {
            progressDialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.working)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } else if (!busy && progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void onUi(Runnable r) {
        runOnUiThread(r);
    }

    private String doExtract(Uri destTreeUri) throws IOException {
        DocumentFile destDir = DocumentFile.fromTreeUri(this, destTreeUri);
        if (destDir == null || !destDir.isDirectory()) throw new IOException("Invalid destination folder");
        final String[] lastFile = {""};
        int count = ZipTools.extract(this, pendingExtractSrc, destDir,
                (files, bytes) -> onUi(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        double mb = bytes / 1048576.0;
                        progressDialog.setMessage(String.format(java.util.Locale.US,
                                "Working…\n%.1f MB written", mb));
                    }
                }));
        return "Extracted " + count + " files";
    }

    private String doRepack(Uri srcTreeUri, Uri outUri) throws IOException {
        DocumentFile srcDir = DocumentFile.fromTreeUri(this, srcTreeUri);
        if (srcDir == null || !srcDir.isDirectory()) throw new IOException("Invalid source folder");
        OutputStream os = getContentResolver().openOutputStream(outUri, "wt");
        if (os == null) throw new IOException("Cannot open output file");
        int count = ZipTools.repack(this, srcDir, os,
                (index, name) -> onUi(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.setMessage("Packing…\n" + index + " files\n" + name);
                    }
                }));
        return "Packed " + count + " files";
    }

    private String doDecompile(Uri inUri, Uri outUri) throws IOException {
        byte[] input = FileHelper.readAll(this, inUri);
        java.io.File tmpIn = new java.io.File(getCacheDir(), "decomp_in.luac");
        java.io.File tmpOut = new java.io.File(getCacheDir(), "decomp_out.lua");
        if (!tmpOut.delete() && tmpOut.exists()) throw new IOException("Cannot clean temp output");

        // Build candidate bytecode variants: plain std first, then T24-converted
        // (order flipped when the T24 signature byte is present).
        java.util.List<byte[]> candidates = new ArrayList<>();
        boolean t24Sig = com.obbpak.tool.pak.T24.looksLikeT24(input);
        if (t24Sig) {
            try {
                candidates.add(com.obbpak.tool.pak.T24.toStandard(input));
            } catch (Exception e) {
                throw new IOException("Not a valid T24/PUBG luac: " + e.getMessage());
            }
        }
        candidates.add(input);

        Exception lastError = null;
        for (byte[] cand : candidates) {
            java.nio.file.Files.write(tmpIn.toPath(), cand);
            try {
                unluac.Main.decompile(tmpIn.getAbsolutePath(), tmpOut.getAbsolutePath());
                if (!tmpOut.exists()) throw new IOException("Decompiler produced no output");
                byte[] out = java.nio.file.Files.readAllBytes(tmpOut.toPath());
                FileHelper.writeAll(this, outUri, out);
                String tag = t24Sig ? " [PUBG T24]" : "";
                return "Decompiled " + input.length + " B → " + out.length + " B" + tag;
            } catch (Exception e) {
                lastError = e;
                if (!tmpOut.delete() && tmpOut.exists()) { /* keep going */ }
            }
        }
        throw new IOException("unluac: " + (lastError != null ? lastError.getMessage() : "failed"));
    }

    private String doCompile(Uri inUri, Uri outUri) throws IOException {
        byte[] src = FileHelper.readAll(this, inUri);
        String chunk = "@" + FileHelper.displayName(this, inUri);
        byte[] compiled = LuaCompiler.compile(src, chunk, false);
        FileHelper.writeAll(this, outUri, compiled);
        return "Compiled " + src.length + " B → " + compiled.length + " B (Lua 5.3)";
    }

    private String doCompileT24(Uri inUri, Uri outUri) throws IOException {
        byte[] src = stripLuaComments(FileHelper.readAll(this, inUri));
        String name = FileHelper.displayName(this, inUri);
        byte[] compiled = LuaCompiler.compile(src, "@" + name, false);
        byte[] t24;
        try {
            t24 = com.obbpak.tool.pak.T24.toT24(compiled, null, LuaCompiler.sizeT());
        } catch (Exception e) {
            throw new IOException("T24 convert: " + e.getMessage());
        }
        FileHelper.writeAll(this, outUri, t24);
        return "PUBG T24: " + src.length + " B → " + t24.length + " B";
    }

    private static byte[] stripLuaComments(byte[] srcBytes) {
        String src = new String(srcBytes, java.nio.charset.StandardCharsets.UTF_8);
        src = src.replaceAll("(?s)--\\[\\[.*?\\]\\]", "");
        src = src.replaceAll("--[^\\n]*", "");
        src = src.replaceAll("[ \\t]+$", "");
        src = src.replaceAll("\\n\\s*\\n+", "\n");
        return src.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------------- PUBG PAK ----------------

    private com.obbpak.tool.pak.RandomReader openReader(Uri uri) throws IOException {
        android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new IOException("Cannot open PAK");
        return new com.obbpak.tool.pak.PfdReader(pfd);
    }

    private String doPubgUnpack(Uri pakUri, Uri destTreeUri) throws IOException {
        DocumentFile destDir = DocumentFile.fromTreeUri(this, destTreeUri);
        if (destDir == null || !destDir.isDirectory()) throw new IOException("Invalid destination");
        com.obbpak.tool.pak.RandomReader rr = openReader(pakUri);
        com.obbpak.tool.pak.TencentPak pak;
        try {
            pak = new com.obbpak.tool.pak.TencentPak(rr, FileHelper.displayName(this, pakUri));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("PAK parse: " + e.getMessage());
        }
        int total = 0, extracted = 0;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (com.obbpak.tool.pak.TencentPak.Entry e : pak.files) {
            if (e.name == null || !"lua".equalsIgnoreCase(
                    e.name.contains(".") ? e.name.substring(e.name.lastIndexOf('.') + 1) : "")) continue;
            if (e.encrypted && e.encryptionMethod == 17) continue;
            total++;
            onUi(() -> progress("Unpacking…\n" + e.name));
            try {
                byte[] data = pak.readEntry(e);
                String fname = e.name;
                int dup = 2;
                while (used.contains(fname)) {
                    int dot = e.name.lastIndexOf('.');
                    fname = dot > 0
                            ? e.name.substring(0, dot) + "_" + dup + e.name.substring(dot)
                            : e.name + "_" + dup;
                    dup++;
                }
                used.add(fname);
                DocumentFile outFile = destDir.findFile(fname);
                if (outFile == null) {
                    String ext = "";
                    int dot = fname.lastIndexOf('.');
                    if (dot >= 0 && dot < fname.length() - 1) ext = fname.substring(dot + 1);
                    outFile = destDir.createFile("application/octet-stream", fname);
                }
                if (outFile == null) throw new IOException("Cannot create " + fname);
                OutputStream os = getContentResolver().openOutputStream(outFile.getUri(), "wt");
                if (os == null) throw new IOException("Cannot write " + fname);
                try { os.write(data); os.flush(); } finally { os.close(); }
                extracted++;
            } catch (IOException ex) {
                onUi(() -> progress("Skip " + e.name + ": " + ex.getMessage()));
            }
        }
        if (total == 0) {
            return "No .lua files found — check keys in PAK KEYS screen";
        }
        return "Extracted " + extracted + "/" + total + " .lua files";
    }

    private void progress(String msg) {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.setMessage(msg);
    }

    private String doPubgRepack(Uri originalPakUri, Uri editDirUri, Uri outUri) throws IOException {
        DocumentFile editDir = DocumentFile.fromTreeUri(this, editDirUri);
        if (editDir == null || !editDir.isDirectory()) throw new IOException("Invalid edit folder");

        List<DocumentFile> luaFiles = new ArrayList<>();
        collectLua(editDir, "", luaFiles);
        if (luaFiles.isEmpty()) throw new IOException("No .lua files in selected folder");

        com.obbpak.tool.pak.RandomReader rr = openReader(originalPakUri);
        com.obbpak.tool.pak.TencentPak pak = new com.obbpak.tool.pak.TencentPak(
                rr, FileHelper.displayName(this, originalPakUri));

        List<com.obbpak.tool.pak.TencentPakWriter.Payload> payloads = new ArrayList<>();
        final int totalFiles = luaFiles.size();
        int idx = 0;
        for (DocumentFile f : luaFiles) {
            idx++;
            final int fileIdx = idx;
            final String nm = f.getName();
            onUi(() -> progress("Compiling " + fileIdx + "/" + totalFiles + "\n" + nm));
            try {
                byte[] src = stripLuaComments(FileHelper.readAll(this, f.getUri()));
                byte[] origT24 = findOriginalEntryBytes(pak, nm, f.getName());
                String sname = origT24 != null
                        ? com.obbpak.tool.pak.T24.extractSourceName(origT24) : null;
                String chunk = (sname != null && !sname.isEmpty()) ? sname : "@" + nm;
                byte[] std = LuaCompiler.compile(src, chunk, false);
                byte[] t24 = com.obbpak.tool.pak.T24.toT24(std,
                        origT24 != null ? java.util.Arrays.copyOfRange(origT24, 0, Math.min(34, origT24.length))
                                : null,
                        LuaCompiler.sizeT());
                com.obbpak.tool.pak.TencentPakWriter.Payload p =
                        new com.obbpak.tool.pak.TencentPakWriter.Payload();
                p.t24 = t24;
                p.name = nm;
                p.relPath = relPathOf(f, editDir);
                payloads.add(p);
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException("Compile " + nm + ": " + ex.getMessage());
            }
        }

        onUi(() -> progress("Copying original PAK…"));
        try (InputStream in = getContentResolver().openInputStream(originalPakUri);
             OutputStream out = getContentResolver().openOutputStream(outUri, "wt")) {
            if (in == null || out == null) throw new IOException("Stream error");
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                final long mb = done / 1048576L;
                onUi(() -> progress("Copying… " + mb + " MB"));
            }
            out.flush();
        }

        onUi(() -> progress("Patching slots…"));
        int patched;
        try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(outUri, "rw")) {
            if (pfd == null) throw new IOException("Cannot reopen output");
            java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
            java.nio.channels.FileChannel ch = fis.getChannel();
            patched = com.obbpak.tool.pak.TencentPakWriter.writeModFiles(pak,
                    new com.obbpak.tool.pak.ChannelWriter(ch),
                    payloads,
                    step -> onUi(() -> progress(step)));
            ch.close();
            fis.close();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Patch: " + e.getMessage());
        }
        return "Repacked: " + patched + "/" + payloads.size() + " files patched";
    }

    private byte[] findOriginalEntryBytes(com.obbpak.tool.pak.TencentPak pak, String name, String fallback) {
        try {
            List<com.obbpak.tool.pak.TencentPak.Entry> m =
                    com.obbpak.tool.pak.TencentPakWriter.findEntries(pak, name, name);
            for (com.obbpak.tool.pak.TencentPak.Entry e : m) {
                if (e.encrypted && e.encryptionMethod == 17) continue;
                return pak.readEntry(e);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String relPathOf(DocumentFile f, DocumentFile root) {
        StringBuilder sb = new StringBuilder(f.getName());
        DocumentFile cur = f.getParentFile();
        while (cur != null && !cur.getUri().equals(root.getUri())) {
            sb.insert(0, cur.getName() + "/");
            cur = cur.getParentFile();
        }
        return sb.toString();
    }

    private void collectLua(DocumentFile dir, String prefix, List<DocumentFile> out) {
        for (DocumentFile f : dir.listFiles()) {
            String nm = f.getName();
            if (nm == null) continue;
            if (f.isDirectory()) {
                collectLua(f, prefix.isEmpty() ? nm : prefix + "/" + nm, out);
            } else if (nm.toLowerCase().endsWith(".lua")) {
                out.add(f);
            }
        }
    }

    private String doXor(Uri inUri, Uri outUri) throws IOException {
        byte[] data = FileHelper.readAll(this, inUri);
        byte[] key = XorTools.parseKey(pendingXorKey != null ? pendingXorKey : "");
        byte[] result = XorTools.xor(data, key);
        FileHelper.writeAll(this, outUri, result);
        return "XOR done with " + key.length + "-byte key";
    }
}
