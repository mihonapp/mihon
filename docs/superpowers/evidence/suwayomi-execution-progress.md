# Suwayomi 计划执行记录

- 工作区：`<repository>\.worktrees\suwayomi`
- 分支：`codex/suwayomi-evolution`
- 原始 HEAD：`d88913d13078262ac051d9745111880362e2ff9a`
- 起始修改快照提交：`a75a1d76f`（原12文件修改及执行计划），原checkout保留。
- 原始补丁：`%TEMP%\mihonw-suwayomi-20260915\starting.patch`
- 用户已授权执行全计划，直接采用subagent结果，不进行重复review。
- 构建只能经过 `.superpowers/sdd/run-gradle.ps1 -GradleArguments @(...)`，以跨进程Mutex串行。

| 任务 | 状态 | 证据/负责人 |
|---|---|---|
| T0 基线 | 已完成 | 2026-09-15-suwayomi-baseline.md；a75a1d76f |
| T1 上下文/生命周期 | 已实现，集成复跑中 | suwayomi-context-persistence.md、suwayomi-source-lifecycle.md；追加失败加载清理、真实默认偏好和沙箱临时文件修复 |
| T2 网络/IPC | 已实现，定向回归通过 | suwayomi-network-and-desktop.md、suwayomi-host-network-bridge.md |
| T3 兼容矩阵 | 7个真实APK加载通过，外站流程另验 | ../../suwayomi-extension-compatibility.md；84个运行时图源，18项通过 |
| T4 浏览器 | 本地真实链路通过，网站样本未全验收 | suwayomi-webview-host.md；JBR21/JCEF、Android桥、父进程网络、Cookie、退出联动已验证 |
| T5 下载 | 已实现，真实沙箱完整流程通过 | suwayomi-download-recovery.md；调度、损坏恢复、取消、离线阅读回归 |
| T6 更新调度 | 已实现并通过定向验证 | suwayomi-update-recovery.md；唯一运行入口、恢复与UI接线 |
| T7 跟踪/凭据 | 本地及HTTP契约通过；真实账户待验证 | suwayomi-tracking-recovery.md；11服务、凭据迁移、恢复与冲突UI |
| T8 备份 | 桌面与Android编码器交叉验证通过 | suwayomi-backup-compatibility.md；模型样本不冒充目标应用实际恢复 |
| T9 UI/性能 | 实施中 | suwayomi-network-and-desktop.md、suwayomi-workflow-integration.md；10,000库通过，真实窗口及最终发行仍待验收 |
| T10 Windows能力 | 原生隔离与计划任务通过；安装接线验证中 | suwayomi-windows-extension-isolation.md、suwayomi-windows-background.md |
| T11 发行 | 实施中 | suwayomi-windows-release-acceptance.md；0.2.0统一版本、资源、卸载钩子、portable回滚工程，尚非最终发行 |

## 2026-09-15 集成回归

- 共享Gradle wrapper session 79724：BUILD SUCCESSFUL，3m18s。
- reader-core：178项，177通过、1跳过（极端图像另有专用任务）。
- desktop-library-data：114项全部通过。
- extension-sdk：23项全部通过。
- 同轮桌面选择RuntimeFactory、OnlineReaderIntegration、ReaderScreenActions、ReaderChromeParity、ReaderOverlayVisibility、LibraryPresenter、LargeLibraryWorkflow测试通过。其XML随后被其他代理的定向测试替换，不从后续XML倒推这一轮数量。
- session 43129：网络/Cookie/优先级、10,000库、任务和更新UI等9个测试类37项通过。
- 后续代理的真实流程测试暴露JDK17旧File.createTempFile在AppContainer查询卷信息失败，改为NIO临时文件创建，不放宽目录权限；正在真实流程复跑。

本文只记录已发生结果。真实生产账户、Win10/Win11干净机器、真实网站完整阅读和最终安装尚未通过的部分不计为完成。

## 最新集成收尾

