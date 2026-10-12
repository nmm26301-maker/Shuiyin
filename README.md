# 水印工坊 0.7.1 · 自动去水印与去 BGM

Android 10 及以上 arm64 手机开发测试版，目标 Android 13，versionCode 14。沿用 0.6.6 原签名，可覆盖安装；不必卸载旧版。

## 本次完成

- **0.7.1 画质修正**：固定区域与自动去水印导出保留输入画面尺寸和帧率，避免仅擦角落水印却把 4K／高帧率素材压成 1080p／30 帧。视频仍需重新编码，效果与耗时取决于设备性能。

- **默认自动去水印，无需首次框选**：本机 Tesseract OCR 识别图片及视频多个采样画面中的边缘平台文字、@账号和相关博主名称，避开中央字幕区域。视频需要水印在至少两个采样帧中出现；最多修复 8 处固定区域。未检测到可靠区域时保留画面并明确提示，不能保证识别所有水印。
- **去 BGM 开关**：链接页可开启“保存视频时去 BGM · 保留人声”，单项、批量、队列共用；首页也可选择本地视频直接处理。内置 sherpa-onnx + UVR_MDXNET_9482 模型，无需自行配置服务器或下载模型。去 BGM 会分离人声与伴奏，保留人声，再与原视频画面合并；不是静音或简单滤波。可能残留背景音乐、损伤语音，歌曲中的人声也可能被保留。
- **完整保存链路**：下载 → 自动识别和修复 → 可选去 BGM → 登记最终成品。处理中间文件隐藏；后续失败或取消会清理中间文件。下载与人声分离使用实际阶段进度，合回视频后才显示保存完成。
- 保留手动多区域模式、记忆位置、暂停继续、失败重试、多链接队列。手动位置清理不再重置其他处理开关。
- 沿用 973 条 gallery-dl 图集规则、1,752 条 yt-dlp 视频／音频规则及常用平台入口。**规则数量不是平台数量，也不代表逐站验证成功**；本次重点完成保存后的自动处理，未新增“全平台保证”。

## 使用

1. 进入“链接提取”，粘贴分享链接，点“解析链接”。小红书保留专用解析；图集站优先 gallery-dl，其他站先用 yt-dlp，失败时尝试图集或网页素材。
2. 要获取原图时点“提取图集原图”；视频封面仍明确标注为封面。
3. 批量保存点“多链接保存队列”，粘贴多条分享文案，选择类型，添加队列并点“开始／继续”。取消当前任务后可继续；失败项目可以单独或一起重试。
4. 默认自动识别去水印，不弹出框选窗口。链接页可直接打开“保存视频时去 BGM”开关；不需要去 BGM 时保持关闭。
5. 自动识别漏检时，在菜单“保存处理设置”关闭自动识别，改用手动模式。手动模式可框选最多 8 处并记住同平台、类型和比例的位置；自动模式逐项重新识别，不复用旧位置。未执行修复的项目会在结果中说明。

输出保存在系统 Pictures / Movies / Music 下的“水印工坊”，并进入作品列表。下载后处理中间原片和修复文件保持隐藏，全部选定处理成功后只发布最终成品。真实下载字节、速度、剩余时间和处理阶段显示在科幻进度面板；单文件下载最多 2GB，保存次数不限。批次进度代表当前素材，不是全队列整体百分比。

## 实际限制与验证

0.7.0：原有 298 项主机回归与新增 28 项自动识别／两阶段事务检查通过。真实 Tesseract 使用打包模型识别合成样例，再将检测到的位置送入生产 FFmpeg 滤镜，验证两个边缘文字水印被修复、中央字幕保留。真实 UVR 模型通过生产 JNA 绑定输出两段人声音频，验证时长、有限采样、非静音结果及取消清理。合成视频检查确认音频延迟、末尾补静音和原画面编码包保留。详细边界见 VALIDATION-0.7.0.md。

0.7.1：新增去水印导出尺寸与 60 帧样例检查；验证记录见 VALIDATION-0.7.1.md。

