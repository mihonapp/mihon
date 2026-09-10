# Mihon Windows 阅读与扩展商店兼容性审计（2026-09-10）

> 项目：`mihon-w`。本报告区分“仓库索引可读取”“扩展包可安装”“扩展源码可在 Windows 宿主运行”三个阶段；任一前置阶段通过，不代表后续阶段自动兼容。

## 1. 当前问题类型与处置状态

| 范围 | 兼容性问题类型 | 触发条件 / 复现 | 当前处置 | 遗留风险 |
| --- | --- | --- | --- | --- |
| 商店索引 | 数据格式差异 | `index.min.json` 以 UTF-8 BOM 或空白开头；旧实现按第 1 字节判断并误当 protobuf | 已修复：跳过 BOM/JSON 空白后嗅探；新增本地 HTTP 回归测试 | 索引和 gzip 解压仍是无界读取，恶意仓库可造成内存压力 |
| 商店索引 | 接口 URL 差异 | `NetworkExtensionStore.extensionListUrl` 为 `lists/extensions.pb` 等相对 URL；旧实现直接交给 OkHttp，抛 `IllegalArgumentException` | 已修复：相对仓库基址解析；成功/失败响应均关闭 | 跨域绝对列表 URL 当前允许，需要在信任模型中决定是否限制 |
| 商店索引 | JSON / protobuf 双格式 | `index.pb` 实际返回 JSON `NetworkExtensionStore` 对象，或分离列表返回 JSON 对象 | 已修复：现代 JSON 对象、protobuf、旧 JSON 数组统一嗅探；兼容 `CONTENT_WARNING_*` 枚举名 | 尚无大型第三方现代 JSON 仓库的联机样本矩阵 |
| 商店发现 | 仓库入口差异 | 用户粘贴 `repo.json`、`index.pb`、`index.min.json` 或 GitHub `raw/tree` URL | 已支持规范化为仓库基址；真实 Keiyoushi `index.pb` 联机测试通过（扩展数 > 1000） | GitHub `blob` 页面 URL 尚未规范化；非 GitHub 重定向由 OkHttp 处理但没有专项测试 |
| 包下载/安装 | 包格式差异 | 原生 `.mext` 含 `manifest.json`；Tachiyomi APK/JAR 含 `AndroidManifest.xml`、`classes.dex` 或 JVM class | 已有 `.mext` 校验、SHA-256、签名/信任检查和 APK/JAR 转换器 | 转换器只处理主 `classes.dex`，未处理 multidex；转换前逐条 `readBytes()` 无独立膨胀上限；v2/v3-only APK 明确拒绝 |
| 扩展宿主 | API / 依赖缺失 | Android 扩展从 Injekt 获取 `NetworkHelper`、`Json`，或要求默认客户端含异常/User-Agent/Cloudflare 拦截器 | 已补兼容对象与幂等 Injekt 注册；对应宿主测试存在 | Android SDK 面、WebView/JS、原生库、动态资源、未覆盖的 okhttp/kotlinx 二进制 API 仍可能 `ClassNotFound`/`NoSuchMethod` |
| 扩展宿主 | 版本冲突 | 清单 `libVersion` 超出宿主实际 shim 能力；当前校验器只检查 `>= 1.0`，没有上界 | 商店解析覆盖 1.4/1.6 字段；宿主具备 1.4/1.6 常用 API 的一部分 | 必须增加显式兼容范围/能力表，不能把“校验通过”当作“API 兼容”；真实包 smoke test当前是可选门禁 |
| 扩展安全 | 签名/信任差异 | 索引签名键、APK signer、升级 signer 不一致，或仓库没有签名元数据 | 已有仓库签名键回填、APK v1 签名验证、显式信任和升级 signer 检查 | 旧仓库无签名键时仍需用户决策；分离列表跨域后的信任边界需明确 |
| 阅读源 | 文件名与内容不一致 | 独立 WebP/AVIF/HEIF/JXL/TIFF 使用 `.bin`、错误后缀或无后缀；旧工厂只识别 PNG/JPEG/GIF/BMP/WBMP magic，且独立源再次按后缀拒绝 | 已修复：独立图片统一复用 `ImageFormatDetector`，通过 magic 选择；后缀不再二次否决 | WBMP 仍走独立旧探测路径，且不在 `ReaderImageFormat` 枚举中，需决定是否正式纳入解码矩阵 |
| 阅读解码 | 编解码器依赖缺失 | JRE ImageIO 不支持 AVIF/HEIF/JXL/部分 TIFF、CMYK JPEG 或方向元数据 | 已使用内置 ImageIO + APNG 解码器 + 随包 ImageMagick 7.1.2-31 Q8；安装包/便携包已含 codec | 动画 AVIF/HEIF 序列尚未形成固定验收样本；外部 worker 启动失败仍需用户可见诊断 |
| 阅读容器 | 容器格式/安全差异 | ZIP/CBZ、EPUB、7z/CB7、RAR/CBR、TAR/CBT 及 gzip/bzip2/xz tar；错误扩展名、路径穿越、超大条目 | 已按 magic 选择容器，带路径、条目数、XML 深度、展开字节和内存预算限制 | RAR5/加密档案/分卷档案、DRM EPUB 不作为当前已通过能力；需真实样本矩阵记录清楚 |
| 阅读网络链路 | 接口与生命周期 | 在线扩展页经 broker 下载、落盘后切换到本地 reader；取消/失败后重试 | 已有 `DesktopChapterSourceFactory` 在线准备与本地委托、reader 取消恢复测试 | 需要把真实扩展的“搜索→章节→图片→缓存→离线重开”纳入发布门禁 |

