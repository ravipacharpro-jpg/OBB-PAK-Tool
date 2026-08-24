package com.obbpak.tool.pak;

import com.obbpak.tool.PakKeysHolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * PUBG "T24" Lua 5.3 bytecode <-> clean standard Lua 5.3 dump converter.
 *
 * T24 quirks (reverse-engineered, matches reference tooling):
 *  - 34-byte header (rc-style layout, size_t byte = 4), main proto at offset 34
 *  - opcode numbers shuffled
 *  - all strings XOR'd with a 32-byte key
 *  - line info packed as one byte per instruction + abslineinfo section
 *
 * STD side = Lua 5.3 final dump body WITHOUT abslineinfo, strings written as
 * [0xFF][u64 total][bytes] so width never depends on build ABI.
 */
public final class T24 {

    private T24() {}

    public static boolean isLua53(byte[] d) {
        return d != null && d.length >= 12 && d[0] == 0x1B && d[1] == 'L' && d[2] == 'u'
                && d[3] == 'a' && d[4] == 0x53;
    }

    // ---------------- opcode maps ----------------

    // name-based ground truth
    private static final String[] STD_OPS = {
            "MOVE", "LOADK", "LOADKX", "LOADBOOL", "LOADNIL",
            "GETUPVAL", "GETTABUP", "GETTABLE", "SETTABUP", "SETUPVAL",
            "SETTABLE", "NEWTABLE", "SELF", "ADD", "SUB",
            "MUL", "MOD", "POW", "DIV", "IDIV",
            "BAND", "BOR", "BXOR", "SHL", "SHR",
            "UNM", "BNOT", "NOT", "LEN", "CONCAT",
            "JMP", "EQ", "LT", "LE", "TEST",
            "TESTSET", "CALL", "TAILCALL", "RETURN", "FORLOOP",
            "FORPREP", "TFORCALL", "TFORLOOP", "SETLIST", "CLOSURE",
            "VARARG", "EXTRAARG"
    };

    /** index = T24 opcode, value = std opcode or -1 when unmapped (identity). */
    private static final int[] T24_MAP_NAME = buildNameMap();

    private static int[] buildNameMap() {
        Map<Integer, String> shuffled = new java.util.HashMap<>();
        shuffled.put(0, "ADD");   shuffled.put(1, "SUB");   shuffled.put(2, "MUL");
        shuffled.put(5, "DIV");   shuffled.put(7, "BAND");  shuffled.put(10, "SHL");
        shuffled.put(12, "UNM");  shuffled.put(14, "NOT");  shuffled.put(15, "LEN");
        shuffled.put(16, "CONCAT");
        shuffled.put(17, "MOVE"); shuffled.put(18, "LOADK"); shuffled.put(20, "LOADBOOL");
        shuffled.put(21, "LOADNIL"); shuffled.put(22, "GETUPVAL"); shuffled.put(23, "GETTABUP");
        shuffled.put(24, "GETTABLE");
        shuffled.put(8, "SETTABUP"); shuffled.put(9, "SETUPVAL"); shuffled.put(27, "SETTABLE");
        shuffled.put(28, "NEWTABLE"); shuffled.put(29, "SELF");
        shuffled.put(30, "JMP");  shuffled.put(31, "EQ");   shuffled.put(32, "LT");
        shuffled.put(33, "LE");   shuffled.put(34, "TEST"); shuffled.put(35, "TESTSET");
        shuffled.put(36, "CALL"); shuffled.put(37, "TAILCALL"); shuffled.put(38, "RETURN");
        shuffled.put(39, "FORLOOP"); shuffled.put(40, "FORPREP"); shuffled.put(41, "TFORCALL");
        shuffled.put(42, "TFORLOOP");
        shuffled.put(43, "SETLIST"); shuffled.put(44, "CLOSURE"); shuffled.put(45, "VARARG");

        int[] map = new int[64];
        for (int i = 0; i < 64; i++) map[i] = -1;
        for (java.util.Map.Entry<Integer, String> e : shuffled.entrySet()) {
            for (int i = 0; i < STD_OPS.length; i++) {
                if (STD_OPS[i].equals(e.getValue())) { map[e.getKey()] = i; break; }
            }
        }
        return map;
    }

