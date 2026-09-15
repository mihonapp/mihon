# Suwayomi 演进功能覆盖与验收证据映射

本表是 **2026-09-15 当前工作树的功能与证据快照，不是完整 Windows 端或正式发行已完成的声明**。基准为[原批准设计](../specs/2026-08-31-windows-port-design.md)及[本次 T0–T11 计划](../plans/2026-09-15-suwayomi-based-mihonw-evolution.md)。工作树为 `D:\my project\mihon-w\.worktrees\suwayomi`，`codex/suwayomi-evolution`；起始快照 `a75a1d76f` 不代表后续所有工作已提交或已进入发行包。

本文直接采用各负责代理和根代理已有报告，不重新执行测试或复核其实现。不同时间、过滤条件和构建版本的测试数量不相加；后续 Gradle 会覆盖 XML，因此不从当前 XML 倒推旧运行。报告中较早的“复跑中”状态，由较新的具体通过结果补充；涉及最终包、GUI、安装和外部账户的门槛仍保留。

## 证据等级与来源

- **代码/契约**：实现入口与确定性测试存在且负责报告给出通过结果，不代表外站或安装环境通过。
- **本机真实链路**：实际 Windows API、进程、SQLite、HTTP、下载文件、JCEF 或 EXE 被执行；测试内容仍可能是合成数据。
- **外部样本部分验证**：真实 APK 或当前 Android 编码器被使用，只覆盖报告明确执行的行为。
- **外部未验证**：缺真实账户、目标应用、站点流程或干净系统，没有结果可计入完成。
- **已知限制/缺口**：报告明确指出的能力限制或尚缺实现，不以“待验证”掩盖。

| 引用 | 直接采用的报告 | 核心证据及范围 |
| --- | --- | --- |
| E0 | [基线](2026-09-15-suwayomi-baseline.md)、[上游映射](../../upstream/suwayomi-reference.md) | 起始修改快照、固定上游来源；54 项中 49 执行通过、5 环境跳过，仅为起始基线 |
| E1 | [Context](suwayomi-context-persistence.md)、[生命周期](suwayomi-source-lifecycle.md) | Context/资产/偏好/身份/卸载；生命周期 36 项通过，生成的有效 multidex 与身份发现契约 |
| E2 | [宿主网络](suwayomi-host-network-bridge.md)、[网络与桌面](suwayomi-network-and-desktop.md) | 标准 OkHttp broker、Cookie/代理/取消/优先级、图片处理；独立合同与本地 HTTP |
| E3 | [真实扩展矩阵](../../suwayomi-extension-compatibility.md) | 最终报告 7 个官方真实 APK 转换并加载，84 个运行时源；18 项、0 跳过；离线请求/偏好行为详见原矩阵 |
| E4 | [WebView](suwayomi-webview-host.md) | 实际 JBR21/JCEF、本地页面、JS、Cookie、Android 桥、退出/崩溃清理；没有生产验证码/完整网站验收 |
| E5 | [下载恢复](suwayomi-download-recovery.md) | 8 类 27 项通过，包含真实小 PNG、本地 HTTP、损坏页修复与离线读取 |
| E6 | [更新恢复](suwayomi-update-recovery.md) | 14 项通过，唯一调度入口、来源并发、超时、取消、持久退避、启动补跑 |
| E7 | [跟踪恢复](suwayomi-tracking-recovery.md) | 15 类 44 项通过；原生 Credential Manager 中文读写删除实测；新增服务本地 HTTP 契约 |
| E8 | [备份](suwayomi-backup-compatibility.md) | 桌面备份 32 项、调度 5 项；当前 Android 编码器交叉验证 3 项；均非用户实机恢复 |
| E9 | [完整工作流](suwayomi-workflow-integration.md) | 真实安装合成 mext→隔离 host/IPC/HTTP→SQLite→下载→关 host 离线阅读→更新→备份双导入，1/1 通过；联合宿主 45 项含 2 外部包跳过 |
| E10 | [Windows 隔离](suwayomi-windows-extension-isolation.md)、[Windows 后台](suwayomi-windows-background.md) | 隔离 6/6；后台联合 22 项无跳过，含真实临时计划任务和跨进程锁 |
| E11 | [EXE 阅读矩阵](suwayomi-packaged-reader-matrix.md) | 13:15 预发行 app-image 三独立 EXE 退出 0；六模式、七资产、GIF 帧、持久化及 reader 内存预算 |
| E12 | [发行工程](suwayomi-windows-release-acceptance.md) | 统一版本、资源/许可、打包与维护脚本；安装执行仍有明确缺口 |
| E13 | [执行记录](suwayomi-execution-progress.md) | 集成运行：reader-core 178 项含 1 跳过、数据层 114、SDK 23；关键 UI/Runtime/书库选择测试通过，非整个桌面模块总通过声明 |

