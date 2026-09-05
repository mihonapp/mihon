package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    // Settings & Diagnostics
    preferenceStore: mihon.desktop.preferences.DesktopPreferenceStore? = null,
    readerSettingsStore: mihon.desktop.reader.DesktopReaderSettingsStore? = null,
    diagnosticService: mihon.desktop.diagnostics.DiagnosticBundleService? = null,
    onExportBackup: () -> Unit = {},
) {
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
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
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
                                onImportBackup = onImportBackup,
                                onExportBackup = onExportBackup,
                            )
                        } else {
                            Text(
                                text = selected.label,
                                modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    DesktopDestination.About -> {
                        mihon.desktop.ui.settings.AboutScreen()
                    }
                    DesktopDestination.Browse -> {
                        browseContent?.invoke() ?: Text(
                            text = selected.label,
                            modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        )
                    }
                    else -> {
                        Text(
                            text = selected.label,
                            modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
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
    NavigationRailItem(
        selected = destination == selected,
        onClick = { onDestinationSelected(destination) },
        icon = { Text(destination.shortLabel) },
        label = { Text(destination.label) },
        alwaysShowLabel = false,
    )
}
