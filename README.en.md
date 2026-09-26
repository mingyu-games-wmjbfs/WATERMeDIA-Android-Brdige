# WATERMeDIA: Android Bridge

[![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3fb950.svg)](#requirements)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.235%2B-e8942a.svg)](#requirements)
[![WATERMeDIA](https://img.shields.io/badge/WATERMeDIA-2.1.36%20~%202.1.37-8b5cf6.svg)](#requirements)
[![Version](https://img.shields.io/badge/version-1.0.4-lightgrey.svg)](https://github.com/mingyu-games-wmjbfs/WATERMeDIA-Android-Brdige/releases)

**English** · [简体中文](README.md)

Brings the VLC that **WATERMeDIA** (and mods built on it, such as **WATERFrAMES**) needs on Android, so video
screens and block music actually play on Java Edition launchers like PojavLauncher and FCL.

> 中文说明见 [`README.md`](README.md)（内容更详细，含逐版本的真机排障记录）。

---

## What it fixes

WATERMeDIA 2.1.x decodes audio and video through VLC, but it only ships native binaries for desktop platforms:

```java
// WATERMeDIA 2.1.36 / 2.1.37  PlayerAPI.java
private final boolean wrapped = Platform.isWindows() && Platform.is64Bit();
...
} else {
    LOGGER.warn(IT, "[NOT A BUG] {} doesn't contains VLC binaries for your OS and ARCH, "
        + "you had to download it manually from 'https://www.videolan.org/vlc/'", WaterMedia.NAME);
}
```

Only Windows x64 unpacks a bundled VLC. Android reports `os.name = Linux`, so it is treated as a plain Linux
host and expected to find VLC in the system. Android has neither a system VLC nor any loadable `libvlc.so`, so
`NativeDiscovery` fails, `PlayerAPI.isReady()` stays `false` forever — screens stay black, music is silent, and
in the worst case the whole game fails to load.

This mod bundles the **official VLC for Android binaries** and plugs them into WATERMeDIA's own native
discovery chain.

---

## Requirements

| Item | Requirement |
|---|---|
| Minecraft | **1.21.1** (client) |
| Mod loader | **NeoForge 21.1.235 or newer** (any 21.1.x) |
| Required dependency | **WATERMeDIA 2.1.36 or 2.1.37** (the VLC / videolan4j generation) |
| Launchers | Android Java Edition launchers: PojavLauncher, FCL (Fold Craft Launcher) and forks |
| Architecture | `arm64-v8a` (most devices), `armeabi-v7a` (32-bit JVM), `x86_64` (emulators); no `x86` |
| Server | **Not needed** — client side only; VLC playback only happens on the client |

> ⚠️ **Not for WATERMeDIA 3.x.** Since 3.0.0 WATERMeDIA uses an FFmpeg backend and has neither `videolan4j`
> nor the discovery hook this mod relies on. The dependency range is pinned to `[2.1.36, 3.0.0)`, so NeoForge
> will report the unmet dependency instead of loading a broken combination.

---

## Installation and usage

1. Make sure your `mods/` folder already contains **WATERMeDIA 2.1.36 or 2.1.37** (client);
2. Download **`watermedia_android_bridge-1.0.4+mc1.21.1-neoforge.jar`** from
   [Releases](https://github.com/mingyu-games-wmjbfs/WATERMeDIA-Android-Brdige/releases)
   (if no release is published yet, build it yourself — see “Building from source”) and put it in the same
   `mods/` folder;
3. Start the game. On the **first launch** it extracts about 43 MiB per ABI of native libraries into
   **app-internal storage** and prints the exact path to the log; later launches reuse it (a version marker
   prevents re-extraction);
4. Play any video with a WATERFrAMES screen, projector or block.

**WATERFrAMES needs no patch of its own** — it obtains its player through WATERMeDIA's `PlayerAPI`, so it
recovers automatically as soon as VLC is available.

### How to tell it is working

After starting the game, `latest.log` should contain (keywords: `Android Bridge`, `VideoLan4J`):

```
[WATERMeDIA: Android Bridge/INFO] using /data/user/0/<package>/files/watermedia_android_bridge/arm64-v8a (payload and VLC's JNI_OnLoad initialised)
[WATERMeDIA: Android Bridge/INFO] pointed WATERMeDIA at the bundled VLC through .../config/watermedia/custom_vlc_path.txt
[VideoLan4J/NativeDiscovery/INFO] Successfully loaded VLC 3.0.23 Vetinari in '...' using 'WaterMedia Config Provider'
[watermedia/Bootstrap/INFO] Module PlayerAPI loaded successfully
[WATERMeDIA: Android Bridge/INFO] registered Android VLC factories: ... --aout=opensles,audiotrack,any
```

`Successfully loaded VLC 3.0.23 Vetinari` together with `Module PlayerAPI loaded successfully` means VLC is ready.

---

## Configuration

The first launch writes `config/watermedia_android_bridge.properties`:

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Set to `false` to leave WATERMeDIA completely untouched |
| `abi` | `auto` | Force a payload ABI (`arm64-v8a` / `armeabi-v7a` / `x86_64`) |
| `installLocation` | `auto` | Base directory for extraction. `auto` picks a directory the **system loader can really load from**; an absolute path also works |
| `forceInstall` | `false` | Re-extract on every launch (troubleshooting) |
| `overrideFactory` | `true` | Re-register the default VLC factories (Android audio output depends on it) |
| `jnaFallback` | `true` | Allow using a JNA native library from the payload when the launcher provides none (none is bundled by default — see the licence section) |
| `audioOutput` | `opensles,audiotrack,any` | VLC audio output modules, tried in order; **if you get no sound, try `audiotrack,opensles,any` or `any`** |
| `extraVlcArguments` | empty | Extra libvlc switches, separated by commas or spaces |

Debug system properties: `-Dwatermedia.androidbridge.forceAndroid=true` (force the Android path off-device) and
`-Dwatermedia.androidbridge.gameDir=<path>` (override game directory detection).

---

## Troubleshooting

**Read the log first**: search `latest.log` for `WATERMeDIA: Android Bridge` and `VideoLan4J`. When discovery
fails, WATERMeDIA additionally prints VLC's own log to `logs/videolan-discovery.log` — that one is the most
useful.

| Symptom | Cause | Fix |
|---|---|---|
| `is not accessible for the namespace "clns-NN"` | The payload sits on emulated/external storage (`/storage/emulated/0`), which Android's linker namespace refuses to load from | Fixed automatically since 1.0.1 (app-internal storage). If it still appears, point `installLocation` at `/data/user/0/<package>/files/` and set `forceInstall=true` |
| `assertion "s_jvm != NULL" failed` + `OpenJDK exited with code : 6` | VLC's Android `JNI_OnLoad` never ran (JNA's bare `dlopen` does not trigger it) | Fixed since 1.0.2 (explicit `System.load` + a bundled `android.os.Environment` stub). If it reappears, please attach the full log |
| `watermedia` fails with `java.lang.OutOfMemoryError` (stack: `RenderAPI.createByteBuffer` / `memAlignedAlloc`) | LWJGL's **aligned allocation** is unavailable on that device; the error is thrown during mod construction and `NeoFLoader` only catches `Exception` | Fixed since 1.0.3 (automatic direct-buffer fallback). If you also see `the direct buffer fallback ... failed too`, the process really is out of memory — lower `-Xms/-Xmx` |
| `SIGSEGV` in `libc.so` (`strtol`) plus `Could not find any graphics adapters` at startup | **Unrelated to this mod**: the launcher's custom renderer plugin (e.g. `libltw_turbo.so`, reporting an `undefined symbol`) crashes during GL initialisation | Switch the launcher renderer from “Custom” back to a built-in one, or update/remove that renderer plugin |
| `bilibili_media` fails with `NoClassDefFoundError: me/shedaniel/autoconfig/ConfigData` | That third-party mod is missing its **Cloth Config** dependency | Install Cloth Config or remove that mod |
| Video but no sound | The device's `opensles` output is unusable | Change `audioOutput` to `audiotrack,opensles,any`, or simply `any` |
| First launch is slow | About 43 MiB of native libraries are being extracted | Expected, first launch only; later launches reuse the cached payload |

---

## How it works (short version)

No WATERMeDIA file is modified — only its own public extension points are used:

1. **Bundle and extract**: the official VLC for Android `libvlc.so` (a monolithic build, libvlc **3.0.23
   Vetinari**) plus its only external dependency `libc++_shared.so` are extracted into app-internal storage,
   and the directory contract videolan4j expects is completed (`libvlc.so` + a `libvlccore.so` alias + a
   readable `plugins/` folder — the monolithic build does not load modules from there).
2. **Location probing**: every candidate directory is actually passed to `System.load`; only a location the
   system loader accepts is used (Android forbids loading native libraries from external storage, which file
   permission checks alone cannot reveal).
3. **Hooking the discovery chain**: `config/watermedia/custom_vlc_path.txt` is written — the file WATERMeDIA
   reads itself — plus a same-priority `IProvider` as a second path.
4. **Triggering `JNI_OnLoad`**: `System.load(libvlc.so)` is called explicitly. The Android VLC build records
   the JavaVM only inside `JNI_OnLoad`, and JNA's `dlopen` never triggers it — without this step `libvlc_new`
   aborts the whole JVM.
5. **Replacing the default arguments**: `PlayerAPI.registerFactory` pins the audio output to
   `--aout=opensles,audiotrack,any` (`audiotrack` needs VLC's Android Java layer, `opensles` is pure native,
   so it comes first with `any` as the last resort).
6. **Optional rescue (Mixin)**: the LWJGL aligned allocation inside `RenderAPI.createByteBuffer` is
   redirected; on failure it falls back to WATERMeDIA's own direct-buffer path, so a single failing 156 KB
   allocation no longer fails the whole mod load.

The full source-level analysis and the per-version on-device debugging record are in
[`docs/TECHNICAL.md`](docs/TECHNICAL.md) (currently Chinese only).

---

## Known limitations and verification status

* **Verified on a real device** (FCL / Android 16 / aarch64 / NeoForge 21.1.248): VLC 3.0.23 loads,
  the `PlayerAPI` module loads, the Android audio arguments take effect, and the game starts and joins a world.
* **Playback quality still needs to be confirmed on your own device**: whether video actually renders and
  whether audio is audible depends on the device and launcher audio backend. If something is off, open an
  issue with your `latest.log`.
* Android uses VLC's software decoding; 4K/high-bitrate performance depends on the device.
* Supports WATERMeDIA **2.1.x** only (the VLC generation); there is no payload for `x86` (32-bit x86).
* This mod is an unofficial third-party compatibility patch, not affiliated with the WATERMeDIA authors.

---

## Building from source

Requires JDK 17+ (no Gradle and no network needed to package; preparing the dependencies needs network once).

```powershell
# 1) download WATERMeDIA sources/binaries, the VLC Android APKs and the licence texts (needs network)
tools\download-deps.ps1
tools\download-vlc-apk.ps1
tools\download-wm37.ps1
tools\fetch-license-texts.ps1

# 2) extract the native libraries from the official APKs into src/main/resources
tools\pack-payload.ps1

# 3) compile and package with javac + jar (compile-time dependencies come from the
#    NeoForge 1.21.1 libraries installed on this machine); the build refuses to run
#    if a source file is missing its SPDX licence header
tools\build.ps1

# 4) 76 integration checks (including extracting straight from the packaged jar, the
#    JNI_OnLoad contract, the Mixin injection point and licence compliance)
tools\itest.ps1
```

* `tools/build.ps1` reads its compile-time dependencies (NeoForge / FancyModLoader / JNA / Log4j / Gson) from
  the local PCL2 library folder; change `$mc` at the top of the script on another machine.
* `src/main/resources/watermedia_android/natives/**` (about 127 MiB of `.so`) is generated by
  `tools/pack-payload.ps1` from the official APKs and is **not meant to be committed**.
* Source layout: `src/main/java` (10 classes, including the `android/os/Environment` stub and
  `mixin/RenderAPIMixin`) and `src/main/resources` (`META-INF/neoforge.mods.toml`, the mixin config, the
  service registration and `META-INF/licenses/`).

---

## Download and checksums

From [Releases](https://github.com/mingyu-games-wmjbfs/WATERMeDIA-Android-Brdige/releases):

| File | Size | SHA-256 |
|---|---|---|
| `watermedia_android_bridge-1.0.4+mc1.21.1-neoforge.jar` | 59.25 MiB | `49BAC37E59D857B90CD1C368283CF1BAF1A8AF3E184C8C1CFE1B42E2EFCBF85F` |

The source lives in this repository (`src/`, `tools/`), so no separate source archive is published — see
“Building from source” to package it yourself.

---

## Licence and credits

**This mod is licensed under [`GPL-3.0-or-later`](LICENSE).** That is not a preference but a direct consequence
of bundling VLC's Android monolithic build: the desktop VLC keeps its LGPL core separate from GPL modules
through dynamic plugins, while the Android build links them statically, so distributing the jar falls under the
GPL. (Upstream `vlc-android` README, verbatim: *"VLC for Android is licensed under GPLv2 (or later). Android
libraries make this, de facto, a GPLv3 application."*)

| Bundled content | Licence | Licence texts |
|---|---|---|
| `libvlc.so` (VLC for Android 3.7.1, libvlc 3.0.23) | GPL-2.0-or-later (the combined work is distributed as GPL-3.0+) | `META-INF/licenses/GPL-3.0.txt`, `GPL-2.0.txt` |
| LibVLC engine | LGPL-2.1-or-later | `META-INF/licenses/LGPL-2.1.txt` |
| `libc++_shared.so` (LLVM libc++) | Apache-2.0 with LLVM exception | `META-INF/licenses/Apache-2.0.txt`, `LLVM-exception.txt` |

The complete notice is
[`META-INF/licenses/THIRD-PARTY-NOTICES.txt`](src/main/resources/META-INF/licenses/THIRD-PARTY-NOTICES.txt).

**Credits**

* **The VideoLAN team** — libVLC and VLC for Android (the binaries bundled here are their official builds);
* **SrRapero720 / Goedix** — [WATERMeDIA](https://github.com/WaterMediaTeam/watermedia) and
  [WATERFrAMES](https://github.com/SrRapero720/WATERFrAMES) (no code of theirs is included; this mod depends on
  WATERMeDIA at runtime);
* **The PojavLauncher and FCL teams** — the Android Java Edition runtime and JNA native support;
* and everyone who sent in device logs.

**Disclaimer**: this is an unofficial third-party patch with no affiliation to Mojang, Microsoft, VideoLAN or
the WATERMeDIA authors. It contains no Minecraft code or assets. Minecraft is a trademark of Mojang Studios.

> The licence notes above are engineering-level reasoning, not legal advice.
