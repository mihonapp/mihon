# 桌面实际窗口验收

本报告记录预发行app-image的实际鼠标、键盘和窗口截图，最终发行身份另见发行报告。

## 环境与包身份

- Windows 11家庭版Insider Preview 10.0.26220，i9-13980HX，24核/32逻辑处理器，约32GiB内存。不是Win10/Win11干净虚拟机验收。
- 独立数据目录：`.superpowers/sdd/ui-validation-20260915/profile-100`。只导入当前Android编码器生成的非空测试备份和reader fixture，没有操作用户书库。
- 实际 `MihonW.exe` 启动，主运行时Java17.0.18。完整EXE、cfg、应用JAR哈希记录于 `desktop-app/build/verification/suwayomi-ui/prerelease-image.json`。
- UI证据使用 `@oai/sky` 窗口截图及实际输入；不以Compose节点测试冒充真实EXE截图。

## 发现与修复

1. 第一轮EXE命令行导入成功，但GUI立即退出1：`LifecycleRegistry.addObserver must be called on the main thread`。兼容宿主的MainDispatcherFactory在没有其他provider的主程序中也被选中，错误地把主线程判作Android兼容线程。修复为普通桌面进程使用Swing EDT、扩展进程使用Handler；DesktopMainThreadTest检查实际EDT及LifecycleRegistry。重建后GUI正常启动。
2. 设置及关于页面各有一个旧 `1.0.0-desktop (Phase 8)` 文案，统一读取生成的0.2.0版本资源。
3. 900×600窄窗的语言按钮把English挤成竖排，设置导航底部无法到达。改FlowRow换行、左侧导航可滚动，关于页面也可滚动；修复后的最终DPI截图待追加。

## 已观察通过

- 宽窗：书库、实际本地漫画详情、章节入口、网络及后台设置显示可用。
- 本地阅读：真实fixture章节解码显示；单击隐藏工具栏后滚轮切换到不同页面，工具栏保持隐藏；Esc返回原详情。
- 900×600窄窗：书库工具条换行；详情切换为单栏并可滚动访问章节筛选、排序、书签、下载和阅读按钮。
- 真实CLI导入当前Android编码器样本及本地目录均退出0，UI呈现导入的漫画。

截图目录：`desktop-app/build/verification/suwayomi-ui/`，包含 `library-wide.png`、`detail-wide.png`、`settings-wide.png`、`reader-wheel-hidden.png`、`library-narrow.png`、`detail-narrow.png`。`settings-narrow-before.png`是修复前失败证据，不能视为通过截图。

## 进程内存初测

`scripts/measure-desktop-performance.ps1`按进程树采样，区分jpackage启动器和真正加载jvm.dll的进程。第一次jcmd探测启动器失败后改为枚举树中的JVM，避免把启动器13MB工作集误当应用总内存。

两部测试漫画、未启用浏览器，65.57秒/11个样本：进程树工作集最高378257408 bytes（约360.7MiB）；private bytes冷启动最高751341568，末次512901120 bytes。独立jcmd在实际JVM PID34060上观察堆已提交57344KiB、使用24470KiB，metaspace使用51276KiB。原始JSONL和初次失败诊断保留。

这是短时书库空闲测量，不能证明长阅读平台期、冷启动时间或帧率。30分钟主动阅读压力测试由独立报告记录；150%/200%缩放与窄窗修复复验将在新构建后追加。

## 14:17 预发行包复验

`createDistributable` run 92466 passed in 1m10s. EXE SHA256 remains `88C84A9F42411DECCBECA230AFF1CD055CA76E565C6745D116324397FE43F961`; application JAR SHA256 is `E4F11944E2C18308A553C927C096FB29DD29647B3ABB9ECF5E8A86705D6451CE`. This image includes the Swing main-thread fix, responsive settings layout, and optional reader soak. It predates the subsequent optional-null JSON and about-runtime-label corrections.

- 100%: 900×600 settings language buttons wrap correctly; the left navigation scrolls to Advanced and Diagnostics and opens the real network/background settings content.
- 150%: process-local `-Dsun.java2d.uiScale=1.5`, same logical window, language layout and 0.2.0 version remain readable. Tab visibly focuses the library navigation button; Return opens the library.
- 200%: process-local `-Dsun.java2d.uiScale=2`, settings and About content are legible and scrollable. About displayed a hardcoded OpenJDK21 label although the image runs Java17; all three locales now use the actual `java.version`. Final package recheck is still required for this last text fix.
- Scaling tests did not change Windows display/security settings and do not substitute for physical multi-monitor DPI migration or a full page/input matrix.

New screenshots: `settings-100-fixed.png`, `settings-100-nav-scroll.png`, `settings-100-advanced.png`, `settings-150-fixed.png`, `library-150-keyboard.png`, `settings-200-fixed.png`. `about-200-before-runtime-label.png` records the discovered incorrect runtime text, not final acceptance.

## 10,000-manga database and real EXE

The deterministic fixture writer exports a nonempty 156744-byte backup before its batch-removal assertions. The real EXE imported **10,000 manga and 10,000 chapters** into the independent `profile-10000`; import exit code 0. In the 1280×800 GUI, typed search `分组31` selected the expected group, mouse wheel advanced to later cards, and the bookmark filter selected the expected ten manga. Category badges display unread chapter totals (5000 before search, 50 for the selected group), not total manga counts.

Screenshots: `library-10000.png`, `library-10000-search.png`, `library-10000-scroll.png`, `library-10000-bookmarks.png`. The filter dialog's remaining English cycle hint was found and moved to English/Simplified/Traditional localized strings.

Run 95124 database/presenter measurements: seed 679ms; first snapshot 106ms; first search 23ms; 30 subsequent distinct searches P95 12ms/max14ms; remove100 204ms; one full snapshot. This measures observable presenter results over real SQLite and excludes frame rendering; the 300ms search target is met for this fixture/reference machine. These screenshots do not establish the 33ms frame-time target. The 10,000-manga GUI process briefly used about 611MB working set before adding its launcher, so the small-library <500MB idle observation must not be generalized to a 10,000-manga profile.