## T0–T11 功能映射

| 任务及计划中的功能 | 当前实现定位 | 已有验证 | 未验证边界与真实缺口 |
| --- | --- | --- | --- |
| **T0 基线与上游归属**：保存原修改、固定源码/包身份、已有 source 恢复/Factory/取消、上游差异和许可 | 起始快照、`docs/upstream/suwayomi-reference.md`、共享 Gradle Mutex wrapper | E0；起始行为测试通过，原 checkout 改动保留 | 起始包和后续包不是同一证据对象；仍需以最终提交和完整文件摘要确定发行身份，不能拿基线通过替代最终验收 |
| **T1 生命周期与偏好**：按扩展 files/cache/preferences、初始化前回放、并发一次加载、多源、原 sourceId、多 dex/assets/API、替换/卸载回滚与句柄清理 | `DesktopSourceManager`、`DesktopExtensionInstaller`、`TachiyomiExtensionConverter`、`ExtensionHostEngine`、`ExtensionExecutionContext`、`Application/Context/AssetManager/FileSharedPreferences` | E1；36 项生命周期通过；E3 真实多源和偏好；E9 真实安装与重新启动；沙箱中旧临时文件 API 的问题已改为 NIO | SharedPreferences.apply 当前同步落盘；扩展自行创建的线程不自动继承上下文。安装目录与元数据发布之间的**进程强杀窗口没有日志事务**；不能把正常异常回滚等同断电恢复。代表性 OS 锁文件/安装中断完整验收未覆盖 |
| **T2 网络**：扩展/source/request 身份、声明域/动态域、自定义拦截器/getImage、代理/UA/头/Referer/重定向、Cookie、取消及错误分类 | `DesktopNetworkHelper`、`DesktopCookieStore`、`NetworkRequestScheduler`、`SourcePageDownload`、`OnlineChapterSource`、`IpcSession/IpcContracts`、宿主 `NetworkHelper/BrokerTransport/BrokeredHttpClient` | E2；本地 HTTP 的 Cookie/重定向/代理/取消/二进制/重复头；E9 真实隔离进程图片往返；E10 直接 socket 被拒 | 宿主 `Chain.connection()` 为 null，宿主自定义 socket/TLS/连接池不会变成父进程策略；自定义 DNS 等不应从接口存在推定完全迁移。企业代理/证书、生产登录过期/挑战页面仍未完整实测 |
| **T3 Android/Mihon 兼容**：六类样本、Bitmap/Uri/时钟/偏好/注入/序列化/Rx、Looper/Handler/JS/资源、摘要与兼容版本缓存、明确失败 | 转换器 `AxmlManifestParser`、`DexConstructorNormalizer`；宿主 `android/androidx/eu.kanade` 兼容层；`QuickJs` Rhino 适配；版本化转换缓存 | E3：7 个真实 APK、84 个源转换加载；BiliManga 偏好改变真实请求路径，NHentai 离线解析，其他源请求/模型枚举；T4 承担 Handler/WebView 契约 | **加载成功不等于浏览→详情→章节→图片全站通过**。MangaPlus 图像解密未实证；EZManga/部分 protobuf/JSON 用的是故意不匹配的本地 HTML，不算真实解析成功。QuickJs 只支持已测试的 evaluate/create/close 子集，不支持 native bytecode/bindings；模糊 DEX 构造器形式明确拒绝。新 ABI 的后续结果以 E3 最新条目为准 |
| **T4 浏览器**：按需独立运行时、headers/JS/Cookie、会话身份、登录/验证返回重试、取消/销毁/崩溃恢复、体积和内存 | `desktop-webview-host`、`DesktopWebViewManager`、`WebViewIpc/WebViewBridge`、Android WebView/Handler；Runtime 的 `onWebView` 与现有页面入口 | E4：实际 JCEF 本地页面和 Android API→父网络→Cookie 全链；约 1.2 s 页面就绪、进程树约 404.5 MiB；强杀 JVM 后 Chromium 子孙退出；构造 manager 不启动浏览器 | **缺真实登录/验证码站点及代表性 WebView 扩展完整阅读**。高级 loadData/jsBridge/resource replacement DSL、WebSocket、文件上传和完整 Android WebView API 不受当前覆盖保证。MangaFire 自动验证码分支未执行。浏览器约 680 MiB 独立运行时是已记录体积，不能套用主应用空闲预算 |
| **T5 下载**：公平与并发/Retry-After、持久队列、有效页复用/损坏修复、原子发布、坏队列保留、暂停/取消/磁盘失联 | `DesktopDownloader`、`DownloadStore/Models/DiskProvider`；共同图片链与 T2 调度；任务中心恢复/错误字段 | E5 27 项；E9 真正隔离扩展图片下载及关 host 后离线解码 | 队列继续采用**原子 JSON+有效快照**，是本计划明确保留的实现，未迁入数据库。真实物理满盘、强制断电、拔盘及安装版运行仍缺实测；带宽限速不在当前报告的已验证能力中 |
| **T6 更新**：同一手动/定时入口、来源分组、分类与跳过规则、自动下载、取消、最后完成/失败退避、启动有界补跑 | `library/update/LibraryUpdateService/Scheduler/StateStore`；旧 `updates/DesktopLibraryUpdateService` 为适配；Runtime/任务 UI | E6 14 项；E2/E13 接线测试；E9 新隔离 host 从 HTTP 更新新增一章 | 无真实站点长周期任务与生产网络恢复观测；自动下载入队不等于后台进程退出前全部下载完成。关闭应用后的 OS 运行另见 T10 |
| **T7 跟踪/账户**：保留 9 服务并补 Hikka/MangaBaka、凭据迁移、离线有序队列、启动/联网/登录触发、401/429/5xx、冲突处理 | `DesktopTrackerManager/Store`、`OfflineTrackingQueue`、`TrackOnReadSyncService`、`TrackingConflictResolver`、`WindowsCredentialStore`、11 个 tracker、跟踪恢复 UI | E7 44 项；原生随机 Credential target 写/读/删；本地 HTTP 验证新服务路径/字段；Runtime worker 与冲突/重新登录入口见 E2 | **生产账户未读写验证**，不能宣称所有 11 个服务登录到退出全流程通过。新增 MangaBaka/Hikka 当前支持合法 token 输入；缺 MihonW 注册 OAuth client/reference/secret/redirect，浏览器回调、token exchange/refresh 生命周期尚未完成。便携跨机器需重新登录 |
| **T8 备份/恢复**：漫画/source/章节身份、分类顺序、进度/书签/历史/tracking/偏好、缺源/未知设置报告、幂等、失败回滚、原子导出和保留 | `AndroidBackupCodec/Validator/Importer/Exporter`、`BackupMergePolicy/SupportedPreferencePolicy`、`DesktopBackupScheduler`、持久 ImportReport | E8：桌面 32+调度 5；独立官方 Suwayomi 模型生成样本；当前 Android Backup serializer 3 项交叉验证；E9 SQLite 双导入不重复 | **没有真实用户脱敏备份和 Android/Suwayomi 实际应用导入结果**。当前 Android 编码器生成的 499 B 样本仍是合成数据。Suwayomi 私有 meta/serverSettings、SyncYomi uid/version 不承诺保留；偏好按白名单迁移并报告跳过；备份不含下载图片。升级前 DB 快照/回滚与长恢复进度、协作取消的产品验收不能由 codec 测试替代 |
| **T9 桌面全流程/性能**：统一详情、显式入库/移除、分类/跟踪/章节操作、加载取消/错误、万部库、六模式/动画/大图、宽窄/DPI/焦点/滚轮、启动/帧率/内存目标 | `MihonDesktopApp`、`LibraryPresenter/MangaDetailScreen/BrowsePresenter`、`ReaderScreen`、`DesktopReaderFactory/ContentPipeline`、`reader-core`、`measure-desktop-performance.ps1` | E9 真实本地完整工作流；E11 三独立 EXE、六模式七资产、38 tiles/阶段、GIF 两帧、进度 1→2→6；E2 万部 SQLite 搜索 27 ms、初始 418 ms、批量移除 250 ms；E13 关键 UI/取消回归 | E9 源为合成 mext，不是生产网站；E11 为 13:15 **headless 预发行包**，不是新 GUI 包验收。万部库是单次测量，**不是 P95≤300 ms、帧 P95≤33 ms、≤5 s 冷启或 <500 MB 空闲的证据**。30 分钟平台期、100/150/200% DPI、真实轮滚/焦点/多窗口完整矩阵由根 GUI 验收补充；当前本文不计完成 |
| **T10 Windows 进程/后台**：逐扩展隔离、身份 IPC、无直接网络、资源/退出联动、默认关闭计划任务、跨进程锁、路径迁移/卸载清理 | `WindowsExtensionProcessManager/AppContainerLauncher/AuthenticatedPipe/JobObject`；`WindowsBackgroundScheduler/DesktopProfileLock`；后台 CLI、设置、Runtime | E10 隔离 6/6：真实 AppContainer token、PID+nonce/反例、私有目录/兄弟目录、直接网络拒绝、Job 内存配额/关闭；22 项后台测试含真实临时 schtasks create/query/remove、跨进程锁、实际临时 SQLite 备份 | 根集成实测 CPU hard cap：2/2，通过内核查询确认无配额、25%、50% 三种配置；扩展默认50%，浏览器不继承。第三方 JDK17 File.createTempFile 卷查询仍可能被拒；不静默回退无隔离。计划任务使用已登录当前用户，不承诺注销执行；注册测试不等于实际 EXE 定时业务完成。卸载钩子/旧安装路径清理的发行实测见 T11 |
| **T11 发行/更新/验收**：统一提交与版本、四类产物/摘要、独立运行时与许可、Win10/11、0.1.3 升级/迁移回滚/摘要失败/portable替换/关联/卸载数据、安装后 T9、功能映射 | `desktop-version.txt`、`desktop-app/build.gradle.kts`、`DesktopAppUpdateService`、`MihonUpdater.ps1`、安装维护/WiX 钩子、`verify-msi-package.ps1`、`verify-packaged-reader-artifact.ps1`；本文 | E12：统一 0.2.0 工程和资源、CLI/更新测试、PowerShell 摘要拒绝/失败回滚/保留数据、静态 MSI 验证；E11 真 EXE reader；根已报告含 Swing dispatcher 修复的 app-image 已重建，GUI 100/150/200%与万部库已追加实测，最终发行包仍需按新身份验收 | **尚无同一最终提交全产物发行验收、干净 Win10 22H2/Win11、真实旧版升级/回滚/卸载证明**。安装执行曾被自动审批拒绝，未绕过。第一 MSI 缺钩子已由只读检查抓出；新 MSI/EXE/ZIP 与钩子结果以 E12 最终记录为准。新包不能继承旧 EXE/JAR 的通过身份；本文只完成证据映射，不修改全局进度或宣布正式发行 |

