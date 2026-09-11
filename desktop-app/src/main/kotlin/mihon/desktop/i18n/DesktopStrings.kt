package mihon.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.track.TrackStatus
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderSessionError
import mihon.reader.source.ReaderFailure
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
    fun libraryUnreadCount(count: Int): String
    val libraryAllCategory: String

    // Library Display, Filter & Sort
    val libraryDisplayMode: String
    val libraryDisplayComfortable: String
    val libraryDisplayCompact: String
    val libraryDisplayCoverOnly: String
    val libraryDisplayList: String
    val libraryGridSize: String
    val libraryFilterAndSort: String
    val libraryFilterTab: String
    val librarySortTab: String
    val libraryFilterReset: String
    val libraryFilterUnread: String
    val libraryFilterDownloaded: String
    val libraryFilterStarted: String
    val libraryFilterCompleted: String
    val libraryFilterBookmarked: String
    val librarySortDefault: String
    val librarySortAlphabetical: String
    val librarySortLastRead: String
    val librarySortLastUpdate: String
    val librarySortUnreadCount: String
    val librarySortTotalChapters: String
    val librarySortDateAdded: String
    val librarySortAscending: String
    val librarySortDescending: String

    // Library Batch Actions
    val libraryBatchSelect: String
    fun libraryBatchSelected(count: Int): String
    val libraryBatchSelectAll: String
    val libraryBatchDeselectAll: String
    val libraryBatchChangeCategory: String
    val libraryBatchMarkRead: String
    val libraryBatchMarkUnread: String
    val libraryBatchDownload: String
    val libraryBatchDownloadNext1: String
    val libraryBatchDownloadNext5: String
    val libraryBatchDownloadAllUnread: String
    val libraryBatchRemove: String
    val libraryBatchRemoveConfirmTitle: String
    fun libraryBatchRemoveConfirmMessage(count: Int): String
    val libraryBatchDone: String

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
    val mangaDetailChangeCover: String
    val mangaDetailResetCover: String
    val mangaDetailEditInfo: String
    val mangaDetailAuthor: String
    val mangaDetailArtist: String
    val mangaDetailStatus: String
    val mangaDetailGenres: String
    val mangaDetailNotes: String
    val mangaDetailResetToSource: String
    val mangaDetailSave: String

    // Chapters & Chapter Actions
    val chapters: String
    val sortSourceOrder: String
    val sortChapterNumber: String
    val sortUploadDate: String
    val filterUnread: String
    val filterRead: String
    val filterUnreadOnly: String
    val filterReadOnly: String
    val filterDownloaded: String
    val filterDownloadedOnly: String
    val filterNotDownloadedOnly: String
    val filterBookmarked: String
    val filterBookmarkedOnly: String
    val filterNotBookmarkedOnly: String
    val markAsRead: String
    val markAsUnread: String
    val markPreviousAsRead: String
    val downloadChapter: String
    val downloadNext1: String
    val downloadNext5: String
    val downloadNext10: String
    val downloadNext25: String
    val downloadUnread: String
    val downloadAll: String
    val readerPreviousChapter: String
    val readerNextChapter: String
    val readerChapterList: String
    val readerCurrentChapter: String
    val readerChapterDrawerSearch: String
    val readerChapterDrawerNoResults: String
    val mangaDetailSearchChaptersPlaceholder: String
    val deleteDownload: String
    val bookmarkChapter: String
    val removeBookmark: String
    val chapterBatchSelect: String
    fun chapterBatchSelected(count: Int): String
    val chapterBatchSelectAll: String
    val chapterBatchInvert: String
    val chapterBatchBookmark: String
    val chapterBatchRemoveBookmark: String
    val chapterBatchMarkAsRead: String
    val chapterBatchMarkAsUnread: String
    val chapterBatchDownload: String
    val chapterBatchDeleteDownload: String
    val mangaDetailCoverView: String
    val mangaDetailCoverSave: String
    val mangaDetailCoverSaved: String
    val mangaNotesTitle: String
    val mangaNotesEdit: String
    val mangaNotesSave: String
    val mangaNotesPlaceholder: String

    // Storage & Cache Cleaner
    val storageCleanerTitle: String
    val storageCleanerDescription: String
    val storageCleanerDownloadSize: String
    val storageCleanerClearRead: String
    val storageCleanerClearReadSuccess: String
    val storageCleanerClearImageCache: String
    val storageCleanerClearImageCacheSuccess: String

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
    val browseTabMigration: String
    val browseManageRepositories: String
    val browseRefresh: String
    val browseInstall: String
    val browseUninstall: String
    val browseInstalledBadge: String
    val browseSearchPlaceholder: String
    val browseNetworkDomainsHeader: String
    val browseGlobalSearch: String
    val browseInstallFromFile: String
    val browseUpdateAll: String

    // Extension Details & Source Preferences
    val extensionInfo: String
    val extensionOpenRepo: String
    val extensionEnableAll: String
    val extensionDisableAll: String
    val extensionClearCookies: String
    val extensionVersion: String
    val extensionLanguage: String
    val extensionAgeRating: String
    val extensionNsfwShort: String
    val extensionNsfwWarning: String
    val extensionUninstall: String
    val extensionUpdate: String
    val extensionTrust: String
    val extensionRevoke: String
    val extensionUntrusted: String
    val extensionObsolete: String
    val extensionObsoleteWarning: String
    val extensionIncognitoMode: String
    val extensionIncognitoSummary: String
    val extensionDebugInfoCopied: String
    val extensionCookiesCleared: String
    val sourcePreferencesTitle: String
    val sourcePreferencesEmpty: String
    val sourcePreferencesUnsupported: String
    val sourcePreferencesSaveError: String
    val actionOk: String
    val actionCancel: String
    val actionSettings: String

    // Reader
    val readerModeSingleLtr: String
    val readerModeSingleRtl: String
    val readerModeWebtoon: String
    val readerScaleFitScreen: String
    val readerScaleFitWidth: String
    val readerScaleFitHeight: String
    val readerScaleOriginal: String
    val readerEscapeHint: String
    val readerColorFilter: String
    val readerFilterNone: String
    val readerFilterInvert: String
    val readerFilterGrayscale: String
    val readerFilterInvertGrayscale: String
    val readerFilterSepia: String
    val readerFilterNight: String
    val readerBackgroundColor: String
    val readerBgDarkGray: String
    val readerBgBlack: String
    val readerBgWhite: String
    val readerBgWarmCream: String
    val readerCropBorders: String
    val readerCropBordersWebtoon: String
    val readerWebtoonMaxWidth: String
    val readerWebtoonSidePadding: String

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
    val settingsAppThemeTitle: String
    val settingsThemeAmoledTitle: String
    val settingsThemeAmoledSubtitle: String
    fun appThemeName(theme: DesktopAppTheme): String

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

    // Incognito mode
    val incognitoTitle: String
    val incognitoBannerText: String
    val incognitoDisable: String
    val incognitoDescription: String

    // Stats
    val statsTitle: String
    val statsOverview: String
    val statsTotalManga: String
    val statsReadDuration: String
    val statsCompletedManga: String
    val statsChapters: String
    val statsTotalChapters: String
    val statsReadChapters: String
    val statsUnreadChapters: String
    val statsReadPercentage: String
    val statsTopGenres: String
    val statsNoGenres: String
    val statsStatuses: String
    val statsStatusOngoing: String
    val statsStatusCompleted: String
    val statsStatusHiatus: String
    val statsStatusCancelled: String
    val statsStatusUnknown: String
    val statsReadingProgress: String
    val statsProgressUnread: String
    val statsProgressInProgress: String
    val statsProgressFinished: String
    val statsCategories: String
    val statsNoCategories: String
    val statsTrackers: String
    val statsTrackedTitles: String
    val statsMeanScore: String
    val statsActiveTrackers: String
    val statsRefresh: String

    // Backup Automation
    val backupAutoTitle: String
    val backupAutoDescription: String
    val backupInterval: String
    val backupIntervalOff: String
    val backupInterval6Hours: String
    val backupInterval12Hours: String
    val backupIntervalDaily: String
    val backupInterval2Days: String
    val backupIntervalWeekly: String
    val backupLocation: String
    val backupLocationDefault: String
    val backupRetention: String
    val backupLastAutoBackup: String
    val backupNever: String
    val backupNow: String

    // Library Update & Automation
    val libraryUpdateTitle: String
    val libraryUpdateNow: String
    val libraryUpdating: String
    val libraryUpdateInterval: String
    val libraryUpdateIntervalManual: String
    val libraryUpdateInterval6Hours: String
    val libraryUpdateInterval12Hours: String
    val libraryUpdateIntervalDaily: String
    val libraryUpdateInterval2Days: String
    val libraryUpdateIntervalWeekly: String
    val libraryUpdateSkipCompleted: String
    val libraryUpdateSkipUnread: String
    val libraryAutoDownloadNew: String
    val notificationsDesktopEnabled: String
    val libraryLastUpdate: String

    // Web & Cookie Management
    val cookieManagerTitle: String
    val cookieManagerDescription: String
    val cookieManagerButton: String
    val openInBrowser: String

    // Categories
    val categoryManageTitle: String
    val categoryNewNameLabel: String
    val categoryAdd: String
    val categoryEmpty: String
    val categoryRename: String
    val categoryDelete: String
    val categoryRenameTitle: String
    val categoryNameLabel: String
    val categorySave: String
    val categorySetTitle: String
    val categoryNoneExist: String
    val categoryNoneCreated: String

    // Edit Manga Info
    val mangaDetailTitleLabel: String
    val mangaDetailStatusLicensed: String
    val mangaDetailStatusPublishingFinished: String
    val mangaDetailGenresLabel: String
    val mangaDetailDescriptionLabel: String
    val mangaDetailSaveSuccess: String
    fun mangaDetailStatusOption(status: Long): String

    // Cookie & Clearance Manager
    fun cookieConfiguredDomains(count: Int): String
    val cookieAddDomain: String
    val cookieNoCustomDomains: String
    fun cookieCountSubtitle(count: Int, hasCustomUa: Boolean): String
    val cookieDomainLabel: String
    val cookieRawCookiesLabel: String
    val cookieCustomUaLabel: String
    val cookieDelete: String
    val cookieSave: String
    fun cookieRemovedStatus(domain: String): String
    fun cookieSavedStatus(count: Int, domain: String): String

    // Tracker & Tracking
    fun trackerConnectTitle(name: String): String
    fun trackerAuthTitle(name: String): String
    fun trackerLoginTitle(name: String): String
    val trackerServerUrl: String
    val trackerUsername: String
    val trackerPasswordOrApiKey: String
    fun trackerAuthUrl(url: String): String
    val trackerToken: String
    val trackerAccountName: String
    val trackerPassword: String
    val trackerConnect: String
    val trackerLogin: String
    val trackerLoginFailed: String
    val trackerServerAuthHelp: String
    val trackingRequestFailed: String
    fun trackingTitle(mangaTitle: String): String
    val trackingNotTracking: String
    val trackingNotLoggedIn: String
    val trackingTrack: String
    val trackingEdit: String
    val trackingRemove: String
    fun trackingSearchTitle(name: String): String
    val trackingSearch: String
    fun trackingTotalChapters(count: Long): String
    fun trackingEditTitle(title: String): String
    val trackingChaptersRead: String
    val trackingScore: String
    fun trackStatusLabel(status: TrackStatus): String

    // Reader Chrome, Settings & Messages
    val readerFullscreen: String
    val readerBorderless: String
    val readerShortcutsTitle: String
    val readerShortcutsHelp: String
    fun readerCropToggle(active: Boolean): String
    fun readerCoverOffsetToggle(active: Boolean): String
    val readerModeDualLtr: String
    val readerModeDualRtl: String
    val readerModeVertical: String
    fun readerModeLabel(mode: ReadingMode): String
    fun readerScaleLabel(scale: ScaleMode): String
    fun readerFilterLabel(filter: ReaderColorFilter): String
    fun readerBackgroundLabel(bg: ReaderBackgroundColor): String
    val readerSettingsDialogTitle: String
    val readerClickRegions: String
    val readerRegionLeft: String
    val readerRegionCenter: String
    val readerRegionRight: String
    fun readerActionLabel(action: ReaderClickAction): String
    fun readerLeftBoundary(percent: Int): String
    fun readerCenterBoundary(percent: Int): String
    fun readerWheelLabel(behavior: ReaderWheelBehavior): String
    val readerReserveCover: String
    val readerCropBordersPaged: String
    val readerWebtoonLayout: String
    val readerWebtoonWidthFull: String
    fun readerWebtoonWidthDp(width: Int): String
    fun readerWebtoonSidePaddingPercent(padding: Int): String
    val readerLoadingChapter: String
    val readerClosed: String
    fun readerErrorMessage(error: ReaderSessionError): String

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    fun browseSourceLanguage(lang: String): String
    val browseSourcePopular: String
    val browseSourceLatest: String
    val browseSearchTitlesPlaceholder: String
    val browseSearchButton: String
    fun browseFiltersButton(count: Int): String
    val browseNoMangaFound: String
    val browseInLibraryBadge: String
    val browsePrevPage: String
    fun browsePageNumber(page: Int): String
    val browseNextPage: String
    fun browseInstallExtensionTitle(name: String): String
    fun browsePackageLabel(pkg: String): String
    fun browseVersionLabel(version: String): String
    fun browseLanguageLabel(lang: String): String
    val browseNetworkPermissionNotice: String
    val browseTrustAndInstall: String
    val browseAddRepository: String
    val browseConfiguredRepositories: String
    val browseRemoveRepository: String
    val browseSourceBrowse: String
    val browsePin: String
    val browseUnpin: String
    val browseUpdate: String
    val browseDisable: String
    val browseEnable: String
    fun onlineDetailSource(name: String, lang: String): String
    fun onlineDetailAuthor(author: String): String
    fun onlineDetailArtist(artist: String): String
    fun onlineDetailGenres(genres: String): String
    fun onlineDetailChapters(count: Int): String
    fun onlineDetailScanlator(scanlator: String): String
    val migrateSelectSourceHeader: String
    val migrateNoMangaInLibrary: String
    fun migrateMangaCount(count: Int): String
    val migrateViewManga: String
    val migrateBackToSources: String
    val migrateSelectMangaHeader: String
    val migrateAction: String
    fun migrateDialogTitle(title: String): String
    val migrateDialogSubtitle: String
    val migrateNoOtherSources: String
    val migrateTargetSourceLabel: String
    val migrateSearchPlaceholder: String
    val migrateSelectedBadge: String
    val migrateConfirm: String
    val filterDialogTitle: String
    val filterReset: String
    val filterNoAvailable: String
    val filterApply: String
    val filterAscending: String
    val filterDescending: String
    val globalSearchEnterQuery: String
    val globalSearchNoSources: String
    val globalSearchViewAll: String
    fun globalSearchError(error: String): String
    val globalSearchNoResults: String

    // Settings & Diagnostics & Reports
    val settingsDownloadCustomPath: String
    val settingsDownloadCustomPathPlaceholder: String
    val settingsDownloadAheadTitle: String
    val settingsDownloadAheadDesc: String
    val settingsDownloadAheadDisabled: String
    fun settingsDownloadAheadChapters(count: Int): String
    val settingsDeleteReadChaptersTitle: String
    val settingsDeleteReadChaptersDesc: String
    fun settingsTrackerLoggedInAs(user: String, server: String?): String
    val settingsTrackerLogout: String
    fun settingsLibraryUpdateResult(checked: Int, newChapters: Int): String
    val settingsLibraryUpdateCompleted: String
    fun settingsLibraryUpdateFailed(msg: String): String
    val readerUnavailable: String
    val readerOpeningChapter: String
    fun importReportTitle(id: Long): String
    fun importReportManga(inserted: Long, merged: Long): String
    fun importReportChapters(inserted: Long, merged: Long): String
    fun importReportCategories(count: Long): String
    fun importReportPreferences(imported: Long, skipped: Long): String
    fun importReportSkipCategories(categories: String): String
    fun importReportCategory(category: String): String
    val mangaDetailBackToLibrary: String

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
    override fun libraryUnreadCount(count: Int) = "$count unread"
    override val libraryAllCategory = "All"

    // Library Display, Filter & Sort
    override val libraryDisplayMode = "Display Mode"
    override val libraryDisplayComfortable = "Comfortable Grid"
    override val libraryDisplayCompact = "Compact Grid"
    override val libraryDisplayCoverOnly = "Cover Only"
    override val libraryDisplayList = "List"
    override val libraryGridSize = "Card Size"
    override val libraryFilterAndSort = "Filter & Sort"
    override val libraryFilterTab = "Filter"
    override val librarySortTab = "Sort"
    override val libraryFilterReset = "Reset Filters"
    override val libraryFilterUnread = "Unread"
    override val libraryFilterDownloaded = "Downloaded"
    override val libraryFilterStarted = "Started"
    override val libraryFilterCompleted = "Completed"
    override val libraryFilterBookmarked = "Bookmarked"
    override val librarySortDefault = "Default"
    override val librarySortAlphabetical = "Alphabetical"
    override val librarySortLastRead = "Last Read"
    override val librarySortLastUpdate = "Last Update"
    override val librarySortUnreadCount = "Unread Count"
    override val librarySortTotalChapters = "Total Chapters"
    override val librarySortDateAdded = "Date Added"
    override val librarySortAscending = "Ascending"
    override val librarySortDescending = "Descending"

    // Library Batch Actions
    override val libraryBatchSelect = "Select"
    override fun libraryBatchSelected(count: Int) = "$count selected"
    override val libraryBatchSelectAll = "Select All"
    override val libraryBatchDeselectAll = "Deselect All"
    override val libraryBatchChangeCategory = "Set Categories"
    override val libraryBatchMarkRead = "Mark Read"
    override val libraryBatchMarkUnread = "Mark Unread"
    override val libraryBatchDownload = "Download"
    override val libraryBatchDownloadNext1 = "Next chapter"
    override val libraryBatchDownloadNext5 = "Next 5 chapters"
    override val libraryBatchDownloadAllUnread = "All unread chapters"
    override val libraryBatchRemove = "Remove from Library"
    override val libraryBatchRemoveConfirmTitle = "Remove Selected Manga"
    override fun libraryBatchRemoveConfirmMessage(count: Int) =
        "Are you sure you want to remove $count manga from your library?"
    override val libraryBatchDone = "Done"

    override fun mangaDetailSource(name: String) = "Source $name"
    override val mangaDetailStatusOngoing = "Ongoing"
    override val mangaDetailStatusCompleted = "Completed"
    override val mangaDetailStatusUnknown = "Unknown"
    override val mangaDetailInLibrary = "Remove from Library"
    override val mangaDetailAddToLibrary = "Add to Library"
    override val mangaDetailCategories = "Categories"
    override val mangaDetailTracking = "Tracking"
    override fun mangaDetailResume(chapter: String) = "Resume $chapter"
    override fun mangaDetailStart(chapter: String) = "Start $chapter"
    override val mangaDetailNoChapters = "No chapters found"
    override val mangaDetailBack = "Back"
    override val mangaDetailChangeCover = "Change Cover"
    override val mangaDetailResetCover = "Reset Cover"
    override val mangaDetailEditInfo = "Edit Info"
    override val mangaDetailAuthor = "Author"
    override val mangaDetailArtist = "Artist"
    override val mangaDetailStatus = "Status"
    override val mangaDetailGenres = "Genres"
    override val mangaDetailNotes = "Personal Notes"
    override val mangaDetailResetToSource = "Reset to Source"
    override val mangaDetailSave = "Save"

    // Chapters & Chapter Actions
    override val chapters = "Chapters"
    override val sortSourceOrder = "Source Order"
    override val sortChapterNumber = "Chapter Number"
    override val sortUploadDate = "Upload Date"
    override val filterUnread = "Unread"
    override val filterRead = "Read"
    override val filterUnreadOnly = "Unread Only"
    override val filterReadOnly = "Read Only"
    override val filterDownloaded = "Downloaded"
    override val filterDownloadedOnly = "Downloaded Only"
    override val filterNotDownloadedOnly = "Not Downloaded"
    override val filterBookmarked = "Bookmarked"
    override val filterBookmarkedOnly = "Bookmarked Only"
    override val filterNotBookmarkedOnly = "Not Bookmarked"
    override val markAsRead = "Mark Read"
    override val markAsUnread = "Mark Unread"
    override val markPreviousAsRead = "Mark Previous as Read"
    override val downloadChapter = "Download"
    override val downloadNext1 = "Next 1 chapter"
    override val downloadNext5 = "Next 5 chapters"
    override val downloadNext10 = "Next 10 chapters"
    override val downloadNext25 = "Next 25 chapters"
    override val downloadUnread = "All unread"
    override val downloadAll = "All chapters"
    override val readerPreviousChapter = "Previous chapter"
    override val readerNextChapter = "Next chapter"
    override val readerChapterList = "Chapters"
    override val readerCurrentChapter = "Current"
    override val readerChapterDrawerSearch = "Filter chapters..."
    override val readerChapterDrawerNoResults = "No chapters found"
    override val mangaDetailSearchChaptersPlaceholder = "Filter chapters..."
    override val deleteDownload = "Delete Download"
    override val bookmarkChapter = "Bookmark"
    override val removeBookmark = "Remove Bookmark"
    override val chapterBatchSelect = "Select chapters"
    override fun chapterBatchSelected(count: Int): String = "$count selected"
    override val chapterBatchSelectAll = "Select all"
    override val chapterBatchInvert = "Invert selection"
    override val chapterBatchBookmark = "Bookmark"
    override val chapterBatchRemoveBookmark = "Remove bookmark"
    override val chapterBatchMarkAsRead = "Mark as read"
    override val chapterBatchMarkAsUnread = "Mark as unread"
    override val chapterBatchDownload = "Download"
    override val chapterBatchDeleteDownload = "Delete download"
    override val mangaDetailCoverView = "View cover"
    override val mangaDetailCoverSave = "Save cover"
    override val mangaDetailCoverSaved = "Cover saved successfully"
    override val mangaNotesTitle = "Notes"
    override val mangaNotesEdit = "Edit note"
    override val mangaNotesSave = "Save note"
    override val mangaNotesPlaceholder = "Add personal notes for this manga..."

    // Storage & Cache Cleaner
    override val storageCleanerTitle = "Data & Storage Management"
    override val storageCleanerDescription =
        "Manage downloaded chapters and image caches to free up local disk space."
    override val storageCleanerDownloadSize = "Downloaded Chapters Size"
    override val storageCleanerClearRead = "Delete Read Chapters"
    override val storageCleanerClearReadSuccess = "Deleted read chapters"
    override val storageCleanerClearImageCache = "Clear Image Disk Cache"
    override val storageCleanerClearImageCacheSuccess = "Image disk cache cleared"

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
    override val browseTabMigration = "Migration"
    override val browseManageRepositories = "Manage Repositories"
    override val browseRefresh = "Refresh"
    override val browseInstall = "Install"
    override val browseUninstall = "Uninstall"
    override val browseInstalledBadge = "Installed"
    override val browseSearchPlaceholder = "Search sources or extensions"
    override val browseNetworkDomainsHeader = "Declared Network Domains:"
    override val browseGlobalSearch = "Global Search"
    override val browseInstallFromFile = "Install .mext"
    override val browseUpdateAll = "Update All"

    override val extensionInfo = "Extension info"
    override val extensionOpenRepo = "Open repository"
    override val extensionEnableAll = "Enable all"
    override val extensionDisableAll = "Disable all"
    override val extensionClearCookies = "Clear cookies"
    override val extensionVersion = "Version"
    override val extensionLanguage = "Language"
    override val extensionAgeRating = "Age rating"
    override val extensionNsfwShort = "18+"
    override val extensionNsfwWarning = "This extension contains adult (18+) content.\nProceed only if you consent to view sensitive material."
    override val extensionUninstall = "Uninstall"
    override val extensionUpdate = "Update"
    override val extensionTrust = "Trust"
    override val extensionRevoke = "Revoke"
    override val extensionUntrusted = "Untrusted"
    override val extensionObsolete = "Obsolete"
    override val extensionObsoleteWarning = "This extension was deprecated and will not receive any further updates. Consider uninstalling it."
    override val extensionIncognitoMode = "Incognito mode"
    override val extensionIncognitoSummary = "Reading history will not be recorded for manga from this extension."
    override val extensionDebugInfoCopied = "Extension debug information copied to clipboard"
    override val extensionCookiesCleared = "Cookies cleared"
    override val sourcePreferencesTitle = "Source preferences"
    override val sourcePreferencesEmpty = "No configurable preferences for this source."
    override val sourcePreferencesUnsupported = "This source does not support configurable preferences."
    override val sourcePreferencesSaveError = "Failed to save preference"
    override val actionOk = "OK"
    override val actionCancel = "Cancel"
    override val actionSettings = "Settings"

    override val readerModeSingleLtr = "Left to Right"
    override val readerModeSingleRtl = "Right to Left"
    override val readerModeWebtoon = "Webtoon/Vertical"
    override val readerScaleFitScreen = "Fit Screen"
    override val readerScaleFitWidth = "Fit Width"
    override val readerScaleFitHeight = "Fit Height"
    override val readerScaleOriginal = "Original Size"
    override val readerEscapeHint = "Press Escape to exit fullscreen"
    override val readerColorFilter = "Color Filter"
    override val readerFilterNone = "None"
    override val readerFilterInvert = "Invert"
    override val readerFilterGrayscale = "Grayscale"
    override val readerFilterInvertGrayscale = "Invert Grayscale"
    override val readerFilterSepia = "Sepia"
    override val readerFilterNight = "Night"
    override val readerBackgroundColor = "Background Color"
    override val readerBgDarkGray = "Dark Gray"
    override val readerBgBlack = "Black"
    override val readerBgWhite = "White"
    override val readerBgWarmCream = "Warm Cream"
    override val readerCropBorders = "Smart Crop Borders"
    override val readerCropBordersWebtoon = "Smart Crop Borders (Webtoon)"
    override val readerWebtoonMaxWidth = "Webtoon Max Width"
    override val readerWebtoonSidePadding = "Webtoon Side Padding"

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
    override val settingsAppThemeTitle = "App Theme Palette"
    override val settingsThemeAmoledTitle = "Pure Black AMOLED Theme"
    override val settingsThemeAmoledSubtitle = "Use pure black (#000000) for background and surfaces in dark mode"
    override fun appThemeName(theme: DesktopAppTheme) = when (theme) {
        DesktopAppTheme.DEFAULT -> "Mihon (Default)"
        DesktopAppTheme.GREEN_APPLE -> "Green Apple"
        DesktopAppTheme.CATPPUCCIN -> "Catppuccin"
        DesktopAppTheme.TOKYONIGHT -> "Tokyo Night"
        DesktopAppTheme.LAVENDER -> "Lavender"
        DesktopAppTheme.MIDNIGHT_DUSK -> "Midnight Dusk"
        DesktopAppTheme.NORD -> "Nord"
        DesktopAppTheme.STRAWBERRY_DAIQUIRI -> "Strawberry Daiquiri"
        DesktopAppTheme.TAKO -> "Tako"
        DesktopAppTheme.TEALTURQUOISE -> "Teal Turquoise"
        DesktopAppTheme.TIDAL_WAVE -> "Tidal Wave"
        DesktopAppTheme.YINYANG -> "Yin & Yang"
        DesktopAppTheme.YOTSUBA -> "Yotsuba"
        DesktopAppTheme.MONOCHROME -> "Monochrome"
    }

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

    // Incognito mode
    override val incognitoTitle = "Incognito Mode"
    override val incognitoBannerText = "Incognito mode is active: Reading history and tracker updates are paused."
    override val incognitoDisable = "Disable"
    override val incognitoDescription = "Pauses reading history and remote tracker sync while reading manga."

    // Stats
    override val statsTitle = "Statistics"
    override val statsOverview = "Overview"
    override val statsTotalManga = "Titles in Library"
    override val statsReadDuration = "Reading Time"
    override val statsCompletedManga = "Completed Titles"
    override val statsChapters = "Chapters"
    override val statsTotalChapters = "Total Chapters"
    override val statsReadChapters = "Read Chapters"
    override val statsUnreadChapters = "Unread Chapters"
    override val statsReadPercentage = "Completion Rate"
    override val statsTopGenres = "Top Genres"
    override val statsNoGenres = "No genre data available"
    override val statsStatuses = "Status Distribution"
    override val statsStatusOngoing = "Ongoing"
    override val statsStatusCompleted = "Completed"
    override val statsStatusHiatus = "On Hiatus"
    override val statsStatusCancelled = "Cancelled"
    override val statsStatusUnknown = "Unknown"
    override val statsReadingProgress = "Reading Progress"
    override val statsProgressUnread = "Unread"
    override val statsProgressInProgress = "In Progress"
    override val statsProgressFinished = "Finished"
    override val statsCategories = "Categories"
    override val statsNoCategories = "No categories configured"
    override val statsTrackers = "Tracking"
    override val statsTrackedTitles = "Tracked Titles"
    override val statsMeanScore = "Average Score"
    override val statsActiveTrackers = "Active Trackers"
    override val statsRefresh = "Refresh"

    // Backup Automation
    override val backupAutoTitle = "Automated Backups"
    override val backupAutoDescription = "Automatically export and prune periodic backups in the background."
    override val backupInterval = "Backup Frequency"
    override val backupIntervalOff = "Disabled"
    override val backupInterval6Hours = "Every 6 hours"
    override val backupInterval12Hours = "Every 12 hours"
    override val backupIntervalDaily = "Daily"
    override val backupInterval2Days = "Every 2 days"
    override val backupIntervalWeekly = "Weekly"
    override val backupLocation = "Backup Location"
    override val backupLocationDefault = "Default folder (backups/)"
    override val backupRetention = "Maximum Backups to Keep"
    override val backupLastAutoBackup = "Last automated backup:"
    override val backupNever = "Never"
    override val backupNow = "Backup Now"

    // Library Update & Automation
    override val libraryUpdateTitle = "Library Update"
    override val libraryUpdateNow = "Update Library Now"
    override val libraryUpdating = "Updating library..."
    override val libraryUpdateInterval = "Automatic Update Frequency"
    override val libraryUpdateIntervalManual = "Manual only"
    override val libraryUpdateInterval6Hours = "Every 6 hours"
    override val libraryUpdateInterval12Hours = "Every 12 hours"
    override val libraryUpdateIntervalDaily = "Daily"
    override val libraryUpdateInterval2Days = "Every 2 days"
    override val libraryUpdateIntervalWeekly = "Weekly"
    override val libraryUpdateSkipCompleted = "Skip completed manga"
    override val libraryUpdateSkipUnread = "Skip manga with unread chapters"
    override val libraryAutoDownloadNew = "Automatically download new chapters"
    override val notificationsDesktopEnabled = "Show desktop notifications for new chapters"
    override val libraryLastUpdate = "Last library update:"

    // Web & Cookie Management
    override val cookieManagerTitle = "Cookie & Cloudflare Clearance Manager"
    override val cookieManagerDescription =
        "Manage cookies and custom user-agents to bypass web and anti-bot challenges."
    override val cookieManagerButton = "Open Cookie Manager"
    override val openInBrowser = "Open in Browser"

    // Categories
    override val categoryManageTitle = "Manage Categories"
    override val categoryNewNameLabel = "New category name"
    override val categoryAdd = "Add"
    override val categoryEmpty = "No custom categories yet"
    override val categoryRename = "Rename"
    override val categoryDelete = "Delete"
    override val categoryRenameTitle = "Rename Category"
    override val categoryNameLabel = "Category name"
    override val categorySave = "Save"
    override val categorySetTitle = "Set Categories"
    override val categoryNoneExist = "No categories exist. Create categories first."
    override val categoryNoneCreated = "No custom categories created yet."

    // Edit Manga Info
    override val mangaDetailTitleLabel = "Title"
    override val mangaDetailStatusLicensed = "Licensed"
    override val mangaDetailStatusPublishingFinished = "Publishing Finished"
    override val mangaDetailGenresLabel = "Genres / Tags (comma-separated)"
    override val mangaDetailDescriptionLabel = "Description"
    override val mangaDetailSaveSuccess = "Save Changes"
    override fun mangaDetailStatusOption(status: Long) = when (status) {
        1L -> mangaDetailStatusOngoing
        2L -> mangaDetailStatusCompleted
        3L -> mangaDetailStatusLicensed
        4L -> mangaDetailStatusPublishingFinished
        5L -> statsStatusCancelled
        6L -> statsStatusHiatus
        else -> mangaDetailStatusUnknown
    }

    // Cookie & Clearance Manager
    override fun cookieConfiguredDomains(count: Int) = "Configured Domains ($count)"
    override val cookieAddDomain = "+ Add"
    override val cookieNoCustomDomains = "No custom domain cookies configured"
    override fun cookieCountSubtitle(count: Int, hasCustomUa: Boolean) =
        "$count cookies configured" + if (hasCustomUa) " • Custom UA" else ""
    override val cookieDomainLabel = "Domain (e.g. mangadex.org)"
    override val cookieRawCookiesLabel = "Raw Cookies (e.g. cf_clearance=xxx; token=yyy)"
    override val cookieCustomUaLabel = "Custom User-Agent (Optional)"
    override val cookieDelete = "Delete"
    override val cookieSave = "Save Cookies"
    override fun cookieRemovedStatus(domain: String) = "Removed cookies for $domain"
    override fun cookieSavedStatus(count: Int, domain: String) = "Saved $count cookies for $domain"

    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "Connect to $name"
    override fun trackerAuthTitle(name: String) = "Authenticate with $name"
    override fun trackerLoginTitle(name: String) = "Log in to $name"
    override val trackerServerUrl = "Server URL (e.g. https://komga.example.com)"
    override val trackerUsername = "Username"
    override val trackerPasswordOrApiKey = "Password or API Key"
    override fun trackerAuthUrl(url: String) = "Authorization URL: $url"
    override val trackerToken = "API Token / Access Token"
    override val trackerAccountName = "Account Name (Optional)"
    override val trackerPassword = "Password"
    override val trackerConnect = "Connect"
    override val trackerLogin = "Log In"
    override val trackerLoginFailed = "Login failed. Check the account, credential and server address, then retry."
    override val trackerServerAuthHelp = "For an API key, leave username empty. For a password, enter both fields."
    override val trackingRequestFailed = "The tracking request failed. Check the connection and login, then retry."
    override fun trackingTitle(mangaTitle: String) = "Tracking - $mangaTitle"
    override val trackingNotTracking = "Not tracking"
    override val trackingNotLoggedIn = "Not logged in"
    override val trackingTrack = "Track"
    override val trackingEdit = "Edit"
    override val trackingRemove = "Remove"
    override fun trackingSearchTitle(name: String) = "Search $name"
    override val trackingSearch = "Search"
    override fun trackingTotalChapters(count: Long) = "$count chapters"
    override fun trackingEditTitle(title: String) = "Edit Tracking - $title"
    override val trackingChaptersRead = "Chapters Read"
    override val trackingScore = "Score"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "Reading"
        TrackStatus.COMPLETED -> "Completed"
        TrackStatus.ON_HOLD -> "On Hold"
        TrackStatus.DROPPED -> "Dropped"
        TrackStatus.PLAN_TO_READ -> "Plan to Read"
        TrackStatus.REREADING -> "Rereading"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "Fullscreen"
    override val readerBorderless = "Borderless"
    override val readerShortcutsTitle = "Keyboard Shortcuts"
    override val readerShortcutsHelp = "Shortcuts"
    override fun readerCropToggle(active: Boolean) = if (active) "Crop: On" else "Crop: Off"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "Cover offset: On" else "Cover offset: Off"
    override val readerModeDualLtr = "Dual LTR"
    override val readerModeDualRtl = "Dual RTL"
    override val readerModeVertical = "Vertical"
    override fun readerModeLabel(mode: ReadingMode) = when (mode) {
        ReadingMode.SINGLE_LTR -> "Single LTR"
        ReadingMode.SINGLE_RTL -> "Single RTL"
        ReadingMode.DUAL_LTR -> readerModeDualLtr
        ReadingMode.DUAL_RTL -> readerModeDualRtl
        ReadingMode.VERTICAL -> readerModeVertical
        ReadingMode.WEBTOON -> "Webtoon"
    }
    override fun readerScaleLabel(scale: ScaleMode) = when (scale) {
        ScaleMode.ORIGINAL -> "Original"
        ScaleMode.FIT_WIDTH -> "Fit width"
        ScaleMode.FIT_HEIGHT -> "Fit height"
    }
    override fun readerFilterLabel(filter: ReaderColorFilter) = when (filter) {
        ReaderColorFilter.NONE -> "None"
        ReaderColorFilter.INVERT -> "Invert"
        ReaderColorFilter.GRAYSCALE -> "Grayscale"
        ReaderColorFilter.INVERT_GRAYSCALE -> "Invert grayscale"
        ReaderColorFilter.SEPIA -> "Sepia"
        ReaderColorFilter.NIGHT -> "Night"
    }
    override fun readerBackgroundLabel(bg: ReaderBackgroundColor) = when (bg) {
        ReaderBackgroundColor.DARK_GRAY -> "Dark gray"
        ReaderBackgroundColor.BLACK -> "Black"
        ReaderBackgroundColor.WHITE -> "White"
        ReaderBackgroundColor.WARM_CREAM -> "Warm cream"
    }
    override val readerSettingsDialogTitle = "Reader settings"
    override val readerClickRegions = "Click regions"
    override val readerRegionLeft = "Left"
    override val readerRegionCenter = "Center"
    override val readerRegionRight = "Right"
    override fun readerActionLabel(action: ReaderClickAction) = action.name.lowercase()
    override fun readerLeftBoundary(percent: Int) = "Left boundary $percent%"
    override fun readerCenterBoundary(percent: Int) = "Center boundary $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "Wheel: scroll page"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "Wheel: flip page"
    }
    override val readerReserveCover = "Reserve cover in dual-page modes"
    override val readerCropBordersPaged = "Smart crop borders (Paged)"
    override val readerWebtoonLayout = "Webtoon layout"
    override val readerWebtoonWidthFull = "Max width: Full screen"
    override fun readerWebtoonWidthDp(width: Int) = "Max width: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "Side padding: $padding%"
    override val readerLoadingChapter = "Loading chapter…"
    override val readerClosed = "Reader closed"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "This page could not be found. It may have been moved or deleted."
        is ReaderFailure.UnsupportedFormat -> "This chapter format is not supported."
        is ReaderFailure.RemoteImage ->
            "The page could not be downloaded.\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "Encrypted chapter containers are not supported."
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "This local chapter is no longer available. Locate or re-import it."
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "This page is corrupt or unreadable."
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "This image format is not supported."
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "This chapter exceeds the safe reader limits."
        is ReaderFailure.EmptyChapter -> "This chapter contains no readable pages."
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "This chapter contains no readable pages."
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "This local chapter is unavailable. Locate or re-import it."
            ReaderErrorCode.PAGE_NOT_FOUND -> "This page could not be found."
            ReaderErrorCode.PAGE_DECODE_FAILED -> "This page could not be decoded."
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "The reader reached its memory limit."
            ReaderErrorCode.INVALID_PROGRESS -> "The saved reading position is invalid."
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "Language: " + lang.uppercase()
    override val browseSourcePopular = "Popular"
    override val browseSourceLatest = "Latest"
    override val browseSearchTitlesPlaceholder = "Search titles..."
    override val browseSearchButton = "Search"
    override fun browseFiltersButton(count: Int) = if (count > 0) "Filters ($count)" else "Filters"
    override val browseNoMangaFound = "No manga found"
    override val browseInLibraryBadge = "IN LIBRARY"
    override val browsePrevPage = "Previous"
    override fun browsePageNumber(page: Int) = "Page $page"
    override val browseNextPage = "Next"
    override fun browseInstallExtensionTitle(name: String) = "Install Extension: $name"
    override fun browsePackageLabel(pkg: String) = "Package: $pkg"
    override fun browseVersionLabel(version: String) = "Version: $version"
    override fun browseLanguageLabel(lang: String) = "Language: $lang"
    override val browseNetworkPermissionNotice = "Review network permissions before installing extensions."
    override val browseTrustAndInstall = "Trust & Install"
    override val browseAddRepository = "Add"
    override val browseConfiguredRepositories = "Configured Repositories:"
    override val browseRemoveRepository = "Remove"
    override val browseSourceBrowse = "Browse"
    override val browsePin = "☆ Pin"
    override val browseUnpin = "★ Unpin"
    override val browseUpdate = "Update"
    override val browseDisable = "Disable"
    override val browseEnable = "Enable"
    override fun onlineDetailSource(name: String, lang: String) = "Source: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "Author: $author"
    override fun onlineDetailArtist(artist: String) = "Artist: $artist"
    override fun onlineDetailGenres(genres: String) = "Genres: $genres"
    override fun onlineDetailChapters(count: Int) = "Chapters ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "Scanlator: $scanlator"
    override val migrateSelectSourceHeader = "Select a source to migrate manga from:"
    override val migrateNoMangaInLibrary = "No manga from online sources in library."
    override fun migrateMangaCount(count: Int) = "$count manga in library"
    override val migrateViewManga = "View Manga"
    override val migrateBackToSources = "Back to Sources"
    override val migrateSelectMangaHeader = "Select a manga to migrate to another source"
    override val migrateAction = "Migrate"
    override fun migrateDialogTitle(title: String) = "Migrate: $title"
    override val migrateDialogSubtitle =
        "Search for a matching title in target source to transfer reading progress, bookmarks, and categories."
    override val migrateNoOtherSources = "No other sources available for migration."
    override val migrateTargetSourceLabel = "Target Source:"
    override val migrateSearchPlaceholder = "Search title..."
    override val migrateSelectedBadge = "✓ Selected"
    override val migrateConfirm = "Confirm Migration"
    override val filterDialogTitle = "Source Filters"
    override val filterReset = "Reset"
    override val filterNoAvailable = "No filters available for this source."
    override val filterApply = "Apply Filters"
    override val filterAscending = "▲ Asc"
    override val filterDescending = "▼ Desc"
    override val globalSearchEnterQuery = "Enter a search query to search across all installed and built-in sources."
    override val globalSearchNoSources = "No sources available or search not started."
    override val globalSearchViewAll = "View All"
    override fun globalSearchError(error: String) = "Error: $error"
    override val globalSearchNoResults = "No results in this source"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "Custom Download Path"
    override val settingsDownloadCustomPathPlaceholder = "Leave blank for default (media/downloads)"
    override val settingsDownloadAheadTitle = "Download Ahead While Reading"
    override val settingsDownloadAheadDesc = "Automatically download the next unread chapters while reading."
    override val settingsDownloadAheadDisabled = "Disabled"
    override fun settingsDownloadAheadChapters(count: Int) = if (count == 1) "1 chapter" else "$count chapters"
    override val settingsDeleteReadChaptersTitle = "Delete Read Chapters"
    override val settingsDeleteReadChaptersDesc =
        "Automatically delete downloaded chapter files when marked as read."
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "Logged in as $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "Log Out"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "Checked $checked titles, $newChapters new chapters"
    override val settingsLibraryUpdateCompleted = "Update completed"
    override fun settingsLibraryUpdateFailed(msg: String) = "Update failed: $msg"
    override val readerUnavailable = "The reader is unavailable in this runtime."
    override val readerOpeningChapter = "Opening chapter…"
    override fun importReportTitle(id: Long) = "Report $id"
    override fun importReportManga(inserted: Long, merged: Long) = "Manga: $inserted inserted, $merged merged"
    override fun importReportChapters(inserted: Long, merged: Long) = "Chapters: $inserted inserted, $merged merged"
    override fun importReportCategories(count: Long) = "Categories linked: $count"
    override fun importReportPreferences(
        imported: Long,
        skipped: Long,
    ) = "Preferences: $imported imported, $skipped skipped"
    override fun importReportSkipCategories(categories: String) = "Skip categories: $categories"
    override fun importReportCategory(category: String) = "Category: $category"
    override val mangaDetailBackToLibrary = "Back to Library"
}

