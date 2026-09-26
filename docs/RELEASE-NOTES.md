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

## 本版本变更（1.0.5）

修复**「音频正常、视频画面永远停在第一帧（一块纯白）」**。按两条互不相关的原因分别下药，
并把整条视频链路的关键节点写进日志，这样万一还没好，日志本身就能指明卡在哪一步：

1. **OpenGL 像素类型（最可能的元凶）** —— WATERMeDIA 用桌面专用的
   `GL_UNSIGNED_INT_8_8_8_8_REV`(0x8367) 上传视频帧，**OpenGL ES 不接受这个类型**
   （只回一个 `GL_INVALID_ENUM`），于是 `glTexImage2D` 被拒绝、纹理自始至终没有存储，
   Minecraft 采样不完整纹理，画面就是一块纯白而音频照常。桥接现在接管
   `RenderAPI.uploadBuffer`，改用桌面与 GLES 通用的 `GL_UNSIGNED_BYTE`：对 `GL_RGBA`
   而言两者内存字节序完全相同，画面不受影响；另外加入 GL 错误自检、尺寸变化时重新分配、
   以及「先分配空存储再 `glTexSubImage2D`」的兜底路径。这条路径**绝不抛异常**——
   WATERMeDIA 只有在 `uploadBuffer` 正常返回后才释放帧信号量，异常会让 VLC 的视频输出
   线程永久卡死（正是「视频冻住、音频没事」的形态）。
2. **视频输出模块与解码器** —— 新增两个 Android 专属参数：
   `--vout=vmem`（强制 WATERMeDIA 回调视频所需的 vmem 输出，绕开需要 Java Surface 的
   `android_display`/`android_window`）与 `--avcodec-hw=none`（Android 的 MediaCodec
   硬解要往 Surface 送帧，这里没有 Surface，可能"打开成功却永远不出图"）。
   两者都可以在 `config/watermedia_android_bridge.properties` 里改
   （`videoOutput` / `hardwareDecoding`，例如 `hardwareDecoding=any` 可换回硬解省电）。
3. **可诊断性** —— 日志新增视频链路节点：`video player #1 created` →
   `first video frame from VLC: WxH` → `video texture upload works`，附 GL 错误明细与
   每 30 秒一次的心跳统计；玩家创建后 20 秒内一个帧都没到，会明确警告
   「libvlc 没有产出画面」。

**本版没有改动**：内置 VLC 仍是同一份官方 VLC-Android 3.7.1（libvlc 3.0.23），
payload 版本 `vlc3.0.23-android3.7.1-r2` 不变，因此升级不会重新解包（秒开）；
许可与依赖声明不变（`GPL-3.0-or-later`；WATERMeDIA 仍是必需依赖、不打包进本模组）。

### 如果画面还是白的，请这样反馈

把 `latest.log` 发来即可，日志会直接指明位置：

| 日志里看到 | 说明 |
|---|---|
| 只有 `video player #N created`，没有 `first video frame from VLC` | libvlc 侧（视频输出/解码器）没出帧，问题不在 OpenGL |
| 两条都有，还有 `video texture upload works`，屏幕仍白 | 帧和上传都正常，问题在渲染侧（WATERFrAMES/屏幕本身） |
| 出现 `GL error` / `could not upload a video frame` | 上传被驱动拒绝，错误码和尝试过的回退路径都写在同一条日志里 |

## 历史版本（1.0.4）

* 许可合规：确认 `GPL-3.0-or-later`，随包附上全部许可全文（jar 内 `META-INF/licenses/`），
  所有源码加 SPDX 头，仓库根新增 `LICENSE`；
* 不再随包 `libjnidispatch.so`（第三方二进制，在 FCL/PojavLauncher 上因版本不匹配基本不启用）；
* 功能与 1.0.3 完全一致。

## 校验与支持

* 附件 SHA-256 与详细说明见仓库 README；
* 遇到问题请在 Issue 里附上 `latest.log`（关键字 `Android Bridge`、`VideoLan4J`），
  以及 `logs/videolan-discovery.log`（如果存在）。
