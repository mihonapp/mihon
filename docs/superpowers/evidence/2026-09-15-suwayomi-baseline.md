# Suwayomi 演进基线

2026-09-15，在原checkout `D:\my project\mihon-w` 测试起始12文件修改；期间原checkout源码未修改。

- HEAD：`d88913d13078262ac051d9745111880362e2ff9a`。
- 隔离工作区完整保存为提交 `a75a1d76f`。
- 命令：`:extension-host:test --tests '*ExtensionHostEngineTest' :desktop-app:test --tests '*DesktopSourceManagerTest' --tests '*DesktopSourceManagerSourcePreferenceIpcTest' --tests '*WindowsExtensionProcessManagerTest' --tests '*ReaderScreenActionsTest' :reader-core:test --tests '*DefaultReaderSessionTest'`。
- 参数：Corretto23、`--no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process --console=plain`。
- 结果：`BUILD SUCCESSFUL in 59s`。
- XML总数：54 tests，49执行通过，5 skipped，0 failures/errors。5个跳过在WindowsExtensionProcessManagerTest，需额外进程测试开关；不作为真机host验证。
- 此验证覆盖已有source恢复、SourceFactory、阅读取消修改，不证明完整计划/发行完成。
- 原包时间：app-image和MSI 2026-09-15，installer EXE 2026-09-11，portableZIP 2026-09-12；下一发行阶段须统一重建。

后续新增回归已复现：Context持久化/隔离3项、下载4项、assets丢失、偏好业务前回放、安装自路径替换、direct APK source身份、API上限、IPC取消、域授权隔离和Cookie持久化/路径，以及HTTP取消。
