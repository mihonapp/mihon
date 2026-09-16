# mihondesk — Windows 发行与升级

mihondesk 是 AI 辅助开发的 Windows 漫画阅读器，基于 Mihon。当前发行版本由 `desktop-version.txt` 管理。

## 发行文件

| 文件 | 用途 |
| --- | --- |
| `mihondesk-<版本>.exe` | Windows 安装向导 |
| `mihondesk-<版本>.msi` | MSI 安装包 |
| `mihondesk-<版本>-windows-x64-portable.zip` | 便携版，解压后运行 `mihondesk/mihondesk.exe` |
| `mihon-build-info.properties` | 版本、源码提交和工作树状态 |
| `SHA256SUMS.txt` | 文件摘要 |

下载入口：[GitHub Releases](https://github.com/1873412297-art/mihondesk/releases/latest)。发行包内置 Java 17；网页辅助进程使用独立 Java 21/JCEF 运行时。

## 数据与升级兼容

- 安装版的默认程序目录为 `%LOCALAPPDATA%\mihondesk`。
- 安装版继续使用 `%APPDATA%\MihonW` 数据目录，以便从 0.2.4 及以前版本升级时保留书架、图源、设置和登录数据。
- Windows 安装升级识别码保持 `07E02BEA-9179-4E54-A1AF-CFC185C91398`。
- 凭据存储、文件关联 ProgID 与后台任务的内部标识保持兼容，程序显示名和新启动文件为 `mihondesk` / `mihondesk.exe`。
- 便携版数据位于启动文件同目录的 `data` 文件夹。升级时保留该文件夹。

## 干净发行规则

发行包不预装图源或扩展仓库，也不包含账号或阅读数据。首次使用请自行添加仓库或安装扩展。

EXE、MSI 和便携 ZIP 都依赖 `verifyCleanDistribution`，打包前执行 `scripts/verify-release-clean.ps1`。检查会拒绝个人数据目录、已安装扩展包、偏好文件、Cookie 和数据库。用于验证的独立数据目录、日志与升级备份放在发行目录以外。

## 构建与验证

在已配置本仓库构建环境的 Windows PowerShell 中：

```powershell
.\gradlew.bat :desktop-app:assembleWindowsRelease
```

统一产物位于 `desktop-app/build/releases/<版本>/`，其中 `app-image/mihondesk` 为应用目录。发布前核对构建身份、包内容、安装升级和 SHA-256，并运行相关验证：

```powershell
.\scripts\verify-release-clean.ps1 -ImagePath '<应用目录>'
.\scripts\verify-msi-package.ps1 -MsiPath '<MSI 路径>'
.\scripts\verify-desktop-clean-machine.ps1 -PortableZip '<便携 ZIP 路径>' -SkipBuild
```

最后一个脚本验证独立目录中的便携运行、版本、帮助和备份导出。Windows 10 / 11 的全新系统验收需另外执行。

推送代码分支不会发布 Windows 新版本。仓库保留的上游 Release 工作流只为上游 Android 仓库运行。Windows 发布时还需更新首页与更新日志，将验证后的 EXE、MSI、便携 ZIP、版本与构建信息、SHA-256 清单上传到对应版本的 GitHub Release，核对远程附件摘要后设为最新正式版。清单应只列出随 Release 上传的文件。

## 更新

从 0.2.5 起，内置更新检查访问 `1873412297-art/mihondesk` 仓库的最新 Release，并按安装版或便携版选择附件。0.2.4 及更早版本仍需先手动下载新版本。

便携包包含 `mihondesk-updater.ps1`。它校验 SHA-256、等待调用程序退出、保留 `data`、替换程序并运行 `--version`；验证失败时恢复旧程序。可使用 `-ExecutableName MihonW.exe` 处理旧名称的归档。不要将带个人数据的 ZIP 用作更新包。

后台任务卸载清理仅删除指向当前安装程序的任务；升级后启用的后台任务由程序重新绑定到新的启动文件。
