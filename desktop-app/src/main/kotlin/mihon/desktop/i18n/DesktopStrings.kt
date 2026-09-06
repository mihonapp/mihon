package mihon.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import mihon.desktop.navigation.DesktopDestination
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    System("system", "Follow System"),
    SimplifiedChinese("zh-CN", "简体中文"),
    TraditionalChinese("zh-TW", "繁體中文"),
    English("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: System
    }
}

interface DesktopStrings {
    val appName: String

    // Navigation
    fun destinationLabel(destination: DesktopDestination): String
    fun destinationShortLabel(destination: DesktopDestination): String

    // Library
    val libraryTitle: String
    val libraryImportBackup: String
    val libraryImportLocal: String
    val librarySearchPlaceholder: String
    val libraryManageCategories: String
    val libraryEmptyTitle: String
    val libraryEmptySubtitle: String
    fun libraryNoMatchTitle(query: String): String
    val libraryNoMatchSubtitle: String
    val libraryRetry: String
    fun libraryChaptersCount(count: Int): String
    fun libraryUnreadCount(count: Int): String
    val libraryAllCategory: String

    // Manga Detail
    fun mangaDetailSource(name: String): String
    val mangaDetailStatusOngoing: String
    val mangaDetailStatusCompleted: String
    val mangaDetailStatusUnknown: String
    val mangaDetailInLibrary: String
    val mangaDetailAddToLibrary: String
    val mangaDetailCategories: String
    val mangaDetailTracking: String
    fun mangaDetailResume(chapter: String): String
    fun mangaDetailStart(chapter: String): String
    val mangaDetailNoChapters: String
    val mangaDetailBack: String

    // Updates
    val updatesTitle: String
    val updatesCheckButton: String
    val updatesChecking: String
    val updatesEmptyTitle: String
    val updatesEmptySubtitle: String
    val updatesReadButton: String

    // History
    val historyTitle: String
    val historyHeaderSubtitle: String
    val historySearchPlaceholder: String
    val historyClearAll: String
    val historyEmptyTitle: String
    val historyEmptySubtitle: String
    val historyToday: String
    val historyYesterday: String
    val historyResumeButton: String
    val historyDeleteButton: String
    val historyClearDialogTitle: String
    val historyClearDialogMessage: String
    val historyNoMatch: String
    fun historyGroupTitle(title: String): String

    // Downloads
    val downloadsTitle: String
    val downloadsPauseAll: String
    val downloadsResumeAll: String
    val downloadsClearCompleted: String
    val downloadsEmptyTitle: String
    val downloadsEmptySubtitle: String
    val downloadsStatusDownloading: String
    val downloadsStatusPaused: String
    val downloadsStatusCompleted: String
    val downloadsStatusError: String
    val downloadsCancel: String
    val downloadsRetry: String
    fun downloadsActiveSpeed(activeCount: Int, speedText: String): String

    // Browse
    val browseTitle: String
    val browseTabSources: String
    val browseTabExtensions: String
    val browseManageRepositories: String
    val browseRefresh: String
    val browseInstall: String
    val browseUninstall: String
    val browseInstalledBadge: String
    val browseSearchPlaceholder: String
    val browseNetworkDomainsHeader: String

    // Reader
    val readerModeSingleLtr: String
    val readerModeSingleRtl: String
    val readerModeWebtoon: String
    val readerScaleFitScreen: String
    val readerScaleFitWidth: String
    val readerScaleFitHeight: String
    val readerScaleOriginal: String
    val readerEscapeHint: String

    // Settings
    val settingsTitle: String
    val settingsSectionGeneral: String
    val settingsSectionAppearance: String
    val settingsSectionReader: String
    val settingsSectionDownloads: String
    val settingsSectionTracking: String
    val settingsSectionBackup: String
    val settingsSectionAdvanced: String

    // Settings - General
    val settingsLanguageTitle: String
    val settingsLanguageSystem: String
    val settingsLanguageSimplifiedChinese: String
    val settingsLanguageTraditionalChinese: String
    val settingsLanguageEnglish: String
    val settingsAppInfoTitle: String
    fun settingsVersionLabel(version: String): String
    fun settingsPlatformLabel(platform: String): String

    // Settings - Appearance
    val settingsThemeModeTitle: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String

    // Settings - Reader
    val settingsDefaultReadingMode: String
    val settingsDefaultScaleMode: String
    val settingsMouseWheelBehavior: String
    val settingsWheelScrollPage: String
    val settingsWheelFlipPage: String
    val settingsInvertWheel: String
    val settingsPageTransitions: String
    val settingsAnimateTransitions: String
    val settingsDoubleSpread: String

