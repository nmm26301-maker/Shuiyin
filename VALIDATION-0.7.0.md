# 0.7.0 验证记录

验证日期：2026-10-12。主机为 Linux x86_64；APK 为 Android arm64-v8a。**没有连接 Android 真机或模拟器。**

## 已执行

- 使用 SDK 35 / build-tools 35.0.0、ECJ/JRE 17 手动构建 APK；D8 完成转换，APK 签名验证通过。
- 包名 com.zizai.watermark，versionCode 13，versionName 0.7.0，minSdk 29，targetSdk 33。签名 SHA-256 与原 0.6.6 一致：7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb。
- 原有 tooling/test-links.py 全部通过，共 298 个计数检查，加上打包 gallery Python 集成检查。包括真实 HTTP 中断续传、取消、网页脚本、多区域滤镜、字幕／音轨保留、队列恢复与图集解析固定样例。
- 新增 AutoProcessingTests：28 项，覆盖两处自动识别、中央字幕保护、低置信度、短暂视频文字、黑边坐标、行／词重复框、无检测、两阶段成品事务、去 BGM 失败、登记失败与取消时中间文件清理。
- 实际 Tesseract + 打包的 chi_sim/eng traineddata：在合成图片识别平台文字和 @creator，经生产 AutoWatermarkRules 和 VideoFilters 自动生成修复区域，调用实际 FFmpeg 修复视频。两个文字水印的亮色像素明显减少，中央字幕像素保留。该测试验证 OCR 到滤镜的真实链路，不代表真实平台素材的准确率。
- 实际 sherpa-onnx 1.13.8 Linux C API + 生产 SourceSeparator JNA 绑定 + APK 同一 UVR_MDXNET_9482 模型：处理上游约 10.9 秒 audio_example.wav，跨两块生成非静音人声输出；输出长度与输入完全相同，所有采样有效，且与输入不同；取消清理通过。这不等于人声质量盲测，也不证明 Android 加载成功。
- 生产 BgmCommands 实际 FFmpeg 测试：延迟 0.5 秒的音频仍保持前段静音；音频先结束时补静音而不截短 3 秒画面；输出同时有视频和音轨；视频压缩数据包 SHA-256 与输入一致。
- APK ZIP 完整性、无重复 ZIP 项、三个模型 SHA-256、arm64 OCR/JNA/sherpa/ONNX/FFmpeg/Python 必要原生组件检查通过。

## 未验证和明确边界

- Android 手机安装、启动、Tesseract/JNA/ONNX 原生库加载、MediaStore 隐藏／登记／删除、实际 UI 和长期运行尚未进行设备验证。
- 未新增各平台真实下载验证；仍沿用 0.6.6 平台范围和其已声明的验证边界。
- OCR 是边缘文字规则；纯图形、中央水印、快速移动水印、复杂背景、花字可能漏检。视频检测多个帧后生成固定区域，不是逐帧跟踪。
- UVR 分离可能残留伴奏、保留背景歌曲中的人声或损伤说话声。分块边界和手机性能仍需真实素材检查。取消在原生推理块结束后生效。
- 本版保持前台处理，不包含后台下载服务。去 BGM 输入最多 1GB、30 分钟，需要额外缓存空间；失败不会被当作成功去 BGM。
- Gradle/CI 配方已更新，但本记录的实际 APK 来自手动构建路径；不把尚未运行的 GitHub Actions 当作成功证据。

复现命令见 README 的构建与 test-auto.py 段落。精确 APK 大小、校验值和版本保存在 RELEASE-0.7.0.json。
