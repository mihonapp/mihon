package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailUiState

internal const val DESKTOP_MAIN_HEADLINE_TEST_TAG = "desktop-main-headline"
internal const val DESKTOP_NAVIGATION_RAIL_TEST_TAG = "desktop-navigation-rail"

@Composable
fun DesktopShell(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    libraryState: LibraryUiState = LibraryUiState(),
    mangaDetailState: MangaDetailUiState = MangaDetailUiState(),
    onLibraryQueryChange: (String) -> Unit = {},
    onMangaSelected: (Long) -> Unit = {},
    onBackFromMangaDetail: () -> Unit = {},
    onReadChapter: (Long) -> Unit = {},
    onMangaDetailRetry: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onImportLocal: () -> Unit = {},
    onLibraryRetry: () -> Unit = {},
    // Downloads
    downloadsQueue: List<mihon.desktop.download.DesktopDownload> = emptyList(),
    isDownloaderRunning: Boolean = false,
    downloadSpeedBytesPerSec: Double = 0.0,
    onPauseAllDownloads: () -> Unit = {},
    onResumeAllDownloads: () -> Unit = {},
    onClearCompletedDownloads: () -> Unit = {},
    onCancelDownload: (Long) -> Unit = {},
    onRetryDownload: (Long) -> Unit = {},
    // Updates
    updatedChapters: List<mihon.desktop.updates.UpdatedChapterItem> = emptyList(),
    isUpdatingLibrary: Boolean = false,
    lastUpdateResult: mihon.desktop.updates.LibraryUpdateResult? = null,
    onCheckForUpdates: () -> Unit = {},
    // Browse
    browseContent: (@Composable () -> Unit)? = null,
    // History
    historyGroups: List<mihon.desktop.history.DesktopHistoryGroup> = emptyList(),
    historyQuery: String = "",
    onHistoryQueryChange: (String) -> Unit = {},
    onDeleteHistoryItem: (Long) -> Unit = {},
    onClearAllHistory: () -> Unit = {},
    // Category & Tracking
    onCategorySelected: (Long) -> Unit = {},
    onManageCategories: () -> Unit = {},
    onEditMangaCategories: () -> Unit = {},
    onOpenTracking: () -> Unit = {},
    // Library Phase 11 callbacks
    onDisplayModeChange: (mihon.desktop.ui.library.LibraryDisplayMode) -> Unit = {},
    onGridSizeChange: (Float) -> Unit = {},
    onOpenFilterDialog: () -> Unit = {},
    onCloseFilterDialog: () -> Unit = {},
    onFilterChange: (mihon.desktop.ui.library.LibraryFilterState) -> Unit = {},
    onSortChange: (mihon.desktop.ui.library.LibrarySortState) -> Unit = {},
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onToggleMangaSelection: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onBatchChangeCategories: () -> Unit = {},
    onBatchSetCategories: (List<Long>) -> Unit = {},
    onBatchCloseCategoryDialog: () -> Unit = {},
    onBatchMarkRead: (Boolean) -> Unit = {},
    onBatchDownload: (Int) -> Unit = {},
    onBatchRemoveFromLibrary: () -> Unit = {},
    // Settings & Diagnostics
    preferenceStore: mihon.desktop.preferences.DesktopPreferenceStore? = null,
    readerSettingsStore: mihon.desktop.reader.DesktopReaderSettingsStore? = null,
    diagnosticService: mihon.desktop.diagnostics.DiagnosticBundleService? = null,
    trackerManager: mihon.desktop.track.DesktopTrackerManager? = null,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    onExportBackup: () -> Unit = {},
    onPreferencesChanged: ((mihon.desktop.preferences.DesktopPreferences) -> Unit)? = null,
    // Phase 14: Stats & Incognito
    statsData: mihon.desktop.stats.DesktopStatsData = mihon.desktop.stats.DesktopStatsData(),
    onRefreshStats: () -> Unit = {},
    incognitoMode: Boolean = false,
    onToggleIncognito: () -> Unit = {},
    // Phase 15: Library update & Cookie management
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    cookieStore: mihon.desktop.extension.DesktopCookieStore? = null,
    onUpdateLibrary: () -> Unit = {},
    // Phase 16: Manga edit info, chapter filter/sort & actions, storage cleaner
    onEditInfo: () -> Unit = {},
    onDismissEditInfo: () -> Unit = {},
    onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onResetMangaInfo: () -> Unit = {},
    onChapterFilterChange: (mihon.desktop.ui.library.ChapterFilterState) -> Unit = {},
    onChapterSortChange: (mihon.desktop.ui.library.ChapterSortState) -> Unit = {},
    onToggleBookmark: (Long) -> Unit = {},
    onToggleRead: (Long) -> Unit = {},
    onMarkPreviousRead: (Long) -> Unit = {},
    onDownloadChapter: (Long) -> Unit = {},
    onDeleteDownload: (Long) -> Unit = {},
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: java.nio.file.Path? = null,
    diskCacheDir: java.nio.file.Path? = null,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var isCookieManagerOpen by remember { mutableStateOf(false) }
    val primary = DesktopDestination.entries.take(5)
    val secondary = DesktopDestination.entries.drop(5)
    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail(
                modifier = Modifier.fillMaxHeight()
                    .width(80.dp)
                    .testTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        primary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                    Column {
                        HorizontalDivider()
                        secondary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                if (incognitoMode) {
                    Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("incognito-banner"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.incognitoBannerText,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            androidx.compose.material3.Button(
                                onClick = onToggleIncognito,
                                modifier = Modifier.testTag("incognito-disable-button"),
                            ) {
                                Text(strings.incognitoDisable)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    when (selected) {
                        DesktopDestination.Library -> {
                            LibraryScreen(
                                state = libraryState,
                                detailState = mangaDetailState,
                                onQueryChange = onLibraryQueryChange,
                                onMangaSelected = onMangaSelected,
                                onBackFromDetail = onBackFromMangaDetail,
                                onReadChapter = onReadChapter,
                                onDetailRetry = onMangaDetailRetry,
                                onImportBackup = onImportBackup,
                                onImportLocal = onImportLocal,
                                onRetry = onLibraryRetry,
                                onCategorySelected = onCategorySelected,
                                onManageCategories = onManageCategories,
                                onEditMangaCategories = onEditMangaCategories,
                                onOpenTracking = onOpenTracking,
                                onDisplayModeChange = onDisplayModeChange,
                                onGridSizeChange = onGridSizeChange,
                                onOpenFilterDialog = onOpenFilterDialog,
                                onCloseFilterDialog = onCloseFilterDialog,
                                onFilterChange = onFilterChange,
                                onSortChange = onSortChange,
                                onToggleSelectionMode = onToggleSelectionMode,
                                onToggleMangaSelection = onToggleMangaSelection,
                                onSelectAll = onSelectAll,
                                onDeselectAll = onDeselectAll,
                                onBatchChangeCategories = onBatchChangeCategories,
                                onBatchSetCategories = onBatchSetCategories,
                                onBatchCloseCategoryDialog = onBatchCloseCategoryDialog,
                                onBatchMarkRead = onBatchMarkRead,
                                onBatchDownload = onBatchDownload,
                                onBatchRemoveFromLibrary = onBatchRemoveFromLibrary,
                                isUpdatingLibrary = isUpdatingLibrary,
                                onUpdateLibrary = onUpdateLibrary,
                                onEditInfo = onEditInfo,
                                onDismissEditInfo = onDismissEditInfo,
                                onSaveMangaInfo = onSaveMangaInfo,
                                onResetMangaInfo = onResetMangaInfo,
                                onChapterFilterChange = onChapterFilterChange,
                                onChapterSortChange = onChapterSortChange,
                                onToggleBookmark = onToggleBookmark,
                                onToggleRead = onToggleRead,
                                onMarkPreviousRead = onMarkPreviousRead,
                                onDownloadChapter = onDownloadChapter,
                                onDeleteDownload = onDeleteDownload,
                            )
                        }
                        DesktopDestination.History -> {
                            mihon.desktop.ui.history.HistoryScreen(
                                groups = historyGroups,
                                query = historyQuery,
                                onQueryChange = onHistoryQueryChange,
                                onReadChapter = onReadChapter,
                                onDeleteItem = onDeleteHistoryItem,
                                onClearAll = onClearAllHistory,
                            )
                        }
                        DesktopDestination.Downloads -> {
                            mihon.desktop.ui.tasks.DownloadsScreen(
                                queue = downloadsQueue,
                                isRunning = isDownloaderRunning,
                                speedBytesPerSec = downloadSpeedBytesPerSec,
                                onPauseAll = onPauseAllDownloads,
                                onResumeAll = onResumeAllDownloads,
                                onClearCompleted = onClearCompletedDownloads,
                                onCancel = onCancelDownload,
                                onRetry = onRetryDownload,
                            )
                        }
                        DesktopDestination.Updates -> {
                            mihon.desktop.ui.updates.UpdatesScreen(
                                updatedChapters = updatedChapters,
                                isUpdating = isUpdatingLibrary,
                                lastResult = lastUpdateResult,
                                onCheckForUpdates = onCheckForUpdates,
                                onReadChapter = onReadChapter,
                            )
                        }
                        DesktopDestination.Settings -> {
                            if (preferenceStore != null && readerSettingsStore != null) {
                                mihon.desktop.ui.settings.SettingsScreen(
                                    preferenceStore = preferenceStore,
                                    readerSettingsStore = readerSettingsStore,
                                    diagnosticService = diagnosticService,
                                    trackerManager = trackerManager,
                                    backupScheduler = backupScheduler,
                                    updateScheduler = updateScheduler,
                                    onOpenCookieManager = { isCookieManagerOpen = true },
                                    onImportBackup = onImportBackup,
                                    onExportBackup = onExportBackup,
                                    onPreferencesChanged = onPreferencesChanged,
                                    downloadCacheCleaner = downloadCacheCleaner,
                                    downloadsDir = downloadsDir,
                                    diskCacheDir = diskCacheDir,
                                )
                            } else {
                                Text(
                                    text = strings.destinationLabel(selected),
                                    modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                        DesktopDestination.Stats -> {
                            mihon.desktop.ui.stats.StatsScreen(
                                data = statsData,
                                onRefresh = onRefreshStats,
                            )
                        }
                        DesktopDestination.About -> {
                            mihon.desktop.ui.settings.AboutScreen()
                        }
                        DesktopDestination.Browse -> {
                            browseContent?.invoke() ?: Text(
                                text = strings.destinationLabel(selected),
                                modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }

                    if (isCookieManagerOpen && cookieStore != null) {
                        mihon.desktop.ui.network.CookieManagerDialog(
                            cookieStore = cookieStore,
                            onDismissRequest = { isCookieManagerOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationItem(
    destination: DesktopDestination,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    NavigationRailItem(
        selected = destination == selected,
        onClick = { onDestinationSelected(destination) },
        icon = {
            Icon(
                imageVector = destinationIcon(destination),
                contentDescription = strings.destinationLabel(destination),
            )
        },
        label = { Text(strings.destinationLabel(destination)) },
        alwaysShowLabel = false,
    )
}
