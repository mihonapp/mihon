# 为 mihondesk 做贡献

欢迎改进 mihondesk 的 Windows 阅读体验、扩展兼容、文档和测试。

## 反馈问题或建议

请使用[本仓库 Issues](https://github.com/1873412297-art/mihondesk/issues)，选择对应表单。问题报告请提供程序版本、Windows 版本、安装方式、复现步骤和错误信息；扩展问题请附扩展名称与版本。

提交前可查看[最新发布说明](https://github.com/1873412297-art/mihondesk/releases/latest)和[功能覆盖说明](docs/superpowers/evidence/suwayomi-feature-coverage.md)。

## 代码贡献

向本仓库提交 Pull Request，并说明解决的问题、最终行为与验证方式。涉及较大功能或结构调整时，建议先通过 Issue 说明目标。参与讨论请遵守[行为准则](CODE_OF_CONDUCT.md)。

Windows 相关模块主要包括：

| 模块 | 职责 |
| --- | --- |
| `desktop-app` | Compose Desktop 界面、应用服务与 Windows 发行 |
| `reader-core` | 阅读会话、本地格式、图片与内存管理 |
| `desktop-library-data` | 桌面书库与持久化 |
| `extension-sdk` / `extension-host` | 扩展契约、兼容层与隔离宿主 |
| `desktop-webview-host` | 独立网页窗口与浏览器运行时 |

请熟悉 Kotlin 与 Gradle；界面工作使用 Compose Desktop。构建使用仓库中的 Gradle Wrapper，Windows 安装包需要在 Windows 环境生成。主程序使用 Java 17 工具链，浏览器辅助进程使用独立 Java 21/JCEF 运行时。

在已经配置好本仓库构建环境的 PowerShell 中：

```powershell
# 运行桌面程序
.\gradlew.bat :desktop-app:run

# 生成 EXE、MSI、便携 ZIP、构建身份与校验清单
.\gradlew.bat :desktop-app:assembleWindowsRelease
```

发行版本由 `desktop-version.txt` 管理，产物位于 `desktop-app/build/releases/<版本>/`。环境与打包细节见 [Windows 发行说明](docs/superpowers/evidence/suwayomi-windows-release-acceptance.md)。

提交前检查改动格式，并运行与改动相关的测试。扩展修复请区分“能够转换或加载”与“真实网站完整阅读通过”；网站自身拒绝访问时，请如实记录结果。

## 上游与许可

本项目基于 [Mihon](https://github.com/mihonapp/mihon)，并参考 [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) 的相关实现。引入代码时请保留来源与适用的版权、许可信息。

项目许可见 [LICENSE](LICENSE)，桌面第三方组件见 [THIRD-PARTY-DESKTOP.txt](THIRD-PARTY-DESKTOP.txt)。