**没有连接 Android 真机或模拟器。** APK 已编译、签名并检查打包完整性；Android 上的 OCR/JNA 原生加载、MediaStore、操作界面及实际素材效果仍需要设备验证。主机集成测试不能证明手机全流程已成功。

自动识别是 OCR 加位置／多帧规则，不是通用视觉分割或移动物体跟踪。纯图形、动态、复杂背景、低清晰度或中央水印可能漏检；像素填补会留痕，无法恢复被遮住的真实内容。图片输出 PNG、长边最多 2560、不保留 EXIF；动态 GIF 自动处理会跳过并说明。固定区域视频修复输出 SDR H.264/AAC MP4、长边最多 1920、约 30fps，HDR 不走此路径。

去 BGM 仅对视频音轨生效：无音轨跳过；本地去 BGM 工具会提示。输入最多 1GB、30 分钟；需要额外缓存空间，采用有上下文的分块推理。取消在原生推理块结束后响应。仅去 BGM 时复制画面编码包，原始画质不因音频分离重编码；去水印已做的视频会沿用修复后的画面。音频重新编码为 AAC，分离质量不能由非静音测试保证。

0.6.6 的图集验证保留：Wikimedia 公开图片解析下载通过；真实 Imgur 提取器使用固定 API 样例验证。Instagram、Pinterest、Pixiv、Flickr、DeviantArt 仅有路由检查，没有逐站登录下载验证。图库仅处理当前作品，最多 2 层、8 个同站单作品委派，不遍历资料页、搜索页或跨站队列。

需登录、区域限制、DRM 或站点变化时可能失败；不绕过访问权限或 DRM。没有后台下载／导出服务，处理期间保持应用前台。

## 引擎与服务

菜单可更新 yt-dlp 并同步其目录，导入／清除本人 Netscape cookies.txt，设置可选 HTTPS 解析接口。图集引擎随安装包更新。默认所有解析在本机进行，没有内置公用解析服务或密钥。

可选服务协议：HTTPS POST `{"url":"https://分享链接"}`，返回 `{"title":"标题","description":"文案","source":"https://原页面","items":[{"kind":"video","url":"https://媒体直链","label":"视频"}]}`；kind 为 video/image/audio，失败返回 error。队列使用本机解析器；专用远程接口目前用于单链接解析。网页辅助可提取页面媒体标签、动态 currentSrc、公开播放清单与同源资源，blob、跨域框架内部和 DRM 不转换成可下载链接。

## 本地功能

| 功能 | 输出／说明 |
| --- | --- |
| 图片文字／Logo 水印 | 位置、颜色、大小、透明度、旋转、平铺；PNG |
| 批量图片水印 | 系统多选最多 30 张，以首张预览的相对位置与样式逐张输出；PNG |
| 图片擦除 | 画笔／矩形、撤销重做、查看原图；邻域像素填补，PNG |
| 水印模板 | 保存一套本地文字样式，不保存 Logo 图片 |
| 视频文字／Logo 水印 | 首帧调整样式和位置，叠加到整段视频；H.264 MP4 |
| 视频压缩、裁剪尺寸、镜像 | 系统 MediaCodec + OpenGL；H.264 MP4 |
| 视频倍速 | 0.5–4×；默认保留声音并维持音调，可勾选静音；H.264 + AAC MP4 |
| 视频固定区域修复 | 最多 8 个固定矩形区域，周围像素插值填补；可切换预览时间，输出 H.264 MP4 |
| 批量视频 | 最多 10 个，统一水印、固定区域修复、压缩、倍速、尺寸裁剪、镜像、时长裁剪、静音、音频提取、MD5、GIF、取帧 |
| 音频提取、静音、时长裁剪 | AAC 无损提取，其他可解码音轨转为 AAC / M4A；关键帧附近重封装为 MP4 |
| 视频取帧／GIF | PNG；GIF 为前 8 秒、宽 320、约 4 帧/秒 |
| 修改文件 MD5 | MP4/MOV 追加合法 free 块；原编码数据不变 |
| 我的文件 | 最多 300 条导出记录、分类、预览、打开、分享和删除 |