- root run95124：六模块格式检查全部通过。桌面598项，590通过、1失败、7环境跳过；失败为新增可选soak字段默认null被输出，破坏既有稳定JSON合同，已加字段级EncodeDefault.NEVER并重跑。原生CPU配额2/2通过。
- 当前Android编码器交叉验证3项及极端图片2项已通过，详见备份报告；还需最终Android APK构建。
- GUI 100/150/200%缩放、窄窗设置导航、实际Tab/Return和10,000漫画搜索/滚动/书签筛选见桌面验收报告；长期阅读压力测试正在独立进程中执行。
- run91339继续执行六模块测试与SQLDelight迁移检查；下载完成测试出现一处断言失败，待失败明细确认并修正。不能将该轮计作全绿。

- run91339最终：extension-host45项/2环境跳过，SDK23、reader178/1跳过、数据层114、WebView协议1，均零失败；SQLDelight迁移通过。桌面598项/7跳过，唯一失败是完成回调尚未执行即读取null的测试竞态。已改CompletableDeferred等待实际回调并finally关闭下载器；生产下载完成顺序未改变。完整XML快照保留于`build/verification/integration-20260915-02`，其余模块无需因这一测试修正重复执行。
- root68490：桌面全量重跑及Android assembleDebug，包含最新运行时文案和筛选本地化修正，结果待下文记录。

- root68490：BUILD SUCCESSFUL，10m1s；桌面598项/0失败/7环境跳过，完整XML保留于`integration-20260915-03`。Android assembleDebug产出5个APK，`app.mihon.dev`、versionCode29、versionName0.20.4-8090；属于Android构建回归，不是Windows0.2.0分发包，也未安装手机。
- 30分钟压力验证在22分钟时因ReaderSession异步选页/跨章乱序而作废。新增`ReaderActionOrderingTest`以反序dispatcher和直接切换章节两个确定性场景复现，root6821为2/2失败；改为session action queue及旧章节操作过滤后，root72832新回归和DefaultReaderSessionTest通过。reader全回归与桌面阅读集成正在执行，不能让此前598项覆盖这一后续reader修改。

- root81430：BUILD SUCCESSFUL，1m37s。新reader-core全量180项/0失败/1专用极大图片跳过；桌面在线阅读、PackagedReaderScenario、ReaderScreenActions、ChromeParity、OverlayVisibility选择测试全部通过。旧的30分钟run01明确作废，后续最终发行目录的长测和三进程验收原始结果保留在`desktop-app/build/verification`；以带哈希的JSON为准。

## 680a062 集成包及界面焦点修正

- `680a062a02e379ceaab7527008495d186b46eaab` 已 fast-forward 合入原工作目录 `main`，原12文件和计划与起始快照逐字一致后另存具名stash，未丢弃或重新覆盖原修改。
- 同提交 `assembleWindowsRelease` 6m9s 成功；app-image/EXE/MSI/ZIP 版本均为0.2.0，revision准确、dirty=false，919个发行文件摘要通过。真实EXE三进程阅读和七格式/六模式验证通过；MSI仅做只读表检查，没有安装。
- 桌面全量 run45255：598项、0失败、7环境跳过，6m32s成功，存档于`integration-680a062-final`。便携版在中文空格目录、无JAVA_HOME且PATH仅含系统目录时，八个CLI进程全部成功，非空Android编码器样本导入/导出/重复导入稳定，914个载荷文件与app-image一致。ZIP中浏览器运行时的原生网络/JS/Cookie/退出验证2项通过，1m36s。
- 最终窗口检查额外发现工具栏选择模式后隐藏会丢失键盘焦点，Esc需先Tab才能生效。已通过真实鼠标输入测试复现，再让阅读内容点击和滚轮恢复阅读器焦点。首个回归先失败，修复后52项相关阅读UI测试与格式检查通过（run53949，40s），详见[焦点修正](suwayomi-reader-keyboard-focus.md)。这一后续变更需生成新的同提交包，不能把680a062旧包当作已包含焦点修正。
