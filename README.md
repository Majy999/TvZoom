# 电视缩放（TvZoom）

一款面向 Android TV / 小米电视的画面缩放工具。它把整幅系统画面等比缩小并居中，四周显示黑边，**不裁剪画面内容**。适用于儿童模式、长辈模式或某些应用界面过大、边缘内容被电视 overscan 遮挡的场景。

> 这是一个需要 ADB 授权、依赖 Android 隐藏 API 的本地工具，不是应用商店中的通用电视应用。请先阅读“兼容性”和“安全说明”。

## 功能

- 50%–100% 连续缩放，滑杆可精细调节。
- 快捷预设：50 / 57 / 60 / 70 / 80 / 90 / 95 / 100%。
- 整屏等比缩放，画面居中，四周黑边，不裁剪。
- “恢复全屏”一键回到 100%。
- 可选“开机自动恢复上次缩放”。
- 可选“保持 ADB 调试开启”，缓解小米电视重启后关闭 ADB 的问题。
- 支持电视遥控器方向键、确认键操作。
- 适配普通 Launcher 与 Android TV Leanback Launcher。

## 快速开始

前提：电视已开启网络 ADB，电脑能通过 `adb connect` 连上电视。下面的 `<电视IP>` 请替换为实际地址。

```sh
adb connect <电视IP>:5555
adb install -r release/TvZoom.apk
adb push release/tvzoom.dex /data/local/tmp/tvzoom.dex
adb shell pm grant com.jypony.tvzoom android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.jypony.tvzoom/.MainActivity
```

首次连接时在电视上勾选“始终允许”。打开 App 后，用遥控器选择 57% 或其他比例并按确认。需要恢复时选择“全屏”或“恢复全屏”，也可以执行：

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom reset'
```

更完整的构建、安装和故障排查见下文。

## 效果与原理

### 和 `wm density` / `wm size` 的区别

`adb shell wm density 430` 修改的是系统显示密度，主要影响界面元素的排版尺寸；它不会把整个画面等比缩小，也不能保证四周出现对称黑边。

本项目通过 SurfaceFlinger 的 **display projection** 设置，把完整的物理屏幕矩形映射到一个居中的目标矩形：

```text
物理屏幕 1920 × 1080
        ┌──────────────────────────────┐
        │         黑边（上）           │
        │    ┌────────────────────┐    │
        │    │                    │    │
        │ 黑 │   缩放后的完整画面  │ 黑 │
        │ 边 │                    │ 边 │
        │    └────────────────────┘    │
        │         黑边（下）           │
        └──────────────────────────────┘
```

源矩形始终是完整的物理屏幕，目标矩形按比例缩小并居中。因此画面内容不会被裁掉，空出来的区域由系统合成黑边。

例如 57%：

```text
目标宽 = 1920 × 57% = 1094
目标高 = 1080 × 57% = 615
左边距 = (1920 - 1094) / 2 = 413
上边距 = (1080 - 615) / 2 = 232
```

### App 工作链路

1. `MainActivity` 提供缩放比例、预设、恢复全屏和开关。
2. `ZoomController` 通过本机 `127.0.0.1:5555` 建立 ADB 连接。
3. ADB 执行：

   ```sh
   CLASSPATH=/data/local/tmp/tvzoom.dex \
     app_process /system/bin tvzoom.TvZoom set 57
   ```

4. `tvzoom.TvZoom` 调用 `SurfaceControl.Transaction#setDisplayProjection()`，把完整屏幕映射到居中的缩放矩形。
5. 恢复全屏执行 `tvzoom.TvZoom reset`，把源和目标都设回完整物理屏幕。

`tvzoom.dex` 是随 App 一起安装的辅助程序，必须放在 `/data/local/tmp/tvzoom.dex`。它不是普通应用代码，而是由 `app_process` 以 shell 权限启动的 Java 程序。

### 开机保持

小米电视的系统组件 `MiTVSettings3` 会在开机广播阶段把 `adb_enabled` 重置为 `0`。本项目采用以下机制恢复：

