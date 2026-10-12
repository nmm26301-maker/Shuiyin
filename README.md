# 水印工坊 0.6.6 · Android

面向 Android 10 及以上 arm64 手机的开发测试版；目标 Android 13，versionCode 12。沿用原工程签名，可以覆盖安装 0.6.5。

## 本次扩展

- 内置 gallery-dl 1.32.16 图集引擎及纯 Python 依赖，与 yt-dlp 视频／音频引擎并行使用。Instagram 作品、Pinterest、Pixiv、DeviantArt、Flickr、Imgur 等由上游图集解析器提取图片及混合媒体；登录内容可能需要本人有效 Cookie。
- 图集引擎 973 条上游规则、视频／音频引擎 1,752 条规则均可搜索、分页查看。规则不是独立平台数量，也不表示每条规则都能成功下载。49 个常用平台入口沿用前版。
- 多链接队列最多保留 20 条链接，支持视频／图片优先、只保存图集图片、只保存音频。重复链接在相同模式下跳过；一条链接最多提取 24 个素材。没有每日保存次数或会员额度。
- 每个成品保存后记录媒体库地址；重试会重新解析源页面取得当前地址，并检查已保存项目是否仍存在。暂停、退出后再次打开可继续等待／失败项目，不接续旧的临时字节缓存。清理完成项目或移出队列不会删除已保存文件。
- 队列不把视频封面作为图集原图；音频模式优先独立音频，没有时尝试从视频提取。分享多段含链接的文字可进入队列编辑页，确认添加后开始保存。
- 图集请求保留解析器的 User-Agent 和 Referer，继续使用导入的本人 Cookie 或应用内网页会话；程序不读取其他浏览器的 Cookie。

## 使用

1. 进入“链接提取”，粘贴分享链接，点“解析链接”。小红书保留专用解析；图集站优先 gallery-dl，其他站先用 yt-dlp，失败时尝试图集或网页素材。
2. 要获取原图时点“提取图集原图”；视频封面仍明确标注为封面。
3. 批量保存点“多链接保存队列”，粘贴多条分享文案，选择类型，添加队列并点“开始／继续”。取消当前任务后可继续；失败项目可以单独或一起重试。
4. 首次保存图片或视频时确认去水印选区，最多 8 处，可移动、缩放和删除。平台标识与博主头像／名字分别框选，字幕留在框外。小红书底部两处建议框仅供调整，不是自动识别。
5. 同平台、同素材类型和画面比例会记住整组选区。水印位置改变后，在“保存去水印设置”清除位置重新框选。跳过修复或关闭后保存原片，结果会说明未执行去水印。

输出保存在系统 Pictures / Movies / Music 下的“水印工坊”，并进入作品列表。下载后处理中间原片保持隐藏，修复成功保留最终成品。真实下载字节、速度、剩余时间和处理阶段显示在科幻进度面板；单文件下载最多 2GB，保存次数不限。批次进度代表当前素材，不是全队列整体百分比。

## 实际限制与验证

新增图集脚本在开发主机上以打包的依赖完成 Wikimedia 公开图片解析和下载，文件通过图片解码检查。真实上游 Imgur 提取器以 API 固定样例验证图集、同站单作品委派与混合素材；Instagram、Pinterest、Pixiv、Flickr、DeviantArt 只验证 URL 路由匹配，没有逐站登录或下载验证。

现有 268 项回归、新增 30 项 Java 图集／队列检查及打包 Python 集成检查通过。双角落水印、八区域修复、选区外字幕与音轨保留仍通过生产滤镜样例检查。详细结果见 VALIDATION.md 和 VALIDATION-0.6.6.txt。

**未连接 Android 真机或模拟器。** 手机上的 Python 原生启动、网络、选区弹窗、队列恢复、MediaStore 和 UI 仍需设备检查；主机测试不能证明这些步骤在手机成功。图集引擎只处理当前作品，允许最多 2 层、8 个同站单作品委派，不遍历资料页、搜索页或跨站队列。目录保留完整上游规则，部分规则因此不会在本应用入口返回素材。

需要登录、区域限制、加密保护或站点变化时可能失败。不实现 DRM 解密、移动水印跟踪或生成式视频修复；固定区域像素填补可能留痕，无法恢复被遮挡的真实内容。图片修复输出 PNG、长边最多 2560，不保留原始 EXIF；GIF 不做自动修复。视频修复输出 SDR H.264/AAC MP4，长边最多 1920、约 30fps，HDR 不支持该路径。处理期间保持应用前台；尚未实现后台下载服务。

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

Android Studio / Gradle：JDK 17、SDK Platform 35、Build Tools 35.0.0、Gradle 8.9、AGP 8.7.3，运行 `gradle --no-daemon assembleDebug`。保留 extractNativeLibs=true / useLegacyPackaging=true，运行时需要已解压的原生程序。

手动构建：

```bash
python3 tooling/prepare-engine.py --dir /path/to/dependencies
python3 tooling/build-offline.py --sdk /path/to/sdk --deps /path/to/dependencies --ecj /path/to/ecj.jar
```

输出 build-offline/WatermarkWorkshop-0.6.6.apk。使用 JDK 的 javac 时可省略 --ecj。开发签名 tooling/development.jks，别名 androiddebugkey，密码 android。签名 SHA-256：7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb。

图集源码及许可证包含在 app/src/main/res/raw/gallery_bundle ZIP；六个 Python 包均为校验固定的纯 Python wheel。tooling/gallery-lock.json 保留下载地址与 SHA-256。重建同一图集包运行 `python3 tooling/prepare-gallery.py --cache /path/to/wheels`；修改 runner 或依赖后需同步 GalleryEngine.SHA，测试会校验一致性。

构建后回归：

```bash
python3 tooling/test-links.py --ecj /path/to/ecj.jar --json /path/to/org-json.jar --deps /path/to/dependencies --sample-video /path/to/sample.mp4
python3 tests/live-gallery.py
```

回归需要 Node、FFmpeg/FFprobe、Python 3、NumPy；可选公开图集网络检查还需要 Pillow。live-gallery.py 默认 Wikimedia Example.jpg，可传入其他公开图片作品 URL；不要把默认样例通过视为其他平台验证。

第三方来源、许可证及构建材料见 THIRD_PARTY_NOTICES.md、LICENSE、LICENSE-MIT 和 licenses/。gallery-dl 作为独立 Python 子进程保留 GPL-2.0-only 条款，不修改其上游源码；其他组件各自许可证适用。
