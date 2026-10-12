# 第三方组件与来源

水印工坊 0.7.0 的原有 Android 应用组件按 GNU GPL v3 分发，完整条款见 LICENSE。原有自编图片／视频工具的 MIT 通告保留在 LICENSE-MIT。本文不是替代组件许可证。

| 组件 | 版本 | 来源与许可证 |
| --- | --- | --- |
| youtubedl-android library / common / ffmpeg | 0.18.1 | https://github.com/yausername/youtubedl-android ; Maven 坐标 io.github.junkfood02.youtubedl-android；GPL-3.0 |
| yt-dlp 脚本 | 2026.08.19 | https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19 ; Unlicense，脚本 ZIP 内包含 Python 源码与其组件通告 |
| Kotlin stdlib / jdk7 / jdk8 | 1.7.22 | https://github.com/JetBrains/kotlin ; Apache-2.0 |
| JetBrains annotations | 13.0 | https://github.com/JetBrains/java-annotations ; Apache-2.0 |
| Jackson core / databind / annotations | 2.11.1 | https://github.com/FasterXML ; Apache-2.0 |
| Commons IO | 2.5 | https://commons.apache.org/proper/commons-io/ ; Apache-2.0 |
| Commons Compress | 1.12 | https://commons.apache.org/proper/commons-compress/ ; Apache-2.0 |
| Python、QuickJS、FFmpeg 及运行库 | 上游 AAR 的 arm64 构建 | 来自 youtubedl-android 0.18.1 AAR 的未修改原生文件；各组件许可证适用 |

JAR 中的 LICENSE / NOTICE 保存在 licenses/；不要移除。上游 Android 包的 Kotlin 对应源码保存在 third_party/youtubedl-android/，不参与本应用源码编译。

原生构建材料与对应源码应从上游 Android 引擎发布及其构建仓库取得：

- https://github.com/yausername/youtubedl-android
- https://github.com/JunkFood02/youtubedl-android
- https://github.com/termux/termux-packages
- https://ffmpeg.org/download.html
- https://www.python.org/downloads/source/
- https://bellard.org/quickjs/

Maven source JAR 可按 tooling/engine-lock.json 中坐标取得，文件名使用 artifact-version-sources.jar。依赖缓存不包含在应用工程 ZIP；prepare-engine.py 可重建校验后的缓存。上游提供的 native AAR 未在本次任务中自行从 C/C++ 编译；正式再分发这些二进制时，应一并准备其完整对应源码、准确构建修订和原生依赖许可证。

Seal、Douyin_TikTok_Download_API、XHS-Downloader 仅作选型参考，未打包它们的应用或服务端代码。本应用与这些项目及原平台无隶属关系。

## 独立图集子进程（0.6.6）

图集组件通过独立 Python 子进程执行。gallery-dl 与各依赖源文件、版权声明和完整许可证保存在 raw/gallery_bundle ZIP，同时将许可证副本放在 licenses/gallery/。原有 LICENSE 不改变这些组件自己的许可证。自编 gallery_runner.py 与 Android 桥接代码按原有项目条款；runner 明确为 MIT。

| 组件 | 版本 | 许可证／来源 |
| --- | --- | --- |
| gallery-dl | 1.32.16 | GPL-2.0-only；https://github.com/mikf/gallery-dl / https://codeberg.org/mikf/gallery-dl |
| Requests | 2.34.2 | Apache-2.0；https://github.com/psf/requests |
| urllib3 | 2.8.0 | MIT；https://github.com/urllib3/urllib3 |
| idna | 3.20 | BSD-3-Clause；https://github.com/kjd/idna |
| charset-normalizer | 3.5.2 | MIT；https://github.com/jawah/charset_normalizer |
| certifi | 2026.7.22 | MPL-2.0；https://github.com/certifi/python-certifi |

这些包使用未修改的纯 Python wheel，精确来源与 SHA-256 见 tooling/gallery-lock.json。wheel 内包含可执行的完整 Python 源码及包元数据，重建步骤见 README；没有打包它们的 C 扩展或独立网络服务。

## 自动去水印与人声分离（0.7.0）

| 组件 | 固定来源 | 许可证／说明 |
| --- | --- | --- |
| Tesseract4Android | adaptech-cz/Tesseract4Android 4.9.0 | Apache-2.0，未修改上游 AAR；内含 Tesseract、Leptonica、libjpeg、libpng 运行库，各自条款适用 |
| Tesseract fast eng / chi_sim | tesseract-ocr/tessdata_fast 4.1.0 | Apache-2.0；使用原始 traineddata |
| JNA | java-native-access/jna 5.18.1 | 选用 Apache-2.0（上游亦提供 LGPL-2.1-or-later 选项） |
| sherpa-onnx C API | k2-fsa/sherpa-onnx v1.13.8 Android arm64 发布包 | Apache-2.0；未修改二进制 |
| ONNX Runtime | 上述 sherpa-onnx 发布包内 libonnxruntime.so | MIT，保留 Microsoft 版权声明 |
| UVR_MDXNET_9482.onnx | k2-fsa/sherpa-onnx 的 source-separation-models 发布页 | UVR / MDX-Net 模型，由 sherpa 添加元数据；原始模型来源 TRvlvr/model_repo。代码许可证不等同于单独的训练权重许可，上游模型文件没有随附独立许可证，不在此重新授权 |

精确下载 URL 和 SHA-256 见 tooling/auto-lock.json。相应代码许可副本保存在 licenses/auto/。自动检测、分块 JNA 调用、保存事务及界面集成为本项目新增代码。Tesseract4Android 原生依赖及 sherpa-onnx 依赖来源可在各自上述标签的构建脚本中查阅；本项目没有重新编译这些上游二进制。

UVR 来源说明：https://k2-fsa.github.io/sherpa/onnx/source-separation/models.html 。人声分离的 vocals-first 输出顺序依据 sherpa-onnx v1.13.8 的 offline-source-separation-uvr-impl.h，并通过同版实际 C API 测试。
