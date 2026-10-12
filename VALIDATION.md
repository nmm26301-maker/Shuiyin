# 0.6.6 验证记录

- 完整 tooling/test-links.py：前版 268 项检查及新增 GalleryQueueTests 30 项通过。另执行打包 gallery_bundle 集成测试：校验固定 ZIP、完整 973 条规则、五个平台真实提取器路由、真实上游 Imgur 图集与混合素材、同站 gallery 委派、无适配链接失败。图集 API 固定样例不代表真实 Imgur 下载。
- 公开 Wikimedia Example.jpg 以打包 Python 脚本和依赖提取 4 个图片地址，下载首张 9,022 字节 JPEG，172×178，通过 Pillow 解码验证；SHA-256 99bd915a3819b1fee3722328ae904dde5459e9b15d56abd98b903c770a2f582c。记录见 tests/fixtures/live-gallery-0.6.6.json。只对该公开样例执行外部下载，不宣称 Instagram、Pinterest 等平台下载通过。
- 队列新增检查覆盖分享文字多链接去重、20 条上限原子性、模式区分、非法 URL、完成记录持久化、中断恢复、失败重试、已完成项目保留、封面过滤、音频模式和签名 URL 刷新后的素材身份。
- 图集媒体 Java 桥接检查覆盖生产 runner 输出、User-Agent/Referer 保留与注入排除、扩展名与 HTTPS 检查、24 项上限、站点域名边界。桥接使用 Android 内置 Python 3.12 对应的纯 Python 包；未在手机执行它。
- 多区域修复回归继续验证两处角落标识、八处标识、字幕与音轨保留；没有引入移动水印识别。
- 资源、Java、DEX、ZIP、对齐及 APK v3 签名通过；versionName 0.6.6 / versionCode 12，Android 10+，目标 Android 13，arm64-v8a；开发签名沿用前版。
- 未连接真机或模拟器。队列 UI、手机 Python 启动、媒体库及中断恢复未完成设备端验证；队列需要前台操作，不是后台服务。导入本人 Cookie 不代表绕过平台权限。

原始回归输出见 VALIDATION-0.6.6.txt；复现步骤见 README。

---

# 0.6.3 验证记录

## 本轮范围

本轮将链接保存与本地区域去水印接为同一次保存，同时加入当前任务内的直链恢复及网页动态播放地址读取。没有新增平台专用解析器；28 项平台目录与 1,752 个引擎条目沿用前版，仍不代表全球平台全部可用。

## 206 项检查通过

- LinkTests 31、EngineTests 16、XhsTests 29、TransferTests 46、平台路由 30：前版解析、媒体签名、会话范围、结构化进度及引擎替换回归通过。
- SaveWorkflowTests 40：真实本机 HTTP 服务先声明完整长度后中途关闭连接，核对 Range/If-Range 请求、强 ETag、恢复输出逐字节相同；服务器忽略 Range 返回 200 时重写而非重复追加；弱 ETag 不使用续传；未知长度可以读取但不编造百分比；变更标识、错误范围、超限和编码不一致拒绝，重试耗尽最多 3 次。
- 保存事务检查覆盖隐藏原片经修复后删除、原片无需修复时先登记后完成、处理失败与登记失败不报成功、取消后清理原片；同平台相同比例记忆选区，横竖屏与照片/视频选区分离，非有限值与全图范围拒绝。
- WebView 实际扫描脚本在 Node DOM fixture 执行，8 项通过：无扩展名的 currentSrc、音频、去重、同源 iframe、跨域异常隔离、最近播放资源与签名参数保留、排除 blob/HTTP/无关账号状态、读取当前笔记及标题。此检查不是 Android WebView 真机运行检查。
- 真实 FFmpeg 水印样例生成与处理：使用生产 VideoFilters 和 NativeProcess，白色矩形水印位于蓝色背景，带 440Hz AAC 音轨。经用户选区模板同一验证函数转换成 delogo 参数后执行导出，6 项媒体检查通过：水印区域回填蓝色、区域外画面保持、H.264 320×180、AAC 保留、时长约 2 秒、音高保持 440Hz。它只证明固定区域简单背景样例，不证明任意复杂或移动水印均可去除。