## 2. 涉及模块、文件和关键接口

### 商店与扩展兼容

| 文件 | 关键接口 / 职责 |
| --- | --- |
| `desktop-app/.../extension/ExtensionStoreService.kt` | `fetchRepository`、`parseIndex`、`parseProtobufIndex`、仓库 URL 规范化、签名键回填 |
| `desktop-app/.../extension/DesktopNetworkExtensionStore.kt` | 与 Mihon `NetworkExtensionStore` 对齐的 protobuf/JSON 字段、`ExtensionList`、`Resources`、`ContentWarning` |
| `desktop-app/.../extension/DesktopExtensionInstaller.kt` | `downloadAndInstall`、`installFromLocalFile`、摘要/签名/信任策略、转换与落盘 |
| `desktop-app/.../extension/compat/TachiyomiExtensionConverter.kt` | AndroidManifest AXML 解析、DEX→JAR、APK/JAR→`.mext` |
| `extension-sdk/.../validator/ExtensionPackageValidator.kt` | 清单、ZIP 路径、条目数和展开大小校验 |
| `extension-host/.../host/ExtensionClassLoader.kt` | 加载 `WindowsSource`、Tachiyomi `CatalogueSource`、`SourceFactory` |
| `extension-host/.../host/ExtensionHostEngine.kt` | Injekt 兼容注册、扩展加载、源生命周期和 IPC 命令 |
| `extension-host/.../eu/kanade/tachiyomi/network/NetworkHelper.kt` | Android 扩展期望的默认客户端与拦截器 |
| `desktop-app/.../extension/DesktopSourceManager.kt` | 宿主进程调用、源/筛选/偏好 IPC、在线章节接口 |

### 阅读兼容

| 文件 | 关键接口 / 职责 |
| --- | --- |
| `reader-core/.../source/ChapterSource.kt` | `ChapterSourceFactory.create`、`pages`、`open`、关闭语义 |
| `reader-core/.../source/LocalChapterSourceFactory.kt` | 依 magic 分派目录、独立图、ZIP/EPUB、7z、RAR、TAR 及压缩 TAR |
| `reader-core/.../source/StandaloneImageChapterSource.kt` | 独立图片大小上限、单页输入生命周期 |
| `reader-core/.../source/ImageEntryPolicy.kt` | 容器内图片后缀白名单与安全路径规范化 |
| `reader-core/.../image/ImageFormatDetector.kt` | JPEG/PNG/GIF/WebP/AVIF/HEIF/JXL/BMP/TIFF magic/brand 探测 |
| `reader-core/.../image/ImageContracts.kt` | `PageDecoder.probe`、`decodeFull`、`decodeRegion`、`ImageMetadata` |
| `reader-core/.../image/CompositePageDecoder.kt` | ImageIO、纯 Java APNG、随包 codec 路由 |
| `desktop-app/.../reader/codec/PackagedCodecPageDecoder.kt` | ImageMagick worker、超时/取消/进程树清理、BGRA 输出验证 |
| `desktop-app/.../reader/DesktopChapterSourceFactory.kt` | 在线章节准备、缓存、再委托本地 source factory |
| `desktop-app/.../reader/DesktopReaderFactory.kt` | 把 chapter source、decoder、预算和 session 组装进桌面阅读器 |

