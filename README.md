# OBB-PAK TOOL

Original Android tool for game modders — **OBB / PAK extract & repack** + **Lua 5.3 decompile / compile**, written from scratch (no Python/Chaquopy needed).

## Features

| Tool | What it does |
|------|--------------|
| Extract | Unzip any `.obb` / `.pak` (zip-based) archive to a folder |
| Repack | Pack a folder back into `.obb` / `.pak` |
| Decompile | `.luac` → readable `.lua` source (unluac, Lua 5.0–5.4 headers incl. 5.3) |
| Compile | `.lua` → `.luac` bytecode via real Lua 5.3.6 compiler built with NDK |
| XOR | Encrypt / decrypt files with custom key (text or hex like `0xAB CD`) |

## Build

APK builds automatically on every push via GitHub Actions.
For an installable release: run the workflow manually (**Actions → Build APK → Run workflow**) with *Create Release* enabled.

Manual build:

```bash
gradle assembleDebug   # output: app/build/outputs/apk/debug/
```

## Tech

- Pure Java app (minSdk 26, target 34)
- Lua 5.3.6 compiled natively per-ABI (arm64-v8a, armeabi-v7a, x86_64) through JNI
- unluac decompiler vendored from [HansWessels/unluac](https://github.com/HansWessels/unluac)
