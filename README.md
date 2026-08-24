# OBB-PAK TOOL

Original Android tool for game modders — **OBB / PAK extract & repack** + **Lua 5.3 decompile / compile** + **PUBG encrypted PAK support**, written from scratch (no Python/Chaquopy needed).

## Features

| Tool | What it does |
|------|--------------|
| Extract | Unzip any `.obb` / `.pak` (zip-based) archive to a folder |
| Repack | Pack a folder back into `.obb` / `.pak` |
| PUBG PAK UNPACK | Decrypt + decompress `.lua` from Tencent/UE-style encrypted paks (v8–v12 full, v13/v14 base-footer probe) |
| PUBG PAK REPACK | Compile edited `.lua`, convert to T24 bytecode, patch slots in place |
| Decompile | `.luac` → readable `.lua` source — auto-detects **PUBG T24** bytecode (shuffled opcodes + XOR strings) |
| Compile | `.lua` → `.luac` via real Lua 5.3.6 built with NDK; optional **T24 output for PUBG** |
| XOR tool | Encrypt / decrypt any file with custom key (text or hex) |
| PAK KEYS | **All crypto keys editable in-app** — ZUC keystream, RSA moduli, SM4 secrets, SIMPLE1/2, T24 string key. Import/export as JSON. |

## Supported encryption
- Index: AES-CBC with RSA-extracted key/IV (v>7), SIMPLE1 fallback
- Files: SM4 (custom Tencent S-BOX, methods 2/4/31+), SIMPLE1 (XOR 0x79), SIMPLE2 (rolling), method 17 passthrough
- Compression: zlib + zstd (+ dictionary from `zsdic` entry)

## Build

APK builds automatically on every push via GitHub Actions.
For an installable release: run the workflow manually (**Actions → Build APK → Run workflow**) with *Create Release* enabled.

Manual build:

```bash
gradle assembleDebug   # output: app/build/outputs/apk/debug/
```

## Tech

- Pure Java app (minSdk 26, target 34)
- Lua 5.3.6 + zstd 1.5.6 compiled natively per-ABI (arm64-v8a, armeabi-v7a, x86_64) through JNI
- unluac decompiler vendored from [HansWessels/unluac](https://github.com/HansWessels/unluac)
- T24 ↔ standard Lua 5.3 bytecode converters ported and round-trip tested

