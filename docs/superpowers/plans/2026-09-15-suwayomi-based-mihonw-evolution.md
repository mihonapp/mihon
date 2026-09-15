# 基于 Suwayomi-Server 的 MihonW 构建与优化执行计划

> **For agentic workers:** 使用 writing-plans 的任务划分和 executing-plans 的执行方式推进；独立子任务可并行。直接采用负责该子任务的 subagent 调查结果，不重复审查其已完成的调查。必要的实现测试和最终产品验收照常执行。

**Goal:** 参考 Suwayomi-Server 已有实现，补齐 MihonW 的扩展兼容、网络会话、后台任务、数据交换和 Windows 发行能力，继续完成已批准的完整 Windows 版本。

**Architecture:** 保留 Compose Desktop 界面、reader-core、SQLDelight 数据库和独立 extension-host。将 Suwayomi 中适用的行为与源码移植到现有模块，主程序继续拥有书库、下载和账户数据；新增浏览器能力通过按需启动的独立组件提供。当前计划不改变既定原生桌面产品路线。

**Tech Stack:** Kotlin、Compose Desktop Material 3、coroutines/StateFlow、SQLDelight/SQLite、OkHttp、JVM extension-host、Windows Job Object、Gradle/jpackage；浏览器兼容采用 Suwayomi 的 JCEF/KCEF 方向，单独处理原生组件和运行时。

**状态：** 2026-09-15 调查完成，执行计划已编写。下文未勾选事项尚未执行；本轮仅新增本文档，未修改应用代码、安装程序或用户数据。

## 全局约束

- 完整目标沿用 `docs/superpowers/specs/2026-08-31-windows-port-design.md`，包含书库、图源、扩展、阅读、下载、历史、分类、跟踪、备份、设置、更新和 Windows 发行。
- 支持 Windows 10 22H2 / Windows 11 x64，交付安装程序与 portable ZIP。
- 保留 Material 3 桌面布局、中文界面、窗口与鼠标键盘行为。
- 浏览或阅读在线漫画不自动入书架、加入分类；入库操作显式且可撤销。
- 普通滚轮翻页保持阅读控件隐藏；加载章节时 Esc、返回和取消立即响应。
- Android 原工程保持可构建。仅在本批任务影响共享模块、依赖或根构建配置时执行对应 Android 回归。
- 既有数据以 MihonW SQLDelight 为唯一业务事实来源，不引入另一套 Suwayomi 数据库，不变更既有 sourceId、漫画和章节身份。
- 扩展兼容范围以真实样本和 API 契约证明，不承诺任意 Android APK 均可运行。
- 保留用户已有未提交改动。执行前记录其归属与内容，只提交本任务涉及的文件，不使用 reset/clean 覆盖工作。
- 直接采用 subagent 已交付的调查，不做重复调查或循环审查。并行实施按文件归属划分；同一 checkout 的 Gradle 构建串行执行。
- 每个任务交付可运行行为、针对性测试和简短证据；同一版本通过的测试不因流程要求反复重跑。

## 1. 本次调查基线

### MihonW

- 工作目录：`D:\my project\mihon-w`。
- 当前分支：`main`；HEAD：`d88913d13078262ac051d9745111880362e2ff9a`。
- 当前仅一个 Git worktree；已有 12 个未提交修改文件，478 行新增、65 行删除。
- 未提交修改已包含 source 缓存失效后的单次重载、SourceFactory 去重/原子替换、加载中取消和资源释放。下一步应收口这些修改，不重复实现。
- 桌面主模块当前是 Kotlin/JVM，复用 Compose Multiplatform；不要把所有模块已完成 KMP 拆分当作现状。
- 打包配置版本为 `0.1.3`，桌面运行时设置 Java 17。构建沿用本机 Corretto 23。
- 现有 app-image 时间为 2026-09-15 09:57:25、MSI 为 09:57:49；installer EXE 为 2026-09-11，portable ZIP 为 2026-09-12。各包并非同批产物，时间戳也不能证明对应当前全部改动。
- 本次未重新运行构建、测试或安装，因此不把已有报告当作当前整个工作区的通过证明。

### Suwayomi