## 3. 可重复触发步骤

### 仓库索引兼容

1. 启动本地 HTTP 服务，令 `/repo/index.pb` 返回 404，`/repo/index.min.json` 返回 `EF BB BF` + 空白 + JSON 数组。
2. 调用 `ExtensionStoreService.fetchRepository(http://127.0.0.1:<port>/repo)`。
3. 修复前：作为 protobuf 解码并抛 `ProtobufDecodingException`；修复后：得到 1 个扩展。

1. 令 `/repo/index.pb` 返回 `extensionListUrl="lists/extensions.pb"` 的 protobuf，列表放在 `/repo/lists/extensions.pb`。
2. 修复前：OkHttp 因 URL 无 scheme 抛 `IllegalArgumentException`；修复后：相对仓库基址解析并返回扩展。

1. 令 `/repo/index.pb` 返回现代 JSON `NetworkExtensionStore` 对象，`contentWarning` 使用 `CONTENT_WARNING_SAFE`。
2. 修复前：按旧 JSON 数组反序列化并抛 `JsonDecodingException`；修复后：按现代 store schema 解析。

### 独立现代图片

1. 分别创建具有有效 WebP、AVIF、HEIF、JXL、TIFF 文件头但扩展名为 `.bin` 的独立章节资源。
2. 调用 `LocalChapterSourceFactory.create(assetKind="IMAGE")`。
3. 修复前：`UnsupportedFormat`；修复后：每个资源均产生一个可打开的页面。完整像素解码仍由后续 decoder/真实 fixture 门禁负责。

### 扩展宿主常见触发

1. 安装仅含 APK Signature Scheme v2/v3 的 APK：安装器明确返回不支持，而不是静默安装。
2. 安装带 `classes2.dex` 才包含 source class 的 multidex APK：当前转换器只转换 `classes.dex`，加载阶段会找不到类。
3. 加载调用尚未提供 Android API、WebView、JNI `.so` 或不兼容依赖签名的扩展：宿主可能在类加载或首次调用时失败。
4. 不提供 `-Dmihon.extension.smoke.package=<path>`：真实扩展 smoke test会跳过，因此普通全测不能证明任意仓库 APK 可运行。

## 4. 兼容目标与验收标准

### 商店/扩展目标

- 仓库入口：支持 `repo.json`、`index.pb`、`index.min.json` 完整 URL及仓库基址；支持 GitHub raw/tree 常见形式。
- 索引载荷：支持 gzip/非 gzip、protobuf `NetworkExtensionStore`、现代 JSON `NetworkExtensionStore`、旧 JSON 扩展数组；嵌入式或 `extensionListUrl` 分离式列表均可。
- 版本范围：索引数据至少覆盖 extension lib 1.4 和 1.6；运行时只对已有兼容 API 与真实 smoke 包声明通过。新增显式宿主 API 范围前，不承诺任意 `>=1.0` 扩展都可执行。
- 包目标：Windows 原生 `.mext`/JVM `jarUrl` 为完整支持路径；v1 可验证、单 DEX、无 JNI/重 Android UI 依赖的 Tachiyomi APK为迁移兼容路径，不把原 APK 原样执行定义为目标。
- 验收：列表字段/URL/签名键正确；下载摘要正确；签名与升级 signer 策略生效；安装后宿主可加载源，并通过搜索、详情、章节、页面 URL、筛选、源偏好及网络 broker 的端到端调用。