    // Settings - Downloads
    val settingsDownloadLocation: String
    val settingsDefaultStorageFolder: String
    val settingsParallelDownloads: String

    // Settings - Tracking
    val settingsTrackingTitle: String
    val settingsTrackingDescription: String
    fun settingsConnectedTrackers(count: Int): String

    // Settings - Backup
    val settingsBackupTitle: String
    val settingsBackupDescription: String
    val settingsExportBackupButton: String
    val settingsImportBackupButton: String

    // Settings - Advanced
    val settingsDiagnosticsTitle: String
    val settingsRunIntegrityCheck: String
    val settingsExportDiagnosticBundle: String
    fun settingsDatabaseIntegrity(status: String): String
    fun settingsOsInfo(info: String): String
    fun settingsJavaInfo(info: String): String
    fun settingsLogFilesCount(count: Int): String
    fun settingsBundleExportedTo(path: String): String

    // About
    val aboutTitle: String
    val aboutSubtitle: String
    fun aboutVersion(version: String): String
    val aboutArchitecture: String
    val aboutRuntime: String
    val aboutDatabase: String
    val aboutProtocol: String
    val aboutLicensesTitle: String
    val aboutLicensesDesc1: String
    val aboutLicensesDesc2: String

    // Dialogs
    val dialogOk: String
    val dialogDone: String
    val dialogClose: String
    val dialogCancel: String
    val importDialogTitle: String
    val importDialogProgress: String
    val importDialogCompleteTitle: String
    val importDialogFailedTitle: String
    val importDialogRejectedTitle: String
    val backupDialogTitle: String
    fun backupExportSuccess(path: String): String
    fun backupExportFailed(err: String): String

    companion object {
        fun resolve(language: AppLanguage, defaultLocale: Locale = Locale.getDefault()): DesktopStrings {
            return when (language) {
                AppLanguage.English -> EnglishStrings
                AppLanguage.SimplifiedChinese -> SimplifiedChineseStrings
                AppLanguage.TraditionalChinese -> TraditionalChineseStrings
                AppLanguage.System -> {
                    val lang = defaultLocale.language.lowercase(Locale.ROOT)
                    if (lang == "zh") {
                        val country = defaultLocale.country.uppercase(Locale.ROOT)
                        val script = defaultLocale.script.lowercase(Locale.ROOT)
                        if (country in setOf("TW", "HK", "MO") || script == "hant") {
                            TraditionalChineseStrings
                        } else {
                            SimplifiedChineseStrings
                        }
                    } else {
                        EnglishStrings
                    }
                }
            }
        }
    }
}

object EnglishStrings : DesktopStrings {
    override val appName = "Mihon W"

    override fun destinationLabel(destination: DesktopDestination): String = destination.label
    override fun destinationShortLabel(destination: DesktopDestination): String = destination.shortLabel

    override val libraryTitle = "Library"
    override val libraryImportBackup = "Import Android backup"
    override val libraryImportLocal = "Import local manga"
    override val librarySearchPlaceholder = "Search title or author"
    override val libraryManageCategories = "Categories"
    override val libraryEmptyTitle = "Your library is empty"
    override val libraryEmptySubtitle = "Import an existing Tachiyomi/Mihon backup (.tachibk) or a local comic folder."
    override fun libraryNoMatchTitle(query: String) = "No manga match “$query”"
    override val libraryNoMatchSubtitle = "Try a different search term or clear the filter."
    override val libraryRetry = "Retry"
    override fun libraryChaptersCount(count: Int) = "$count chapters"
    override fun libraryUnreadCount(count: Int) = "$count unread"
    override val libraryAllCategory = "All"

    override fun mangaDetailSource(name: String) = "Source $name"
    override val mangaDetailStatusOngoing = "Ongoing"
    override val mangaDetailStatusCompleted = "Completed"
    override val mangaDetailStatusUnknown = "Unknown"
    override val mangaDetailInLibrary = "In Library"
    override val mangaDetailAddToLibrary = "Add to Library"
    override val mangaDetailCategories = "Categories"
    override val mangaDetailTracking = "Tracking"
    override fun mangaDetailResume(chapter: String) = "Resume $chapter"
    override fun mangaDetailStart(chapter: String) = "Start $chapter"
    override val mangaDetailNoChapters = "No chapters found"
    override val mangaDetailBack = "Back"

    override val updatesTitle = "Updates"
    override val updatesCheckButton = "Check for updates"
    override val updatesChecking = "Checking..."
    override val updatesEmptyTitle = "No recent chapter updates"
    override val updatesEmptySubtitle = "Your library is up to date."
    override val updatesReadButton = "Read"

