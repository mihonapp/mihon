<div align="center">

<img src=".github/assets/logo.png" alt="mihondesk" width="88" />

# mihondesk

**在 Windows 上管理书架，阅读漫画。**

AI 辅助开发的开源 Windows 漫画阅读器 · 基于 Mihon · Kotlin / Compose Desktop

![AI 辅助开发项目](https://img.shields.io/badge/AI-assisted%20development-7c3aed)

[![最新版本](https://img.shields.io/github/v/release/1873412297-art/mihondesk?label=Release&color=0057d9)](https://github.com/1873412297-art/mihondesk/releases/latest)
[![下载量](https://img.shields.io/github/downloads/1873412297-art/mihondesk/total?label=Downloads&color=0057d9)](https://github.com/1873412297-art/mihondesk/releases)
![Windows x64](https://img.shields.io/badge/Windows-x64-0078d4)
[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-64748b)](LICENSE)

**[下载 Windows 版](https://github.com/1873412297-art/mihondesk/releases/latest)** · [版本说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.5) · [反馈问题](https://github.com/1873412297-art/mihondesk/issues)

</div>

**AI 项目说明**：本仓库的 Windows 移植、功能迭代、问题修复和文档维护使用 AI 编程助手协助完成。上游 Mihon、Suwayomi 及其他第三方项目的贡献与许可归属见文末。

## 下载与安装

当前版本：**0.2.5**。面向 **Windows 10 22H2 / Windows 11，x64**，发行包已内置运行环境，无需另装 Java。

程序、安装包和快捷方式现已统一命名为 **mihondesk**。安装版继续使用原有数据目录，支持从 MihonW 升级。

| 下载 | 适用方式 |
| --- | --- |
| **[EXE 安装包](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.5/mihondesk-0.2.5.exe)** | 推荐使用，下载后按向导安装 |
| [MSI 安装包](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.5/mihondesk-0.2.5.msi) | 需要 MSI 安装方式时使用 |
| [便携 ZIP](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.5/mihondesk-0.2.5-windows-x64-portable.zip) | 完整解压到可写目录，运行其中的 `mihondesk.exe` |
| [SHA-256 校验文件](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.5/SHA256SUMS.txt) | 校验下载文件的完整性 |

**干净发行包**：不预装图源或扩展仓库，不包含开发者的书架、账号、Cookie、阅读记录或个人设置。首次使用请自行添加仓库或安装扩展；升级会继续使用你本机已有的数据。

安装版默认将数据保存在 `%APPDATA%\MihonW`；便携版保存在 `mihondesk.exe` 同目录下的 `data` 文件夹。迁移便携版时请一并保留该文件夹。

## 可以做什么

| 功能 | 说明 |
| --- | --- |
| 本地阅读 | 导入图片目录，以及 ZIP / CBZ、RAR / CBR、7z / CB7、TAR 和图片型 EPUB 等文件 |
| 桌面阅读器 | 单页、双页、纵向和条漫模式，阅读方向切换、缩放、键盘与鼠标操作 |
| 书架管理 | 分类、搜索、阅读进度和历史；在线漫画通过明确的加入书架操作收藏 |
| 图源浏览 | 安装扩展，浏览、搜索漫画并查看章节；支持部分 Mihon / Tachiyomi 扩展的转换与兼容运行 |
| 离线与更新 | 章节下载、下载队列恢复、书库章节更新 |
| 备份与恢复 | 导入、导出 Mihon 兼容备份；具体字段支持范围见下方兼容说明 |

## 开始使用

1. **安装或解压程序**，运行 `mihondesk.exe`。
2. **阅读本地文件**：使用本地导入入口选择漫画文件或图片目录。
3. **阅读在线内容**：在浏览页安装扩展包，再选择图源。安装入口支持 `.mext`，也可尝试转换受支持的扩展 `.apk`。
4. **保留阅读数据**：使用备份功能定期导出。备份不包含已下载的漫画图片。

## 0.2.5 更新

- 程序显示名、窗口标题、通知、安装包、快捷方式与启动文件统一改为 mihondesk。
- 更新检查改为访问本仓库，并识别新名称的安装包与便携 ZIP。
- 保留原有升级识别码、数据目录与账号存储，已有书架和配置继续使用。
- 延续 0.2.4 的干净发行规则：无预装图源或仓库，打包前拦截误混入的个人数据。

详见[本版发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.5)。此前的图源兼容修复见 [0.2.3 发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.3)。

## 当前限制

mihondesk 仍在持续开发，桌面端尚未覆盖 Mihon 的全部功能。

- **扩展兼容性因图源而异**：扩展通过桌面兼容层运行；能够安装或加载，并不代表该图源的所有功能都可用。
- **网站验证与封锁**：登录或验证可以使用程序提供的网页入口；能否通过取决于网站。0.2.3 测试时，EZmanga 的网页和 API 均返回 403 封锁，本版本能识别并说明该情况，无法解除网站封锁。
- **旧版本升级**：0.2.4 及更早版本的内置更新检查指向旧仓库，首次升级请从[本仓库 Releases](https://github.com/1873412297-art/mihondesk/releases/latest)手动下载。
- **备份与跟踪服务**：部分设置不会迁移，生产账户的完整登录与同步流程仍需继续验证。请查看[备份兼容范围](docs/superpowers/evidence/suwayomi-backup-compatibility.md)和[功能覆盖说明](docs/superpowers/evidence/suwayomi-feature-coverage.md)。

## 反馈与开发

遇到问题请在[本仓库 Issues](https://github.com/1873412297-art/mihondesk/issues)反馈，附上 mihondesk 版本、Windows 版本、复现步骤和错误信息。图源问题请同时提供扩展名称及版本。

欢迎提交改进，参见[贡献指南](CONTRIBUTING.md)。开发资料：

- [扩展兼容矩阵](docs/suwayomi-extension-compatibility.md)
- [功能覆盖与验证范围](docs/superpowers/evidence/suwayomi-feature-coverage.md)
- [Windows 打包与验收说明](docs/superpowers/evidence/suwayomi-windows-release-acceptance.md)

## 上游与许可

本项目是 [Mihon](https://github.com/mihonapp/mihon) 的独立 Windows 移植项目，基于其阅读与备份相关实现，并参考 [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) 的扩展兼容与后台服务实现。感谢上游项目及所有贡献者。Android 原版请访问 [Mihon 官网](https://mihon.app)。

项目采用 [Apache License 2.0](LICENSE)。桌面端第三方组件及许可见 [THIRD-PARTY-DESKTOP.txt](THIRD-PARTY-DESKTOP.txt)。程序本身不提供或托管漫画内容。

Copyright © 2015 Javier Tomás

Copyright © 2024 Mihon Open Source Project