原始素材不覆盖。输出目录为 Pictures / Movies / Music 下的“水印工坊”。图片水印读入长边最多 3072、修复最多 1800、Logo 最多 1024，可能进一步采样。视频重新编码长边最多 1920、约 30 fps，保留声音时当前支持 AAC，HDR 素材会拒绝重新编码。图片修复不等同于生成式 AI。视频修复使用固定选区，复杂背景可能出现模糊；**移动物体跟踪与生成式视频修复尚未实现。** 修改 MD5 不去除版权、画面水印或平台标记。

批量视频以首个视频设置参数，水印／修复位置按各视频画面的相对位置映射。单个失败会继续处理下一项，并汇总文件名和原因。导出完成的文件逐个保留，取消仅停止后续处理。

下载单项上限 2GB。倍速保留声音、固定区域修复及非 AAC 转换使用本机 FFmpeg 软件编码，输入与输出各上限 1GB，需要缓存空间，长视频可能较慢；HDR 仍会拒绝重新编码。处理期间保持应用前台；未实现后台下载／导出服务。应用申请 INTERNET，使用系统文件选择器，不申请“所有文件”或整个相册读取权限。

## 构建与复现

Android Studio / Gradle：JDK 17、SDK Platform 35、Build Tools 35.0.0、Gradle 8.9、AGP 8.7.3，先运行 `python3 tooling/prepare-auto.py --dir build-deps/auto`，再运行 `gradle --no-daemon assembleDebug`。保留 extractNativeLibs=true / useLegacyPackaging=true，运行时需要已解压的原生程序。

手动构建：

```bash
python3 tooling/prepare-engine.py --dir /path/to/dependencies
python3 tooling/prepare-auto.py --dir /path/to/auto-components
python3 tooling/build-offline.py --sdk /path/to/sdk --deps /path/to/dependencies --auto-deps /path/to/auto-components --ecj /path/to/ecj.jar
```

输出 build-offline/WatermarkWorkshop-0.7.1.apk。使用 JDK 的 javac 时可省略 --ecj。开发签名 tooling/development.jks，别名 androiddebugkey，密码 android。签名 SHA-256：7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb。

图集源码及许可证包含在 app/src/main/res/raw/gallery_bundle ZIP；六个 Python 包均为校验固定的纯 Python wheel。tooling/gallery-lock.json 保留下载地址与 SHA-256。重建同一图集包运行 `python3 tooling/prepare-gallery.py --cache /path/to/wheels`；修改 runner 或依赖后需同步 GalleryEngine.SHA，测试会校验一致性。

构建后回归：

```bash
python3 tooling/test-links.py --ecj /path/to/ecj.jar --json /path/to/org-json.jar --deps /path/to/dependencies --sample-video /path/to/sample.mp4
python3 tests/live-gallery.py
```

回归需要 Node、FFmpeg/FFprobe、Python 3、NumPy；可选公开图集网络检查还需要 Pillow。live-gallery.py 默认 Wikimedia Example.jpg，可传入其他公开图片作品 URL；不要把默认样例通过视为其他平台验证。

第三方来源、许可证及构建材料见 THIRD_PARTY_NOTICES.md、LICENSE、LICENSE-MIT 和 licenses/。gallery-dl 作为独立 Python 子进程保留 GPL-2.0-only 条款，不修改其上游源码；其他组件各自许可证适用。

自动处理组件来源、字节数和 SHA-256 固定在 tooling/auto-lock.json。prepare-auto.py 校验后生成 app/libs、jniLibs 与 assets/auto_models，这些大文件不提交 Git。APK 已内置它们，手机首次使用无需联网取模型。手动构建与 Gradle 均使用同一组组件。

自动处理主机集成测试：

```bash
python3 tooling/test-auto.py --ecj /path/to/ecj.jar --json /path/to/org-json.jar \
  --jna /path/to/jna-5.18.1.jar --sherpa-lib /path/to/sherpa-linux/lib \
  --audio /path/to/audio_example.wav
```

需要 Tesseract 命令行、Pillow、NumPy、FFmpeg，以及与 APK 同版的 Linux sherpa-onnx 1.13.8；Linux 测试库不随 APK 分发。OCR 样例是合成测试画面，不是实际平台全覆盖评估。