    override val historyTitle = "History"
    override val historyHeaderSubtitle = "Resume recently read chapters and track reading activity"
    override val historySearchPlaceholder = "Search history by manga or chapter"
    override val historyClearAll = "Clear history"
    override val historyEmptyTitle = "No reading history recorded yet"
    override val historyEmptySubtitle = "Manga you read will appear here."
    override val historyToday = "Today"
    override val historyYesterday = "Yesterday"
    override val historyResumeButton = "Resume"
    override val historyDeleteButton = "Delete"
    override val historyClearDialogTitle = "Clear reading history?"
    override val historyClearDialogMessage =
        "This will permanently remove all reading history entries. Read chapters in your library will remain marked as read."
    override val historyNoMatch = "No matching history found"
    override fun historyGroupTitle(title: String) = title

    override val downloadsTitle = "Downloads"
    override val downloadsPauseAll = "Pause All"
    override val downloadsResumeAll = "Resume All"
    override val downloadsClearCompleted = "Clear Completed"
    override val downloadsEmptyTitle = "No downloads in queue"
    override val downloadsEmptySubtitle = "Chapters you download will appear here."
    override val downloadsStatusDownloading = "Downloading"
    override val downloadsStatusPaused = "Paused"
    override val downloadsStatusCompleted = "Completed"
    override val downloadsStatusError = "Error"
    override val downloadsCancel = "Cancel"
    override val downloadsRetry = "Retry"
    override fun downloadsActiveSpeed(activeCount: Int, speedText: String) = "$activeCount active items • $speedText"

    override val browseTitle = "Browse"
    override val browseTabSources = "Sources"
    override val browseTabExtensions = "Extensions"
    override val browseManageRepositories = "Manage Repositories"
    override val browseRefresh = "Refresh"
    override val browseInstall = "Install"
    override val browseUninstall = "Uninstall"
    override val browseInstalledBadge = "Installed"
    override val browseSearchPlaceholder = "Search sources or extensions"
    override val browseNetworkDomainsHeader = "Declared Network Domains:"

    override val readerModeSingleLtr = "Left to Right"
    override val readerModeSingleRtl = "Right to Left"
    override val readerModeWebtoon = "Webtoon/Vertical"
    override val readerScaleFitScreen = "Fit Screen"
    override val readerScaleFitWidth = "Fit Width"
    override val readerScaleFitHeight = "Fit Height"
    override val readerScaleOriginal = "Original Size"
    override val readerEscapeHint = "Press Escape to exit fullscreen"

    override val settingsTitle = "Settings"
    override val settingsSectionGeneral = "General"
    override val settingsSectionAppearance = "Appearance"
    override val settingsSectionReader = "Reader"
    override val settingsSectionDownloads = "Downloads"
    override val settingsSectionTracking = "Tracking"
    override val settingsSectionBackup = "Backup & Restore"
    override val settingsSectionAdvanced = "Advanced & Diagnostics"

    override val settingsLanguageTitle = "Language"
    override val settingsLanguageSystem = "Follow System"
    override val settingsLanguageSimplifiedChinese = "简体中文"
    override val settingsLanguageTraditionalChinese = "繁體中文"
    override val settingsLanguageEnglish = "English"
    override val settingsAppInfoTitle = "Application Info"
    override fun settingsVersionLabel(version: String) = "Version: $version"
    override fun settingsPlatformLabel(platform: String) = "Platform: $platform"

    override val settingsThemeModeTitle = "Theme Mode"
    override val settingsThemeSystem = "System"
    override val settingsThemeLight = "Light"
    override val settingsThemeDark = "Dark"

    override val settingsDefaultReadingMode = "Default Reading Mode"
    override val settingsDefaultScaleMode = "Default Scale Mode"
    override val settingsMouseWheelBehavior = "Mouse Wheel Behavior"
    override val settingsWheelScrollPage = "Scroll Page"
    override val settingsWheelFlipPage = "Flip Page"
    override val settingsInvertWheel = "Invert Wheel Direction"
    override val settingsPageTransitions = "Page Transitions"
    override val settingsAnimateTransitions = "Animate page transitions"
    override val settingsDoubleSpread = "Double Page Spread (Horizontal)"

    override val settingsDownloadLocation = "Download Location"
    override val settingsDefaultStorageFolder = "Default Windows storage folder"
    override val settingsParallelDownloads = "Parallel Chapter Downloads"

    override val settingsTrackingTitle = "Enhanced Desktop Tracking"
    override val settingsTrackingDescription =
        "Multi-service sync (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi) with automatic offline queue and conflict resolution."
    override fun settingsConnectedTrackers(count: Int) = "Connected Trackers: $count available"

