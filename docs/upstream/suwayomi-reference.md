# Suwayomi 参考记录

- 正式基线：`v2.3.2243` / `1d583ca4a718646e17a4c62d23fe8e5edccaf774`。
- 调查与特定修复参考：`d10e000e1fdcac6f3c84d002f0b459c90c1b00f3`。
- 两版本JVM target为21；MihonW主程序仍为17。按模块适配，不直接依赖完整server。
- 完整官方永久链接和架构边界见 `docs/superpowers/plans/2026-09-15-suwayomi-based-mihonw-evolution.md`。

| 上游 | 本地 | 采用行为 | 本地差异 |
|---|---|---|---|
| Extension.kt / PackageTools.kt | DesktopSourceManager / Installer / Converter / ExtensionHostEngine | 单次工厂实例化、加载失效恢复、资源/多dex与卸载 | Windows独立host；保留源身份和事务替换 |
| AndroidCompat | android Context/Application/SharedPreferences | 持久上下文和资源访问 | 按扩展目录隔离，避免全局公共临时目录 |
| NetworkHelper.kt | DesktopNetworkHelper / host NetworkHelper | 统一请求策略与会话 | 来源隔离、严格Cookie匹配、取消传播 |
| DownloadManager.kt / Downloader.kt | DesktopDownloader / DownloadStore | 同源并发限制、公平调度与恢复 | 保留有序JSON原子快照，不退化为ID集合 |

以上为行为参考。复制上游源码时必须另记录精确源文件、保留相应版权/许可头，并纳入发行清单；未复制的独立实现不伪称已直接整合上游模块。