- `BootReceiver` 监听 `LOCKED_BOOT_COMPLETED`、`BOOT_COMPLETED`、`USER_UNLOCKED`、`MY_PACKAGE_REPLACED` 等广播。
- 使用持久化 `JobScheduler` 任务，开机约 10 秒后重放上次比例。
- ADB 保活任务在约 5 秒、20 秒、60 秒、180 秒后重试，并每 15 分钟检查一次。
- 缩放 watchdog 每 15 分钟检查一次，比例不是 100% 时重新应用。
- 通过 `WRITE_SECURE_SETTINGS` 写回 `Settings.Global.adb_enabled = 1`。

系统重置 ADB 与本 App 恢复 ADB 之间存在时间差，因此不能保证“重启过程中 ADB 一秒都不断”。但可以做到开机后自动恢复，通常不需要人工重新打开 ADB。

## 兼容性

### 已实测

| 项目 | 值 |
| --- | --- |
| 电视 | 小米电视 |
| 型号 | `MiTV-MFTR0` |
| `ro.product.model` | `MiTV-MFTR0` |
| 产品代号 | `freeguy` |
| 系统 | Android 14 / API 34 |
| 分辨率 | 1920 × 1080 |
| 系统 density | 320 |
| 连接方式 | 网络 ADB（端口 5555） |

此前版本在以上设备上验证过：57% 缩放后画面居中、四周黑边、内容不裁剪；重启后可恢复上次比例和 ADB。该结论仅代表该设备与系统版本，不代表其他型号一定能复现。

> 验证状态：本次整理时对 `TvZoom.java` 做了反射改造，使其能用标准 `android.jar` 编译，并重新构建了 APK/DEX。发布产物已通过 APK 签名校验和 SHA-256 校验，但整理后的版本尚未再次连接电视做回归实测。首次使用前请确认你能通过 ADB 恢复全屏。

### 其他机型

项目使用 `SurfaceControl.Transaction`，并通过反射调用隐藏的 `setDisplayProjection` 与 SurfaceFlinger display token 接口，理论上适用于 Android TV 8.0+，但**其他品牌、其他小米型号和其他系统版本均未验证**。不同厂商可能：

- 修改 SurfaceFlinger 接口或 display token 获取方式；
- 在系统层强制 overscan、画面比例或 HDMI 输出模式；
- 禁止通过 `app_process` 访问 SurfaceFlinger；
- 在关机/开机流程中更激进地重置 ADB。

如果设备无法获取 SurfaceFlinger token，App 会显示“应用失败”，不会静默修改其他设置。建议先在可恢复的环境测试。

## 前置条件

- 电视与电脑在同一局域网。
- 电视已开启“开发者选项 → USB 调试 / ADB 调试”。
- 电视 ADB 监听端口为 `5555`，或你能自行建立端口转发。
- 电脑已安装 Android SDK Platform Tools（`adb`）。
- 首次连接时，电视会弹出“允许 USB 调试吗？”，请勾选“始终允许”并确认。

检查连接：

以下示例 IP 为 `192.168.1.100`，请替换为电视的实际地址：

```sh
adb connect 192.168.1.100:5555
adb devices -l
```

应看到类似：

```text
192.168.1.100:5555  device product:freeguy model:MiTV-MFTR0 device:freeguy
```

> `adb devices` 显示 `unauthorized` 时，先在电视上确认授权弹窗；显示 `offline` 时可执行 `adb kill-server && adb start-server` 后重连。

## 构建

### 方式一：脚本构建（推荐，无需 Gradle）

需要 JDK 17、Android SDK Build-Tools 34 和 Android 14（API 34）平台。

```sh
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
./scripts/build-all.sh
```

如果 SDK 不在默认位置，也可以直接指定：

```sh
BUILD_TOOLS_DIR=/path/to/build-tools/34.0.0 \
ANDROID_JAR=/path/to/platforms/android-34/android.jar \
./scripts/build-all.sh
```

产物：

```text
build/TvZoom.apk
build/tvzoom.dex
```

生成带 SHA-256 校验文件的发布副本：

```sh
./scripts/release.sh
```