    override val settingsBackupTitle = "Cross-Platform Backup Exchange"
    override val settingsBackupDescription =
        "Mihon W produces full Android-compatible ProtoBuf .tachibk backups (gzipped) containing your library, categories, reading history, tracking records, and preferences."
    override val settingsExportBackupButton = "Export Backup (.tachibk)"
    override val settingsImportBackupButton = "Import Backup (.tachibk)"

    override val settingsDiagnosticsTitle = "Database & System Diagnostics"
    override val settingsRunIntegrityCheck = "Run Integrity Check"
    override val settingsExportDiagnosticBundle = "Export Diagnostic Bundle (.zip)"
    override fun settingsDatabaseIntegrity(status: String) = "Database Integrity: $status"
    override fun settingsOsInfo(info: String) = "OS: $info"
    override fun settingsJavaInfo(info: String) = "Java Runtime: $info"
    override fun settingsLogFilesCount(count: Int) = "Log Files Count: $count"
    override fun settingsBundleExportedTo(path: String) = "Diagnostic bundle exported to: $path"

    override val aboutTitle = "About Mihon W"
    override val aboutSubtitle = "Mihon Desktop Port for Windows"
    override fun aboutVersion(version: String) = "Version: $version"
    override val aboutArchitecture = "Target Architecture: Windows 10/11 x64"
    override val aboutRuntime = "Runtime: OpenJDK 21 / Compose Multiplatform Desktop"
    override val aboutDatabase = "Database: SQLite with SQLDelight (Driver: SingleConnectionSqliteDriver)"
    override val aboutProtocol = "Protocol: Android-compatible Protocol Buffers (.tachibk)"
    override val aboutLicensesTitle = "Open Source Licenses"
    override val aboutLicensesDesc1 = "Based on Mihon and Tachiyomi open source project."
    override val aboutLicensesDesc2 = "Licensed under the Apache License, Version 2.0."

    override val dialogOk = "OK"
    override val dialogDone = "Done"
    override val dialogClose = "Close"
    override val dialogCancel = "Cancel"
    override val importDialogTitle = "Importing library"
    override val importDialogProgress = "Checking and importing the selected content…"
    override val importDialogCompleteTitle = "Import complete"
    override val importDialogFailedTitle = "Import failed"
    override val importDialogRejectedTitle = "Import rejected"
    override val backupDialogTitle = "Backup Export"
    override fun backupExportSuccess(path: String) = "Backup successfully exported to:\n$path"
    override fun backupExportFailed(err: String) = "Backup export failed:\n$err"
}

object SimplifiedChineseStrings : DesktopStrings {
    override val appName = "Mihon W"

