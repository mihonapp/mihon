# mihondesk 更新日志

这里记录 mihondesk Windows 版的更新。安装包与完整发布说明见 [GitHub Releases](https://github.com/1873412297-art/mihondesk/releases)。继承自 Android 上游的历史日志保留在 [Mihon 更新日志](docs/upstream/MIHON_CHANGELOG.md)。

## [0.2.13](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.13)

- 修复恢复下载时图源隔离进程路由丢失，以及下载队列、本地文件和离线记录不同步的问题。
- 已完成章节的本地文件缺失时，可以重新下载；清理缓存后同步修正下载状态。
- 支持选择下载目录，并展示当前生效路径。
- 兼容 NHentai.xxx 图片 CDN 的受限跳转，保留按扩展隔离的网络访问规则。
- 修复深色与纯黑主题下阅读器标题、图标、页码及更新日历文字不可见的问题。
- 补齐阅读菜单、图片操作、章节筛选、下载进度、应用锁、扩展管理和更新日历的中文；简体、繁体、英文切换会同步更新界面。
- 统一扩展、翻译组、Cookie 与网站设置等用词，日期按应用选择的语言显示。图源提供的书名、章节名和翻译组名称保留原文。

## [0.2.10](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.10)

- 更换为全新的展开书页与书签图标，统一窗口、任务栏、桌面快捷方式和安装包。
- 更新 GitHub 横幅、项目介绍、贡献指南和反馈模板，补充品牌资源与导出说明。
- 将 Windows 版更新日志与保留的 Mihon 上游历史分开，方便查阅对应版本。
- 更新 Windows 构建产物路径，并限制上游网站通知工作流只在上游仓库运行。

## [0.2.9](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.9)

- 阅读器顶部新增“书籍详情”，保存进度后打开当前书籍详情。
- 支持查看未收藏的下载书籍，并在详情、阅读器和原入口之间正确返回。
- 鼠标停留在阅读器工具栏时保持显示，避免按钮自动隐藏。
- 移除默认内置的 MangaDex，发行版不预装在线图源或扩展仓库。

## [0.2.8](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.8)

- 下载页可直接阅读已经完成下载的章节。
- 使用已下载文件离线打开内容，并保留阅读进度。

## 更早版本

程序与安装包已统一命名为 mihondesk，并保持旧版本的数据升级兼容。更早版本的具体改动请查看[历史 Releases](https://github.com/1873412297-art/mihondesk/releases)。