## 原批准设计的用户功能与架构覆盖

下表补充不宜仅按 T 编号合并的原始要求。实现位置用于寻找当前入口；“已有入口”本身不算验收通过。

| 原设计要求 | 当前对应实现/计划 | 已有证据与剩余边界 |
| --- | --- | --- |
| 完整 Windows 功能目标，Android 能继续跟随上游 | `desktop-app`、`desktop-library-data`、`reader-core`、`extension-sdk/host`、独立浏览器；T0–T11 | 路线保留。桌面仍有 Kotlin/JVM 模块，平台实现多在 `desktop-app/platform`，不声称已全部按原蓝图拆成 KMP 模块 |
| 主程序拥有 DB/书库/凭据，扩展不能直接操作 | SQLDelight repository、Runtime、逐包 AppContainer、typed IPC/HTTP broker；T1/T2/T10 | E9/E10 实际边界。取消、超时、身份校验有证据；CPU 配额已由原生测试确认；完整协议能力协商仍不在已验证集合 |
| Windows 扩展包、原仓库目录、发布者/摘要/签名/能力/域信任、明确不支持 | manifest/validator、installer、trust/store、转换器；T1/T3 | 验证、缓存、拒绝/回滚和真实包加载有证据；不承诺任意 APK 无损转换。所有发布者信任 UI/真实仓库升级体验尚无完整发行实测 |
| 书库、本地漫画、搜索筛选排序、分类和批量操作 | LibraryPresenter/SQLite、local import、`DesktopCategoryService/CategoryDialog`；T8/T9 | 万部真实 DB、类别和字段备份、显式入库已验证；完整网格范围选择/拖放/触摸操作及长列表绘制性能未由这些结果证明 |
| 在线浏览、统一详情、章节筛选/书签、主动入库/可撤销 | Browse/统一 MangaDetail、OnlineMangaSyncService、LibraryPresenter | E9 预览不收藏、显式加入；E13 统一详情/reader 选择回归。两个入口所有操作的真实 GUI 验收仍需单独记录 |
| 下载/速度/任务中心/错误通知、离线阅读 | Downloader/DownloadStore、DownloadsScreen、更新 UI、notification 服务；T5/T6/T9 | E5/E9 实际文件与恢复通过。原设计数据库队列由当前计划明确调整为原子 JSON；带宽限速、系统通知/托盘实际显示等无本轮完整证据 |
| 历史、分类、书签与跟踪 | HistoryService/HistoryScreen、CategoryService、TrackingDialog、进度 repository；T7–T9 | E8/E9 字段及历史真实保存、E7 跟踪合同；不能由历史持久化推定所有历史 UI 操作验收完毕 |
| 六种阅读模式、封面偏移、原始/适宽/适高、缩放/平移、跨章、窗口模式 | reader-core、ReaderSettings/ReaderWindowController、Compose renderer | E11 六模式及实际解码、E13 已有阅读回归；模式状态/离屏解码不等于所有视觉布局、fullscreen/borderless 和用户手势实机验收 |
| 鼠标/滚轮/键盘/触摸板/触摸、点击区、加载可取消、隐藏控件规则 | ReaderInputMapper、ReaderGesture/OverlayVisibility、ReaderScreen/DefaultReaderSession | 关键取消、控件和输入回归见 E13；原设计全输入设备矩阵与打包 UI 人工操作仍未完整覆盖 |
| 普通/透明/动画/损坏/极大图片，有界预取与缓存 | ImageIO/Skia/native codec、tiled reader、memory budget | E11 合成 JPEG/透明 PNG/GIF/损坏输入/20,000² PNG和 archive/EPUB；E8 Android联测同轮 extremeImageTest 2项。256 MiB是reader计账预算，**不是总进程 RSS**；其他现代格式以各自 codec 报告为准，不由该七资产矩阵推定 |
| 桌面导航、宽屏详情/窄窗、中文 Material 3、主题/系统色 | DesktopShell、MihonDesktopTheme/ThemeRegistry、SettingsScreen、统一详情 | 当前 UI 与主题入口存在，E2/E13 部分回归；Windows accent/theme变化、全页面 100/150/200% DPI、布局截图和非占位功能检查仍由 GUI 验收负责 |
| 快捷键、Ctrl/Shift 选择、右键、拖放、焦点/无障碍、窗口恢复/多显示器 | 现有 shell/library/reader 输入、`WindowPlacement`、ReaderWindowController | 有对应入口及部分既有 UI 测试，但此次没有完整快捷键/无障碍/多显示器及独立多窗口验收报告，不记作完整达标 |
| installed/portable 数据目录、非 ASCII/长路径/移动介质 | DesktopProfileDirectories/Lock、文件导入/下载、portable 脚本；T5/T10/T11 | 中文/空格参数、隔离profile、磁盘失联合同有证据；长路径、真实拔盘、portable 全应用不泄漏用户状态到安装版目录需发行实测 |
| Credential Manager、Cookie安全、诊断脱敏/轮转/导出 | WindowsCredentialStore、DesktopTrackerStore/CookieStore、DiagnosticBundleService | token迁移与原生读写已证明。不能由 token 凭据测试推定所有敏感 Cookie 均存 Credential Manager，也不能推定整个诊断包/日志生命周期已通过敏感数据检查 |
| DB版本迁移、一致快照、失败回滚、只读恢复 | DesktopLibraryDatabaseFactory、SQLDelight schema/migration、备份事务 | 数据层114项与备份事务证据存在；**应用升级实际迁移失败→还原快照→只读恢复整链**没有当前发行实测，不能与导入事务回滚等同 |
| 定时更新/错过周期补跑、关闭应用仍可后台运行 | T6 scheduler、T10 Windows tasks/CLI/profile lock | 到期一次、持久退避、真实注册/锁/备份业务已测；注销用户、安装路径改变后实际唤起及卸载任务清理实测未覆盖 |
| 11服务绑定/搜索/进度/退出、系统浏览器OAuth、离线/冲突 | T7 tracker/queue/credential 与 UI | 契约和恢复通过；新服务token输入已实现但自动OAuth配置/回调/刷新未完成，生产账号联调仍缺 |
| Mihon备份双向互通，报告不可迁移设置 | T8 codecs/importer/exporter/scheduler | 官方Suwayomi模型和当前Android编码器数据级互通；实际Android/Suwayomi应用恢复、真实用户数据往返缺失；下载文件/密钥不随备份迁移 |
| GitHub Releases更新、摘要/签名策略、旧版升级失败回退、portable原子替换 | DesktopAppUpdateService、MihonUpdater、jpackage/WiX；T11 | 摘要拒绝、模拟可执行失败回退和数据保留脚本通过；不等于生产下载→安装→启动→DB迁移成功，也不等于所有签名策略/真实旧版回滚通过 |
| installer EXE/MSI与portableZIP，Windows10/11 x64，无开发机依赖 | 打包资源/Java17主runtime/Java21浏览器、codec、license index；T11 | 已有 app-image构建和真EXE headless证据，发行任务仍在推进；干净双系统、无缓存浏览器初始化、文件关联、安装/升级/卸载与数据选择未验收 |
| 启动≤5s、空闲目标<500MB、万部库可用、长阅读内存稳定 | 性能脚本、万部库测试、reader budget；T9 | 单次搜索27ms和reader峰值约164MiB不能证明全部性能SLO。实际万部库30次搜索P95 12ms、GUI 100/150/200%与窄窗/键盘/搜索滚动筛选见[桌面验收](suwayomi-desktop-acceptance.md)；帧率和启动P95仍未测，30分钟压力测试另列 |
| Android回归门槛 | Android backup unit contracts及现有Android工程 | 当前编码器3项通过，不是 Android 全单测和 APK assemble 全绿证据；共享依赖/构建变化后的最终Android构建门槛仍要独立记录 |