## Android 打包

- 资源、Java、DEX、ZIP 条目完整性、对齐和签名通过。DEX 含 DownloadRepair、SaveTransaction、RepairTemplate、RangeDownload、WebMediaScanner 及科幻保存面板。
- 6 个 arm64 原生组件逐字节等同固定上游 AAR，内置脚本散列等同固定 yt-dlp 2026.08.19。
- com.zizai.watermark，版本 0.6.3 / versionCode 9，最低 Android 10，目标 Android 13，arm64-v8a。
- 开发签名 SHA-256：7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb，沿用前版。
- 最终 APK 大小：54,920,294 字节；SHA-256：`247bb079a2db26dceae154690ab400571c2477aad16b56d2aa5bc242298e14fd`。

## 实际使用边界

首次保存图片或视频时需要明确框选水印位置，成功后记住同平台、类型和比例的选区。原片没有水印可直接保存。链接菜单可关闭自动处理或清除选区记忆。不是任意水印自动识别，位置改变需重新框选；复杂背景可能留痕。图片输出 PNG、长边最多 2560；视频输出 SDR MP4、长边最多 1920、30fps，动态图 GIF 与 HDR 不支持此自动修复。

中间原片保持 MediaStore IS_PENDING，在正常成功、失败或取消路径清理；进程被系统强杀时，本轮未验证 pending 条目的系统清理。此版本不提供后台服务与退出应用后的队列恢复。

未连接 Android 真机或模拟器，首次框选对话框布局、位置记忆、WebView 实际播放捕获、手机 FFmpeg、MediaStore 访问及相册写入尚未完成真机验证。本轮没有重新测试外部平台真实下载；此前小红书与海外样例结果见下方历史记录。

复现：

```bash
python3 tooling/test-links.py --ecj /path/to/ecj.jar --json /path/to/json.jar --deps /path/to/engine-cache --sample-video /path/to/sample.mp4
```

扫描脚本检查需 Node，实际修复样例需 FFmpeg/FFprobe、NumPy。构建方法见 README。

实现参考：

- https://www.rfc-editor.org/rfc/rfc9110.html （If-Range 与强表示标识）
- https://www.ffmpeg.org/ffmpeg-filters.html （delogo 固定区域周围像素插值）

---

# 前版 0.6.2 验证记录（历史）

本次包含科幻保存面板、实际传输统计、主流平台目录与短链接路由、应用内登录会话传递及内置引擎升级修复。

## 自动检查

- LinkTests 31 项、EngineTests 16 项、XhsTests 29 项、TransferTests 46 项、平台路由 30 项，共 152 项检查通过。
- 传输检查覆盖真实字节百分比、速度、剩余时间、未知长度、估计长度标识、重试重置、只在提交完成后显示 100%、结构化引擎进度、Cookie 域名与路径范围和注入拒绝。
- 使用实际打包的 yt-dlp ZIP 引擎下载本地 HTTP 视频样例，取得结构化进度事件，输出与原视频逐字节相同。
- 引擎替换检查覆盖旧版本升级、保留较新版本、散列不匹配时保持原文件、成功替换后临时文件清理。
- 元数据检查新增预览分镜不得误报视频、无 codec 的音视频格式识别；含 DRM 的格式被排除。
- 28 个平台目录项按实际引擎规则核对：26 个有专用或内置解析路径，快手及 Threads 仍是网页辅助路径。目录匹配检查不等于真实下载成功验证。
- 内置引擎有 1,752 个提取器条目；这不是 1,752 个独立平台，也不代表每个站点都已实测。

## 真实链接检查

