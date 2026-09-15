# T10 Windows 后台更新、备份与单实例数据目录

日期：2026-09-15。工作区：`D:\my project\mihon-w\.worktrees\suwayomi`，分支 `codex/suwayomi-evolution`。未提交，未修改用户真实计划任务或安装。

## 已实现行为

- `platform/WindowsBackgroundScheduler.kt`：真实 `System32/schtasks.exe` 进程调用，使用参数数组，不经命令行 shell 拼接。支持 `register`、`query`、`remove`、`removeAll`、`runNow`、`reconcile`。构造无副作用。查询和删除保留退出码/错误文本；删除已不存在的任务在英/简/繁 Windows 错误文本下幂等，未知错误仍上报。
- 任务名为 `MihonW-<profile hash>-LibraryUpdate|Backup`，安装路径改变时保持任务名，通过 `/Create /F` 更新实际 EXE 路径。不会枚举或修改其他应用任务。
- XML 使用明确 UTF-16LE BOM，独立 `Command`、`Arguments`、`WorkingDirectory` 节点，Windows 参数转义覆盖空格、中文、反斜线和引号。`IgnoreNew` 防止相同任务并行；`StartWhenAvailable` 允许补触发，CLI 自身只执行一个到期周期。采用当前用户 `InteractiveToken` 和 `LeastPrivilege`，不保存密码、不提权。
- `platform/DesktopProfileLock.kt`：FileChannel 真实跨进程排他锁；数据目录规范化后，锁从打开 SQLite 前持有到 Runtime 关闭。关闭不删除锁文件，避免删除重建导致双持有。`Main.kt` 已接线，重复后台进程输出 `PROFILE_IN_USE` / SKIPPED 并退出 0；普通重复启动退出 75。扩展 host 模式不持有主数据目录锁。
- `DesktopCommand.kt` / `DesktopCommandRunner.kt`：新增 `--background-update`、`--background-backup`。调用现有 `checkAndRunAutoUpdate()` / `checkAndRunAutoBackup()`，遵守关闭/周期/最后运行时间，不强制重刷、不打开 Compose 窗口；输出结构化 SUCCEEDED/SKIPPED/FAILED，失败退出非零。
- `DesktopPreferenceStore.kt`：`backgroundTasksEnabled = false`，属性键 `background.tasks_enabled`。默认不注册系统后台任务。
- `ui/settings/BackgroundSettingsCard.kt` / `SettingsScreen.kt`：高级设置新增简/繁/英后台开关。两个周期均关闭时明确提示先配置周期；启用/移除失败显示原因并尝试回滚。启用成功后才保存开关。启用后修改更新或备份周期会更新系统任务。
- `DesktopRuntime.kt`：只从当前进程实际 `MihonW.exe` 构造后台调度器，不误用开发机 `java.exe`。只有普通 UI 启动且用户已启用时才刷新安装路径；后台命令不会递归注册。主代理已承接 `MihonDesktopApp → DesktopShell → SettingsScreen` 的 scheduler 参数。

Task Scheduler XML 依据 Microsoft 的[每日触发器 XML 示例](https://learn.microsoft.com/en-us/windows/win32/taskschd/daily-trigger-example--xml-)与[任务计划架构](https://learn.microsoft.com/en-us/windows/win32/taskschd/task-scheduler-schema)。未复制 Suwayomi 源码。

## 验证与已发现问题

- 红阶段：新增后台 CLI 解析测试在原版本抛 `CommandLineException`，随后加入入口。
- 真机 Windows 计划任务测试只在 `MIHON_W_TEST_SCHEDULED_TASK=1` 启用，创建随机 `MihonW-Test-<UUID>` 临时任务，查询定义后在 finally 清理，并确认不存在。首次真实调用发现无 BOM 的 UTF-16LE 被 schtasks 拒绝（根元素错误），修复 BOM 后 `BUILD SUCCESSFUL in 49s`。
- 同进程重复持锁、关闭后重新持锁；Windows PowerShell 子进程对相同文件范围加锁被拒绝（73），父锁释放后成功（0）。
- 任务 XML 覆盖中文/空格/& 路径、当前用户权限、错过周期行为；mock runner 覆盖无构造副作用、注册后临时 XML 清理、安装路径变化身份稳定、查询/移除。
- `BackgroundCommandTest` 使用真实临时 SQLite 与真实备份导出，验证关闭周期 SKIPPED，开启周期运行一次并实际产生一个 `.tachibk`，再次运行不重复备份。
- `BackgroundSettingsCardTest` 验证默认关闭、两个周期关闭时中文提示且不调用 OS、显式启用注册成功后保存偏好。

最终联合测试结果：`BUILD SUCCESSFUL in 34s`，共 22 项，0 failure / 0 error / 0 skipped。

| 测试类 | 数量 |
|---|---:|
| WindowsBackgroundSchedulerTest | 6 |
| BackgroundCommandTest | 1 |
| BackgroundSettingsCardTest | 2 |
| DesktopCommandTest | 13 |

本轮设置 `MIHON_W_TEST_SCHEDULED_TASK=1`，真实 Windows 计划任务测试没有跳过；已完成临时任务创建、查询、删除、查询不存在。逐项读取本轮 `desktop-app/build/test-results/test/TEST-*.xml` 确认数量。本任务路径 `git diff --check` 通过。

全部 Gradle 经 `.superpowers/sdd/run-gradle.ps1` 共享 Mutex。新文件定向格式化，共享 SettingsScreen/prefs/Runtime 的统一格式化由主代理负责。

## API 与剩余接线

- `WindowsBackgroundScheduler(executable, profileDirectory).reconcile(enabled, updateIntervalHours, backupIntervalHours)` 返回各任务结果；注册失败抛异常，`lastFailure` 保留 UI 可读原因。需要卸载清理时使用同 profile 的 `removeAll()`，由安装器按用户选择调用。
- `DesktopProfileLock.acquire(profileRoot).use { ... }` 已由 Main 在运行时创建前持有；内部测试直接构造 Runtime 不额外持锁。
- `SettingsScreen(..., backgroundScheduler = runtime.backgroundScheduler)` 参数由主代理接入。

**边界：** 当前 Windows 用户需要保持登录，支持关闭 MihonW 后执行；不承诺注销后运行。不启动本机真实用户的后台计划。卸载安装器调用 removeAll 的最终发行接线、实际打包 EXE 的定时启动验收仍由 T11 完成；本轮真机验证计划任务的注册/查询/移除，没有让隔离 cmd.exe 临时任务执行业务。后台更新产生的自动下载进入现有持久队列，不宣称本轮已验收退出前所有下载都完成。AppContainer、Named Pipe 沙箱由另一任务负责。
