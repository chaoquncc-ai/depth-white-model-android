# 白模构建（Depth Anything V2 for Android）

在手机上把视频转成灰度深度图（白模风格）。使用 **Kotlin + Jetpack Compose**，端侧 **ONNX Runtime** 运行 Depth Anything V2，逐帧推理，不把整段视频载入内存。

功能对齐 Windows Gradio 转换器，并针对中端机做了显存/内存控制。

另有独立应用 **白模远端**：手机只当 Gradio 遥控（全屏 WebView），深度转换在 Windows 电脑上运行。见下文「白模远端」。

## 功能

- 从相册或文件选择视频（MP4 / MOV）
- 模型：Small（默认，**APK 已内置**，约 100MB）/ Base（约 390MB，按需下载）/ Large（约 1.3GB，界面保留但建议不要在中端机使用）
- 推理分辨率：省显存 384（默认）/ 平衡 518 / 较高 756 —— **先缩小再推理**
- 导出分辨率：360p / 480p（默认）/ 640 / 原始 / 自定义最大宽度
- 输出画质：极小 / 小（默认）/ 中
- 反转深度
- 时序平滑 EMA（开关 + α）
- 尽可能保留原音频
- 输出时长：5s / 8s / 10s（默认）/ 全部
- 转换完成后可按「起始秒 + 5/8/10 秒」再导出片段
- 通知栏前台任务，进度显示「第 N / M 帧」
- OOM 时给出中文说明（改 Small、384、480p、缩短时长）

**Debug APK 内置 Depth Anything V2 Small**，默认模型首次启动不需要联网。Base / Large 仍按需下载。权重文件不进 Git（约 100MB），由 CI 在 `assembleDebug` 前拉取到 `app/src/main/assets/models/`。

## 环境要求

- Android Studio Hedgehog / Ladybug 或更新（Koala 亦可）
- JDK 17
- Android SDK Platform 35、Build-Tools 35
- 真机 Android 8.0+（API 26）；推理请用 **arm64** 真机，模拟器仅适合点界面

## 用 Android Studio 打开

1. `File → Open`，选中本仓库根目录（含 `settings.gradle.kts` 的那一层）
2. 等待 Gradle 同步
3. 若提示 SDK，安装 **Android 35** 与 **Build-Tools 35.0.0**
4. 连接手机，打开「USB 调试」，点击 Run

`local.properties` 由 Android Studio 自动生成，不要提交。命令行示例：

```
sdk.dir=/home/YOU/Android/Sdk
```

本地若希望 Debug 包也带上 Small，先执行：

```bash
chmod +x scripts/download-models.sh
./scripts/download-models.sh small --assets
```

没有该步骤时 `./gradlew assembleDebug` 仍可编译，只是 APK 不含默认权重，首次转换会走网络下载。

## 命令行编译 Debug APK

```bash
chmod +x gradlew
./gradlew assembleDebug
```

APK 路径：

```
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug 包名为 `com.chaoqun.depthwhite.debug`。启动器名称与通知渠道为 **白模构建**。

`./gradlew assembleDebug` 现在会同时编译本地 `:app` 与远端 `:remote`。只编远端请用 `./gradlew :remote:assembleDebug`。

单元测试：

```bash
./gradlew testDebugUnitTest
```

GitHub Actions 会在 push/PR 时：

1. 下载 Depth Anything V2 **Small** ONNX 到 `app/src/main/assets/models/`（不提交该文件）
2. 执行单元测试与 `assembleDebug`（含本地 `:app` 与远端 `:remote`）
3. 检查 APK 内含 Small 权重，并把本地 APK 作为 artifact 上传
4. 额外产出并上传远端 artifact **`baimo-remote-debug.apk`**（不含 ONNX）

## 模型

默认使用 Hugging Face 上的 Depth Anything V2 ONNX（Small / Base / Large 同源导出）：

- Small：`depth_anything_v2_vits.onnx`（约 100MB，Apache-2.0）— **随 APK 分发**
- Base：`depth_anything_v2_vitb.onnx`（约 390MB，CC-BY-NC-4.0）— 按需下载
- Large：`depth_anything_v2_vitl.onnx`（约 1.3GB，CC-BY-NC-4.0）— 按需下载

应用启动时若 assets 里有 Small，会拷到内部 `files/models/`，**跳过网络下载**。Base / Large 会依次尝试 `huggingface.co` 与 `hf-mirror.com`。

本地预下载（不打进 APK）：

```bash
chmod +x scripts/download-models.sh
./scripts/download-models.sh small
./scripts/download-models.sh base   # 可选
```

文件落在仓库 `models/`（已 gitignore）。把 Small 打进 APK：

```bash
./scripts/download-models.sh small --assets
```

请勿把 `*.onnx` 提交到 Git。若必须离线构建且无法访问 Hugging Face，可临时把 Small 放到 `app/src/main/assets/models/`（仍建议保持 gitignore，用 CI 拉取）。不要打包 Base / Large。

未内置且未下载时，界面会提示「模型未下载」，点「开始转换」会先下载再推理。

## 白模远端（手机当遥控，转换在电脑上）

独立 Gradle 模块 `:remote`，包名 `com.chaoqun.baimo.remote`，启动器名称 **白模远端**。与本地 ONNX 应用 `:app`（白模构建）互不影响，可同时安装。

手机只显示电脑上的 Gradio 页面：选视频、看进度、下载结果。推理不在手机上跑，APK 也不包含 ONNX。

默认服务器：`http://103.47.82.57:47860`（设置里可改，会记住）。**电脑端必须保持 Gradio + frp 运行**，否则手机打不开页面，也无法转换。

