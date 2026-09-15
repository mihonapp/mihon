# T5 下载恢复与来源公平调度

工作树：`D:\my project\mihon-w\.worktrees\suwayomi`，分支 `codex/suwayomi-evolution`。仅修改下载模块及其测试；没有复制上游源码。行为依据为本地计划 T5，延续现有 JSON 队列、共同 getImage 下载链和离线目录。

## 行为

- 保持队列显示顺序，以来源轮转选取下一章；全局章节/页面上限保持原设置，新增 `sourceParallelism`（默认每源一章）。失败来源让出后续调度机会。并发认领受同步保护。
- 恢复时不相信 READY 标记或文件非空：检查已知容器完整性并用 ImageIO 有界采样解码；ImageIO 不支持的格式走桌面 Skia 编码图片检查。只重新下载缺失/损坏页，重新计算磁盘上有效页的字节数。
- 页面先写 `.part`，校验成功才原子替换；失败删除 `.part` 并保留原页。恢复时清理未完成 `.part`。章节全部页再校验后才发布，已有章节先保留到 `.previous`，替换失败移回。
- 队列保存保留上一份有效 `.bak`；主文件读取失败先从快照恢复，`DownloadStore.recoveryReport` 明确暴露原文件与恢复来源。恢复本身不覆盖主文件；后续保存前把损坏原文归档为唯一 `.corrupt` 文件，损坏内容不会轮换进有效快照。
- 下载盘不可访问视为不可写。队列持久化失败停止调度并通过 `DesktopDownloader.storageError` 暴露错误；保留之前成功写入的队列和页。
- 暂停/恢复等待上一轮协程停止，generation 防止上一轮 finally 清除新一轮运行状态。取消章节等待该任务结束后再清理临时目录，避免清理后旧请求又写回；写页和章节完成前检查协程取消。

## 验证

主任务已运行初始 `DownloadRecoveryTest` 并确认四项红灯：损坏队列恢复、截断 JPEG 完成检查、单 worker 来源轮转、并发时另一来源获得槽位。

新增八项 `DownloadRecoveryTest` 覆盖上述四项以及损坏原文归档、失败页写入保留旧页并清理 partial、下载卷失联、真实 HTTP 恢复只重下损坏页。

原 `DesktopDownloaderTest` / `DownloadDiskProviderTest` 正常路径原先采用随机字节或四字节 JPEG 头；已替换为真实小 PNG，不降低新的校验条件。离线阅读两项原有测试本就使用实际 PNG。

绿灯结果：2026-09-15 经共享 Mutex 串行执行 `:desktop-app:test --tests mihon.desktop.download.*`，`BUILD SUCCESSFUL in 1m 8s`，8 类共 27 测试，0 失败/0 错误：DesktopDownloaderTest 6、DownloadAheadTest 2、DownloadAndReadOfflinePipelineTest 1、DownloadCacheCleanerTest 3、DownloadDiskProviderTest 3、DownloadRecoveryTest 8、DownloadStoreTest 3、OfflineDownloadedChapterReaderTest 1。测试 XML 位于 `desktop-app/build/test-results/test/TEST-mihon.desktop.download.*.xml`。

下载范围 `git diff --check` 通过。Spotless 已格式化下载文件，但模块级检查仍报告其他负责人文件中的两处长行（ExtensionNetworkSessionContractTest、TachiyomiExtensionCompatibilityTest），已通知 root，不能将该次 Spotless 执行标为整模块通过。

## 跨模块接线与边界

- root 负责 T2 底层 IPC/OkHttp 取消、429 Retry-After、host 级并发与前台阅读优先；下载层不添加第二套退避。现有共同下载链已重试 408/429/5xx。
- UI/Runtime 需订阅 `store.recoveryReport` 和 `storageError` 展示恢复/磁盘失败信息，本任务未改 UI。
- Skia 不支持或无法解码的文件保持失败，不宣称任意格式可恢复。实际站点、磁盘物理满盘、强制断电和 Windows 安装版流程尚需产品验收；自动测试不代替这些实测。