    override fun destinationLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "书架"
        DesktopDestination.Updates -> "更新"
        DesktopDestination.History -> "历史"
        DesktopDestination.Browse -> "浏览"
        DesktopDestination.Downloads -> "下载"
        DesktopDestination.Settings -> "设置"
        DesktopDestination.About -> "关于"
    }

    override fun destinationShortLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "书"
        DesktopDestination.Updates -> "更"
        DesktopDestination.History -> "历"
        DesktopDestination.Browse -> "览"
        DesktopDestination.Downloads -> "载"
        DesktopDestination.Settings -> "设"
        DesktopDestination.About -> "关"
    }

    override val libraryTitle = "书架"
    override val libraryImportBackup = "导入 Android 备份"
    override val libraryImportLocal = "导入本地漫画"
    override val librarySearchPlaceholder = "搜索标题或作者"
    override val libraryManageCategories = "分类管理"
    override val libraryEmptyTitle = "书架为空"
    override val libraryEmptySubtitle = "导入现有的 Tachiyomi/Mihon 备份 (.tachibk) 或本地漫画文件夹。"
    override fun libraryNoMatchTitle(query: String) = "没有找到与 “$query” 匹配的漫画"
    override val libraryNoMatchSubtitle = "尝试其他搜索词或清除筛选条件。"
    override val libraryRetry = "重试"
    override fun libraryChaptersCount(count: Int) = "$count 话"
    override fun libraryUnreadCount(count: Int) = "$count 未读"
    override val libraryAllCategory = "全部"

    override fun mangaDetailSource(name: String) = "图源 $name"
    override val mangaDetailStatusOngoing = "连载中"
    override val mangaDetailStatusCompleted = "已完结"
    override val mangaDetailStatusUnknown = "未知"
    override val mangaDetailInLibrary = "已在书架"
    override val mangaDetailAddToLibrary = "添加到书架"
    override val mangaDetailCategories = "分类"
    override val mangaDetailTracking = "进度记录"
    override fun mangaDetailResume(chapter: String) = "继续阅读 $chapter"
    override fun mangaDetailStart(chapter: String) = "开始阅读 $chapter"
    override val mangaDetailNoChapters = "未找到任何章节"
    override val mangaDetailBack = "返回"

    override val updatesTitle = "更新"
    override val updatesCheckButton = "检查更新"
    override val updatesChecking = "检查中..."
    override val updatesEmptyTitle = "暂无最近更新"
    override val updatesEmptySubtitle = "书架中的所有漫画均已是最新。"
    override val updatesReadButton = "阅读"

    override val historyTitle = "历史"
    override val historyHeaderSubtitle = "继续阅读最近章节并追踪阅读记录"
    override val historySearchPlaceholder = "搜索历史记录"
    override val historyClearAll = "清空历史"
    override val historyEmptyTitle = "暂无阅读历史"
    override val historyEmptySubtitle = "您阅读的漫画将显示在此处。"
    override val historyToday = "今天"
    override val historyYesterday = "昨天"
    override val historyResumeButton = "继续阅读"
    override val historyDeleteButton = "删除"
    override val historyClearDialogTitle = "清空阅读历史？"
    override val historyClearDialogMessage = "这将永久移除所有阅读历史记录。书架中已读章节仍将保留已读状态。"
    override val historyNoMatch = "未找到匹配的历史记录"
    override fun historyGroupTitle(title: String) = when (title) {
        "Today" -> historyToday
        "Yesterday" -> historyYesterday
        else -> title
    }

    override val downloadsTitle = "下载"
    override val downloadsPauseAll = "全部暂停"
    override val downloadsResumeAll = "全部继续"
    override val downloadsClearCompleted = "清除已完成"
    override val downloadsEmptyTitle = "下载队列为空"
    override val downloadsEmptySubtitle = "下载的章节将显示在此处。"
    override val downloadsStatusDownloading = "下载中"
    override val downloadsStatusPaused = "已暂停"
    override val downloadsStatusCompleted = "已完成"
    override val downloadsStatusError = "出错了"
    override val downloadsCancel = "取消"
    override val downloadsRetry = "重试"
    override fun downloadsActiveSpeed(activeCount: Int, speedText: String) = "$activeCount 个进行中 • $speedText"

    override val browseTitle = "浏览"
    override val browseTabSources = "图源"
    override val browseTabExtensions = "插件"
    override val browseManageRepositories = "图源仓库管理"
    override val browseRefresh = "刷新"
    override val browseInstall = "安装"
    override val browseUninstall = "卸载"
    override val browseInstalledBadge = "已安装"
    override val browseSearchPlaceholder = "搜索图源或插件"
    override val browseNetworkDomainsHeader = "声明的网络域名:"

    override val readerModeSingleLtr = "单页式（从左到右）"
    override val readerModeSingleRtl = "单页式（从右到左）"
    override val readerModeWebtoon = "条漫"
    override val readerScaleFitScreen = "适应屏幕"
    override val readerScaleFitWidth = "适应宽度"
    override val readerScaleFitHeight = "适应高度"
    override val readerScaleOriginal = "原始大小"
    override val readerEscapeHint = "按 Esc 退出全屏"

    override val settingsTitle = "设置"
    override val settingsSectionGeneral = "常规"
    override val settingsSectionAppearance = "外观"
    override val settingsSectionReader = "阅读"
    override val settingsSectionDownloads = "下载"
    override val settingsSectionTracking = "进度记录"
    override val settingsSectionBackup = "备份与还原"
    override val settingsSectionAdvanced = "高级与诊断"

    override val settingsLanguageTitle = "界面语言"
    override val settingsLanguageSystem = "跟随系统"
    override val settingsLanguageSimplifiedChinese = "简体中文"
    override val settingsLanguageTraditionalChinese = "繁體中文"
    override val settingsLanguageEnglish = "English"
    override val settingsAppInfoTitle = "应用程序信息"
    override fun settingsVersionLabel(version: String) = "版本: $version"
    override fun settingsPlatformLabel(platform: String) = "平台: $platform"

    override val settingsThemeModeTitle = "主题模式"
    override val settingsThemeSystem = "跟随系统"
    override val settingsThemeLight = "浅色"
    override val settingsThemeDark = "深色"

    override val settingsDefaultReadingMode = "默认阅读模式"
    override val settingsDefaultScaleMode = "默认缩放模式"
    override val settingsMouseWheelBehavior = "鼠标滚轮行为"
    override val settingsWheelScrollPage = "滚动页面"
    override val settingsWheelFlipPage = "翻页"
    override val settingsInvertWheel = "反转滚轮方向"
    override val settingsPageTransitions = "翻页动画"
    override val settingsAnimateTransitions = "开启动画过渡"
    override val settingsDoubleSpread = "双页拼合 (横向)"

    override val settingsDownloadLocation = "下载存储位置"
    override val settingsDefaultStorageFolder = "Windows 默认存储路径"
    override val settingsParallelDownloads = "同时下载章节数"

    override val settingsTrackingTitle = "增强型桌面进度记录"
    override val settingsTrackingDescription =
        "支持多平台同步 (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi)，提供离线队列与冲突解决。"
    override fun settingsConnectedTrackers(count: Int) = "支持的记录平台: $count 个可用"

    override val settingsBackupTitle = "跨平台备份交换"
    override val settingsBackupDescription =
        "Mihon W 可生成与 Android 端完全兼容的 ProtoBuf .tachibk 备份 (gzip压缩)，包含书架、分类、阅读历史、追踪记录及设置。"
    override val settingsExportBackupButton = "导出备份 (.tachibk)"
    override val settingsImportBackupButton = "导入备份 (.tachibk)"

    override val settingsDiagnosticsTitle = "数据库与系统诊断"
    override val settingsRunIntegrityCheck = "运行完整性检查"
    override val settingsExportDiagnosticBundle = "导出诊断包 (.zip)"
    override fun settingsDatabaseIntegrity(status: String) = "数据库完整性: $status"
    override fun settingsOsInfo(info: String) = "操作系统: $info"
    override fun settingsJavaInfo(info: String) = "Java 运行时: $info"
    override fun settingsLogFilesCount(count: Int) = "日志文件数: $count"
    override fun settingsBundleExportedTo(path: String) = "诊断包已导出至: $path"

    override val aboutTitle = "关于 Mihon W"
    override val aboutSubtitle = "Mihon Windows 桌面移植版"
    override fun aboutVersion(version: String) = "版本: $version"
    override val aboutArchitecture = "目标架构: Windows 10/11 x64"
    override val aboutRuntime = "运行时: OpenJDK 21 / Compose Multiplatform Desktop"
    override val aboutDatabase = "数据库: SQLite + SQLDelight (驱动: SingleConnectionSqliteDriver)"
    override val aboutProtocol = "协议: Android 兼容 Protocol Buffers (.tachibk)"
    override val aboutLicensesTitle = "开源许可证"
    override val aboutLicensesDesc1 = "基于 Mihon 和 Tachiyomi 开源项目构建。"
    override val aboutLicensesDesc2 = "基于 Apache License 2.0 许可证开源。"

    override val dialogOk = "确定"
    override val dialogDone = "完成"
    override val dialogClose = "关闭"
    override val dialogCancel = "取消"
    override val importDialogTitle = "正在导入书架"
    override val importDialogProgress = "正在检查并导入所选内容…"
    override val importDialogCompleteTitle = "导入完成"
    override val importDialogFailedTitle = "导入失败"
    override val importDialogRejectedTitle = "导入被拒绝"
    override val backupDialogTitle = "备份导出"
    override fun backupExportSuccess(path: String) = "备份已成功导出至:\n$path"
    override fun backupExportFailed(err: String) = "备份导出失败:\n$err"
}