### 编译 / 安装

```bash
chmod +x gradlew
./gradlew :remote:assembleDebug
adb install -r remote/build/outputs/apk/debug/remote-debug.apk
```

Debug 包名为 `com.chaoqun.baimo.remote.debug`。GitHub Actions 会把同一份包上传为 artifact **`baimo-remote-debug.apk`**。

在 GitHub 仓库的 Actions 一次成功构建里下载该 artifact，传到手机后允许「未知来源」安装即可。本包允许明文 HTTP，以便访问 `http://` 的 frp 地址。

### 使用

1. 在 Windows 上启动 Gradio 转换器，并用 frp 暴露到手机能访问的地址（默认如上）。
2. 打开「白模远端」。需要改地址时点右上角设置，可「恢复默认地址」。
3. 点 Gradio 的上传按钮：会走 **系统相册 / 照片选择器（仅视频）**，而不是先弹出文件管理器。相册选择器不可用时，才回退到 `ACTION_PICK` / `ACTION_GET_CONTENT`（仍然限制 `video/*`）。
4. 转换在电脑上完成后，点下载。结果会进系统「下载」或「影片」（`Movies`/`Download` 下的 `BaiMoRemote/`），并出现通知。
5. 菜单提供刷新、在浏览器打开、保持屏幕常亮（默认开）、清除缓存。

设置页会说明：计算在 PC 上，本应用不做端侧推理。

## 内存建议（避免 OOM 闪退）

| 机型 | 推荐 |
| --- | --- |
| 6GB 及以下 | Small + 省显存 384 + 480p 或 360p + 输出 10 秒 |
| 8GB | Small/Base + 384 或 518 + 480p |
| 旗舰大内存 | 才考虑 Large / 756 / 原始分辨率 / 全部时长 |

实现上的约束：

- 只按帧解码，回收 Bitmap，ONNX 输入/输出缓冲复用
- 编解码器在 `finally` 中 `stop/release`
- 不把整段视频读进 RAM
- 长任务走 WorkManager 前台服务 + 通知
- `largeHeap=true`，ONNX 默认 2 线程；NNAPI 失败则回退 CPU

若仍 OOM，界面会显示：

> 内存不足（OOM）。请改用 Small 模型、推理分辨率「省显存 384」、导出 360p/480p，并缩短输出时长后重试。

## 权限

运行时申请：读取视频（API 33+ `READ_MEDIA_VIDEO`，更早为存储权限）以及通知权限（用于转换进度）。选相册视频走系统选择器，通常不需要额外存储权限即可读取该文件。默认 Small 不需要网络；仅在下载 Base / Large 或 APK 未打入 Small 时才访问网络。

## 技术栈

- minSdk 26 / targetSdk 35
- Jetpack Compose Material3（中文界面）
- ONNX Runtime Android 1.20（尝试 NNAPI，不稳定则 CPU）
- MediaCodec / MediaExtractor / MediaMuxer 逐帧管线
- WorkManager 前台服务

## 许可

应用代码 MIT。Depth Anything V2 权重请遵守其各自许可证（Small 为 Apache-2.0；Base/Large 为 CC-BY-NC-4.0，商用前请核对）。