输出到 `release/TvZoom.apk`、`release/tvzoom.dex` 和 `release/SHA256SUMS`。脚本会先清空 `release/` 中的旧文件，避免发布目录混入过期产物。

### 方式二：Android Studio / Gradle

用 Android Studio 打开仓库根目录，等待 Gradle 同步后构建 `app` 模块。如果本机已安装 Gradle，也可以执行：

```sh
gradle :app:assembleDebug
```

注意：Gradle 只负责构建 **App APK**；`tvzoom.dex` 仍需执行 `./scripts/build-dex.sh` 生成并推送到电视。

## 安装与首次授权

### 自动安装

```sh
ADB_SERIAL=192.168.1.100:5555 ./scripts/install.sh
```

脚本会依次：

1. 安装 `build/TvZoom.apk`（不存在时自动使用 `release/TvZoom.apk`）；
2. 推送 `build/tvzoom.dex`（不存在时自动使用 `release/tvzoom.dex`）到 `/data/local/tmp/tvzoom.dex`；
3. 授予 `WRITE_SECURE_SETTINGS`；
4. 启动 App。

`ADB_SERIAL` 可以省略；只有一台设备时脚本会使用默认设备。

没有自行构建时，可以直接使用仓库 `release/` 中的预构建文件。也可以用 `TVZOOM_APK` 和 `TVZOOM_DEX` 指定其他文件：

```sh
TVZOOM_APK=/path/to/TvZoom.apk \
TVZOOM_DEX=/path/to/tvzoom.dex \
ADB_SERIAL=192.168.1.100:5555 \
./scripts/install.sh
```

### 手动安装

```sh
adb install -r build/TvZoom.apk
adb push build/tvzoom.dex /data/local/tmp/tvzoom.dex
adb shell pm grant com.jypony.tvzoom android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.jypony.tvzoom/.MainActivity
```

`WRITE_SECURE_SETTINGS` 是系统级权限，无法在电视界面上点击授予，必须执行一次 `pm grant`。该授权通常会在重启后保留；**卸载重装后需要重新授权**。

检查授权：

```sh
adb shell dumpsys package com.jypony.tvzoom | grep WRITE_SECURE_SETTINGS
```

## 电视端操作

1. 在电视应用列表中找到“电视缩放”并打开。
2. 使用左右方向键在预设之间移动，按确认直接应用。
3. 使用上下方向键在滑杆、预设、按钮和开关之间移动。
4. 用滑杆选择 50%–100% 的任意比例，再按“应用缩放”。
5. 需要回到正常画面时，按“恢复全屏”，或选择 100% 预设。
6. 保持“开机自动恢复上次缩放”开启，重启后会自动重放上次比例。
7. 保持“保持 ADB 调试开启”开启，App 会在开机后尝试恢复 ADB。

首次点击“应用缩放”时，电视可能再次弹出 ADB 授权。请选择“始终允许”，否则本机 ADB 连接会失败。

## 恢复全屏

### App 内恢复

打开“电视缩放”，点击“恢复全屏”。

### ADB 命令恢复

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom reset'
```

如果缩放导致电视界面难以操作，可先通过 ADB 恢复，再处理 App。

## 卸载

```sh
ADB_SERIAL=192.168.1.100:5555 ./scripts/uninstall.sh
```

脚本会先尝试恢复全屏，再卸载 App 并删除 `/data/local/tmp/tvzoom.dex`。

手动卸载：

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom reset'
adb uninstall com.jypony.tvzoom
adb shell rm -f /data/local/tmp/tvzoom.dex
```

## 命令行工具

