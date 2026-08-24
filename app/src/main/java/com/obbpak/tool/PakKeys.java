package com.obbpak.tool;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * All PAK / T24 crypto keys — fully user-editable from the app.
 * Stored as JSON at filesDir/pak_keys.json. Defaults come from the
 * public PUBG mobile research config; users can swap them for any game.
 */
public class PakKeys {

    public String zucKeystreamHex = "A6D17AB4D4783A411C3F5045034F43015AA0D38CDE5364D359CAA8ED5495C626E09B258496D67A0E1FFBEE0AB84D0C43D373283C2513CE0AC8CF93C4D8CBD95D";
    public String rsaMod1Hex = "CBE8B9F2504050EF9831B719E9A6249A6D238505ADE909BDE78C180DED6072A0C3347B8AF4780E1F212D952D82D4BF7F233C1ECA499E1F9D9A85B4FAD759F54BABC1666C5DE411EA9E4B2374425DD6C6F54333BBC8F2610FE6063E4D0D6C21A671A8F7C3740555E5DC06D4E1691C456DB4116C0C012BF7B206E8311AAAEC689952BF804EF638F09D5822B4117B114208F14DEB459E80CB770E5B0D7978E21F5E6CED4999D3583108221A7AB28B960277ADB5690A332784019D9C195BE4EA9EA0A09459010F236465DE0D59C3EF7324E954E1118D93EE19F299760C2CDB963CE87973EA5ECC9BBE81C27D4C7C8572AC07E9BCEAC9BD72AB7A56A3C0AD736ABCE4";
    public String rsaMod2Hex = "7F58E8A39A4DA4E87357DDD650EAA16D3B5CE95B213D1030A662566444796A78A84AE9AC3DBFFDE7F41094896696835DAF13B89E6EC2B84963B1B1BAF7151DA245C3FBFAE2A6AE18B2684D03F9229DE2C91440F2A3A3BCDE1E5680C16722A88039C73560D5D43F4B6562C2EEA5B1D926D86B51108A2643C70FB74D6442CE3A08339B8FD8F660AE88129B7AB8C46F2FA58124485CCCB1E987B05A6DA65A01858ED3F89905449AE42BB07290FCB9994BF22E26610BCABB9804783A3B9587917F3D97316EDDA15C5E13F79066407B55A93B291B68A4AC42A98D6E35FED84B14A792D154E62028DDAD20FC301951E5924BE9AD62FB719DD94CC30CAB871BEC4377A8";
    public int simple1Key = 0x79;
    public String simple2KeyHex = "E55B4ED1";
    public String sm4Secret2 = "Q0hVTKey$as*1ZFlQCiA";
    public String sm4Secret4 = "eb691efea914241317a8";
    public List<String> sm4SecretNew = new ArrayList<>();
    public String t24KeyHex = "112136474657a78d9d8490d8ab008c35261af7e45805b8b31507d02c1e8ff6c8";

    private static final String[] DEFAULT_SM4_NEW = {
            "xG2qW5lP7lV2iN5fN5pG", "xT1cJ6dL5wC0kK1rB4dK", "qC4jS5bZ6fL5xE6nD4zA",
            "gD4jQ2aL3bS3lC3xT0iW", "xU1yQ8wE9zY3gZ3bT5aE", "uQ3cO2dX7xY4xU7gH7iS",
            "gW1fR0jK6wQ4oN0oK1kZ", "aJ4pV7iZ7pU4wP2aC2cZ", "cX6jT3cM2oT3vK0kJ1qN",
            "iT2vS0cS6yT6cZ1sE1lO", "hM1pH9iY8wM9hT4lN5uJ", "kG6bC8jK0fL0dE4sH4mL",
            "dB6lB3vE0eZ8wM8rI0aC", "tP7sP7nI9rA2vQ4cV5yQ", "aT0cL1yN4pT3sZ7eM2vY",
            "uV6fU8fC9zN3mP5dH8mN"
    };

    public PakKeys() {
        for (String s : DEFAULT_SM4_NEW) sm4SecretNew.add(s);
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "pak_keys.json");
    }

    public static PakKeys load(Context ctx) {
        PakKeys k = new PakKeys();
        File f = file(ctx);
        if (!f.exists()) return k;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            k.fromJson(new JSONObject(new String(buf, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
        }
        return k;
    }

    public void save(Context ctx) throws Exception {
        JSONObject o = toJson();
        try (FileOutputStream out = new FileOutputStream(file(ctx))) {
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("zuc_keystream_hex", zucKeystreamHex);
        o.put("rsa_mod1_hex", rsaMod1Hex);
        o.put("rsa_mod2_hex", rsaMod2Hex);
        o.put("simple1_key_hex", String.format("%02X", simple1Key));
        o.put("simple2_key_hex", simple2KeyHex);
        o.put("sm4_secret_2", sm4Secret2);
        o.put("sm4_secret_4", sm4Secret4);
        JSONArray arr = new JSONArray();
        for (String s : sm4SecretNew) arr.put(s);
        o.put("sm4_secret_new", arr);
        o.put("t24_key_hex", t24KeyHex);
        return o;
    }

    public void fromJson(JSONObject o) {
        zucKeystreamHex = o.optString("zuc_keystream_hex", zucKeystreamHex);
        rsaMod1Hex = o.optString("rsa_mod1_hex", rsaMod1Hex);
        rsaMod2Hex = o.optString("rsa_mod2_hex", rsaMod2Hex);
        simple1Key = Integer.parseInt(o.optString("simple1_key_hex", "79"), 16);
        simple2KeyHex = o.optString("simple2_key_hex", simple2KeyHex);
        sm4Secret2 = o.optString("sm4_secret_2", sm4Secret2);
        sm4Secret4 = o.optString("sm4_secret_4", sm4Secret4);
        JSONArray arr = o.optJSONArray("sm4_secret_new");
        if (arr != null && arr.length() > 0) {
            sm4SecretNew.clear();
            for (int i = 0; i < arr.length(); i++) sm4SecretNew.add(arr.optString(i));
        }
        t24KeyHex = o.optString("t24_key_hex", t24KeyHex);
    }

    // ---- parsed views ----

    public long[] keystream() {
        long[] ks = new long[16];
        for (int i = 0; i < 16 && (i * 8 + 8) <= zucKeystreamHex.length(); i++) {
            ks[i] = Long.parseLong(zucKeystreamHex.substring(i * 8, i * 8 + 8), 16);
        }
        return ks;
    }

    public byte[] rsaMod1() { return hex(rsaMod1Hex); }

    public byte[] rsaMod2() { return hex(rsaMod2Hex); }

    public byte[] simple2Key() { return hex(simple2KeyHex); }

    public byte[] t24Key() { return hex(t24KeyHex); }

    private static byte[] hex(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        String h = sb.toString();
        int len = h.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public String sm4SecretFor(int encMethod) {
        if (encMethod == 2) return sm4Secret2;
        if (encMethod == 4) return sm4Secret4;
        int idx = Math.floorMod(encMethod - 31, sm4SecretNew.size());
        return sm4SecretNew.get(idx) + encMethod;
    }
}