object SimplifiedChineseStrings : DesktopStrings {
    override val appName = "Mihon W"

    override fun destinationLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "书架"
        DesktopDestination.Updates -> "更新"
        DesktopDestination.History -> "历史"
        DesktopDestination.Browse -> "浏览"
        DesktopDestination.Downloads -> "下载"
        DesktopDestination.Stats -> "统计"
        DesktopDestination.Settings -> "设置"
        DesktopDestination.About -> "关于"
    }

    override fun destinationShortLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "书"
        DesktopDestination.Updates -> "更"
        DesktopDestination.History -> "历"
        DesktopDestination.Browse -> "览"
        DesktopDestination.Downloads -> "载"
        DesktopDestination.Stats -> "统"
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
    override fun libraryUnreadCount(count: Int) = "$count 未读"
    override val libraryAllCategory = "全部"

    // Library Display, Filter & Sort
    override val libraryDisplayMode = "展示模式"
    override val libraryDisplayComfortable = "舒适网格"
    override val libraryDisplayCompact = "紧凑网格"
    override val libraryDisplayCoverOnly = "纯封面"
    override val libraryDisplayList = "列表"
    override val libraryGridSize = "封面大小"
    override val libraryFilterAndSort = "筛选与排序"
    override val libraryFilterTab = "筛选"
    override val librarySortTab = "排序"
    override val libraryFilterReset = "重置筛选"
    override val libraryFilterUnread = "未读"
    override val libraryFilterDownloaded = "已下载"
    override val libraryFilterStarted = "开始阅读"
    override val libraryFilterCompleted = "已完结"
    override val libraryFilterBookmarked = "已加书签"
    override val librarySortDefault = "默认"
    override val librarySortAlphabetical = "字母顺序"
    override val librarySortLastRead = "最后阅读"
    override val librarySortLastUpdate = "最后更新"
    override val librarySortUnreadCount = "未读话数"
    override val librarySortTotalChapters = "总话数"
    override val librarySortDateAdded = "添加时间"
    override val librarySortAscending = "升序"
    override val librarySortDescending = "降序"

    // Library Batch Actions
    override val libraryBatchSelect = "多选"
    override fun libraryBatchSelected(count: Int) = "已选择 $count 项"
    override val libraryBatchSelectAll = "全选"
    override val libraryBatchDeselectAll = "取消全选"
    override val libraryBatchChangeCategory = "设置分类"
    override val libraryBatchMarkRead = "标记已读"
    override val libraryBatchMarkUnread = "标记未读"
    override val libraryBatchDownload = "下载"
    override val libraryBatchDownloadNext1 = "下一话"
    override val libraryBatchDownloadNext5 = "下 5 话"
    override val libraryBatchDownloadAllUnread = "全部未读"
    override val libraryBatchRemove = "移出书架"
    override val libraryBatchRemoveConfirmTitle = "移出所选漫画"
    override fun libraryBatchRemoveConfirmMessage(count: Int) =
        "确定要将选中的 $count 本漫画从书架中移出吗？"
    override val libraryBatchDone = "完成"

    override fun mangaDetailSource(name: String) = "图源 $name"
    override val mangaDetailStatusOngoing = "连载中"
    override val mangaDetailStatusCompleted = "已完结"
    override val mangaDetailStatusUnknown = "未知"
    override val mangaDetailInLibrary = "移出书架"
    override val mangaDetailAddToLibrary = "添加到书架"
    override val mangaDetailCategories = "分类"
    override val mangaDetailTracking = "进度记录"
    override fun mangaDetailResume(chapter: String) = "继续阅读 $chapter"
    override fun mangaDetailStart(chapter: String) = "开始阅读 $chapter"
    override val mangaDetailNoChapters = "未找到任何章节"
    override val mangaDetailBack = "返回"
    override val mangaDetailChangeCover = "更换封面"
    override val mangaDetailResetCover = "重置封面"
    override val mangaDetailEditInfo = "编辑信息"
    override val mangaDetailAuthor = "作者"
    override val mangaDetailArtist = "画师"
    override val mangaDetailStatus = "状态"
    override val mangaDetailGenres = "标签"
    override val mangaDetailNotes = "个人笔记"
    override val mangaDetailResetToSource = "恢复图源默认"
    override val mangaDetailSave = "保存"

    // Chapters & Chapter Actions
    override val chapters = "章节"
    override val sortSourceOrder = "图源顺序"
    override val sortChapterNumber = "章节编号"
    override val sortUploadDate = "更新时间"
    override val filterUnread = "未读"
    override val filterRead = "已读"
    override val filterUnreadOnly = "仅未读"
    override val filterReadOnly = "仅已读"
    override val filterDownloaded = "已下载"
    override val filterDownloadedOnly = "仅已下载"
    override val filterNotDownloadedOnly = "仅未下载"
    override val filterBookmarked = "已书签"
    override val filterBookmarkedOnly = "仅书签"
    override val filterNotBookmarkedOnly = "仅无书签"
    override val markAsRead = "标为已读"
    override val markAsUnread = "标为未读"
    override val markPreviousAsRead = "将更早章节标为已读"
    override val downloadChapter = "下载"
    override val downloadNext1 = "下一话"
    override val downloadNext5 = "下 5 话"
    override val downloadNext10 = "下 10 话"
    override val downloadNext25 = "下 25 话"
    override val downloadUnread = "全部未读"
    override val downloadAll = "全部章节"
    override val readerPreviousChapter = "上一话"
    override val readerNextChapter = "下一话"
    override val readerChapterList = "章节列表"
    override val readerCurrentChapter = "当前"
    override val readerChapterDrawerSearch = "过滤章节..."
    override val readerChapterDrawerNoResults = "未找到匹配章节"
    override val mangaDetailSearchChaptersPlaceholder = "过滤章节..."
    override val deleteDownload = "删除下载"
    override val bookmarkChapter = "添加书签"
    override val removeBookmark = "移除书签"
    override val chapterBatchSelect = "多选章节"
    override fun chapterBatchSelected(count: Int): String = "已选择 $count 话"
    override val chapterBatchSelectAll = "全选"
    override val chapterBatchInvert = "反选"
    override val chapterBatchBookmark = "添加书签"
    override val chapterBatchRemoveBookmark = "移除书签"
    override val chapterBatchMarkAsRead = "标记已读"
    override val chapterBatchMarkAsUnread = "标记未读"
    override val chapterBatchDownload = "下载"
    override val chapterBatchDeleteDownload = "删除下载"
    override val mangaDetailCoverView = "查看封面"
    override val mangaDetailCoverSave = "保存封面"
    override val mangaDetailCoverSaved = "封面已成功保存"
    override val mangaNotesTitle = "个人便签"
    override val mangaNotesEdit = "编辑便签"
    override val mangaNotesSave = "保存便签"
    override val mangaNotesPlaceholder = "添加关于此漫画的个人笔记或备注..."

    // Storage & Cache Cleaner
    override val storageCleanerTitle = "数据与存储管理"
    override val storageCleanerDescription = "管理已下载的漫画章节与本地图片缓存以释放存储空间。"
    override val storageCleanerDownloadSize = "已下载章节占用"
    override val storageCleanerClearRead = "清理已读章节下载"
    override val storageCleanerClearReadSuccess = "已清理已读章节"
    override val storageCleanerClearImageCache = "清理图片磁盘缓存"
    override val storageCleanerClearImageCacheSuccess = "图片磁盘缓存已清理"

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
    override val browseTabExtensions = "插件商店"
    override val browseTabMigration = "图源迁移"
    override val browseManageRepositories = "图源仓库管理"
    override val browseRefresh = "刷新"
    override val browseInstall = "安装"
    override val browseUninstall = "卸载"
    override val browseInstalledBadge = "已安装"
    override val browseSearchPlaceholder = "搜索图源或插件"
    override val browseNetworkDomainsHeader = "声明的网络域名:"
    override val browseGlobalSearch = "全局搜索"
    override val browseInstallFromFile = "本地安装 (.mext)"
    override val browseUpdateAll = "全部更新"

    override val extensionInfo = "插件信息"
    override val extensionOpenRepo = "打开仓库"
    override val extensionEnableAll = "全部启用"
    override val extensionDisableAll = "全部禁用"
    override val extensionClearCookies = "清除 Cookies"
    override val extensionVersion = "版本"
    override val extensionLanguage = "语言"
    override val extensionAgeRating = "年龄分级"
    override val extensionNsfwShort = "18+"
    override val extensionNsfwWarning = "本插件包含成人（18+）内容。\n建议仅在知情且同意的情况下使用。"
    override val extensionUninstall = "卸载"
    override val extensionUpdate = "更新"
    override val extensionTrust = "信任"
    override val extensionRevoke = "撤销信任"
    override val extensionUntrusted = "未受信任"
    override val extensionObsolete = "已废弃"
    override val extensionObsoleteWarning = "此扩展已过时且已被废弃。建议卸载。"
    override val extensionIncognitoMode = "无痕模式"
    override val extensionIncognitoSummary = "该插件的漫画将不会记录阅读历史"
    override val extensionDebugInfoCopied = "已复制插件调试信息到剪贴板"
    override val extensionCookiesCleared = "已清除 Cookies"
    override val sourcePreferencesTitle = "图源偏好设置"
    override val sourcePreferencesEmpty = "此图源没有可配置的偏好设置"
    override val sourcePreferencesUnsupported = "此图源不支持偏好设置配置"
    override val sourcePreferencesSaveError = "保存偏好设置失败"
    override val actionOk = "确定"
    override val actionCancel = "取消"
    override val actionSettings = "设置"

    override val readerModeSingleLtr = "单页式（从左到右）"
    override val readerModeSingleRtl = "单页式（从右到左）"
    override val readerModeWebtoon = "条漫"
    override val readerScaleFitScreen = "适应屏幕"
    override val readerScaleFitWidth = "适应宽度"
    override val readerScaleFitHeight = "适应高度"
    override val readerScaleOriginal = "原始大小"
    override val readerEscapeHint = "按 Esc 退出全屏"
    override val readerColorFilter = "色彩滤镜"
    override val readerFilterNone = "无"
    override val readerFilterInvert = "反色"
    override val readerFilterGrayscale = "黑白灰阶"
    override val readerFilterInvertGrayscale = "反转灰阶"
    override val readerFilterSepia = "暖色怀旧"
    override val readerFilterNight = "夜间微光"
    override val readerBackgroundColor = "阅读背景色"
    override val readerBgDarkGray = "深灰"
    override val readerBgBlack = "纯黑"
    override val readerBgWhite = "纯白"
    override val readerBgWarmCream = "暖米白"
    override val readerCropBorders = "智能裁切白边"
    override val readerCropBordersWebtoon = "智能裁切白边 (条漫)"
    override val readerWebtoonMaxWidth = "条漫最大宽度限制"
    override val readerWebtoonSidePadding = "条漫侧边距"

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
    override val settingsAppThemeTitle = "应用主题配色"
    override val settingsThemeAmoledTitle = "纯黑 AMOLED 深色模式"
    override val settingsThemeAmoledSubtitle = "在深色模式下使用纯黑 (#000000) 背景与卡片表面"
    override fun appThemeName(theme: DesktopAppTheme) = when (theme) {
        DesktopAppTheme.DEFAULT -> "Mihon 经典蓝绿"
        DesktopAppTheme.GREEN_APPLE -> "青苹果 (Green Apple)"
        DesktopAppTheme.CATPPUCCIN -> "Catppuccin 萌系紫"
        DesktopAppTheme.TOKYONIGHT -> "东京夜景 (Tokyo Night)"
        DesktopAppTheme.LAVENDER -> "薰衣草 (Lavender)"
        DesktopAppTheme.MIDNIGHT_DUSK -> "午夜暮色 (Midnight Dusk)"
        DesktopAppTheme.NORD -> "北欧冷蓝 (Nord)"
        DesktopAppTheme.STRAWBERRY_DAIQUIRI -> "草莓甜心 (Strawberry)"
        DesktopAppTheme.TAKO -> "Tako 触手紫"
        DesktopAppTheme.TEALTURQUOISE -> "青绿湖水 (Teal Turquoise)"
        DesktopAppTheme.TIDAL_WAVE -> "潮汐深海 (Tidal Wave)"
        DesktopAppTheme.YINYANG -> "阴阳高对比 (Yin & Yang)"
        DesktopAppTheme.YOTSUBA -> "四叶草暖橙 (Yotsuba)"
        DesktopAppTheme.MONOCHROME -> "极简单色 (Monochrome)"
    }

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

    // Incognito mode
    override val incognitoTitle = "无痕模式"
    override val incognitoBannerText = "无痕模式已启用：阅读历史与跟踪同步已暂停。"
    override val incognitoDisable = "关闭"
    override val incognitoDescription = "阅读漫画时暂停记录阅读历史并阻断外部跟踪器同步。"

    // Stats
    override val statsTitle = "统计中心"
    override val statsOverview = "总览"
    override val statsTotalManga = "书架漫画数"
    override val statsReadDuration = "总阅读时长"
    override val statsCompletedManga = "已完结作品"
    override val statsChapters = "章节统计"
    override val statsTotalChapters = "总章节数"
    override val statsReadChapters = "已读章节"
    override val statsUnreadChapters = "未读章节"
    override val statsReadPercentage = "阅读完成率"
    override val statsTopGenres = "题材偏好"
    override val statsNoGenres = "暂无题材数据"
    override val statsStatuses = "连载状态分布"
    override val statsStatusOngoing = "连载中"
    override val statsStatusCompleted = "已完结"
    override val statsStatusHiatus = "休刊中"
    override val statsStatusCancelled = "已取消"
    override val statsStatusUnknown = "未知"
    override val statsReadingProgress = "阅读进度分布"
    override val statsProgressUnread = "未开始"
    override val statsProgressInProgress = "阅读中"
    override val statsProgressFinished = "已读完"
    override val statsCategories = "分类分布"
    override val statsNoCategories = "未配置分类"
    override val statsTrackers = "跟踪统计"
    override val statsTrackedTitles = "已追踪作品"
    override val statsMeanScore = "平均评分"
    override val statsActiveTrackers = "活跃跟踪器"
    override val statsRefresh = "刷新"

    // Backup Automation
    override val backupAutoTitle = "自动定时备份"
    override val backupAutoDescription = "在后台自动导出并修剪清理定期的漫画备份。"
    override val backupInterval = "备份周期"
    override val backupIntervalOff = "关闭"
    override val backupInterval6Hours = "每 6 小时"
    override val backupInterval12Hours = "每 12 小时"
    override val backupIntervalDaily = "每天"
    override val backupInterval2Days = "每 2 天"
    override val backupIntervalWeekly = "每周"
    override val backupLocation = "备份存储目录"
    override val backupLocationDefault = "默认目录 (backups/)"
    override val backupRetention = "最大保留备份数"
    override val backupLastAutoBackup = "上次自动备份："
    override val backupNever = "从不"
    override val backupNow = "立即备份"

    // Library Update & Automation
    override val libraryUpdateTitle = "书架更新"
    override val libraryUpdateNow = "立即检查书架更新"
    override val libraryUpdating = "正在检查并同步书架更新..."
    override val libraryUpdateInterval = "自动更新频率"
    override val libraryUpdateIntervalManual = "仅手动"
    override val libraryUpdateInterval6Hours = "每 6 小时"
    override val libraryUpdateInterval12Hours = "每 12 小时"
    override val libraryUpdateIntervalDaily = "每天"
    override val libraryUpdateInterval2Days = "每 2 天"
    override val libraryUpdateIntervalWeekly = "每周"
    override val libraryUpdateSkipCompleted = "跳过已完结作品"
    override val libraryUpdateSkipUnread = "跳过存在未读章节的作品"
    override val libraryAutoDownloadNew = "自动下载新更新章节"
    override val notificationsDesktopEnabled = "发现新章节时推送桌面系统通知"
    override val libraryLastUpdate = "上次书架更新："

    // Web & Cookie Management
    override val cookieManagerTitle = "Cookie 与网络穿透管理器"
    override val cookieManagerDescription = "管理图源域名的 Cookie 与专属 User-Agent，轻松穿透 Cloudflare 5 秒盾与防爬挑战。"
    override val cookieManagerButton = "打开 Cookie 管理器"
    override val openInBrowser = "在浏览器中打开"

    // Categories
    override val categoryManageTitle = "分类管理"
    override val categoryNewNameLabel = "新分类名称"
    override val categoryAdd = "添加"
    override val categoryEmpty = "暂无自定义分类"
    override val categoryRename = "重命名"
    override val categoryDelete = "删除"
    override val categoryRenameTitle = "重命名分类"
    override val categoryNameLabel = "分类名称"
    override val categorySave = "保存"
    override val categorySetTitle = "设置分类"
    override val categoryNoneExist = "尚无任何分类，请先创建分类。"
    override val categoryNoneCreated = "尚未创建自定义分类。"

    // Edit Manga Info
    override val mangaDetailTitleLabel = "标题"
    override val mangaDetailStatusLicensed = "已授权"
    override val mangaDetailStatusPublishingFinished = "已完结(刊登结束)"
    override val mangaDetailGenresLabel = "标签/题材 (以英文逗号分隔)"
    override val mangaDetailDescriptionLabel = "简介"
    override val mangaDetailSaveSuccess = "保存更改"
    override fun mangaDetailStatusOption(status: Long) = when (status) {
        1L -> mangaDetailStatusOngoing
        2L -> mangaDetailStatusCompleted
        3L -> mangaDetailStatusLicensed
        4L -> mangaDetailStatusPublishingFinished
        5L -> statsStatusCancelled
        6L -> statsStatusHiatus
        else -> mangaDetailStatusUnknown
    }

    // Cookie & Clearance Manager
    override fun cookieConfiguredDomains(count: Int) = "已配置域名 ($count)"
    override val cookieAddDomain = "+ 添加"
    override val cookieNoCustomDomains = "尚未配置自定义域名 Cookie"
    override fun cookieCountSubtitle(count: Int, hasCustomUa: Boolean) =
        "$count 个已配置 Cookie" + if (hasCustomUa) " • 自定义 UA" else ""
    override val cookieDomainLabel = "域名 (例如 mangadex.org)"
    override val cookieRawCookiesLabel = "原始 Cookies (例如 cf_clearance=xxx; token=yyy)"
    override val cookieCustomUaLabel = "自定义 User-Agent (选填)"
    override val cookieDelete = "删除"
    override val cookieSave = "保存 Cookie"
    override fun cookieRemovedStatus(domain: String) = "已移除 $domain 的 Cookie"
    override fun cookieSavedStatus(count: Int, domain: String) = "已为 $domain 保存 $count 个 Cookie"

    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "连接到 $name"
    override fun trackerAuthTitle(name: String) = "在 $name 认证"
    override fun trackerLoginTitle(name: String) = "登录到 $name"
    override val trackerServerUrl = "服务器地址 (例如 https://komga.example.com)"
    override val trackerUsername = "用户名"
    override val trackerPasswordOrApiKey = "密码或 API 密钥"
    override fun trackerAuthUrl(url: String) = "授权网址: $url"
    override val trackerToken = "API 令牌 / 访问令牌"
    override val trackerAccountName = "账户名称 (选填)"
    override val trackerPassword = "密码"
    override val trackerConnect = "连接"
    override val trackerLogin = "登录"
    override val trackerLoginFailed = "登录失败，请检查账户、凭证和服务器地址后重试。"
    override val trackerServerAuthHelp = "使用 API 密钥时留空用户名；使用密码时填写用户名和密码。"
    override val trackingRequestFailed = "追踪请求失败，请检查网络和登录状态后重试。"
    override fun trackingTitle(mangaTitle: String) = "进度记录 - $mangaTitle"
    override val trackingNotTracking = "未记录"
    override val trackingNotLoggedIn = "未登录"
    override val trackingTrack = "记录"
    override val trackingEdit = "编辑"
    override val trackingRemove = "解绑"
    override fun trackingSearchTitle(name: String) = "搜索 $name"
    override val trackingSearch = "搜索"
    override fun trackingTotalChapters(count: Long) = "$count 话"
    override fun trackingEditTitle(title: String) = "编辑记录 - $title"
    override val trackingChaptersRead = "已读话数"
    override val trackingScore = "评分"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "阅读中"
        TrackStatus.COMPLETED -> "已读完"
        TrackStatus.ON_HOLD -> "搁置"
        TrackStatus.DROPPED -> "弃坑"
        TrackStatus.PLAN_TO_READ -> "计划阅读"
        TrackStatus.REREADING -> "重读中"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "全屏"
    override val readerBorderless = "无边框"
    override val readerShortcutsTitle = "快捷键速查"
    override val readerShortcutsHelp = "快捷键"
    override fun readerCropToggle(active: Boolean) = if (active) "裁切白边: 开" else "裁切白边: 关"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "封面偏移: 开" else "封面偏移: 关"
    override val readerModeDualLtr = "双页拼合（从左到右）"
    override val readerModeDualRtl = "双页拼合（从右到左）"
    override val readerModeVertical = "连续垂直"
    override fun readerModeLabel(mode: ReadingMode) = when (mode) {
        ReadingMode.SINGLE_LTR -> readerModeSingleLtr
        ReadingMode.SINGLE_RTL -> readerModeSingleRtl
        ReadingMode.DUAL_LTR -> readerModeDualLtr
        ReadingMode.DUAL_RTL -> readerModeDualRtl
        ReadingMode.VERTICAL -> readerModeVertical
        ReadingMode.WEBTOON -> readerModeWebtoon
    }
    override fun readerScaleLabel(scale: ScaleMode) = when (scale) {
        ScaleMode.ORIGINAL -> readerScaleOriginal
        ScaleMode.FIT_WIDTH -> readerScaleFitWidth
        ScaleMode.FIT_HEIGHT -> readerScaleFitHeight
    }
    override fun readerFilterLabel(filter: ReaderColorFilter) = when (filter) {
        ReaderColorFilter.NONE -> readerFilterNone
        ReaderColorFilter.INVERT -> readerFilterInvert
        ReaderColorFilter.GRAYSCALE -> readerFilterGrayscale
        ReaderColorFilter.INVERT_GRAYSCALE -> readerFilterInvertGrayscale
        ReaderColorFilter.SEPIA -> readerFilterSepia
        ReaderColorFilter.NIGHT -> readerFilterNight
    }
    override fun readerBackgroundLabel(bg: ReaderBackgroundColor) = when (bg) {
        ReaderBackgroundColor.DARK_GRAY -> readerBgDarkGray
        ReaderBackgroundColor.BLACK -> readerBgBlack
        ReaderBackgroundColor.WHITE -> readerBgWhite
        ReaderBackgroundColor.WARM_CREAM -> readerBgWarmCream
    }
    override val readerSettingsDialogTitle = "阅读器设置"
    override val readerClickRegions = "点击翻页区域"
    override val readerRegionLeft = "左侧"
    override val readerRegionCenter = "中间"
    override val readerRegionRight = "右侧"
    override fun readerActionLabel(action: ReaderClickAction) = when (action) {
        ReaderClickAction.PREVIOUS -> "上一页"
        ReaderClickAction.NEXT -> "下一页"
        ReaderClickAction.TOGGLE_CHROME -> "切换菜单"
        ReaderClickAction.NONE -> "无操作"
    }
    override fun readerLeftBoundary(percent: Int) = "左侧边界 $percent%"
    override fun readerCenterBoundary(percent: Int) = "中间边界 $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "滚轮: 滚动页面"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "滚轮: 翻页"
    }
    override val readerReserveCover = "双页模式下保留封面独立单页"
    override val readerCropBordersPaged = "智能裁切白边 (单页/双页)"
    override val readerWebtoonLayout = "条漫排版"
    override val readerWebtoonWidthFull = "最大宽度: 全屏"
    override fun readerWebtoonWidthDp(width: Int) = "最大宽度: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "侧边距: $padding%"
    override val readerLoadingChapter = "正在加载章节…"
    override val readerClosed = "阅读器已关闭"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "无法找到此页面，可能已被移动或删除。"
        is ReaderFailure.UnsupportedFormat -> "不支持此章节格式。"
        is ReaderFailure.RemoteImage -> "页面下载失败。\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "不支持加密的章节压缩包。"
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "本地章节已不可用，请重新导入或检查文件位置。"
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "页面文件损坏或无法读取。"
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "不支持此图片格式。"
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "章节内容超出安全阅读限制。"
        is ReaderFailure.EmptyChapter -> "此章节不包含任何可读取的页面。"
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "此章节不包含任何可读取的页面。"
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "图源或本地章节不可用，请定位或重新导入。"
            ReaderErrorCode.PAGE_NOT_FOUND -> "未找到此页面。"
            ReaderErrorCode.PAGE_DECODE_FAILED -> "页面解码失败。"
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "阅读器已达到内存占用上限。"
            ReaderErrorCode.INVALID_PROGRESS -> "保存的阅读进度无效。"
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "图源语言: " + lang.uppercase()
    override val browseSourcePopular = "热门"
    override val browseSourceLatest = "最新"
    override val browseSearchTitlesPlaceholder = "搜索漫画标题..."
    override val browseSearchButton = "搜索"
    override fun browseFiltersButton(count: Int) = if (count > 0) "筛选 ($count)" else "筛选"
    override val browseNoMangaFound = "未找到任何漫画"
    override val browseInLibraryBadge = "已在书架"
    override val browsePrevPage = "上一页"
    override fun browsePageNumber(page: Int) = "第 $page 页"
    override val browseNextPage = "下一页"
    override fun browseInstallExtensionTitle(name: String) = "安装插件: $name"
    override fun browsePackageLabel(pkg: String) = "包名: $pkg"
    override fun browseVersionLabel(version: String) = "版本: $version"
    override fun browseLanguageLabel(lang: String) = "语言: $lang"
    override val browseNetworkPermissionNotice = "安装插件前请确认其网络声明权限。"
    override val browseTrustAndInstall = "信任并安装"
    override val browseAddRepository = "添加"
    override val browseConfiguredRepositories = "已配置的插件仓库:"
    override val browseRemoveRepository = "移除"
    override val browseSourceBrowse = "浏览"
    override val browsePin = "☆ 固定"
    override val browseUnpin = "★ 取消固定"
    override val browseUpdate = "更新"
    override val browseDisable = "停用"
    override val browseEnable = "启用"
    override fun onlineDetailSource(name: String, lang: String) = "图源: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "作者: $author"
    override fun onlineDetailArtist(artist: String) = "画师: $artist"
    override fun onlineDetailGenres(genres: String) = "标签: $genres"
    override fun onlineDetailChapters(count: Int) = "章节 ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "汉化组/发布方: $scanlator"
    override val migrateSelectSourceHeader = "选择要迁出的图源："
    override val migrateNoMangaInLibrary = "书架中暂无来自在线图源的漫画。"
    override fun migrateMangaCount(count: Int) = "书架中有 $count 部漫画"
    override val migrateViewManga = "查看漫画"
    override val migrateBackToSources = "返回图源列表"
    override val migrateSelectMangaHeader = "选择要迁移到其他图源的漫画"
    override val migrateAction = "迁移"
    override fun migrateDialogTitle(title: String) = "迁移: $title"
    override val migrateDialogSubtitle = "在目标图源中搜索对应作品，以完整转移阅读进度、书签和分类。"
    override val migrateNoOtherSources = "没有可用于迁移的其他图源。"
    override val migrateTargetSourceLabel = "目标图源:"
    override val migrateSearchPlaceholder = "搜索标题..."
    override val migrateSelectedBadge = "✓ 已选择"
    override val migrateConfirm = "确认迁移"
    override val filterDialogTitle = "图源筛选"
    override val filterReset = "重置"
    override val filterNoAvailable = "该图源没有可用的筛选器。"
    override val filterApply = "应用筛选"
    override val filterAscending = "▲ 升序"
    override val filterDescending = "▼ 降序"
    override val globalSearchEnterQuery = "输入搜索关键词以在所有已安装及内置图源中查找。"
    override val globalSearchNoSources = "没有可用图源或尚未开始搜索。"
    override val globalSearchViewAll = "查看全部"
    override fun globalSearchError(error: String) = "出错了: $error"
    override val globalSearchNoResults = "该图源中无匹配结果"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "自定义下载路径"
    override val settingsDownloadCustomPathPlaceholder = "留空则使用默认路径 (media/downloads)"
    override val settingsDownloadAheadTitle = "阅读时自动预下载"
    override val settingsDownloadAheadDesc = "在阅读当前章节时，自动在后台下载后续未读章节。"
    override val settingsDownloadAheadDisabled = "已禁用"
    override fun settingsDownloadAheadChapters(count: Int) = "后 $count 话"
    override val settingsDeleteReadChaptersTitle = "自动删除已读章节"
    override val settingsDeleteReadChaptersDesc = "章节被标记为已读后，自动删除本地已下载的文件以节省存储空间。"
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "已登录为 $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "退出登录"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "已检查 $checked 部漫画，发现 $newChapters 个新章节"
    override val settingsLibraryUpdateCompleted = "书架更新已完成"
    override fun settingsLibraryUpdateFailed(msg: String) = "更新失败: $msg"
    override val readerUnavailable = "当前运行环境中阅读器不可用。"
    override val readerOpeningChapter = "正在打开章节…"
    override fun importReportTitle(id: Long) = "导入报告 $id"
    override fun importReportManga(inserted: Long, merged: Long) = "漫画：新增 $inserted 部，合并 $merged 部"
    override fun importReportChapters(inserted: Long, merged: Long) = "章节：新增 $inserted 话，合并 $merged 话"
    override fun importReportCategories(count: Long) = "关联分类：$count 个"
    override fun importReportPreferences(imported: Long, skipped: Long) = "偏好设置：导入 $imported 项，跳过 $skipped 项"
    override fun importReportSkipCategories(categories: String) = "跳过分类：$categories"
    override fun importReportCategory(category: String) = "分类：$category"
    override val mangaDetailBackToLibrary = "返回书架"
}

