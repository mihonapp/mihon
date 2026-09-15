# Windows 安装版扩展宿主握手修复（0.2.1）

## 故障与根因

0.2.0 安装版启动图源时全局报错：`Sandbox pipe handshake failed (alive=false, exit=1): java.util.concurrent.TimeoutException`，宿主 stderr 为空。使用实际已安装 EXE 的回归测试复现同一错误。

JDK 17.0.18 的 jpackage Windows 启动器检查 PATH 是否包含当前应用的 `app` 目录；缺少时会启动另一个自身进程。扩展宿主的干净环境未设置 PATH，触发该路径，与单进程 Job Object 限制以及 IPC 对等进程 PID 校验冲突。普通 `java.exe` 开发测试不会经过这条启动路径。

参考对应版本的上游实现：[WinLauncher.cpp](https://github.com/openjdk/jdk17u/blob/jdk-17.0.18-ga/src/jdk.jpackage/windows/native/applauncher/WinLauncher.cpp#L219)、[AppLauncher.cpp](https://github.com/openjdk/jdk17u/blob/jdk-17.0.18-ga/src/jdk.jpackage/share/native/applauncher/AppLauncher.cpp#L88)。

## 修复

在沙箱中启动 jpackage 应用时，把 PATH 设置为此次暂存的只读 `app` 目录，使启动器在获准的进程中直接启动 JVM。由对应的 EXE 配置文件识别打包应用；普通 Java 宿主启动保持原有行为。

单进程限制、内存与 CPU 配额、AppContainer、网络代理及 Named Pipe 的 PID/nonce 校验均继续生效。

## 回归

`PackagedExtensionHostTest` 必须使用 `MIHON_PACKAGED_EXE` 指定真实 EXE：

- 修复前复现相同 `alive=false, exit=1` 错误。
- 修复后完成握手、PING、重启，并断言宿主没有额外子进程。
- 设置 `MIHON_EXTENSION_SMOKE_DIRECTORY` 后逐包验证安装扩展的源 ID 与偏好读取。

这组测试依赖真实 Windows 分发包，未提供环境变量时显式跳过，不能以开发 JVM 的测试结果代替安装包验证。构建和安装的原始记录保存在对应 0.2.1 交付目录中。
