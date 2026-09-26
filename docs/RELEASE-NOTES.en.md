# Release notes

> `tools/upload-github.ps1 -ReleaseTag ...` uses this file together with
> [`RELEASE-NOTES.md`](RELEASE-NOTES.md) (Chinese) as the GitHub release body, so both languages ship with
> every release. Update both when publishing a new version.

A patch mod that makes WATERMeDIA / WATERFrAMES work on Android by bundling VLC for Android.

## Requirements

| Item | Requirement |
|---|---|
| Minecraft | 1.21.1 (client) |
| NeoForge | 21.1.235 or newer (any 21.1.x) |
| Required dependency | WATERMeDIA **2.1.36 / 2.1.37** |
| Launchers | Android Java Edition launchers such as PojavLauncher and FCL |
| Architecture | arm64-v8a / armeabi-v7a / x86_64 (no 32-bit x86) |

> Not for WATERMeDIA 3.x (FFmpeg backend, without the VLC discovery hook this mod relies on).

## Installation

1. Keep WATERMeDIA 2.1.36 or 2.1.37 in your `mods/` folder;
2. Put the jar attached below into the same `mods/` folder;
3. Start the game. The first launch extracts about 43 MiB of native libraries into **app-internal storage**;
   later launches reuse them.

These two lines in `latest.log` mean it worked:

```
[VideoLan4J/NativeDiscovery/INFO] Successfully loaded VLC 3.0.23 Vetinari in '...' using 'WaterMedia Config Provider'
[watermedia/Bootstrap/INFO] Module PlayerAPI loaded successfully
```

**WATERFrAMES needs no patch of its own** — as soon as VLC is available, its screens, projectors and block
music recover automatically.

## Changes in 1.0.4

* Licence compliance: confirmed `GPL-3.0-or-later`, all licence texts are shipped inside the jar under
  `META-INF/licenses/`, every source file carries an SPDX header, and the repository root has a `LICENSE`;
* `libjnidispatch.so` is no longer bundled (a third-party binary that would almost never activate, since
  FCL/PojavLauncher already provide a matching build);
* functionally identical to 1.0.3.

## Checksums and support

* SHA-256 and detailed documentation are in the repository README;
* If you hit a problem, open an issue with your `latest.log` (keywords `Android Bridge`, `VideoLan4J`) and
  `logs/videolan-discovery.log` if it exists.