    static int[] stdToT24() {
        int[] m = new int[64];
        for (int i = 0; i < 64; i++) m[i] = i;
        for (int t24 = 0; t24 < 64; t24++) {
            int std = T24_MAP_NAME[t24];
            if (std >= 0) m[std] = t24;
        }
        return m;
    }

    static int t24ToStdOp(int op) {
        int v = T24_MAP_NAME[op & 0x3F];
        return v >= 0 ? v : (op & 0x3F);
    }

    // ---------------- function model ----------------

    static final class Fn {
        String src = "";
        int lineDef;
        int lineEnd;
        int numParams;
        int vararg;
        int maxStack;
        int[] code;
        Const[] consts;
        byte[][] upvals;      // pairs of raw bytes
        Fn[] protos;
        int[] lines;
        long[][] absLines;    // pairs {pc,line}
        LocVar[] locs;
        String[] upNames;
    }

    static final class Const {
        static final int NIL = 0, BOOL = 1, FLT = 3, INT = 19, STR = 4, STR20 = 20;
        int type;
        boolean b;
        double f;
        long i;
        byte[] s;
    }

    static final class LocVar {
        String name;
        int start, end;
    }

    // ================= PUBLIC API =================

    /** Converts game T24 bytecode to our clean std dump. */
    public static byte[] toStandard(byte[] t24) throws Exception {
        if (!isLua53(t24)) throw new IllegalArgumentException("Not Lua 5.3 bytecode");
        if (t24.length < 36) throw new IllegalArgumentException("File too small");
        Reader r = new Reader(t24, 34);
        Fn fn = readT24Fn(r);
        Writer w = new Writer();
        writeStdHeader(w, t24.length > 33 ? t24[33] : 1);
        writeStdFn(w, fn);
        return w.bytes();
    }

    /**
     * Converts our std dump into game T24.
     * templateHeader: first 34 bytes of an ORIGINAL T24 file (recommended),
     * or null to synthesize a generic header.
     */
    public static byte[] toT24(byte[] stdDump, byte[] templateHeader, int stdSizeTWidth) throws Exception {
        if (!isLua53(stdDump)) throw new IllegalArgumentException("Not Lua 5.3 bytecode");
        Reader r = new Reader(stdDump, 34); // official 5.3.6: proto starts after 34-byte header
        Fn fn = readStdFn(r, stdSizeTWidth);
        Writer w = new Writer();
        if (templateHeader != null && templateHeader.length >= 34) {
            w.raw(templateHeader, 34);
        } else {
            writeSyntheticT24Header(w);
        }
        writeT24Fn(w, fn);
        return w.bytes();
    }

    public static String extractSourceName(byte[] t24) {
        try {
            if (!isLua53(t24) || t24.length < 36) return null;
            Reader r = new Reader(t24, 34);
            return r.strX();
        } catch (Exception e) {
            return null;
        }
    }

    // ================= readers =================

