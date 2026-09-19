# 白模转换（Depth Anything V2 for Android）

在手机上把视频转成灰度深度图（白模风格）。使用 **Kotlin + Jetpack Compose**，端侧 **ONNX Runtime** 运行 Depth Anything V2，逐帧推理，不把整段视频载入内存。

功能对齐 Windows Gradio 转换器，并针对中端机做了显存/内存控制。

## 功能

- 从相册或文件选择视频（MP4 / MOV）
- 模型：Small（默认，约 100MB）/ Base（约 390MB）/ Large（约 1.3GB，界面保留但建议不要在中端机使用）
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

权重 **不进 Git**。首次使用自动下载（进度条），也可用脚本预下载。

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

Debug 包名为 `com.chaoqun.depthwhite.debug`。

单元测试：

```bash
./gradlew testDebugUnitTest
```

GitHub Actions 会在 push/PR 时执行 `assembleDebug`，并把 APK 作为 artifact 上传。

## 模型下载

默认使用 Hugging Face 上的 Depth Anything V2 ONNX（Small / Base / Large 同源导出）：

- Small：`depth_anything_v2_vits.onnx`（约 100MB，Apache-2.0）
- Base：`depth_anything_v2_vitb.onnx`（约 390MB，CC-BY-NC-4.0）
- Large：`depth_anything_v2_vitl.onnx`（约 1.3GB，CC-BY-NC-4.0）

应用会依次尝试 `huggingface.co` 与 `hf-mirror.com`。也可预下载：

```bash
chmod +x scripts/download-models.sh
./scripts/download-models.sh small
```

文件落在仓库 `models/`（已 gitignore）。可选复制进 APK 资源：

```bash
cp models/depth_anything_v2_vits.onnx app/src/main/assets/models/
```

（会显著增大 APK，一般不建议。）应用启动时若 assets 里有权重，会拷到内部 `files/models/`。

未下载模型时界面会提示「模型未下载」，点「开始转换」会先下载再推理。

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

运行时申请：读取视频（API 33+ `READ_MEDIA_VIDEO`，更早为存储权限）以及通知权限（用于转换进度）。选相册视频走系统选择器，通常不需要额外存储权限即可读取该文件。

## 技术栈

- minSdk 26 / targetSdk 35
- Jetpack Compose Material3（中文界面）
- ONNX Runtime Android 1.20（尝试 NNAPI，不稳定则 CPU）
- MediaCodec / MediaExtractor / MediaMuxer 逐帧管线
- WorkManager 前台服务

## 许可

应用代码 MIT。Depth Anything V2 权重请遵守其各自许可证（Small 为 Apache-2.0；Base/Large 为 CC-BY-NC-4.0，商用前请核对）。
