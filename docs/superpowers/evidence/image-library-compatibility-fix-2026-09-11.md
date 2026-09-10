# 阅读图片与书架兼容修复报告（2026-09-11）

## 1. 图片格式兼容问题

### 现象与根因

- 现象：在线章节会出现空白页，并统一显示“不支持此章节格式”；首页、详情页、书架页的封面在 AVIF 等格式下退化为空占位。
- 根因一：在线页面下载、403/登录失效、服务器返回 HTML/JSON 错误页等异常，都被 `OnlineChapterSource` 包装为 `UnsupportedFormat`，因此真实网络问题被误报为章节容器格式问题。
- 根因二：扩展返回的页面虽然已补取 `HttpSource.getImageUrl(page)`，但源级图片请求头未随页面跨进程传递，要求 Cookie/Referer/自定义请求头的 CDN 会拒绝请求。
- 根因三：阅读器已有按文件魔数识别 JPEG/PNG/WebP/GIF/AVIF/HEIF/JXL/BMP/TIFF 以及打包 ImageMagick 兜底，但首页、详情、书架共用的 `DesktopImageLoader` 只调用 Skia，且本地封面扫描漏掉 `.avif` 等扩展名。

### 涉及代码路径

- 在线章节：`desktop-app/src/main/kotlin/mihon/desktop/extension/OnlineChapterSource.kt`
- 扩展页模型：`extension-sdk/src/main/kotlin/mihon/extension/source/model/SourceModels.kt`
- Mihon 扩展适配：`extension-host/src/main/kotlin/mihon/extension/compat/TachiyomiCatalogueSourceAdapter.kt`
- 错误类型与中文提示：`reader-core/src/main/kotlin/mihon/reader/source/ReaderFailure.kt`、`desktop-app/src/main/kotlin/mihon/desktop/i18n/DesktopStrings.kt`
- 首页/详情/书架共用图片链：`desktop-app/src/main/kotlin/mihon/desktop/image/DesktopImageLoader.kt`，调用方为 `MangaCover`、`MangaBackdropBanner` 及浏览、书架、历史、更新页面。
- 阅读器格式识别/解码：`reader-core/src/main/kotlin/mihon/reader/image/ImageFormatDetector.kt`、`CompositePageDecoder.kt`、`desktop-app/src/main/kotlin/mihon/desktop/reader/codec/PackagedCodecPageDecoder.kt`

### 修复方案

- `Page` 增加向后兼容的 `headers` 默认字段；适配 Tachiyomi `HttpSource.headers`，并继续在 `imageUrl` 缺失时调用原 Mihon 的 `getImageUrl(page)`。
- 在线图片下载合并页面请求头；HTTP/网络失败改为 `ReaderFailure.RemoteImage`，界面提示检查网络或图源登录；200 响应若实际为 HTML/JSON/未知魔数则归类 `UnsupportedImage`，且不写入缓存。
- 页面逻辑名称按 URL 后缀生成，但解码仍以魔数为准，避免所有在线页被伪装成 `.jpg`。
- `DesktopImageLoader` 扩展本地目录/压缩包候选到 GIF、AVIF、HEIF、JXL、TIFF；Skia 解码失败时，AVIF/HEIF/JXL/TIFF 自动调用安装包内同一套受内存、磁盘、时间限制的 ImageMagick，再转成 PNG 交给 Compose。
- 网络图片仅在成功解码后写入磁盘缓存；旧的不可解码缓存会自动删除。异常封面继续降级为现有标题首字/书本占位，不让整个页面崩溃；阅读页则显示可重试的分类错误。
- GIF 在封面等静态列表展示首帧；阅读器链保留动画帧元数据与既有动画显示能力。

### 验收/验证方式

- 自动生成并真实解码 JPEG、PNG、WebP、GIF、AVIF 五种样本，逐一断言尺寸；另验证本地漫画目录能发现并显示 `cover.avif`。
- 在线源测试验证自定义图片请求头能通过受保护端点；HTML 登录页被拒绝为图片且不会进入正常阅读链。
- `reader-core` 全量测试、扩展适配测试和桌面端聚焦测试通过。
- 手工验收：分别在浏览首页、在线详情、书架、历史/更新列表观察五种封面；打开对应章节翻页并重试网络错误。封面异常显示占位，阅读网络错误显示“页面下载失败…”，未知图片显示“不支持此图片格式”。

## 2. 图书加入书架逻辑问题

### 现象与根因

- 现象：已在书架的按钮仍可点击，但再次执行“加入”而不能移除；移除后再加入没有完整流程；章节网络失败时服务吞掉异常并用空列表继续提交，界面会显示加入成功，实际得到无章节的脏书架项。
- 触发条件：重复点击详情页按钮、图源章节接口超时/报错、扩展宿主不可用，或用户希望从在线详情移出后再加入。
- 根因：UI 回调被写死为 `addOrUpdateOnlineManga`；同步服务把章节异常转成 `emptyList()`；收藏状态、章节同步和数据库提交之间缺少明确失败边界。

### 涉及代码路径