原设计明确排除的实时手机/桌面账号同步、首发 ARM64、任意 Android APK 原样执行、纯 WinUI 重设计，不作为本轮功能缺口。Windows 包格式转换与有限 Android 兼容仍是既定路线。

## 不能混同的验收结果

1. **真实 APK 文件 → 转换/加载/离线请求**：E3 是真实外部二进制证据；只有原矩阵明确成功的模型/偏好/请求/离线解析可计入。没有把七个 APK 记成七个网站完整阅读成功。
2. **真实宿主/网络/数据库 → 合成内容全流程**：E9 没有 mock 掉 AppContainer、IPC、HTTP、SQLite 或下载文件，但漫画源与内容是本地 fixture。它证明链路，不证明线上解析、登录或站点可用性。
3. **真实 Android 编码器/官方 Suwayomi serializer → 合成备份**：E8 提高字段兼容可信度；仍缺用户脱敏样本与目标应用实机恢复，不能宣称“Android↔Windows↔Android实机无损往返”。
4. **真实 EXE → headless 阅读**：E11 的 EXE/JAR SHA 与三独立进程是实际包证据；它不覆盖后来 GUI dispatcher 修复后的包、真实鼠标 UI 或最终 release。新包应以新哈希追加验收，不继承旧包身份。
5. **原生 OS / 脚本测试 → 发行环境**：临时计划任务、Credential Manager、Job Object和回滚脚本实证不等于真实 MSI 安装/卸载或两种干净 Windows 系统通过。

