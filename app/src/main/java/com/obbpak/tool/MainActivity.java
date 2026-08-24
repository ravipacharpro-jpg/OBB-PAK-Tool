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
import java.io.OutputStream;
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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AlertDialog progressDialog;

    private Uri pendingExtractSrc;
    private Uri pendingRepackSrc;
    private Uri pendingToolSrc;
    private String pendingXorKey;

    private TextView statusStorage, statusExtract, statusRepack,
            statusDecompile, statusCompile, statusXor;

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
        java.nio.file.Files.write(tmpIn.toPath(), input);
        if (!tmpOut.delete() && tmpOut.exists()) throw new IOException("Cannot clean temp output");
        try {
            unluac.Main.decompile(tmpIn.getAbsolutePath(), tmpOut.getAbsolutePath());
        } catch (RuntimeException e) {
            throw new IOException("unluac: " + e.getMessage());
        }
        if (!tmpOut.exists()) throw new IOException("Decompiler produced no output");
        byte[] out = java.nio.file.Files.readAllBytes(tmpOut.toPath());
        FileHelper.writeAll(this, outUri, out);
        return "Decompiled " + input.length + " B → " + out.length + " B";
    }

    private String doCompile(Uri inUri, Uri outUri) throws IOException {
        byte[] src = FileHelper.readAll(this, inUri);
        String chunk = "@" + FileHelper.displayName(this, inUri);
        byte[] compiled = LuaCompiler.compile(src, chunk, false);
        FileHelper.writeAll(this, outUri, compiled);
        return "Compiled " + src.length + " B → " + compiled.length + " B (Lua 5.3)";
    }

    private String doXor(Uri inUri, Uri outUri) throws IOException {
        byte[] data = FileHelper.readAll(this, inUri);
        byte[] key = XorTools.parseKey(pendingXorKey != null ? pendingXorKey : "");
        byte[] result = XorTools.xor(data, key);
        FileHelper.writeAll(this, outUri, result);
        return "XOR done with " + key.length + "-byte key";
    }
}