### 阅读目标

- 容器：目录、独立图片、ZIP/CBZ、EPUB、7z/CB7、RAR4/CBR、TAR/CBT、tar.gz/tar.bz2/tar.xz；错误后缀时按 magic 识别。
- 图片：JPEG、PNG/APNG、GIF、WebP、AVIF、HEIF/HEIC、JPEG XL、BMP、TIFF；支持错误/缺失后缀的独立图和容器内白名单图。
- 行为：静态全图、区域采样、EXIF 方向、CMYK→sRGB、GIF/WebP/APNG 动画帧与持续时间；取消后不残留 codec 子进程且可继续解码下一页。
- 安全：拒绝路径穿越/链接逃逸/恶意 XML；执行条目数、单页字节、章节展开字节、图像维度/像素和内存预算限制。
- 发布验收：核心与桌面测试零失败；真实 codec fixture 矩阵通过；从仓库真实扩展完成“浏览→章节→在线图片→缓存→离线重开”；EXE/MSI/便携包均含 codec，并在 Windows 10 22H2/Windows 11 x64 实机验证。

## 5. 已尝试方案、验证与遗留工作

### 本轮完成

- 真实 Keiyoushi `index.pb` 联机读取通过，扩展数断言 `> 1000`。
- 新增 `ExtensionRepositoryCompatibilityTest`：覆盖 BOM/空白旧 JSON、现代 JSON store、相对分离 protobuf 列表。
- 修正商店响应关闭、JSON/protobuf 嗅探、现代枚举别名和相对 URL 解析。
- 新增 `StandaloneModernImageSourceTest`，并让独立现代图片与实际 decoder 共用 magic 格式表。
- 聚焦测试通过：`ExtensionRepositoryCompatibilityTest`、`ExtensionStoreServiceTest`、`StandaloneModernImageSourceTest`、`ChapterSourceContractTest`。
- 全量相关模块结果：`reader-core` 173 tests / 0 failures / 0 errors / 1 skipped；`extension-host` 17 / 0 / 0 / 1；`desktop-app` 464 / 0 / 0 / 1。三个模块的 `spotlessCheck` 均通过。跳过项包含需要外部真实扩展包或显式环境开关的 smoke/live 门禁。

### 既有方案

- reader 已实现随包 ImageMagick、纯 Java APNG、TwelveMonkeys/ImageIO 回退、超时/取消恢复和 Windows 打包资产检查。
- extension 已实现 `.mext` 沙箱式 broker、APK/JAR 转换、签名/信任模型、Tachiyomi Source/SourceFactory 适配、筛选与源偏好 IPC。
- `NetworkHelper` 已补 `UncaughtExceptionInterceptor`、User-Agent、Cloudflare 拦截器，宿主启动时补 `Json`/Context/Application 的 Injekt 单例。

### 下一轮优先级

1. 给仓库索引、gzip 解压、扩展下载和 APK 转换器增加流式硬上限，避免无界内存读取。
2. 给 `ExtensionPackageValidator` 增加“已验证 API 能力范围”而非仅 `libVersion >= 1.0`；建立 1.4/1.6 真实扩展样本集。
3. 决定并实现 multidex 策略；明确 v2/v3-only APK、JNI、WebView/JS 扩展的用户提示和支持边界。
4. 扩充动画 AVIF/HEIF、RAR5/加密/分卷、DRM EPUB 的固定样本；不支持项必须返回可诊断错误。
5. 在整理后的干净构建树上执行完整测试、极限内存、真实扩展网络链路、安装包安装/启动/交互门禁。

## 外部格式基线

- Keiyoushi `repo.json`：<https://github.com/keiyoushi/extensions/blob/repo/repo.json>
- Keiyoushi 仓库说明：<https://github.com/keiyoushi/extensions/blob/repo/README.md>
- Keiyoushi 当前扩展开发约定（新扩展使用 lib 1.6，旧 1.4 仍存在）：<https://github.com/keiyoushi/extensions-source/blob/main/CONTRIBUTING.md>