## 正式完成前仍须保留的门槛

| 类别 | 当前缺少的结果或能力 | 对完成判断的影响 |
| --- | --- | --- |
| 外部生产环境 | 代表性普通 HTTP / SourceFactory / 会话型 / WebView 源从安装到图片阅读；真实 tracker 登录、绑定、读写、刷新与退出 | 不宣布生产图源/账户矩阵全面完成；不将 intentional fixture parse error 归因为站点故障 |
| 真实数据交换 | 脱敏用户备份；当前Android/Suwayomi应用实际导入本次导出；关键字段对照 | 不满足原设计真实备份双向迁移完成门槛 |
| 新能力缺口 | 新tracker自动OAuth；WebView高级API/WebSocket等；受限环境第三方旧temp API；安装发布强杀窗口 | 分别是明确实现/兼容限制，不能仅改成“未提供账号”或“网站没测” |
| Windows发行 | 同一最终提交、同版本所有产物、完整SHA、真实安装升级卸载/回滚、关联、portable数据边界；干净Win10 22H2与Win11 x64 | 不满足正式发行完成定义。`verify-desktop-clean-machine`类本机隔离检查也不是干净VM |
| UI/性能 | 新GUI包截图/输入/焦点/DPI/宽窄矩阵；启动/空闲/P95/帧时间/30分钟曲线及硬件方法 | 单元/UI测试和reader预算仅为部分证据；根代理后续GUI结果应单独链接 |
| 故障恢复 | 真实满盘/拔盘/断电、升级迁移失败只读恢复、实际旧版安装失败回退、长备份恢复取消 | 不能把确定性异常测试替代全部系统故障注入 |
| Android与许可 | 最终共享改动后的Android测试/APK构建；最终发行包第三方清单与实际原生文件匹配 | 目前编码器合同及许可工程不足以宣布全部最终门槛通过 |

安装/卸载 smoke 的拟议执行曾整体被自动审批返回 `blocked by policy`，未安装测试 MSI、未注册该拟议任务，也未通过其他途径重试；因此真实安装/卸载钩子执行仍未验证，详见 E12。两份早期 AppContainer runtime 测试缓存清理亦被同样拒绝且未绕过，精确残留路径见 E10；这与后续正常新进程生命周期的自身清理证据分开记录。

本文仅新增功能证据映射；没有修改原计划勾选、全局进度、应用代码或发行状态。最终发布判断应同时满足原批准设计的 Completion Criteria 与 T11 发行门槛，而不是依据本表的行数或单次通过数量。