    private static final class Reader {
        final byte[] d;
        int p;
        Reader(byte[] d, int p) { this.d = d; this.p = p; }
        int u8() throws Exception {
            if (p >= d.length) throw new Exception("Unexpected end of bytecode");
            return d[p++] & 0xFF;
        }
        int u32() throws Exception {
            need(4);
            int v = (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
            p += 4;
            return v;
        }
        long u64() throws Exception {
            need(8);
            long v = 0;
            for (int i = 7; i >= 0; i--) v = (v << 8) | (d[p + i] & 0xFFL);
            p += 8;
            return v;
        }
        void need(int n) throws Exception {
            if (p + n > d.length) throw new Exception("Unexpected end of bytecode");
        }
        String strX() throws Exception {
            int sz = u8();
            int len;
            if (sz == 0) return "";
            if (sz == 0xFF) {
                long total = u64();
                len = (int) (total - 1);
            } else {
                len = sz - 1;
            }
            if (len < 0) throw new Exception("Bad string length");
            need(len);
            byte[] key = PakKeysHolder.get().t24Key();
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) sb.append((char) ((d[p + i] ^ key[i % key.length]) & 0xFF));
            p += len;
            return sb.toString();
        }
        String strPlain(int width) throws Exception {
            int sz = u8();
            long total;
            if (sz == 0) return "";
            if (sz == 0xFF) {
                total = width == 8 ? u64() : (u32() & 0xFFFFFFFFL);
            } else {
                total = sz;
            }
            int len = (int) (total - 1);
            if (len < 0) throw new Exception("Bad string length");
            need(len);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) sb.append((char) (d[p + i] & 0xFF));
            p += len;
            return sb.toString();
        }
    }

    private static Fn readT24Fn(Reader r) throws Exception {
        Fn f = new Fn();
        f.src = r.strX();
        f.lineDef = r.u32();
        f.lineEnd = r.u32();
        f.numParams = r.u8();
        f.vararg = r.u8();
        f.maxStack = r.u8();
        int n = r.u32();
        f.code = new int[n];
        for (int i = 0; i < n; i++) {
            int ins = r.u32();
            f.code[i] = (ins & ~0x3F) | t24ToStdOp(ins);
        }
        n = r.u32();
        f.consts = new Const[n];
        for (int i = 0; i < n; i++) {
            Const c = new Const();
            c.type = r.u8();
            switch (c.type) {
                case Const.NIL: break;
                case Const.BOOL: c.b = r.u8() != 0; break;
                case Const.FLT: c.f = Double.longBitsToDouble(r.u64()); break;
                case Const.INT: c.i = r.u64(); break;
                case Const.STR: case Const.STR20: c.s = strBytesX(r); break;
                default: throw new Exception("Unknown const type " + c.type);
            }
            f.consts[i] = c;
        }
        n = r.u32();
        f.upvals = new byte[n][];
        for (int i = 0; i < n; i++) f.upvals[i] = new byte[]{(byte) r.u8(), (byte) r.u8()};
        n = r.u32();
        f.protos = new Fn[n];
        for (int i = 0; i < n; i++) f.protos[i] = readT24Fn(r);
        n = r.u32();
        f.lines = new int[n];
        for (int i = 0; i < n; i++) f.lines[i] = r.u8();
        n = r.u32();
        r.p += n * 8; // skip abslineinfo (pc:i32, line:i32)
        n = r.u32();
        f.locs = new LocVar[n];
        for (int i = 0; i < n; i++) {
            LocVar lv = new LocVar();
            lv.name = r.strX();
            lv.start = r.u32();
            lv.end = r.u32();
            f.locs[i] = lv;
        }
        n = r.u32();
        f.upNames = new String[n];
        for (int i = 0; i < n; i++) f.upNames[i] = r.strX();
        return f;
    }

    private static byte[] strBytesX(Reader r) throws Exception {
        int sz = r.u8();
        int len;
        if (sz == 0) return new byte[0];
        if (sz == 0xFF) len = (int) (r.u64() - 1);
        else len = sz - 1;
        if (len < 0) throw new Exception("Bad string length");
        r.need(len);
        byte[] key = PakKeysHolder.get().t24Key();
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) ((r.d[r.p + i] ^ key[i % key.length]) & 0xFF);
        r.p += len;
        return out;
    }

    private static byte[] strBytesPlain(Reader r, int width) throws Exception {
        int sz = r.u8();
        long total;
        if (sz == 0) return new byte[0];
        if (sz == 0xFF) {
            total = width == 8 ? r.u64() : (r.u32() & 0xFFFFFFFFL);
        } else {
            total = sz;
        }
        int len = (int) (total - 1);
        if (len < 0) throw new Exception("Bad string length");
        r.need(len);
        byte[] out = new byte[len];
        System.arraycopy(r.d, r.p, out, 0, len);
        r.p += len;
        return out;
    }

    private static Fn readStdFn(Reader r, int sizeTw) throws Exception {
        Fn f = new Fn();
        f.src = r.strPlain(sizeTw);
        f.lineDef = r.u32();
        f.lineEnd = r.u32();
        f.numParams = r.u8();
        f.vararg = r.u8();
        f.maxStack = r.u8();
        int n = r.u32();
        f.code = new int[n];
        for (int i = 0; i < n; i++) f.code[i] = r.u32();
        n = r.u32();
        f.consts = new Const[n];
        for (int i = 0; i < n; i++) {
            Const c = new Const();
            c.type = r.u8();
            switch (c.type) {
                case Const.NIL: break;
                case Const.BOOL: c.b = r.u8() != 0; break;
                case Const.FLT: c.f = Double.longBitsToDouble(r.u64()); break;
                case Const.INT: c.i = r.u64(); break;
                case Const.STR: case Const.STR20: c.s = strBytesPlain(r, sizeTw); break;
                default: throw new Exception("Unknown const type " + c.type);
            }
            f.consts[i] = c;
        }
        n = r.u32();
        f.upvals = new byte[n][];
        for (int i = 0; i < n; i++) f.upvals[i] = new byte[]{(byte) r.u8(), (byte) r.u8()};
        n = r.u32();
        f.protos = new Fn[n];
        for (int i = 0; i < n; i++) f.protos[i] = readStdFn(r, sizeTw);
        n = r.u32();
        f.lines = new int[n];
        for (int i = 0; i < n; i++) f.lines[i] = r.u32();
        n = r.u32();
        f.locs = new LocVar[n];
        for (int i = 0; i < n; i++) {
            LocVar lv = new LocVar();
            lv.name = r.strPlain(sizeTw);
            lv.start = r.u32();
            lv.end = r.u32();
            f.locs[i] = lv;
        }
        n = r.u32();
        f.upNames = new String[n];
        for (int i = 0; i < n; i++) f.upNames[i] = r.strPlain(sizeTw);
        return f;
    }

    // ================= writers =================

    private static final class Writer {
        ByteArrayOutputStream o = new ByteArrayOutputStream(4096);
        void raw(byte[] b, int n) { o.write(b, 0, n); }
        void u8(int v) { o.write(v & 0xFF); }
        void u32(int v) {
            o.write(v & 0xFF);
            o.write((v >>> 8) & 0xFF);
            o.write((v >>> 16) & 0xFF);
            o.write((v >>> 24) & 0xFF);
        }
        void u64(long v) {
            for (int i = 0; i < 8; i++) o.write((int) ((v >>> (i * 8)) & 0xFF));
        }
        void f64(double d) { u64(Double.doubleToLongBits(d)); }
        byte[] bytes() { return o.toByteArray(); }
    }

    private static void writeStdHeader(Writer w, int upvalueCount) {
        // official Lua 5.3.6 dump header (34 bytes incl. main-chunk upvalue count)
        w.raw(new byte[]{0x1B, 'L', 'u', 'a'}, 4);
        w.u8(0x53);          // version
        w.u8(0);             // format
        w.raw(new byte[]{0x19, (byte) 0x93, 0x0D, 0x0A, 0x1A, 0x0A}, 6);
        w.u8(4);             // sizeof(int)
        w.u8(8);             // sizeof(size_t)
        w.u8(4);             // sizeof(Instruction)
        w.u8(8);             // sizeof(lua_Integer)
        w.u8(8);             // sizeof(lua_Number)
        w.u64(0x5678L);      // LUAC_INT (little endian check)
        w.f64(370.5);        // LUAC_NUM
        w.u8(upvalueCount);
    }

    private static void writeSyntheticT24Header(Writer w) {
        // mirrors real luac output but with T24 size_t signature byte = 4
        writeStdHeader(w, 1);
        byte[] out = w.bytes();
        out[13] = 4;
        w.o.reset();
        w.raw(out, out.length);
    }

    private static void writeStrX(Writer w, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        if (len == 0) { w.u8(0); return; }
        if (len + 1 < 0xFF) w.u8(len + 1);
        else { w.u8(0xFF); w.u64(len + 1); }
        byte[] key = PakKeysHolder.get().t24Key();
        for (int i = 0; i < len; i++) w.u8((data[i] ^ key[i % key.length]) & 0xFF);
    }

    private static void writeStrXBytes(Writer w, byte[] data) {
        int len = data.length;
        if (len == 0) { w.u8(0); return; }
        if (len + 1 < 0xFF) w.u8(len + 1);
        else { w.u8(0xFF); w.u64(len + 1); }
        byte[] key = PakKeysHolder.get().t24Key();
        for (int i = 0; i < len; i++) w.u8((data[i] ^ key[i % key.length]) & 0xFF);
    }

    private static void writeT24Fn(Writer w, Fn f) {
        writeStrX(w, f.src == null ? "" : f.src);
        w.u32(f.lineDef);
        w.u32(f.lineEnd);
        w.u8(f.numParams);
        w.u8(f.vararg);
        w.u8(f.maxStack);
        int[] s2t = stdToT24();
        w.u32(f.code.length);
        for (int ins : f.code) {
            int op = ins & 0x3F;
            w.u32((ins & ~0x3F) | s2t[op]);
        }
        w.u32(f.consts.length);
        for (Const c : f.consts) {
            w.u8(c.type);
            switch (c.type) {
                case Const.NIL: break;
                case Const.BOOL: w.u8(c.b ? 1 : 0); break;
                case Const.FLT: w.f64(c.f); break;
                case Const.INT: w.u64(c.i); break;
                case Const.STR: case Const.STR20: writeStrXBytes(w, c.s); break;
                default: throw new RuntimeException("bad const");
            }
        }
        w.u32(f.upvals.length);
        for (byte[] p : f.upvals) { w.u8(p[0]); w.u8(p[1]); }
        w.u32(f.protos.length);
        for (Fn p : f.protos) writeT24Fn(w, p);
        w.u32(f.lines.length);
        for (int ln : f.lines) w.u8(ln & 0xFF);
        final int INTERVAL = 128;
        int absCount = 0;
        for (int pc = INTERVAL; pc < f.lines.length; pc += INTERVAL) absCount++;
        w.u32(absCount);
        for (int pc = INTERVAL; pc < f.lines.length; pc += INTERVAL) {
            w.u32(pc);
            w.u32(f.lines[pc]);
        }
        w.u32(f.locs.length);
        for (LocVar lv : f.locs) {
            writeStrX(w, lv.name == null ? "" : lv.name);
            w.u32(lv.start);
            w.u32(lv.end);
        }
        w.u32(f.upNames.length);
        for (String s : f.upNames) writeStrX(w, s == null ? "" : s);
    }

    private static void writeStrStd(Writer w, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeStrStdBytes(w, data);
    }

    private static void writeStrStdBytes(Writer w, byte[] data) {
        int len = data.length;
        if (len == 0) { w.u8(0); return; }
        if (len + 1 < 0xFF) w.u8(len + 1);
        else { w.u8(0xFF); w.u64(len + 1L); }
        for (byte b : data) w.u8(b & 0xFF);
    }

    private static void writeStdFn(Writer w, Fn f) {
        writeStrStd(w, f.src == null ? "" : f.src);
        w.u32(f.lineDef);
        w.u32(f.lineEnd);
        w.u8(f.numParams);
        w.u8(f.vararg);
        w.u8(f.maxStack);
        w.u32(f.code.length);
        for (int ins : f.code) w.u32(ins);
        w.u32(f.consts.length);
        for (Const c : f.consts) {
            w.u8(c.type);
            switch (c.type) {
                case Const.NIL: break;
                case Const.BOOL: w.u8(c.b ? 1 : 0); break;
                case Const.FLT: w.f64(c.f); break;
                case Const.INT: w.u64(c.i); break;
                case Const.STR: case Const.STR20: writeStrStdBytes(w, c.s); break;
                default: throw new RuntimeException("bad const");
            }
        }
        w.u32(f.upvals.length);
        for (byte[] p : f.upvals) { w.u8(p[0]); w.u8(p[1]); }
        w.u32(f.protos.length);
        for (Fn p : f.protos) writeStdFn(w, p);
        w.u32(f.lines.length);
        for (int ln : f.lines) w.u32(ln);
        w.u32(f.locs.length);
        for (LocVar lv : f.locs) {
            writeStrStd(w, lv.name == null ? "" : lv.name);
            w.u32(lv.start);
            w.u32(lv.end);
        }
        w.u32(f.upNames.length);
        for (String s : f.upNames) writeStrStd(w, s == null ? "" : s);
    }

    /** quick probe: T24 dumps carry size_t signature byte 4 at offset 13 */
    public static boolean looksLikeT24(byte[] d) {
        if (!isLua53(d) || d.length < 36) return false;
        if (d[13] != 4) return false;
        try {
            Reader r = new Reader(d, 34);
            String s = r.strX();
            if (s.isEmpty()) return true;
            char c0 = s.charAt(0);
            return c0 == '@' || c0 == '=' || Character.isLetterOrDigit(c0) || c0 == '_' || c0 == '?';
        } catch (Exception e) {
            return false;
        }
    }
}