- 再次执行未修改的生产 Java 小红书网络解析代码，用户所给 xhslink.cn 短链接成功跳转，读到标题与文案，返回 1 个经媒体响应验证的视频地址和 1 张封面，选中 720p H.264。
- 测试环境使用 HTTPS 代理和公开 DNS 查询结果写入 JVM 专用 hosts 文件；应用自身的网络限制与域名检查未改动。本次验证了候选媒体响应，没有重新完整下载 95MB 视频。
- 此前 0.6.1 对同一链接完成 95,012,369 字节下载，SHA-256 为 250efc448aabf6ff0f660588293ca8db5bf265052fa95a09dd49efe93100f40e；FFprobe 确认 H.264 1280×720、AAC、1138.220422 秒，FFmpeg 完整读取及音轨提取成功。这是前版完整下载记录。
- YouTube 样例成功提取标题及 36 个格式。电脑 Node/EJS 下载检查取得进度事件，但测试网络返回 HTML 媒体响应，FFmpeg 合并拒绝无效输入，进程失败且未生成完成的视频。不能据此宣称 YouTube 真实下载已通过。
- Vimeo 样例返回需要登录；哔哩哔哩样例在 35 秒检查期限内未完成。两者均不记录为下载成功。

## APK 检查与局限

- Android 资源、Java 编译、DEX、对齐、签名及全部 APK ZIP 条目通过；六个 arm64 原生组件逐字节等同固定上游 AAR，内置 yt-dlp 等同工程固定脚本。
- DEX 包含 SaveProgressView、TransferStats、EngineProgress、SessionCookieJar、PlatformRoutes、BundledEngine；APK 平台目录与源码一致。
- 包名 com.zizai.watermark，版本 0.6.2 / versionCode 8，最低 Android 10，目标 Android 13，arm64-v8a。
- 开发签名 SHA-256：7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb，与旧版一致。
- 未连接 Android 真机或模拟器；科幻面板运行布局、手机登录、取消操作及相册写入尚未做本次真机验证。电脑检查不等于 Android 真机验证。
- 解析规则会随平台变化；需要登录的内容仍须用户自身有权访问的会话。尚未完成全世界平台覆盖验证，也不承诺任意链接成功。

最终 APK 大小：54,916,198 字节。
最终 APK SHA-256：`66f83461fa073f11001134d43955d27185fc24fc707ac2f914b9d02a26394454`

复现回归（构建 APK 后，org.json 需使用真实实现 JAR）：

```bash
python3 tooling/test-links.py --ecj /path/to/ecj.jar --json /path/to/json.jar --deps /path/to/engine-cache --sample-video /path/to/sample.mp4
```

可追加 `--live-xhs https://真实分享链接` 执行生产网络解析检查，需要可用的公开 DNS 与 HTTPS 网络。


## 0.6.5 本次验证

- 安装包使用原项目开发签名；版本号 0.6.5，versionCode 11，arm64-v8a、Android 10 以上。
- 实际生产 VideoFilters 与 NativeProcess 运行 FFmpeg 的双角落标识和八区域样例。
- NumPy 检查每处标识像素被替换，中间模拟字幕、未选区域、画面尺寸与 AAC 音轨保留；440Hz 声音音调与 2 秒时长保持。
- 多区域编码/解码、非法模板、旧单框模板重新确认、八区域上限和总选区检查。
- 全部目录与真实 YoutubeDLRequest 的 list-extractors 输出逐条一致，保留上游失效状态。
- 49 个平台样例只做离线 URL 路由匹配，不能据此认定均可下载。无 Android 真机测试。

可复现：完成 tooling/build-offline.py 后运行 tooling/test-links.py；新增 MultiRegionTests.java、validate-multi-regions.py 和 CatalogTests.java 会随该脚本执行。

本次 tooling/test-links.py 完整运行成功：268 项检查。原始输出见 VALIDATION-0.6.5.txt。

0.6.6 APK 最终大小：56309050 字节；SHA-256：`624ed136e0edfbf49113c291581695ee6694e4a23eb70212adb0d2bfda122ae7`。