object TraditionalChineseStrings : DesktopStrings {
    override val appName = "Mihon W"

    override fun destinationLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "書架"
        DesktopDestination.Updates -> "更新"
        DesktopDestination.History -> "歷史"
        DesktopDestination.Browse -> "瀏覽"
        DesktopDestination.Downloads -> "下載"
        DesktopDestination.Stats -> "統計"
        DesktopDestination.Settings -> "設定"
        DesktopDestination.About -> "關於"
    }

    override fun destinationShortLabel(destination: DesktopDestination): String = when (destination) {
        DesktopDestination.Library -> "書"
        DesktopDestination.Updates -> "更"
        DesktopDestination.History -> "歷"
        DesktopDestination.Browse -> "覽"
        DesktopDestination.Downloads -> "載"
        DesktopDestination.Stats -> "統"
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
    override fun libraryUnreadCount(count: Int) = "$count 未讀"
    override val libraryAllCategory = "全部"

    // Library Display, Filter & Sort
    override val libraryDisplayMode = "展示模式"
    override val libraryDisplayComfortable = "舒適網格"
    override val libraryDisplayCompact = "緊湊網格"
    override val libraryDisplayCoverOnly = "純封面"
    override val libraryDisplayList = "清單"
    override val libraryGridSize = "封面大小"
    override val libraryFilterAndSort = "篩選與排序"
    override val libraryFilterTab = "篩選"
    override val librarySortTab = "排序"
    override val libraryFilterReset = "重設篩選"
    override val libraryFilterUnread = "未讀"
    override val libraryFilterDownloaded = "已下載"
    override val libraryFilterStarted = "開始閱讀"
    override val libraryFilterCompleted = "已完結"
    override val libraryFilterBookmarked = "已加書籤"
    override val librarySortDefault = "預設"
    override val librarySortAlphabetical = "字母順序"
    override val librarySortLastRead = "最後閱讀"
    override val librarySortLastUpdate = "最後更新"
    override val librarySortUnreadCount = "未讀話數"
    override val librarySortTotalChapters = "總話數"
    override val librarySortDateAdded = "新增時間"
    override val librarySortAscending = "遞增"
    override val librarySortDescending = "遞減"

    // Library Batch Actions
    override val libraryBatchSelect = "多選"
    override fun libraryBatchSelected(count: Int) = "已選擇 $count 項"
    override val libraryBatchSelectAll = "全選"
    override val libraryBatchDeselectAll = "取消全選"
    override val libraryBatchChangeCategory = "設定分類"
    override val libraryBatchMarkRead = "標記已讀"
    override val libraryBatchMarkUnread = "標記未讀"
    override val libraryBatchDownload = "下載"
    override val libraryBatchDownloadNext1 = "下一話"
    override val libraryBatchDownloadNext5 = "下 5 話"
    override val libraryBatchDownloadAllUnread = "全部未讀"
    override val libraryBatchRemove = "移出書架"
    override val libraryBatchRemoveConfirmTitle = "移出所選漫畫"
    override fun libraryBatchRemoveConfirmMessage(count: Int) =
        "確定要將選取的 $count 部漫畫從書架中移出嗎？"
    override val libraryBatchDone = "完成"

    override fun mangaDetailSource(name: String) = "圖源 $name"
    override val mangaDetailStatusOngoing = "連載中"
    override val mangaDetailStatusCompleted = "已完結"
    override val mangaDetailStatusUnknown = "未知"
    override val mangaDetailInLibrary = "移出書架"
    override val mangaDetailAddToLibrary = "加入書架"
    override val mangaDetailCategories = "分類"
    override val mangaDetailTracking = "進度記錄"
    override fun mangaDetailResume(chapter: String) = "繼續閱讀 $chapter"
    override fun mangaDetailStart(chapter: String) = "開始閱讀 $chapter"
    override val mangaDetailNoChapters = "找不到任何章節"
    override val mangaDetailBack = "返回"
    override val mangaDetailChangeCover = "更換封面"
    override val mangaDetailResetCover = "重設封面"
    override val mangaDetailEditInfo = "編輯資訊"
    override val mangaDetailAuthor = "作者"
    override val mangaDetailArtist = "畫師"
    override val mangaDetailStatus = "狀態"
    override val mangaDetailGenres = "標籤"
    override val mangaDetailNotes = "個人筆記"
    override val mangaDetailResetToSource = "恢復圖源預設"
    override val mangaDetailSave = "儲存"

    // Chapters & Chapter Actions
    override val chapters = "章節"
    override val sortSourceOrder = "圖源順序"
    override val sortChapterNumber = "章節編號"
    override val sortUploadDate = "更新時間"
    override val filterUnread = "未讀"
    override val filterRead = "已讀"
    override val filterUnreadOnly = "僅未讀"
    override val filterReadOnly = "僅已讀"
    override val filterDownloaded = "已下載"
    override val filterDownloadedOnly = "僅已下載"
    override val filterNotDownloadedOnly = "僅未下載"
    override val filterBookmarked = "已書籤"
    override val filterBookmarkedOnly = "僅書籤"
    override val filterNotBookmarkedOnly = "僅無書籤"
    override val markAsRead = "標為已讀"
    override val markAsUnread = "標為未讀"
    override val markPreviousAsRead = "將更早章節標為已讀"
    override val downloadChapter = "下載"
    override val downloadNext1 = "下一話"
    override val downloadNext5 = "下 5 話"
    override val downloadNext10 = "下 10 話"
    override val downloadNext25 = "下 25 話"
    override val downloadUnread = "全部未讀"
    override val downloadAll = "全部章節"
    override val readerPreviousChapter = "上一話"
    override val readerNextChapter = "下一話"
    override val readerChapterList = "章節目錄"
    override val readerCurrentChapter = "目前"
    override val readerChapterDrawerSearch = "過濾章節..."
    override val readerChapterDrawerNoResults = "未找到相符章節"
    override val mangaDetailSearchChaptersPlaceholder = "過濾章節..."
    override val deleteDownload = "刪除下載"
    override val bookmarkChapter = "添加書籤"
    override val removeBookmark = "移除書籤"
    override val chapterBatchSelect = "多選章節"
    override fun chapterBatchSelected(count: Int): String = "已選擇 $count 話"
    override val chapterBatchSelectAll = "全選"
    override val chapterBatchInvert = "反選"
    override val chapterBatchBookmark = "加入書籤"
    override val chapterBatchRemoveBookmark = "移除書籤"
    override val chapterBatchMarkAsRead = "標記為已讀"
    override val chapterBatchMarkAsUnread = "標記為未讀"
    override val chapterBatchDownload = "下載"
    override val chapterBatchDeleteDownload = "刪除下載"
    override val mangaDetailCoverView = "檢視封面"
    override val mangaDetailCoverSave = "儲存封面"
    override val mangaDetailCoverSaved = "封面已成功儲存"
    override val mangaNotesTitle = "個人備忘"
    override val mangaNotesEdit = "編輯備忘"
    override val mangaNotesSave = "儲存備忘"
    override val mangaNotesPlaceholder = "新增關於此漫畫的個人筆記或備忘..."

    // Storage & Cache Cleaner
    override val storageCleanerTitle = "資料與儲存管理"
    override val storageCleanerDescription = "管理已下載的漫畫章節與本機圖片快取以釋放儲存空間。"
    override val storageCleanerDownloadSize = "已下載章節佔用"
    override val storageCleanerClearRead = "清理已讀章節下載"
    override val storageCleanerClearReadSuccess = "已清理已讀章節"
    override val storageCleanerClearImageCache = "清理圖片磁碟快取"
    override val storageCleanerClearImageCacheSuccess = "圖片磁碟快取已清理"

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
    override val browseTabExtensions = "擴充套件商店"
    override val browseTabMigration = "圖源遷移"
    override val browseManageRepositories = "套件庫管理"
    override val browseRefresh = "重新整理"
    override val browseInstall = "安裝"
    override val browseUninstall = "解除安裝"
    override val browseInstalledBadge = "已安裝"
    override val browseSearchPlaceholder = "搜尋圖源或擴充套件"
    override val browseNetworkDomainsHeader = "宣告的網路網域:"
    override val browseGlobalSearch = "全域搜尋"
    override val browseInstallFromFile = "本機安裝 (.mext)"
    override val browseUpdateAll = "全部更新"

    override val extensionInfo = "擴充套件資訊"
    override val extensionOpenRepo = "開啟存放庫"
    override val extensionEnableAll = "全部啟用"
    override val extensionDisableAll = "全部停用"
    override val extensionClearCookies = "清除 Cookie"
    override val extensionVersion = "版本"
    override val extensionLanguage = "語言"
    override val extensionAgeRating = "年齡分級"
    override val extensionNsfwShort = "18+"
    override val extensionNsfwWarning = "此擴充套件包含成人（18+）內容。\n建議僅在知情並同意的情況下使用。"
    override val extensionUninstall = "解除安裝"
    override val extensionUpdate = "更新"
    override val extensionTrust = "信任"
    override val extensionRevoke = "撤銷信任"
    override val extensionUntrusted = "未受信任"
    override val extensionObsolete = "已淘汰"
    override val extensionObsoleteWarning = "此擴充功能已淘汰，不再進行維護。建議解除安裝。"
    override val extensionIncognitoMode = "無痕模式"
    override val extensionIncognitoSummary = "此擴充套件的漫畫將不會記錄閱讀記錄"
    override val extensionDebugInfoCopied = "已複製擴充套件偵錯資訊至剪貼簿"
    override val extensionCookiesCleared = "已清除 Cookie"
    override val sourcePreferencesTitle = "圖源偏好設定"
    override val sourcePreferencesEmpty = "此圖源沒有可設定的偏好設定"
    override val sourcePreferencesUnsupported = "此圖源不支援偏好設定"
    override val sourcePreferencesSaveError = "儲存偏好設定失敗"
    override val actionOk = "確定"
    override val actionCancel = "取消"
    override val actionSettings = "設定"

    override val readerModeSingleLtr = "單頁式（從左到右）"
    override val readerModeSingleRtl = "單頁式（從右到左）"
    override val readerModeWebtoon = "條漫"
    override val readerScaleFitScreen = "符合螢幕"
    override val readerScaleFitWidth = "符合寬度"
    override val readerScaleFitHeight = "符合高度"
    override val readerScaleOriginal = "原始大小"
    override val readerEscapeHint = "按 Esc 結束全螢幕"
    override val readerColorFilter = "色彩濾鏡"
    override val readerFilterNone = "無"
    override val readerFilterInvert = "反色"
    override val readerFilterGrayscale = "黑白灰階"
    override val readerFilterInvertGrayscale = "反轉灰階"
    override val readerFilterSepia = "暖色懷舊"
    override val readerFilterNight = "夜間微光"
    override val readerBackgroundColor = "閱讀背景色"
    override val readerBgDarkGray = "深灰"
    override val readerBgBlack = "純黑"
    override val readerBgWhite = "純白"
    override val readerBgWarmCream = "暖米白"
    override val readerCropBorders = "智慧裁切白邊"
    override val readerCropBordersWebtoon = "智慧裁切白邊 (條漫)"
    override val readerWebtoonMaxWidth = "條漫最大寬度限制"
    override val readerWebtoonSidePadding = "條漫側邊距"

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
    override val settingsAppThemeTitle = "應用程式主題配色"
    override val settingsThemeAmoledTitle = "純黑 AMOLED 深色模式"
    override val settingsThemeAmoledSubtitle = "在深色模式下使用純黑 (#000000) 背景與表面"
    override fun appThemeName(theme: DesktopAppTheme) = when (theme) {
        DesktopAppTheme.DEFAULT -> "Mihon 經典藍綠"
        DesktopAppTheme.GREEN_APPLE -> "青蘋果 (Green Apple)"
        DesktopAppTheme.CATPPUCCIN -> "Catppuccin 萌系紫"
        DesktopAppTheme.TOKYONIGHT -> "東京夜景 (Tokyo Night)"
        DesktopAppTheme.LAVENDER -> "薰衣草 (Lavender)"
        DesktopAppTheme.MIDNIGHT_DUSK -> "午夜暮色 (Midnight Dusk)"
        DesktopAppTheme.NORD -> "北歐冷藍 (Nord)"
        DesktopAppTheme.STRAWBERRY_DAIQUIRI -> "草莓甜心 (Strawberry)"
        DesktopAppTheme.TAKO -> "Tako 觸手紫"
        DesktopAppTheme.TEALTURQUOISE -> "青綠湖水 (Teal Turquoise)"
        DesktopAppTheme.TIDAL_WAVE -> "潮汐深海 (Tidal Wave)"
        DesktopAppTheme.YINYANG -> "陰陽高對比 (Yin & Yang)"
        DesktopAppTheme.YOTSUBA -> "四葉草暖橙 (Yotsuba)"
        DesktopAppTheme.MONOCHROME -> "極簡單色 (Monochrome)"
    }

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

    // Incognito mode
    override val incognitoTitle = "無痕模式"
    override val incognitoBannerText = "無痕模式已啟用：閱讀歷史與跟蹤同步已暫停。"
    override val incognitoDisable = "關閉"
    override val incognitoDescription = "閱讀漫畫時暫停記錄閱讀歷史並阻斷外部跟蹤器同步。"

    // Stats
    override val statsTitle = "統計中心"
    override val statsOverview = "總覽"
    override val statsTotalManga = "書架漫畫數"
    override val statsReadDuration = "總閱讀時長"
    override val statsCompletedManga = "已完結作品"
    override val statsChapters = "章節統計"
    override val statsTotalChapters = "總章節數"
    override val statsReadChapters = "已讀章節"
    override val statsUnreadChapters = "未讀章節"
    override val statsReadPercentage = "閱讀完成率"
    override val statsTopGenres = "題材偏好"
    override val statsNoGenres = "暫無題材數據"
    override val statsStatuses = "連載狀態分布"
    override val statsStatusOngoing = "連載中"
    override val statsStatusCompleted = "已完結"
    override val statsStatusHiatus = "休刊中"
    override val statsStatusCancelled = "已取消"
    override val statsStatusUnknown = "未知"
    override val statsReadingProgress = "閱讀進度分布"
    override val statsProgressUnread = "未開始"
    override val statsProgressInProgress = "閱讀中"
    override val statsProgressFinished = "已讀完"
    override val statsCategories = "分類分布"
    override val statsNoCategories = "未配置分類"
    override val statsTrackers = "跟蹤統計"
    override val statsTrackedTitles = "已追蹤作品"
    override val statsMeanScore = "平均評分"
    override val statsActiveTrackers = "活躍跟蹤器"
    override val statsRefresh = "刷新"

    // Backup Automation
    override val backupAutoTitle = "自動定時備份"
    override val backupAutoDescription = "在後台自動導出並修剪清理定期的漫畫備份。"
    override val backupInterval = "備份週期"
    override val backupIntervalOff = "關閉"
    override val backupInterval6Hours = "每 6 小時"
    override val backupInterval12Hours = "每 12 小時"
    override val backupIntervalDaily = "每天"
    override val backupInterval2Days = "每 2 天"
    override val backupIntervalWeekly = "每週"
    override val backupLocation = "備份存儲目錄"
    override val backupLocationDefault = "默認目錄 (backups/)"
    override val backupRetention = "最大保留備份數"
    override val backupLastAutoBackup = "上次自動備份："
    override val backupNever = "從不"
    override val backupNow = "立即備份"

    // Library Update & Automation
    override val libraryUpdateTitle = "書架更新"
    override val libraryUpdateNow = "立即檢查書架更新"
    override val libraryUpdating = "正在檢查並同步書架更新..."
    override val libraryUpdateInterval = "自動更新頻率"
    override val libraryUpdateIntervalManual = "僅手動"
    override val libraryUpdateInterval6Hours = "每 6 小時"
    override val libraryUpdateInterval12Hours = "每 12 小時"
    override val libraryUpdateIntervalDaily = "每天"
    override val libraryUpdateInterval2Days = "每 2 天"
    override val libraryUpdateIntervalWeekly = "每週"
    override val libraryUpdateSkipCompleted = "跳過已完結作品"
    override val libraryUpdateSkipUnread = "跳過存在未讀章節的作品"
    override val libraryAutoDownloadNew = "自動下載新更新章節"
    override val notificationsDesktopEnabled = "發現新章節時推送桌面系統通知"
    override val libraryLastUpdate = "上次書架更新："

    // Web & Cookie Management
    override val cookieManagerTitle = "Cookie 與網絡穿透管理器"
    override val cookieManagerDescription = "管理圖源域名的 Cookie 與專屬 User-Agent，輕鬆穿透 Cloudflare 5 秒盾與防爬挑戰。"
    override val cookieManagerButton = "打開 Cookie 管理器"
    override val openInBrowser = "在瀏覽器中打開"

    // Categories
    override val categoryManageTitle = "分類管理"
    override val categoryNewNameLabel = "新分類名稱"
    override val categoryAdd = "新增"
    override val categoryEmpty = "暫無自訂分類"
    override val categoryRename = "重新命名"
    override val categoryDelete = "刪除"
    override val categoryRenameTitle = "重新命名分類"
    override val categoryNameLabel = "分類名稱"
    override val categorySave = "儲存"
    override val categorySetTitle = "設定分類"
    override val categoryNoneExist = "尚無任何分類，請先建立分類。"
    override val categoryNoneCreated = "尚未建立自訂分類。"

    // Edit Manga Info
    override val mangaDetailTitleLabel = "標題"
    override val mangaDetailStatusLicensed = "已授權"
    override val mangaDetailStatusPublishingFinished = "已完結(刊登結束)"
    override val mangaDetailGenresLabel = "標籤/題材 (以英文逗號分隔)"
    override val mangaDetailDescriptionLabel = "簡介"
    override val mangaDetailSaveSuccess = "儲存變更"
    override fun mangaDetailStatusOption(status: Long) = when (status) {
        1L -> mangaDetailStatusOngoing
        2L -> mangaDetailStatusCompleted
        3L -> mangaDetailStatusLicensed
        4L -> mangaDetailStatusPublishingFinished
        5L -> statsStatusCancelled
        6L -> statsStatusHiatus
        else -> mangaDetailStatusUnknown
    }

    // Cookie & Clearance Manager
    override fun cookieConfiguredDomains(count: Int) = "已設定網域 ($count)"
    override val cookieAddDomain = "+ 新增"
    override val cookieNoCustomDomains = "尚未設定自訂網域 Cookie"
    override fun cookieCountSubtitle(count: Int, hasCustomUa: Boolean) =
        "$count 個已設定 Cookie" + if (hasCustomUa) " • 自訂 UA" else ""
    override val cookieDomainLabel = "網域 (例如 mangadex.org)"
    override val cookieRawCookiesLabel = "原始 Cookies (例如 cf_clearance=xxx; token=yyy)"
    override val cookieCustomUaLabel = "自訂 User-Agent (選填)"
    override val cookieDelete = "刪除"
    override val cookieSave = "儲存 Cookie"
    override fun cookieRemovedStatus(domain: String) = "已移除 $domain 的 Cookie"
    override fun cookieSavedStatus(count: Int, domain: String) = "已為 $domain 儲存 $count 個 Cookie"

    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "連線到 $name"
    override fun trackerAuthTitle(name: String) = "在 $name 認證"
    override fun trackerLoginTitle(name: String) = "登入到 $name"
    override val trackerServerUrl = "伺服器位址 (例如 https://komga.example.com)"
    override val trackerUsername = "使用者名稱"
    override val trackerPasswordOrApiKey = "密碼或 API 金鑰"
    override fun trackerAuthUrl(url: String) = "授權網址: $url"
    override val trackerToken = "API 權杖 / 存取權杖"
    override val trackerAccountName = "帳戶名稱 (選填)"
    override val trackerPassword = "密碼"
    override val trackerConnect = "連線"
    override val trackerLogin = "登入"
    override val trackerLoginFailed = "登入失敗，請檢查帳戶、憑證和伺服器位址後重試。"
    override val trackerServerAuthHelp = "使用 API 金鑰時留空使用者名稱；使用密碼時填寫使用者名稱和密碼。"
    override val trackingRequestFailed = "追蹤請求失敗，請檢查網路和登入狀態後重試。"
    override fun trackingTitle(mangaTitle: String) = "進度記錄 - $mangaTitle"
    override val trackingNotTracking = "未記錄"
    override val trackingNotLoggedIn = "未登入"
    override val trackingTrack = "記錄"
    override val trackingEdit = "編輯"
    override val trackingRemove = "解除綁定"
    override fun trackingSearchTitle(name: String) = "搜尋 $name"
    override val trackingSearch = "搜尋"
    override fun trackingTotalChapters(count: Long) = "$count 話"
    override fun trackingEditTitle(title: String) = "編輯紀錄 - $title"
    override val trackingChaptersRead = "已讀話數"
    override val trackingScore = "評分"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "閱讀中"
        TrackStatus.COMPLETED -> "已讀完"
        TrackStatus.ON_HOLD -> "擱置"
        TrackStatus.DROPPED -> "棄坑"
        TrackStatus.PLAN_TO_READ -> "計畫閱讀"
        TrackStatus.REREADING -> "重讀中"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "全螢幕"
    override val readerBorderless = "無邊框"
    override val readerShortcutsTitle = "快捷鍵速查"
    override val readerShortcutsHelp = "快捷鍵"
    override fun readerCropToggle(active: Boolean) = if (active) "裁切白邊: 開" else "裁切白邊: 關"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "封面偏移: 開" else "封面偏移: 關"
    override val readerModeDualLtr = "雙頁拼合（從左到右）"
    override val readerModeDualRtl = "雙頁拼合（從右到左）"
    override val readerModeVertical = "連續垂直"
    override fun readerModeLabel(mode: ReadingMode) = when (mode) {
        ReadingMode.SINGLE_LTR -> readerModeSingleLtr
        ReadingMode.SINGLE_RTL -> readerModeSingleRtl
        ReadingMode.DUAL_LTR -> readerModeDualLtr
        ReadingMode.DUAL_RTL -> readerModeDualRtl
        ReadingMode.VERTICAL -> readerModeVertical
        ReadingMode.WEBTOON -> readerModeWebtoon
    }
    override fun readerScaleLabel(scale: ScaleMode) = when (scale) {
        ScaleMode.ORIGINAL -> readerScaleOriginal
        ScaleMode.FIT_WIDTH -> readerScaleFitWidth
        ScaleMode.FIT_HEIGHT -> readerScaleFitHeight
    }
    override fun readerFilterLabel(filter: ReaderColorFilter) = when (filter) {
        ReaderColorFilter.NONE -> readerFilterNone
        ReaderColorFilter.INVERT -> readerFilterInvert
        ReaderColorFilter.GRAYSCALE -> readerFilterGrayscale
        ReaderColorFilter.INVERT_GRAYSCALE -> readerFilterInvertGrayscale
        ReaderColorFilter.SEPIA -> readerFilterSepia
        ReaderColorFilter.NIGHT -> readerFilterNight
    }
    override fun readerBackgroundLabel(bg: ReaderBackgroundColor) = when (bg) {
        ReaderBackgroundColor.DARK_GRAY -> readerBgDarkGray
        ReaderBackgroundColor.BLACK -> readerBgBlack
        ReaderBackgroundColor.WHITE -> readerBgWhite
        ReaderBackgroundColor.WARM_CREAM -> readerBgWarmCream
    }
    override val readerSettingsDialogTitle = "閱讀器設定"
    override val readerClickRegions = "點擊翻頁區域"
    override val readerRegionLeft = "左側"
    override val readerRegionCenter = "中間"
    override val readerRegionRight = "右側"
    override fun readerActionLabel(action: ReaderClickAction) = when (action) {
        ReaderClickAction.PREVIOUS -> "上一頁"
        ReaderClickAction.NEXT -> "下一頁"
        ReaderClickAction.TOGGLE_CHROME -> "切換選單"
        ReaderClickAction.NONE -> "無動作"
    }
    override fun readerLeftBoundary(percent: Int) = "左側邊界 $percent%"
    override fun readerCenterBoundary(percent: Int) = "中間邊界 $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "滾輪: 捲動頁面"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "滾輪: 翻頁"
    }
    override val readerReserveCover = "雙頁模式下保留封面獨立單頁"
    override val readerCropBordersPaged = "智慧裁切白邊 (單頁/雙頁)"
    override val readerWebtoonLayout = "條漫排版"
    override val readerWebtoonWidthFull = "最大寬度: 全螢幕"
    override fun readerWebtoonWidthDp(width: Int) = "最大寬度: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "側邊距: $padding%"
    override val readerLoadingChapter = "正在載入章節…"
    override val readerClosed = "閱讀器已關閉"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "找不到此頁面，可能已被移動或刪除。"
        is ReaderFailure.UnsupportedFormat -> "不支援此章節格式。"
        is ReaderFailure.RemoteImage -> "頁面下載失敗。\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "不支援加密的章節壓縮包。"
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "本機章節已不可用，請重新匯入或檢查檔案位置。"
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "頁面檔案損毀或無法讀取。"
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "不支援此圖片格式。"
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "章節內容超出安全閱讀限制。"
        is ReaderFailure.EmptyChapter -> "此章節不包含任何可讀取的頁面。"
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "此章節不包含任何可讀取的頁面。"
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "圖源或本機章節不可用，請定位或重新匯入。"
            ReaderErrorCode.PAGE_NOT_FOUND -> "找不到此頁面。"
            ReaderErrorCode.PAGE_DECODE_FAILED -> "頁面解碼失敗。"
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "閱讀器已達記憶體佔用上限。"
            ReaderErrorCode.INVALID_PROGRESS -> "儲存的閱讀進度無效。"
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "圖源語言: " + lang.uppercase()
    override val browseSourcePopular = "熱門"
    override val browseSourceLatest = "最新"
    override val browseSearchTitlesPlaceholder = "搜尋漫畫標題..."
    override val browseSearchButton = "搜尋"
    override fun browseFiltersButton(count: Int) = if (count > 0) "篩選 ($count)" else "篩選"
    override val browseNoMangaFound = "找不到任何漫畫"
    override val browseInLibraryBadge = "已在書架"
    override val browsePrevPage = "上一頁"
    override fun browsePageNumber(page: Int) = "第 $page 頁"
    override val browseNextPage = "下一頁"
    override fun browseInstallExtensionTitle(name: String) = "安裝擴充套件: $name"
    override fun browsePackageLabel(pkg: String) = "套件名稱: $pkg"
    override fun browseVersionLabel(version: String) = "版本: $version"
    override fun browseLanguageLabel(lang: String) = "語言: $lang"
    override val browseNetworkPermissionNotice = "安裝擴充套件前請確認其網路宣告權限。"
    override val browseTrustAndInstall = "信任並安裝"
    override val browseAddRepository = "新增"
    override val browseConfiguredRepositories = "已設定的套件庫:"
    override val browseRemoveRepository = "移除"
    override val browseSourceBrowse = "瀏覽"
    override val browsePin = "☆ 固定"
    override val browseUnpin = "★ 取消固定"
    override val browseUpdate = "更新"
    override val browseDisable = "停用"
    override val browseEnable = "啟用"
    override fun onlineDetailSource(name: String, lang: String) = "圖源: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "作者: $author"
    override fun onlineDetailArtist(artist: String) = "畫師: $artist"
    override fun onlineDetailGenres(genres: String) = "標籤: $genres"
    override fun onlineDetailChapters(count: Int) = "章節 ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "漢化組/發布方: $scanlator"
    override val migrateSelectSourceHeader = "選擇要遷出的圖源："
    override val migrateNoMangaInLibrary = "書架中暫無來自線上圖源的漫畫。"
    override fun migrateMangaCount(count: Int) = "書架中有 $count 部漫畫"
    override val migrateViewManga = "查看漫畫"
    override val migrateBackToSources = "返回圖源清單"
    override val migrateSelectMangaHeader = "選擇要遷移到其他圖源的漫畫"
    override val migrateAction = "遷移"
    override fun migrateDialogTitle(title: String) = "遷移: $title"
    override val migrateDialogSubtitle = "在目標圖源中搜尋對應作品，以轉移閱讀進度、書籤和分類。"
    override val migrateNoOtherSources = "沒有可用於遷移的其他圖源。"
    override val migrateTargetSourceLabel = "目標圖源:"
    override val migrateSearchPlaceholder = "搜尋標題..."
    override val migrateSelectedBadge = "✓ 已選取"
    override val migrateConfirm = "確認遷移"
    override val filterDialogTitle = "圖源篩選"
    override val filterReset = "重設"
    override val filterNoAvailable = "該圖源沒有可用的篩選器。"
    override val filterApply = "套用篩選"
    override val filterAscending = "▲ 遞增"
    override val filterDescending = "▼ 遞減"
    override val globalSearchEnterQuery = "輸入關鍵字在所有已安裝及內建圖源中尋找。"
    override val globalSearchNoSources = "沒有可用圖源或尚未開始搜尋。"
    override val globalSearchViewAll = "查看全部"
    override fun globalSearchError(error: String) = "發生錯誤: $error"
    override val globalSearchNoResults = "該圖源中無相符結果"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "自訂下載路徑"
    override val settingsDownloadCustomPathPlaceholder = "留空則使用預設路徑 (media/downloads)"
    override val settingsDownloadAheadTitle = "閱讀時自動預先下載"
    override val settingsDownloadAheadDesc = "在閱讀當前章節時，自動在背景下載後續未讀章節。"
    override val settingsDownloadAheadDisabled = "已停用"
    override fun settingsDownloadAheadChapters(count: Int) = "後 $count 話"
    override val settingsDeleteReadChaptersTitle = "自動刪除已讀章節"
    override val settingsDeleteReadChaptersDesc = "章節被標記為已讀後，自動刪除本機已下載的檔案以節省空間。"
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "已登入為 $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "登出"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "已檢查 $checked 部作品，發現 $newChapters 個新章節"
    override val settingsLibraryUpdateCompleted = "書架更新已完成"
    override fun settingsLibraryUpdateFailed(msg: String) = "更新失敗: $msg"
    override val readerUnavailable = "目前執行環境中閱讀器無法使用。"
    override val readerOpeningChapter = "正在開啟章節…"
    override fun importReportTitle(id: Long) = "匯入報告 $id"
    override fun importReportManga(inserted: Long, merged: Long) = "漫畫：新增 $inserted 部，合併 $merged 部"
    override fun importReportChapters(inserted: Long, merged: Long) = "章節：新增 $inserted 話，合併 $merged 話"
    override fun importReportCategories(count: Long) = "關聯分類：$count 個"
    override fun importReportPreferences(imported: Long, skipped: Long) = "偏好設定：匯入 $imported 項，略過 $skipped 項"
    override fun importReportSkipCategories(categories: String) = "略過分類：$categories"
    override fun importReportCategory(category: String) = "分類：$category"
    override val mangaDetailBackToLibrary = "返回書架"
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
