package com.obbpak.tool;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Editable PAK keys screen. Every crypto parameter used for PAK
 * unpack/repack and T24 bytecode conversion can be changed here.
 */
public class KeysActivity extends Activity {

    private static final int REQ_IMPORT = 500;
    private static final int REQ_EXPORT = 501;

    private PakKeys keys;
    private EditText etZuc, etMod1, etMod2, etSimple1, etSimple2,
            etSec2, etSec4, etSecNew, etT24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        keys = PakKeys.load(this);
        PakKeysHolder.set(keys);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("PAK KEYS");
        title.setTextColor(0xFFFF4646);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Edit any key below — changes apply instantly to unpack/repack. Export to back up or share your key set.");
        hint.setTextColor(0xFF9E9E9E);
        hint.setTextSize(12);
        hint.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(hint);

        etZuc = field(root, "ZUC keystream (32 hex chars × 16 dwords)", keys.zucKeystreamHex);
        etMod1 = field(root, "RSA MOD 1 (index AES key/IV)", keys.rsaMod1Hex);
        etMod2 = field(root, "RSA MOD 2 (index hash)", keys.rsaMod2Hex);
        etSimple1 = field(root, "SIMPLE1 key (hex byte, e.g. 79)",
                String.format("%02X", keys.simple1Key));
        etSimple2 = field(root, "SIMPLE2 key (8 hex chars)", keys.simple2KeyHex);
        etSec2 = field(root, "SM4 secret for method 2", keys.sm4Secret2);
        etSec4 = field(root, "SM4 secret for method 4", keys.sm4Secret4);
        StringBuilder sb = new StringBuilder();
        for (String s : keys.sm4SecretNew) sb.append(s).append('\n');
        etSecNew = area(root, "SM4 NEW secrets (one per line, methods 31+)", sb.toString());
        etT24 = field(root, "T24 Lua string XOR key (64 hex chars)", keys.t24KeyHex);

        root.addView(button("SAVE KEYS", 0xFF2E7D32, v -> onSave()));
        root.addView(button("IMPORT (.json)", 0xFF455A64, v -> pickJson()));
        root.addView(button("EXPORT (.json)", 0xFF455A64, v -> createJson()));
        root.addView(button("RESET TO DEFAULTS", 0xFFB71C1C, v -> {
            keys = new PakKeys();
            PakKeysHolder.set(keys);
            recreate();
        }));

        scroll.addView(root);
        setContentView(scroll);
    }

    private EditText field(LinearLayout root, String label, String value) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFFFFB300);
        l.setTextSize(12);
        l.setPadding(0, pad(), 0, 4);
        root.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(0xFFF5F5F5);
        e.setBackgroundColor(0xFF1B1B1B);
        e.setTextSize(11);
        e.setSingleLine(true);
        root.addView(e);
        return e;
    }

    private EditText area(LinearLayout root, String label, String value) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFFFFB300);
        l.setTextSize(12);
        l.setPadding(0, pad(), 0, 4);
        root.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(0xFFF5F5F5);
        e.setBackgroundColor(0xFF1B1B1B);
        e.setTextSize(11);
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        root.addView(e);
        return e;
    }

    private Button button(String text, int color, android.view.View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(color);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad();
        b.setLayoutParams(lp);
        return b;
    }

    private int pad() {
        return (int) (10 * getResources().getDisplayMetrics().density);
    }

    private void collect() throws Exception {
        keys.zucKeystreamHex = cleanHex(etZuc.getText().toString(), 256);
        keys.rsaMod1Hex = cleanHex(etMod1.getText().toString(), 512);
        keys.rsaMod2Hex = cleanHex(etMod2.getText().toString(), 512);
        keys.simple1Key = Integer.parseInt(cleanHex(etSimple1.getText().toString(), 2), 16) & 0xFF;
        keys.simple2KeyHex = cleanHex(etSimple2.getText().toString(), 8);
        keys.sm4Secret2 = etSec2.getText().toString();
        keys.sm4Secret4 = etSec4.getText().toString();
        keys.sm4SecretNew.clear();
        for (String line : etSecNew.getText().toString().split("\n")) {
            if (!line.trim().isEmpty()) keys.sm4SecretNew.add(line.trim());
        }
        if (keys.sm4SecretNew.isEmpty()) throw new Exception("SM4 NEW secrets cannot be empty");
        keys.t24KeyHex = cleanHex(etT24.getText().toString(), 64);
    }

    private static String cleanHex(String raw, int expectedLen) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        String hex = sb.toString().toUpperCase();
        if (expectedLen > 0 && hex.length() != expectedLen) {
            throw new Exception("Expected " + expectedLen + " hex digits, got " + hex.length()
                    + " (" + label(expectedLen) + ")");
        }
        return hex;
    }

    private static String label(int len) {
        switch (len) {
            case 2: return "1 byte";
            case 8: return "4 bytes";
            case 64: return "32 bytes";
            case 256: return "128 bytes";
            case 512: return "256 bytes";
            default: return len + " digits";
        }
    }

    private void onSave() {
        try {
            collect();
            keys.save(this);
            PakKeysHolder.set(keys);
            Toast.makeText(this, "Keys saved!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickJson() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    private void createJson() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "pak_keys.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                collect();
                byte[] out = keys.toJson().toString(2).getBytes(StandardCharsets.UTF_8);
                java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                if (os == null) throw new Exception("Cannot open output");
                try { os.write(out); os.flush(); } finally { os.close(); }
                Toast.makeText(this, "Exported!", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("Cannot open input");
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                try { while ((n = in.read(buf)) > 0) all.write(buf, 0, n); } finally { in.close(); }
                keys.fromJson(new org.json.JSONObject(new String(all.toByteArray(), StandardCharsets.UTF_8)));
                keys.save(this);
                PakKeysHolder.set(keys);
                recreate();
                Toast.makeText(this, "Imported!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