- 正式参考版本：[v2.3.2243](https://github.com/Suwayomi/Suwayomi-Server/releases/tag/v2.3.2243)，发布于 2026-07-13，提交 `1d583ca4a718646e17a4c62d23fe8e5edccaf774`。
- 源码调查快照：`master@d10e000e1fdcac6f3c84d002f0b459c90c1b00f3`。
- 正式版和当前 master 均要求 JVM 21。正式版 Kotlin 2.4.0 / Gradle 9.5.1，master Kotlin 2.4.10 / Gradle 9.7.1。依据：[版本目录](https://github.com/Suwayomi/Suwayomi-Server/blob/1d583ca4a718646e17a4c62d23fe8e5edccaf774/gradle/libs.versions.toml)、[JVM target 配置](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/build.gradle.kts#L32-L60)。
- 行为对照优先固定正式版。使用 master 的修复时，逐项记录具体文件和提交，不依赖浮动分支。

## 2. 接入方式与取舍

| 方式 | 收益与代价 | 本计划选择 |
|---|---|---|
| 将适用的兼容和服务逻辑移植到现有模块 | 保留现有 UI、数据和阅读器；需要维护明确的兼容边界及上游差异清单 | **主线采用** |
| Compose 客户端连接独立 Suwayomi Server | 可复用完整服务；新增 Java 21、端口、认证、启动管理、API 适配和数据迁移 | 作为以后明确提出服务器/NAS需求时的独立路线 |
| 将整个 server 加入桌面进程依赖 | 必须解决 Java 版本、全局依赖注入、数据库和服务生命周期耦合 | 本轮不采用 |

Suwayomi 是完整服务端，并非可直接加入现有 Java 17 应用的后端 SDK。保留当前 SQLDelight 与 reader-core，能够把工作投入现有产品的实际缺口。上游服务依赖与数据库见 [server/build.gradle.kts](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/build.gradle.kts#L35-L95)。

## 3. 已有能力与本轮补齐范围

| 领域 | 当前已有 | 本轮重点 |
|---|---|---|
| 扩展 | 安装/转换、独立 host、Source/SourceFactory、部分 Android API、偏好 IPC | 初始化与恢复、持久化 Context、API/资源契约、更新卸载生命周期 |
| 网络 | 主程序 broker、兼容扩展 OkHttp、Cookie/UA 设置、共同图片下载入口 | 两条链路的会话/代理/取消一致性，按来源身份隔离 |
| 浏览器能力 | 系统浏览器打开入口；CloudflareInterceptor 目前只是兼容标记 | 按需 WebView、JavaScript 回调、会话交换、明确的验证/失败状态 |
| 下载 | 持久 JSON 队列、原子保存、恢复、章节/页并发、暂停/重试、离线阅读 | 按来源公平调度、损坏页修复、队列损坏恢复与真实取消 |
| 更新 | 分类过滤、跳过规则、自动下载、运行中调度 | 归一两套入口、取消、失败源隔离、Windows 后台唤起 |
| 备份 | 校验、事务导入、导出、字段策略、往返测试、定时保留 | 真实跨应用样本、源迁移身份、失败报告与发行版实测 |
| 跟踪 | 9 个真实 tracker、离线队列、阅读/登录后同步 | 启动/联网后重试、凭据迁移、与当前 Mihon tracker 清单补齐 |
| 界面/阅读 | 统一漫画详情、自适应书库、多阅读模式、预取、分块/动画、缓存 | 流程贯通、长列表性能、加载取消、状态反馈与回归 |
| Windows 发行 | EXE/MSI/ZIP 构建、Job Object、更新下载和脚本 | 一致版本产物、实际升级回滚、干净 Win10/11 验收 |

当前 `SuwayomiTracker.kt` 仅实现 Suwayomi 跟踪客户端，不代表已接入 Suwayomi 服务引擎。当前扩展进程实现为 ProcessBuilder + stdin/stdout IPC + Job Object；旧设计中的 AppContainer 和 Named Pipe 还需独立完成与验证。

## 4. 执行任务

每项任务采用“明确场景 → 补充会暴露缺口的测试 → 实现 → 对应验证 → 记录和提交”的顺序。下面的验收场景就是测试输入和预期结果，不能只写一个接口存在性测试代替实际行为。

### T0：收口当前工作区并固定可执行基线

**文件：** 当前 12 个修改文件；新增 `docs/superpowers/evidence/2026-09-15-suwayomi-baseline.md`、`docs/upstream/suwayomi-reference.md`。

- [x] 记录 HEAD、当前 diff、现有包的版本/时间/文件清单；区分已提交、工作区修改、已有测试及安装产物。
- [x] 使用现有测试验证 source 缺失单次恢复、SourceFactory 只初始化一次、注册表原子切换、加载中取消与关闭资源。
- [x] 只修复该组测试暴露的问题；保存已验证的补丁边界，为后续 `codex/suwayomi-*` 分支确定起点。
- [x] 固定上游 tag/SHA，建立“上游文件 → 本地文件 → 采用原因 → 行为差异 → 验证”的清单。发生源码复制时一并保留原始版权和许可文本，更新第三方清单。

**验证：** `DesktopSourceManagerTest`、`DesktopSourceManagerSourcePreferenceIpcTest`、`WindowsExtensionProcessManagerTest`、`ExtensionHostEngineTest`、`ReaderScreenActionsTest`、`DefaultReaderSessionTest`。已有新增断言通过前，不将修改记为完成。

**交付：** 一个可继续开发的实测基线；不先做全项目重构。

### T1：扩展生命周期与偏好恢复

**修改：**

- `desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopSourceManager.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopExtensionInstaller.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/compat/TachiyomiExtensionConverter.kt`
- `extension-host/src/main/kotlin/mihon/extension/host/ExtensionHostEngine.kt`
- `extension-host/src/main/kotlin/android/app/Application.kt`
- `extension-host/src/main/kotlin/android/content/Context.kt`
- `extension-host/src/main/kotlin/android/content/SharedPreferences.kt`

**新增：** `extension-host/src/test/kotlin/mihon/extension/host/ExtensionContextPersistenceTest.kt`。

- [x] 将 Context files/cache/preferences 按扩展身份分区持久化；源特有偏好继续按 sourceId 区分，避免所有扩展使用公共临时目录。
- [x] 在 source 初始化后、业务调用前回放保存的偏好。重启或崩溃恢复不依赖打开“图源设置”页面。
- [x] 确保同一扩展并发加载共享一次初始化；SourceFactory 一次生成多个 source；失败不发布半套注册表。
- [x] 安装/更新时核对原始 sourceId、入口类、多 dex、assets 和 API 版本范围；扩展来源迁移不生成新的书库身份。
- [x] 卸载/替换时取消所属任务、关闭 ClassLoader 和文件句柄；Windows 锁文件时保留旧可用版本并记录下次启动清理项。

**验收：** 源 A 修改偏好后重启，直接搜索/阅读使用新值；源 B 不受影响；host 崩溃后恢复同样成立；更新失败可继续用旧版本；多语言源不丢失、不重复。

**上游依据：** [Extension.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/extension/Extension.kt)、[PackageTools.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/PackageTools.kt)。

### T2：统一网络、Cookie、代理和取消语义

**修改：**

- `desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopNetworkHelper.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopCookieStore.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/SourcePageDownload.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/OnlineChapterSource.kt`
- `desktop-app/src/main/kotlin/mihon/desktop/extension/WindowsExtensionProcessManager.kt`
- `extension-host/src/main/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`
- `extension-sdk/src/main/kotlin/mihon/extension/ipc/IpcContracts.kt`

**新增：** `desktop-app/src/test/kotlin/mihon/desktop/extension/ExtensionNetworkSessionContractTest.kt`。

- [x] 为网络调用携带扩展/source 身份和 requestId；声明域、动态图片域、Cookie 与取消范围都属于该身份，避免全局域名并集授权给所有扩展。
- [x] 将兼容扩展标准 OkHttp 的实际传输接到统一网络策略，保留扩展自定义拦截器、`getImage` 和图片解混淆执行顺序。
- [x] 统一代理、UA、超时、请求头、Referer、重定向、Cookie 读写和持久化；配置改变后使受影响的客户端/来源缓存失效。
- [x] 用可取消的 OkHttp Call 桥接协程，取消请求向 IPC 和底层 Call 传播；单个阅读请求取消不杀死同一来源的其他下载任务。
- [x] Cookie 按 name/domain/path 唯一键存储，并执行 hostOnly/path/secure/expiry 匹配；接收的 Set-Cookie 能跨 host 重启恢复。
- [x] 明确错误分类：离线、超时、代理/TLS、429、登录过期、验证页面、解析失败；重试只针对可重试错误。

**验收：** 本地 HTTP 测试服务覆盖两源同名 Cookie 隔离、跨域重定向、UA/代理切换、请求头传递、错误页面误当图片、延迟响应取消。取消后 UI 在下一次调度即可返回，底层调用在确定性测试中 1 秒内结束；Cookie 不进入不匹配域。

**上游依据：** [NetworkHelper.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt)。移植行为不照搬上游简化的 Cookie 匹配；保留本地更严格的语义。

### T3：按真实样本补齐 Android/Mihon 兼容契约

**修改：** `extension-host/src/main/kotlin/` 下已存在的 `android`、`androidx`、`eu/kanade/tachiyomi` 兼容类，以及 `desktop-app/src/main/kotlin/mihon/desktop/extension/compat/TachiyomiExtensionConverter.kt`。

**新增：** `extension-host/src/test/kotlin/mihon/extension/host/AndroidCompatContractTest.kt`、`docs/superpowers/evidence/suwayomi-extension-compatibility-matrix.md`。

- [ ] 建立至少 6 类样本：普通 HttpSource、SourceFactory 多源、自定义 headers/Cookie、偏好影响请求、JS/图片处理、WebView。记录包版本、摘要、API 版本与覆盖行为；WebView 类在 T4 验收。
- [ ] 对照已有 Bitmap/BitmapFactory、Uri、SystemClock、Preferences、Injekt、JSON/ProtoBuf、Rx 桥，补充可观察行为的回归，已正确的类保持现状。
- [ ] 对样本实际依赖的 Looper/Handler、AndroidSchedulers、JS 引擎、日期/资源语义建立合同测试并移植实现；不通过新增静默返回空值的 stub 宣称兼容。
- [ ] 转换结果以原包摘要、转换器版本、compat 版本为缓存键；版本变化重新转换，失败结果保留可理解原因。
- [ ] 不支持的 API 在安装/加载时明确报告扩展名和缺失能力，避免进入阅读器后持续转圈。

**验收：** 每个受支持样本走完安装 → 浏览/搜索 → 详情 → 章节 → 页面；相同输入保持源 ID、章节顺序和图片处理结果。真实站点故障单独记录，并用确定性样本证明宿主行为。

**上游依据：** [AndroidCompat](https://github.com/Suwayomi/Suwayomi-Server/tree/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/AndroidCompat/src/main/java)、[BytecodeEditor.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/BytecodeEditor.kt)。

### T4：增加按需浏览器兼容能力

**新增模块：** `desktop-webview-host/build.gradle.kts`、`desktop-webview-host/src/main/kotlin/mihon/webview/Main.kt`、`desktop-webview-host/src/main/kotlin/mihon/webview/WebViewSession.kt`。

**新增接线：** `desktop-app/src/main/kotlin/mihon/desktop/extension/DesktopWebViewManager.kt`、`extension-sdk/src/main/kotlin/mihon/extension/ipc/WebViewIpc.kt`、`desktop-app/src/test/kotlin/mihon/desktop/extension/DesktopWebViewManagerTest.kt`。

**修改：** `settings.gradle.kts`、`desktop-app/build.gradle.kts`，以及样本所需的 host WebView 兼容入口。

- [x] 先完成单个浏览器会话的 Windows 技术验证：JCEF/KCEF 初始化、带 headers 加载、JavaScript 结果回调、Cookie 交换、取消、销毁。
- [x] 浏览器组件按需独立启动；主程序和普通 HTTP 扩展保持 Java 17。浏览器辅助进程使用验证过的 Java 21 运行时及固定 Chromium 原生组件，独立打包并记录摘要。
- [x] 以 sessionId/sourceId 隔离浏览器会话，使用 T2 的网络会话边界；浏览器专用代理和 Cookie 设置与普通请求保持一致。
- [ ] 提供“需要登录/网页验证 → 打开对应页面 → 返回并重试”的完整流程；验证码由用户完成，失败有明确状态。
- [x] 关闭应用或取消请求时销毁会话和辅助进程；崩溃可重建，不影响本地阅读和书库。

**验收：** 普通 HTTP 源不启动浏览器进程；WebView 样本完整阅读；关闭会话后无持续后台加载；登录会话可用于后续图片请求；Chromium 缺失/加载失败时可恢复。分别记录启动时间、常驻内存和包体增量，不将浏览器技术验证直接视为所有网站兼容。

**上游依据：** [KcefWebViewProvider.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/AndroidCompat/src/main/java/xyz/nulldev/androidcompat/webkit/KcefWebViewProvider.kt)、[CEFManager.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/server/util/CEFManager.kt)。

### T5：优化现有下载队列与离线恢复

**修改：** `desktop-app/src/main/kotlin/mihon/desktop/download/DesktopDownloader.kt`、`DownloadStore.kt`、`DownloadModels.kt`、`DownloadDiskProvider.kt`；测试使用同模块下的 `DesktopDownloaderTest.kt`、`DownloadStoreTest.kt`、`DownloadAndReadOfflinePipelineTest.kt`。

- [x] 增加按 source/host 的公平调度及并发上限，429 遵守 Retry-After；前台阅读优先级高于预取和批量下载。
- [x] 保留当前原子 JSON 队列、任务顺序、状态、失败原因和已完成页，不在本轮无理由迁移数据库或替换成仅保存章节 ID 的集合。
- [x] 恢复时验证已有页，损坏或不完整页重新获取；整个章节全部校验通过后才标记下载完成。
- [x] 队列文件损坏时保留原文件并给出恢复信息，支持最近有效快照；不能静默变成空队列。
- [x] 接入 T2 取消语义，处理磁盘满、下载目录失联和临时文件清理；暂停/退出后不继续偷偷写文件。

**验收：** 两源并行，一源持续失败不阻塞另一源；中途退出重启保留已完成页与顺序；损坏页只重下该页；磁盘满保留恢复点；断网后已下载章节完整可读。

**上游依据：** [DownloadManager.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/download/DownloadManager.kt)、[Downloader.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/download/Downloader.kt)。

### T6：归一书库更新与后台调度

**修改：** `desktop-app/src/main/kotlin/mihon/desktop/library/update/LibraryUpdateService.kt`、`LibraryUpdateScheduler.kt`、`LibraryUpdateModels.kt`、`desktop-app/src/main/kotlin/mihon/desktop/updates/DesktopLibraryUpdateService.kt`、`desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`。

**新增：** `desktop-app/src/test/kotlin/mihon/desktop/library/update/LibraryUpdateRecoveryTest.kt`。

- [x] 以当前 UI 使用的 `library/update` 实现为唯一更新入口，迁移旧实例的实际消费者和必要测试后再移除重复实现。
- [x] 同源串行、跨源有界并发；统一任务中心进度、当前来源、失败原因和取消状态。
- [x] 手动、定时和启动补跑共享去重及运行锁；不会对同一漫画启动重复刷新或重复自动下载。
- [x] 保留分类 include/exclude、完结/未读/未开始过滤和新增章节下载设置。
- [x] 显式重抛 CancellationException，保存最后完成时间和重试状态；失败来源不阻塞其他来源。

**验收：** 定时运行中点击手动刷新不重复入队；取消停止后续请求；一源超时其他源完成；重启只执行有界补跑，不积累历史周期的所有任务。Windows 应用关闭后的运行由 T10 接线。

**上游依据：** [Updater.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/update/Updater.kt)。

### T7：完善跟踪服务和账户恢复

**修改：** `desktop-app/src/main/kotlin/mihon/desktop/track/DesktopTrackerManager.kt`、`DesktopTrackerStore.kt`、`OfflineTrackingQueue.kt`、`TrackOnReadSyncService.kt`、`TrackingConflictResolver.kt`、`desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`。

**新增：** `desktop-app/src/main/kotlin/mihon/desktop/platform/WindowsCredentialStore.kt`、`desktop-app/src/main/kotlin/mihon/desktop/track/MangaBakaTracker.kt`、`HikkaTracker.kt`、`desktop-app/src/test/kotlin/mihon/desktop/track/TrackingRecoveryTest.kt`。

- [ ] 复用已有 9 个 tracker，覆盖登录恢复、搜索绑定、读进度、刷新、退出及服务故障；Suwayomi/Kavita 按现有真实实现继续改进。
- [x] 将当前 Android 清单中存在的 MangaBaka/Hikka 补入桌面；以仓库中的对应 Android 实现为字段和业务语义参考，执行时核对官方 API 与 OAuth 配置要求。
- [x] 将明文 preferences token 迁入 Windows Credential Manager：写入并回读成功后才删除旧值；失败不丢失账户。便携版换机器允许重新登录，普通备份不导出密钥。
- [x] 应用启动、登录成功、联网恢复和退避到期触发同一队列处理器，保证单实例处理；离线不会使阅读失败。
- [x] 同一漫画的更新有序处理；401 暂停并提示重新登录，429/5xx 按退避重试；有实际进度冲突时展示本地与远端值。

**验收：** 离线阅读 → 退出 → 重启联网，无需重新登录操作即可同步待办；重复触发不重复发送；token 迁移失败可恢复；新增 tracker 通过真实 HTTP 契约，账户实测结果单独记录。

**上游依据：** [Track.kt](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/Track.kt)。

### T8：备份往返、源迁移与数据恢复

**修改：** `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupImporter.kt`、`AndroidBackupExporter.kt`、`BackupMergePolicy.kt`、`SupportedPreferencePolicy.kt`；`desktop-app/src/main/kotlin/mihon/desktop/backup/DesktopBackupScheduler.kt`。

**测试：** `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupRoundTripTest.kt`；新增 `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/SuwayomiBackupCompatibilityTest.kt`。

- [ ] 为 Android Mihon → MihonW → Android Mihon、Suwayomi → MihonW 建立脱敏真实样本和字段比对；以当前备份 codec 为基础补缺失映射。
- [x] 比较漫画/章节身份、分类顺序、进度、书签、历史、跟踪和可迁移偏好，不能只检查“文件能解析”。
- [x] 缺少扩展、source 暂不可用、未知字段和平台专用设置生成明确导入报告；不将 source 缺失当作丢弃漫画的理由。
- [x] 验证源迁移和重复导入保留进度、书签与库成员状态；事务中途失败回滚，不留下部分导入数据。
- [ ] 验证自动备份原子落盘、保留数量、损坏文件处理以及升级前一致性快照；恢复过程提供进度和取消结果。

**验收：** 真实非空备份在目标应用能打开，关键字段语义一致；损坏备份不改变原库；连续两次导入不重复漫画/分类。Windows 私有字段被明确保留或报告，不悄悄丢失。

**上游依据：** [备份导入](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/ProtoBackupImport.kt)、[备份导出](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/ProtoBackupExport.kt)。

### T9：贯通桌面流程并优化真实性能

**修改范围：** 仅修改测量或流程失败所涉及的既有入口：`desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`、`ui/browse/BrowsePresenter.kt`、`ui/library/LibraryPresenter.kt`、`ui/library/MangaDetailScreen.kt`、`ui/reader/ReaderScreen.kt`、`reader/DesktopReaderContentPipeline.kt`、`reader-core/src/main/kotlin/mihon/reader/session/DefaultReaderSession.kt`。

**新增：** `scripts/measure-desktop-performance.ps1`、`docs/superpowers/evidence/suwayomi-desktop-acceptance.md`。

- [x] 运行安装扩展 → 搜索 → 详情 → 显式入库 → 下载 → 离线阅读 → 进度/历史 → 更新 → 备份的完整场景。（真实 Windows/IPC/HTTP/SQLite，内容为合成 fixture；生产图源验收另列。）
- [ ] 在浏览和书库两入口检查统一详情的刷新、入库/移除、分类、跟踪、章节筛选、批量下载、书签等功能；复用已有页面，不再新建另一套详情。
- [ ] 加载取消、损坏图片重试、切换图源、host 重启都有可理解的状态；取消后不会被过期回调重新导航到阅读器。
- [x] 以 10,000 漫画库测试搜索、筛选、滚动和批量操作；只针对结果修复分页、重复查询、无效重组和封面加载。（真实 EXE 搜索/滚动/书签筛选，SQLite/presenter 批量操作；不代表帧率门槛通过。）
- [ ] 长章节反复翻页、跨章、动画与大图验证预取/分块缓存，保留当前 reader-core 256 MiB 预算；分别记录 JVM heap、进程私有内存和子进程内存，不能把预算当成整个应用 RSS 上限。
- [ ] 截图检查宽屏/窄窗、100%/150%/200% 缩放、键盘焦点和控件布局；覆盖六类阅读模式及滚轮隐藏控件规则。

**验收目标：** 固定参考机器、发布配置下冷启动 ≤5 秒，空闲总进程内存目标 <500 MB（浏览器未启用）；本地书库搜索/筛选 P95 ≤300 ms；60 Hz 页面交互帧时间 P95 ≤33 ms；连续 30 分钟阅读内存进入平台期。记录样本、硬件和测量方法，未达到即报告差距并修复热点。

**参考边界：** 原生阅读布局、预取、输入和格式兼容继续以 Mihon 与本地 reader-core 为准；Suwayomi 主要提供页面获取和后台行为参考。

### T10：完成 Windows 进程与后台能力

**修改：** `desktop-app/src/main/kotlin/mihon/desktop/extension/WindowsExtensionProcessManager.kt`、`extension-sdk/src/main/kotlin/mihon/extension/ipc/IpcSession.kt`、`desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt`、`DesktopCommandRunner.kt`、`desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`。

**新增：** `desktop-app/src/main/kotlin/mihon/desktop/platform/WindowsBackgroundScheduler.kt`、`desktop-app/src/main/kotlin/mihon/desktop/extension/WindowsAppContainerLauncher.kt`、`desktop-app/src/test/kotlin/mihon/desktop/extension/WindowsExtensionIsolationTest.kt`。

- [x] T2 网络代理稳定后，完成原批准设计的 AppContainer 启动、身份验证 Named Pipe IPC 和 Job Object 资源/退出联动；限制扩展访问其私有目录。
- [x] 检测扩展自行创建 HTTP 客户端等绕开统一网络入口的行为；受限制环境无法执行的能力明确报错，不能静默退回无隔离进程。
- [x] 为用户设置的后台更新/备份注册 Windows 计划任务，使用既有可执行程序的无界面命令；默认不开启新的系统后台任务。
- [x] 主界面运行、计划任务和多开共用数据目录锁/任务锁；系统错过触发时间后下次启动有界补跑。
- [ ] 安装路径变化后更新计划任务；卸载时按用户选择保留数据并清理应用拥有的任务。

**验收：** 扩展 host 无法写其他扩展目录或主数据库；普通取消、超限终止与主程序退出都无孤儿进程；中文/空格路径可执行后台命令；计划任务与主程序同时启动不产生重复下载或数据库冲突。

**依赖：** T1、T2、T6；WebView 辅助进程有单独资源和会话边界，不宣称 Chromium 与 JVM 扩展具有完全相同的沙箱能力。

### T11：同版本打包、升级回滚和正式验收

**修改：** `desktop-app/build.gradle.kts`、`desktop-app/src/main/kotlin/mihon/desktop/updates/DesktopAppUpdateService.kt`、`scripts/MihonUpdater.ps1`、`scripts/verify-desktop-clean-machine.ps1`、`README.md`。

**新增：** `docs/superpowers/evidence/suwayomi-windows-release-acceptance.md`、打包用的统一第三方许可清单。

- [ ] 从已集成的同一提交生成 app-image、installer EXE、MSI、portable ZIP；统一版本来源，输出整个发行目录的文件摘要清单。
- [ ] 包含 codec、扩展 host、浏览器辅助组件及其所需运行时，不依赖开发机 PATH/JDK/全局缓存。
- [ ] 修正现有 clean-machine 脚本硬编码 JDK/版本，并准确称其为本机隔离目录检查；真正干净环境验收在 Win10/Win11 虚拟机或实体机执行。
- [ ] 验证旧版升级、数据迁移失败回滚、更新包摘要失败、portable 原子替换、文件关联、卸载保留数据；升级下载成功不等于安装成功。
- [ ] 安装/更新后启动真实 EXE，执行 T9 主流程；记录安装版本、构建提交、完整 app 内容摘要、截图和退出码。
- [ ] 检查 0.1.3 的三个旧格式升级到同一新版本，portable 用户数据仅写自己的 data 目录；新机器无浏览器缓存也能初始化。
- [x] 将原批准设计的每个功能映射至当前实现和验收证据。缺环境、缺账户或站点失效的检查明确列为未验证，不计为完成。

**发行门槛：** 两个 Windows 版本分别通过全流程；三个发行包版本一致；真实非空备份往返通过；至少 6 类扩展样本有结果且代表性的普通 HTTP / SourceFactory / 会话型 / WebView 源完成实机流程；无已知数据丢失、持续加载无法退出或更新失败无法恢复的问题。

## 5. 执行顺序与并行分工

| 批次 | 工作 | 可见交付 |
|---|---|---|
| 第一批 | T0 → T1 → T2 | 当前修复收口，重启后图源设置可靠，搜索/阅读/下载会话一致、可取消 |
| 第二批 | T3；T2 后 T4 与 T5 按不同文件并行 | 更多真实扩展可用，按需浏览器，下载恢复可靠 |
| 第三批 | T6、T7、T8 按模块并行；Runtime 接线由单一负责人合并 | 更新、跟踪、备份完整闭环 |
| 第四批 | T9 与 T10 的独立部分并行 | 桌面性能、全流程、Windows 后台与隔离能力 |
| 第五批 | T11 | 一致的可安装版本及 Win10/Win11 验收证据 |

浏览器原生组件、第三方 OAuth 和干净 Windows 环境存在外部依赖，分别处理；它们不阻塞其他独立模块继续完成。第一批完成后即可先交付改进版供使用，但完整端口仍以 T11 总体验收为准。

## 6. 构建与验证命令

在已确定的工作分支执行，不在计划编写阶段运行。统一 PowerShell 构建环境：

```powershell
$env:JAVA_HOME = 'C:\Users\18734\.jdks\corretto-23.0.2'
$gradleArgs = @('--no-daemon', '--max-workers=1', '-Pkotlin.compiler.execution.strategy=in-process', '--console=plain')
```

T0 重点检查：

```powershell
.\gradlew.bat :extension-host:test --tests '*ExtensionHostEngineTest' @gradleArgs
.\gradlew.bat :desktop-app:test --tests '*DesktopSourceManagerTest' --tests '*DesktopSourceManagerSourcePreferenceIpcTest' --tests '*WindowsExtensionProcessManagerTest' --tests '*ReaderScreenActionsTest' @gradleArgs
.\gradlew.bat :reader-core:test --tests '*DefaultReaderSessionTest' @gradleArgs
```

预期：对应新增/已有回归全部通过，失败任务已定位并修复；不使用被过滤后的少数测试数量代表整个模块结果。

各批集成检查：

```powershell
.\gradlew.bat :extension-sdk:spotlessCheck :extension-sdk:test :extension-host:spotlessCheck :extension-host:test :desktop-library-data:spotlessCheck :desktop-library-data:test :reader-core:spotlessCheck :reader-core:test :desktop-app:spotlessCheck :desktop-app:test @gradleArgs
git diff --check
```

仅对本批涉及的模块运行相应子集；最终发行运行完整集合。引入 `desktop-webview-host` 后追加该模块测试及原生初始化检查。影响 Android/共享构建时追加 `:app:assembleDebug` 和受影响 Android 测试。

发行构建：

```powershell
.\gradlew.bat :desktop-app:createDistributable :desktop-app:packageExe :desktop-app:packageMsi :desktop-app:packagePortableZip @gradleArgs
$releaseExe = 'D:\my project\mihon-w\desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe'
$smokeProcess = Start-Process -FilePath $releaseExe -ArgumentList '--smoke-test' -WindowStyle Hidden -Wait -PassThru
if ($smokeProcess.ExitCode -ne 0) { throw 'Packaged smoke failed' }
```

预期：Gradle 成功、产物存在、打包运行时自检退出 0。之后仍需执行安装、升级和真实 UI 场景，不能用 smoke 代替完整验收。

## 7. 完成的定义

任务完成需要“代码行为 + 对应测试 + 真实入口接线”。发行完成需要“同一提交产物 + 实际安装运行 + 数据往返 + 干净 Win10/Win11 验收”。计划勾选数量、源码文件数量、单次成功启动和旧测试报告均不替代这些证据。

本计划对独立 subagent 的调查直接采纳；最终验收验证的是应用本身的运行结果，而不是重新审查调查过程。