object TraditionalChineseStrings : DesktopStrings {
    override val appName = "Mihon W"

    override fun destinationLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "書架"
        DesktopDestination.Updates -> "更新"
        DesktopDestination.History -> "歷史"
        DesktopDestination.Browse -> "瀏覽"
        DesktopDestination.Downloads -> "下載"
        DesktopDestination.Settings -> "設定"
        DesktopDestination.About -> "關於"
    }

    override fun destinationShortLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "書"
        DesktopDestination.Updates -> "更"
        DesktopDestination.History -> "歷"
        DesktopDestination.Browse -> "覽"
        DesktopDestination.Downloads -> "載"
        DesktopDestination.Settings -> "設"
        DesktopDestination.About -> "關"
    }

    override val libraryTitle = "書架"
    override val libraryImportBackup = "匯入 Android 備份"
    override val libraryImportLocal = "匯入本機漫畫"
    override val librarySearchPlaceholder = "搜尋標題或作者"
    override val libraryManageCategories = "分類管理"
    override val libraryEmptyTitle = "書架是空的"
    override val libraryEmptySubtitle = "匯入現有的 Tachiyomi/Mihon 備份 (.tachibk) 或本機漫畫資料夾。"
    override fun libraryNoMatchTitle(query: String) = "沒有找到符合 “$query” 的漫畫"
    override val libraryNoMatchSubtitle = "嘗試其他搜尋字詞或清除篩選條件。"
    override val libraryRetry = "重試"
    override fun libraryChaptersCount(count: Int) = "$count 話"
    override fun libraryUnreadCount(count: Int) = "$count 未讀"
    override val libraryAllCategory = "全部"

    override fun mangaDetailSource(name: String) = "圖源 $name"
    override val mangaDetailStatusOngoing = "連載中"
    override val mangaDetailStatusCompleted = "已完結"
    override val mangaDetailStatusUnknown = "未知"
    override val mangaDetailInLibrary = "已在書架"
    override val mangaDetailAddToLibrary = "加入書架"
    override val mangaDetailCategories = "分類"
    override val mangaDetailTracking = "進度記錄"
    override fun mangaDetailResume(chapter: String) = "繼續閱讀 $chapter"
    override fun mangaDetailStart(chapter: String) = "開始閱讀 $chapter"
    override val mangaDetailNoChapters = "找不到任何章節"
    override val mangaDetailBack = "返回"

    override val updatesTitle = "更新"
    override val updatesCheckButton = "檢查更新"
    override val updatesChecking = "檢查中..."
    override val updatesEmptyTitle = "暫無最近更新"
    override val updatesEmptySubtitle = "書架中的所有漫畫皆為最新。"
    override val updatesReadButton = "閱讀"

    override val historyTitle = "歷史"
    override val historyHeaderSubtitle = "繼續閱讀最近章節並追蹤閱讀記錄"
    override val historySearchPlaceholder = "搜尋歷史紀錄"
    override val historyClearAll = "清除歷史"
    override val historyEmptyTitle = "暫無閱讀紀錄"
    override val historyEmptySubtitle = "您閱讀的漫畫將顯示於此。"
    override val historyToday = "今天"
    override val historyYesterday = "昨天"
    override val historyResumeButton = "繼續閱讀"
    override val historyDeleteButton = "刪除"
    override val historyClearDialogTitle = "清空閱讀紀錄？"
    override val historyClearDialogMessage = "這將永久移除所有閱讀紀錄。書架中已讀章節仍將保留已讀狀態。"
    override val historyNoMatch = "找不到符合的歷史紀錄"
    override fun historyGroupTitle(title: String) = when (title) {
        "Today" -> historyToday
        "Yesterday" -> historyYesterday
        else -> title
    }

    override val downloadsTitle = "下載"
    override val downloadsPauseAll = "全部暫停"
    override val downloadsResumeAll = "全部繼續"
    override val downloadsClearCompleted = "清除已完成"
    override val downloadsEmptyTitle = "下載佇列是空的"
    override val downloadsEmptySubtitle = "下載的章節將顯示於此。"
    override val downloadsStatusDownloading = "下載中"
    override val downloadsStatusPaused = "已暫停"
    override val downloadsStatusCompleted = "已完成"
    override val downloadsStatusError = "發生錯誤"
    override val downloadsCancel = "取消"
    override val downloadsRetry = "重試"
    override fun downloadsActiveSpeed(activeCount: Int, speedText: String) = "$activeCount 個進行中 • $speedText"

    override val browseTitle = "瀏覽"
    override val browseTabSources = "圖源"
    override val browseTabExtensions = "擴充套件"
    override val browseManageRepositories = "套件庫管理"
    override val browseRefresh = "重新整理"
    override val browseInstall = "安裝"
    override val browseUninstall = "解除安裝"
    override val browseInstalledBadge = "已安裝"
    override val browseSearchPlaceholder = "搜尋圖源或擴充套件"
    override val browseNetworkDomainsHeader = "宣告的網路網域:"

    override val readerModeSingleLtr = "單頁式（從左到右）"
    override val readerModeSingleRtl = "單頁式（從右到左）"
    override val readerModeWebtoon = "條漫"
    override val readerScaleFitScreen = "符合螢幕"
    override val readerScaleFitWidth = "符合寬度"
    override val readerScaleFitHeight = "符合高度"
    override val readerScaleOriginal = "原始大小"
    override val readerEscapeHint = "按 Esc 結束全螢幕"

    override val settingsTitle = "設定"
    override val settingsSectionGeneral = "一般"
    override val settingsSectionAppearance = "外觀"
    override val settingsSectionReader = "閱讀"
    override val settingsSectionDownloads = "下載"
    override val settingsSectionTracking = "進度記錄"
    override val settingsSectionBackup = "備份與還原"
    override val settingsSectionAdvanced = "進階與診斷"

    override val settingsLanguageTitle = "介面語言"
    override val settingsLanguageSystem = "跟隨系統"
    override val settingsLanguageSimplifiedChinese = "簡體中文"
    override val settingsLanguageTraditionalChinese = "繁體中文"
    override val settingsLanguageEnglish = "English"
    override val settingsAppInfoTitle = "應用程式資訊"
    override fun settingsVersionLabel(version: String) = "版本: $version"
    override fun settingsPlatformLabel(platform: String) = "平台: $platform"

    override val settingsThemeModeTitle = "主題模式"
    override val settingsThemeSystem = "跟隨系統"
    override val settingsThemeLight = "淺色"
    override val settingsThemeDark = "深色"

    override val settingsDefaultReadingMode = "預設閱讀模式"
    override val settingsDefaultScaleMode = "預設縮放模式"
    override val settingsMouseWheelBehavior = "滑鼠滾輪行為"
    override val settingsWheelScrollPage = "捲動頁面"
    override val settingsWheelFlipPage = "翻頁"
    override val settingsInvertWheel = "反轉滾輪方向"
    override val settingsPageTransitions = "翻頁動畫"
    override val settingsAnimateTransitions = "開啟轉場動畫"
    override val settingsDoubleSpread = "雙頁拼合 (橫向)"

    override val settingsDownloadLocation = "下載儲存位置"
    override val settingsDefaultStorageFolder = "Windows 預設儲存路徑"
    override val settingsParallelDownloads = "同時下載章節數"

    override val settingsTrackingTitle = "增強型桌面進度記錄"
    override val settingsTrackingDescription =
        "支援多平台同步 (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi)，提供離線佇列與衝突解決。"
    override fun settingsConnectedTrackers(count: Int) = "支援的紀錄平臺: $count 個可用"

    override val settingsBackupTitle = "跨平臺備份交換"
    override val settingsBackupDescription =
        "Mihon W 可生成與 Android 端完全相容的 ProtoBuf .tachibk 備份 (gzip壓縮)，包含書架、分類、閱讀歷史、追蹤記錄及設定。"
    override val settingsExportBackupButton = "匯出備份 (.tachibk)"
    override val settingsImportBackupButton = "匯入備份 (.tachibk)"

    override val settingsDiagnosticsTitle = "資料庫與系統診斷"
    override val settingsRunIntegrityCheck = "執行完整性檢查"
    override val settingsExportDiagnosticBundle = "匯出診斷包 (.zip)"
    override fun settingsDatabaseIntegrity(status: String) = "資料庫完整性: $status"
    override fun settingsOsInfo(info: String) = "作業系統: $info"
    override fun settingsJavaInfo(info: String) = "Java 執行環境: $info"
    override fun settingsLogFilesCount(count: Int) = "記錄檔數量: $count"
    override fun settingsBundleExportedTo(path: String) = "診斷包已匯出至: $path"

    override val aboutTitle = "關於 Mihon W"
    override val aboutSubtitle = "Mihon Windows 桌面移植版"
    override fun aboutVersion(version: String) = "版本: $version"
    override val aboutArchitecture = "目標架構: Windows 10/11 x64"
    override val aboutRuntime = "執行環境: OpenJDK 21 / Compose Multiplatform Desktop"
    override val aboutDatabase = "資料庫: SQLite + SQLDelight (驅動: SingleConnectionSqliteDriver)"
    override val aboutProtocol = "協定: Android 相容 Protocol Buffers (.tachibk)"
    override val aboutLicensesTitle = "開源許可證"
    override val aboutLicensesDesc1 = "基於 Mihon 和 Tachiyomi 開源專案構建。"
    override val aboutLicensesDesc2 = "基於 Apache License 2.0 許可證開源。"

    override val dialogOk = "確定"
    override val dialogDone = "完成"
    override val dialogClose = "關閉"
    override val dialogCancel = "取消"
    override val importDialogTitle = "正在匯入書架"
    override val importDialogProgress = "正在檢查並匯入所選內容…"
    override val importDialogCompleteTitle = "匯入完成"
    override val importDialogFailedTitle = "匯入失敗"
    override val importDialogRejectedTitle = "匯入被拒絕"
    override val backupDialogTitle = "備份匯出"
    override fun backupExportSuccess(path: String) = "備份已成功匯出至:\n$path"
    override fun backupExportFailed(err: String) = "備份匯出失敗:\n$err"
}

val LocalStrings = staticCompositionLocalOf<DesktopStrings> { EnglishStrings }

@Composable
fun ProvideDesktopStrings(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val strings = DesktopStrings.resolve(language)
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}
