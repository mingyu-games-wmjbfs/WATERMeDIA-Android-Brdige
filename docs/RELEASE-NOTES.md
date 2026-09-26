# 发布说明（Release Notes）

> `tools/upload-github.ps1 -ReleaseTag ...` 会把本文件内容作为 GitHub Release 的说明正文。
> 发新版本时先更新这里。

Android 上让 WATERMeDIA / WATERFrAMES 能用的补丁模组（内置 VLC for Android）。

## 适用环境

| 项目 | 要求 |
|---|---|
| Minecraft | 1.21.1（客户端） |
| NeoForge | 21.1.235 及以上（21.1.x） |
| 必需前置 | WATERMeDIA **2.1.36 / 2.1.37** |
| 启动器 | PojavLauncher、FCL 等安卓 Java 版启动器 |
| 设备 | arm64-v8a / armeabi-v7a / x86_64（不含 32 位 x86） |

> 不适用于 WATERMeDIA 3.x（FFmpeg 后端，没有本模组依赖的 VLC 发现钩子）。

## 安装

1. 保持 `mods/` 里有 WATERMeDIA 2.1.36 或 2.1.37；
2. 把本页附件的 jar 放进同一个 `mods/` 目录；
3. 启动游戏。首次启动会把约 43 MiB 原生库解包到**应用内部存储**，之后直接复用。

`latest.log` 里出现下面两行即表示成功：

```
[VideoLan4J/NativeDiscovery/INFO] Successfully loaded VLC 3.0.23 Vetinari in '...' using 'WaterMedia Config Provider'
[watermedia/Bootstrap/INFO] Module PlayerAPI loaded successfully
```

**WATERFrAMES 无需任何额外补丁**——VLC 一旦可用，它的屏幕/投影仪/方块音乐就会自动恢复。

## 本版本变更（1.0.4）

* 许可合规：确认 `GPL-3.0-or-later`，随包附上全部许可全文（jar 内 `META-INF/licenses/`），
  所有源码加 SPDX 头，仓库根新增 `LICENSE`；
* 不再随包 `libjnidispatch.so`（第三方二进制，在 FCL/PojavLauncher 上因版本不匹配基本不启用）；
* 功能与 1.0.3 完全一致。

## 校验与支持

* 附件 SHA-256 与详细说明见仓库 README；
* 遇到问题请在 Issue 里附上 `latest.log`（关键字 `Android Bridge`、`VideoLan4J`），
  以及 `logs/videolan-discovery.log`（如果存在）。