- 书架同步事务：`desktop-app/src/main/kotlin/mihon/desktop/extension/OnlineMangaSyncService.kt`
- 图源异常传播：`desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopSourceManager.kt`
- 在线详情回调：`desktop-app/src/main/kotlin/mihon/desktop/ui/browse/BrowseContentView.kt`
- 按钮状态/文案：`desktop-app/src/main/kotlin/mihon/desktop/ui/browse/OnlineMangaDetailScreen.kt`、`DesktopStrings.kt`
- 持久化：`desktop-library-data/src/main/kotlin/mihon/desktop/library/db/SqlDelightLibraryRepository.kt`、`Library.sq`

### 修复方案

- 加入前先完成详情/章节读取，章节接口失败直接向上抛出；数据库事务尚未开始，因此不会新增或错误收藏记录。
- 同一 `(sourceId, mangaUrl)` 继续复用同一漫画 ID，重复加入只更新同一行与章节，不产生副本。
- 新增 `removeFromLibrary`：只把 `favorite=false`、`dateAdded=0` 并更新时间戳，不删除漫画、章节、阅读进度或稳定 ID；再加入时恢复收藏并设置新的 `dateAdded`。
- 详情按钮按 `inLibrary` 真正切换加入/移出，进行中禁用；失败保持原状态并显示错误。按钮在已收藏时明确显示“移出书架”。
- 远程扩展宿主缺失或章节请求失败不再静默返回空列表。

### 验收/验证方式

- 自动化场景：首次加入成功；重复加入返回同一 ID 且数据库只有一行；移出后 `favorite=false` 但章节仍在；再加入仍为原 ID；章节网络异常时抛错且数据库零新增。
- 手工场景：在线详情连续执行加入 → 再点移出 → 再加入；刷新书架确认状态一致；断网或让图源返回 5xx 后点击加入，确认按钮恢复、错误可见且书架无幽灵记录。

## 3. 参考 Mihon 对应实现

### 现象与根因

- 原 Mihon 在线阅读在 `HttpPageLoader`/`Downloader` 中发现 `page.imageUrl` 为空时调用 `HttpSource.getImageUrl(page)`，并使用图源自己的 client/headers；本项目此前仅部分移植了 URL 解析，未把 headers 完整带到桌面下载链。
- 原 Mihon 在 `App.kt` 配置 Coil，并通过 `MangaCoverFetcher` 使用源专属 client/headers、定制封面与磁盘缓存；现代格式由自定义 `data/coil/ImageDecoder.kt`（libvips）按内容处理。本项目是 Compose Desktop + Skia，不能直接复用 Android Coil/libvips，因此需要共享打包 codec 的桌面等价实现。
- 原 Mihon 的 `MangaViewModel.toggleFavorite()` 明确区分加入与移除；`UpdateManga.awaitUpdateFavorite()` 在加入时写 `dateAdded`、移除时清零，并保留记录。本项目此前只有永远置 true 的同步方法。

### 涉及代码路径

- 原 Mihon 页面 URL：`app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt`
- 原 Mihon 下载流程：`app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt`
- 原 Mihon 图片加载：`app/src/main/java/eu/kanade/tachiyomi/App.kt`、`app/src/main/java/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt`、`ImageDecoder.kt`
- 原 Mihon 收藏切换：`app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt`、`app/src/main/java/eu/kanade/domain/manga/interactor/UpdateManga.kt`
- Windows 对应实现：`TachiyomiCatalogueSourceAdapter.kt` → `SourceModels.Page` → `OnlineChapterSource.kt`；`BrowseContentView.kt` → `OnlineMangaSyncService.kt` → `SqlDelightLibraryRepository.kt`。

### 修复方案

- 保留 Mihon 的行为语义而非照搬 Android 技术栈：页面 URL 与源 headers 由扩展侧解析并跨 IPC 传输；桌面侧负责受控网络、魔数校验、缓存和错误分类。
- 封面链采用“Skia 优先、打包 ImageMagick 兜底”，对应 Mihon 的“平台原生/Coil 优先、自定义现代格式解码器兜底”。
- 收藏操作对齐 Mihon 的 toggle：加入/移除互为逆操作，移除不破坏章节和进度，`dateAdded` 生命周期一致；网络同步失败不能冒充收藏成功。

### 验收/验证方式

- 对照流程逐节点验证：`getPageList` 后页面已有最终 URL/headers；下载响应通过魔数校验；五种目标格式进入同一展示组件；收藏切换前后数据库 ID、favorite、dateAdded、章节数符合预期。
- 版本目标：Windows 10 22H2 / Windows 11 x64；当前扩展 SDK/宿主协议新增字段有默认值，旧页面 payload 可继续反序列化；支持 Tachiyomi/Mihon `CatalogueSource` 与 `HttpSource` 适配层以及内置源。
- 当前遗留：特定站点若依赖 WebView 人机验证或动态 Cookie，仍需先在图源 Cookie/登录界面完成验证；此类情况现在会明确报告网络/登录错误，不再伪装为图片格式错误。