`tvzoom.dex` 也支持独立诊断命令：

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom status'
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom set 70'
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom fit 1280 720'
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom scale 1280 720 320 180'
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom rect 320 180 1600 900'
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom reset'
```

`crop` 是早期诊断命令，会改变源矩形并裁掉部分画面。**App 界面不会调用 `crop`**；日常缩放请使用 `set` / `fit` / `scale` / `rect`。

## 故障排查

### `应用失败：java.net.ConnectException`

App 无法连接本机 `127.0.0.1:5555`。依次检查：

```sh
adb shell settings get global adb_enabled
adb shell getprop service.adb.tcp.port
adb shell netstat -ltn 2>/dev/null | grep 5555
```

确认 ADB 已开启，并重新执行：

```sh
adb kill-server
adb start-server
adb connect 192.168.1.100:5555
```

### `缺少 WRITE_SECURE_SETTINGS`

执行：

```sh
adb shell pm grant com.jypony.tvzoom android.permission.WRITE_SECURE_SETTINGS
```

如果仍失败，确认安装的是本仓库构建的包名 `com.jypony.tvzoom`，并检查是否被系统权限策略拦截。

### `tvzoom.dex` 不存在 / `ClassNotFoundException`

重新推送：

```sh
adb push build/tvzoom.dex /data/local/tmp/tvzoom.dex
adb shell ls -l /data/local/tmp/tvzoom.dex
```

某些系统会清理 `/data/local/tmp`，重启后若缩放任务失败，重新推送即可。

### `应用失败：cannot obtain display token`

设备 SurfaceFlinger 接口与当前实现不兼容，或 `app_process` 没有访问权限。先用 `status` 检查：

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom status'
```

若 `status` 也失败，说明该机型/系统版本未适配。

### 重启后 ADB 又关闭

小米电视可能在开机时主动写回 `adb_enabled=0`。确认：

- App 已获得 `WRITE_SECURE_SETTINGS`；
- “保持 ADB 调试开启”开关为开启；
- App 未被系统“自启动管理”或省电策略限制。

即使全部满足，系统重置与 App 恢复之间仍可能有短暂空窗，无法做到零间隔。

### 画面边缘仍有干扰内容

本工具只缩小整幅系统画面，不会识别或删除某个应用的 UI 元素。若缩小后仍有干扰内容，说明它位于缩放后的完整画面内，需要在该应用内部处理；不要使用 `crop` 来裁掉它，否则会丢失部分画面。

### `screencap` 截图看不到黑边

部分电视的 `screencap` 只截取应用合成层，不能准确反映 HDMI 输出端的物理黑边。以电视实际显示效果为准。

### 电视 overscan / 自动缩放干扰

部分电视会对 HDMI 输入做 overscan。请把电视画面模式设为“点对点”“原始比例”或关闭“过扫描”，否则物理黑边可能被电视再次裁切。

## 安全说明与限制

- 本项目会修改系统显示合成参数，并尝试写入 `adb_enabled`。请只在自有设备上使用。
- `WRITE_SECURE_SETTINGS` 是高权限授权，不要授予来源不明的 APK。
- 项目依赖 Android 隐藏 API；系统升级后可能失效。
- 不建议在儿童模式、企业设备或受管设备上绕过管理员策略。
- 开机恢复依赖系统广播和 JobScheduler，厂商定制系统可能限制后台任务。
- 发布目录中的 APK 使用仓库脚本生成的**本地调试签名**，不是生产签名。公开分发时请自行生成并妥善保管签名密钥。

## 仓库结构

```text
.
├── app/                       Android App 源码与资源
├── src/tvzoom/TvZoom.java     app_process 缩放辅助程序
├── third_party/AdbLib/        ADB 协议库（BSD-3-Clause）
├── scripts/                   构建、安装、卸载、发布脚本
├── release/                   预构建 APK、DEX 和 SHA-256
└── README.md
```

## 第三方组件与许可证

- `third_party/AdbLib` 来自 [cgutman/AdbLib](https://github.com/cgutman/AdbLib)，使用 BSD-3-Clause 许可证，原始许可证见 `third_party/AdbLib/LICENSE`。
- 本项目其余代码以 MIT 许可证发布，见根目录 `LICENSE`。

本项目不包含小米系统 APK、反编译产物、系统私有库或厂商签名文件。

## 免责声明

本项目按“现状”提供，不保证适用于所有电视型号或系统版本。修改显示参数和 ADB 设置可能导致界面异常、调试连接中断或系统行为变化。请确保你能通过物理方式恢复电视，并自行承担使用风险。
