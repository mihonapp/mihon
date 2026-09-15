# T6 书库更新归一与恢复

日期：2026-09-15。工作区：`<repository>\.worktrees\suwayomi`，分支 `codex/suwayomi-evolution`。本任务未提交。

## 已实现

- `desktop-app/src/main/kotlin/mihon/desktop/library/update/LibraryUpdateService.kt`：按 sourceId 分组，同源串行，跨源最多 3 个并发；每项远程刷新默认 60 秒超时。一个来源失败不会取消其他来源。外部 `CancellationException` 原样传播。
- 保留分类 include/exclude、完结、未读、未开始过滤和新增章节自动下载；跳过本地源。刷新使用 `prepareOnlineMangaForReading`，避免在更新期间把用户刚移出书库的漫画再次设为收藏。
- `LibraryUpdateModels.kt`：进度增加当前 sourceId；更新选项增加失败漫画重试范围。
- `LibraryUpdateScheduler.kt`：手动、定时、启动补跑进入同一个 `tryLock`，正在运行时返回空结果而不排队。周期过期在启动立即只跑一次。提供 `cancelUpdate()`、`stop()`、`runState`，取消后停止后续漫画请求并保留可见取消状态。
- `LibraryUpdateStateStore.kt`：独立原子 properties 状态文件保存最后尝试、最后完成、状态、错误、失败漫画 ID、重试次数和时间。失败从 5 分钟指数退避，封顶 6 小时；重启延续退避，重试只刷新失败漫画。重试不会推迟健康来源的完整周期刷新。程序异常中断留下的 RUNNING 状态会触发一次补跑。
- `desktop-app/src/main/kotlin/mihon/desktop/updates/DesktopLibraryUpdateService.kt`：旧签名仅为兼容适配层，委托主服务的过滤、并发、进度、错误处理和结果统计。保留旧单测和离线管线消费者；不再拥有重复更新循环。

## 验证

新增 `desktop-app/src/test/kotlin/mihon/desktop/library/update/LibraryUpdateRecoveryTest.kt`，7 个行为测试：

1. 来源取消异常传播。
2. 来源 A 等待时 B 仍能完成，同源下一漫画不会抢跑。
3. 手动和定时共享锁，重启不会重放历史周期。
4. 失败来源状态落盘、重启后退避、只重试失败漫画；完整周期到期仍刷新健康来源。
5. 来源超时变为明确失败且健康来源完成。
6. 主动取消停止后续漫画，持久化 CANCELLED，未写入成功完成时间。
7. 五来源最多三个并发。

红阶段已运行：原服务吞取消导致断言失败。并发测试初版在超时清理时出现数据库关闭异常，随后修正夹具，确保等待子任务结束再关闭数据库；不将该清理异常作为并发行为证据。随后实现修复。中途两轮 green 因并行 T7 测试先于其新 API 落盘而在 test 编译阶段阻塞，未误报为 T6 通过。

最终测试结果：`BUILD SUCCESSFUL in 35s`，共 14 项测试，0 failure / 0 error。

| 测试类 | 数量 |
|---|---:|
| LibraryUpdateRecoveryTest | 7 |
| LibraryUpdateSchedulerTest | 1 |
| LibraryUpdateServiceTest | 4 |
| DesktopLibraryUpdateServiceTest | 2 |

命令：`& .\.superpowers\sdd\run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','*LibraryUpdateRecoveryTest','--tests','*LibraryUpdateServiceTest','--tests','*LibraryUpdateSchedulerTest')`。

结果逐项读取自 `desktop-app/build/test-results/test/TEST-*LibraryUpdate*.xml`。本任务路径的 `git diff --check` 通过。

全部 Gradle 经 `.superpowers/sdd/run-gradle.ps1` 的共享 Mutex，未直接调用 gradlew。格式化只使用本任务文件绝对路径的 `spotlessIdeHook`，未覆盖其他代理文件。

## Runtime 接线与边界

已将具体 API 发给主代理；Runtime/UI 由主代理单独负责：

- 移除 Runtime 中旧 `updates.DesktopLibraryUpdateService` 实例和字段；真实入口统一使用 `libraryUpdateScheduler`。
- 构造主 scheduler 时传 `recoveryFile = <应用数据目录>.resolve("library-update-state.properties")`。
- 任务中心/UI 展示 `runState.status`、`runState.error`、`currentProgress.currentSourceId`，取消按钮调用 `cancelUpdate()`；退出时调用 `stop()` 或取消所属 scope。

本任务自身不宣称已接完 Runtime/UI；主代理接线后的构建结果由总体验收记录。旧 `DesktopUpdateScheduler` 仅存于兼容测试路径，Runtime 不应再创建它。

跨进程数据目录锁、Windows 计划任务、应用关闭后的执行属于 T10，本任务没有实现。测试使用确定性来源和真实临时 SQLite 数据库，没有用真实网站或安装包执行桌面交互。没有复制 Suwayomi 源码；本次是对本地已有更新器的行为实现。
