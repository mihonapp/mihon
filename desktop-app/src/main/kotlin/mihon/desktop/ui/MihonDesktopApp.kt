package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.window.ReaderWindowController
import mihon.desktop.reader.window.ReaderWindowEscape
import mihon.desktop.track.toDesktopTrackRecord
import mihon.desktop.track.toTrackingRecord
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.track.TrackingDialog
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Frame
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val screen = remember { ScreenBounds(0, 0, screenSize.width, screenSize.height) }
    val savedPlacement = remember(preferences.windowPlacement) {
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false)).sanitize(screen)
    }
    val navigator = remember {
        DesktopNavigator(preferences.lastDestination) { destination ->
            preferences = preferences.copy(lastDestination = destination)
            runtime.preferences.save(preferences)
        }
    }
    val windowState = rememberWindowState(
        placement = if (savedPlacement.maximized) {
            ComposeWindowPlacement.Maximized
        } else {
            ComposeWindowPlacement.Floating
        },
        position = WindowPosition(savedPlacement.x.dp, savedPlacement.y.dp),
        width = savedPlacement.width.dp,
        height = savedPlacement.height.dp,
    )
    var composeWindow: ComposeWindow? by remember { mutableStateOf(null) }
    var readerWindowMode by remember { mutableStateOf(ReaderWindowMode.NORMAL) }
    val readerWindowController = remember(savedPlacement) { ReaderWindowController(savedPlacement) }

    fun currentWindowPlacement(): WindowPlacement = composeWindow?.let { window ->
        WindowPlacement(
            x = window.x,
            y = window.y,
            width = window.width,
            height = window.height,
            maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
        )
    } ?: readerWindowController.normalBounds

    fun applyReaderWindowMode(mode: ReaderWindowMode) {
        readerWindowMode = mode
        when (mode) {
            ReaderWindowMode.FULLSCREEN -> windowState.placement = ComposeWindowPlacement.Fullscreen
            ReaderWindowMode.BORDERLESS -> windowState.placement = ComposeWindowPlacement.Floating
            ReaderWindowMode.NORMAL -> {
                val bounds = readerWindowController.normalBounds.sanitize(screen)
                windowState.placement = ComposeWindowPlacement.Floating
                windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
            }
        }
        val settingsStore = DesktopReaderSettingsStore(runtime.preferences)
        settingsStore.save(settingsStore.load().copy(lastWindowMode = mode))
    }

    fun transitionReaderWindow(mode: ReaderWindowMode) {
        readerWindowController.transition(mode, currentWindowPlacement())
        applyReaderWindowMode(mode)
    }

    fun handleReaderEscape() {
        when (readerWindowController.onEscape(currentWindowPlacement())) {
            ReaderWindowEscape.ReturnedToNormal -> applyReaderWindowMode(ReaderWindowMode.NORMAL)
            ReaderWindowEscape.CloseReader -> navigator.back()
        }
    }
    val presenterScope = rememberCoroutineScope()
    val libraryPresenter = remember(runtime.library) {
        LibraryPresenter(
            repository = runtime.library,
            scope = presenterScope,
            preferences = runtime.preferences,
            downloader = runtime.downloader,
        )
    }
    val libraryState by libraryPresenter.state.collectAsState()
    val mangaDetailState by libraryPresenter.detailState.collectAsState()
    val importController = remember(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot) {
        LibraryImportController(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot)
    }
    val importActions = remember(importController) {
        LibraryImportActions(
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }
    var importState: ImportActionState by remember { mutableStateOf(ImportActionState.Idle) }
    var isTrackingDialogOpen by remember { mutableStateOf(false) }
    DisposableEffect(libraryPresenter) {
        onDispose(libraryPresenter::close)
    }

    val downloader = runtime.downloader
    val downloadsQueue by (
        downloader?.queueState ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }
        ).collectAsState()
    val isDownloaderRunning by (
        downloader?.isRunning ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(false)
        }
        ).collectAsState()
    val downloadSpeed by (
        downloader?.speedBytesPerSec ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(0.0)
        }
        ).collectAsState()

    val isUpdatingLibrary by (
        runtime.libraryUpdateScheduler?.isUpdating
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    val lastUpdateResult by remember {
        kotlinx.coroutines.flow.MutableStateFlow(null)
    }.collectAsState()

    val historyService = runtime.historyService
    val historyState by (
        historyService?.state ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(mihon.desktop.history.HistoryUiState())
        }
        ).collectAsState()
    val statsService = runtime.statsService
    val statsData by (
        statsService?.stats ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(mihon.desktop.stats.DesktopStatsData())
        }
        ).collectAsState()
    LaunchedEffect(navigator.current) {
        if (navigator.current == DesktopDestination.Stats) {
            statsService?.refresh()
        }
    }
    var exportNotification: String? by remember { mutableStateOf(null) }

    key(readerWindowMode == ReaderWindowMode.BORDERLESS) {
        Window(
            onCloseRequest = {
                composeWindow?.let { window ->
                    preferences = preferences.copy(
                        windowPlacement = WindowPlacement(
                            x = window.x,
                            y = window.y,
                            width = window.width,
                            height = window.height,
                            maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
                        ).sanitize(screen),
                    )
                    runtime.preferences.save(preferences)
                }
                exitApplication()
            },
            state = windowState,
            title = "Mihon W",
            undecorated = readerWindowMode == ReaderWindowMode.BORDERLESS,
            onPreviewKeyEvent = { event ->
                val isReaderEscape = navigator.current is DesktopDestination.Reader &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Escape
                if (isReaderEscape) handleReaderEscape()
                isReaderEscape
            },
        ) {
            SideEffect { composeWindow = window }
            MihonDesktopTheme(preferences.themeMode) {
                mihon.desktop.i18n.ProvideDesktopStrings(preferences.language) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        mihon.desktop.image.LocalImageLoader provides runtime.imageLoader,
                        mihon.desktop.image.LocalCustomCoverManager provides runtime.customCoverManager,
                    ) {
                        val strings = mihon.desktop.i18n.LocalStrings.current
                        val destination = navigator.current
                        LaunchedEffect(destination) {
                            if (destination is DesktopDestination.Reader &&
                                readerWindowController.mode == ReaderWindowMode.NORMAL
                            ) {
                                val savedReaderMode =
                                    DesktopReaderSettingsStore(runtime.preferences).load().lastWindowMode
                                if (savedReaderMode != ReaderWindowMode.NORMAL) {
                                    readerWindowController.restore(savedReaderMode, currentWindowPlacement())
                                    applyReaderWindowMode(savedReaderMode)
                                }
                            }
                        }
                        if (destination is DesktopDestination.Reader) {
                            ReaderDestination(
                                destination = destination,
                                runtime = runtime,
                                mangaTitle = mangaDetailState.manga?.title ?: "Reader",
                                chapterTitle =
                                mangaDetailState.chapters.firstOrNull { it.id == destination.chapterId }?.name
                                    ?: "Chapter ${destination.chapterId}",
                                onBack = {
                                    val chapter = mangaDetailState.chapters.firstOrNull {
                                        it.id == destination.chapterId
                                    }
                                    val manga = mangaDetailState.manga
                                    if (chapter != null && manga != null) {
                                        presenterScope.launch {
                                            val prefs = runtime.preferences.load()
                                            if (!prefs.incognitoMode) {
                                                runtime.trackSyncService?.onChapterRead(manga.id, chapter.chapterNumber)
                                            }
                                            if (prefs.downloadAhead > 0) {
                                                runtime.downloader?.checkAndDownloadAhead(
                                                    manga = manga,
                                                    currentChapter = chapter,
                                                    allChapters = mangaDetailState.chapters,
                                                    count = prefs.downloadAhead,
                                                )
                                            }
                                            if (prefs.deleteDownloadedRead && !prefs.incognitoMode) {
                                                runtime.downloader?.deleteDownloadedChapter(manga, chapter)
                                            }
                                        }
                                    }
                                    transitionReaderWindow(ReaderWindowMode.NORMAL)
                                    navigator.back()
                                },
                                onFullscreen = {
                                    val mode = readerWindowController.toggleFullscreen(currentWindowPlacement())
                                    applyReaderWindowMode(mode)
                                },
                                onBorderless = {
                                    val mode = readerWindowController.toggleBorderless(currentWindowPlacement())
                                    applyReaderWindowMode(mode)
                                },
                                onEscape = ::handleReaderEscape,
                            )
                        } else {
                            DesktopShell(
                                selected = destination as DesktopDestination,
                                onDestinationSelected = navigator::navigate,
                                libraryState = libraryState,
                                mangaDetailState = mangaDetailState,
                                onLibraryQueryChange = libraryPresenter::setQuery,
                                onMangaSelected = libraryPresenter::selectManga,
                                onBackFromMangaDetail = { libraryPresenter.selectManga(null) },
                                onReadChapter = { chapterId ->
                                    navigator.navigate(DesktopDestination.Reader(chapterId))
                                },
                                onMangaDetailRetry = libraryPresenter::retryDetail,
                                onImportBackup = {
                                    presenterScope.launch {
                                        importState = ImportActionState.Running
                                        importState = importActions.chooseAndImportBackup()
                                    }
                                },
                                onImportLocal = {
                                    presenterScope.launch {
                                        importState = ImportActionState.Running
                                        importState = importActions.chooseAndImportLocal()
                                    }
                                },
                                onLibraryRetry = libraryPresenter::retry,
                                onDisplayModeChange = libraryPresenter::setDisplayMode,
                                onGridSizeChange = libraryPresenter::setGridSize,
                                onOpenFilterDialog = { libraryPresenter.setFilterDialogOpen(true) },
                                onCloseFilterDialog = { libraryPresenter.setFilterDialogOpen(false) },
                                onFilterChange = libraryPresenter::setFilterState,
                                onSortChange = libraryPresenter::setSortState,
                                onToggleSelectionMode = libraryPresenter::toggleSelectionMode,
                                onToggleMangaSelection = libraryPresenter::toggleMangaSelection,
                                onSelectAll = libraryPresenter::selectAll,
                                onDeselectAll = libraryPresenter::clearSelection,
                                onBatchChangeCategories = { libraryPresenter.setBatchCategoryDialogOpen(true) },
                                onBatchSetCategories = libraryPresenter::batchSetCategories,
                                onBatchCloseCategoryDialog = { libraryPresenter.setBatchCategoryDialogOpen(false) },
                                onBatchMarkRead = libraryPresenter::batchMarkRead,
                                onBatchDownload = libraryPresenter::batchDownload,
                                onBatchRemoveFromLibrary = libraryPresenter::batchRemoveFromLibrary,
                                downloadsQueue = downloadsQueue,
                                isDownloaderRunning = isDownloaderRunning,
                                downloadSpeedBytesPerSec = downloadSpeed,
                                onPauseAllDownloads = { downloader?.pause() },
                                onResumeAllDownloads = { downloader?.resume() },
                                onClearCompletedDownloads = { downloader?.clearCompleted() },
                                onCancelDownload = { downloader?.cancel(it) },
                                onRetryDownload = { downloader?.retry(it) },
                                isUpdatingLibrary = isUpdatingLibrary,
                                lastUpdateResult = lastUpdateResult,
                                onCheckForUpdates = {
                                    presenterScope.launch { runtime.libraryUpdateScheduler?.triggerUpdateNow() }
                                },
                                // Browse
                                browseContent = {
                                    mihon.desktop.ui.browse.BrowseContentView(
                                        runtime = runtime,
                                        onReadChapter = { chapterId ->
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                    )
                                },
                                // History
                                historyGroups = historyState.groups,
                                historyQuery = historyState.query,
                                onHistoryQueryChange = { historyService?.setQuery(it) },
                                onDeleteHistoryItem = { historyService?.deleteItem(it) },
                                onClearAllHistory = { historyService?.clearAll() },
                                // Settings & Diagnostics
                                preferenceStore = runtime.preferences,
                                readerSettingsStore = remember { DesktopReaderSettingsStore(runtime.preferences) },
                                diagnosticService = runtime.diagnosticService,
                                trackerManager = runtime.trackerManager,
                                backupScheduler = runtime.backupScheduler,
                                updateScheduler = runtime.libraryUpdateScheduler,
                                cookieStore = runtime.cookieStore,
                                onUpdateLibrary = {
                                    presenterScope.launch {
                                        runtime.libraryUpdateScheduler?.triggerUpdateNow()
                                    }
                                },
                                onEditInfo = { libraryPresenter.setEditInfoDialogOpen(true) },
                                onDismissEditInfo = { libraryPresenter.setEditInfoDialogOpen(false) },
                                onSaveMangaInfo = libraryPresenter::updateSelectedMangaInfo,
                                onResetMangaInfo = libraryPresenter::resetSelectedMangaInfo,
                                onChapterFilterChange = libraryPresenter::setChapterFilter,
                                onChapterSortChange = libraryPresenter::setChapterSort,
                                onToggleBookmark = libraryPresenter::toggleChapterBookmark,
                                onToggleRead = libraryPresenter::toggleChapterRead,
                                onMarkPreviousRead = libraryPresenter::markPreviousChaptersRead,
                                onDownloadChapter = libraryPresenter::downloadChapter,
                                onDeleteDownload = libraryPresenter::deleteChapterDownload,
                                downloadCacheCleaner = runtime.downloadCacheCleaner,
                                downloadsDir = runtime.downloader?.diskProvider?.downloadsDir
                                    ?: runtime.directories.root.resolve("media").resolve("downloads"),
                                diskCacheDir = runtime.directories.cache,
                                onOpenTracking = { isTrackingDialogOpen = true },
                                onExportBackup = {
                                    presenterScope.launch {
                                        val path = mihon.desktop.ui.library.chooseExportBackup() ?: return@launch
                                        try {
                                            withContext(Dispatchers.IO) {
                                                runtime.backupExporter.export(path)
                                            }
                                            exportNotification = strings.backupExportSuccess(path.toString())
                                        } catch (e: Exception) {
                                            exportNotification = strings.backupExportFailed(e.message ?: "")
                                        }
                                    }
                                },
                                onPreferencesChanged = { preferences = it },
                                // Phase 14: Stats & Incognito
                                statsData = statsData,
                                onRefreshStats = { presenterScope.launch { statsService?.refresh() } },
                                incognitoMode = preferences.incognitoMode,
                                onToggleIncognito = {
                                    val updated = preferences.copy(incognitoMode = !preferences.incognitoMode)
                                    preferences = updated
                                    runtime.preferences.save(updated)
                                },
                            )
                        }
                        val currentTrackingManga = mangaDetailState.manga
                        val trackerMgr = runtime.trackerManager
                        if (isTrackingDialogOpen && currentTrackingManga != null && trackerMgr != null) {
                            val tracksFlow = remember(currentTrackingManga.id) {
                                runtime.library.observeTracking(currentTrackingManga.id)
                            }
                            val tracksList by tracksFlow.collectAsState(initial = emptyList())
                            val desktopTracks = tracksList.map { it.toDesktopTrackRecord() }

                            TrackingDialog(
                                mangaTitle = currentTrackingManga.title,
                                trackers = trackerMgr.trackers,
                                currentTracks = desktopTracks,
                                onDismiss = { isTrackingDialogOpen = false },
                                onSaveTrack = { track ->
                                    val record = track.copy(mangaId = currentTrackingManga.id).toTrackingRecord()
                                    val existing = runtime.library.findTracking(
                                        currentTrackingManga.id,
                                        track.trackerId,
                                    )
                                    if (existing != null) {
                                        runtime.library.updateTracking(record)
                                    } else {
                                        runtime.library.insertTracking(record)
                                    }
                                    presenterScope.launch {
                                        val tracker = trackerMgr.get(track.trackerId)
                                        if (tracker != null && tracker.isLoggedIn) {
                                            try {
                                                tracker.updateRemote(track)
                                            } catch (_: Exception) {
                                                runtime.trackingQueue?.enqueue(track)
                                            }
                                        }
                                    }
                                },
                                onUnbindTrack = { trackerId ->
                                    runtime.library.deleteTracking(currentTrackingManga.id, trackerId)
                                },
                                onSearchTrack = { tracker, query ->
                                    tracker.search(query)
                                },
                            )
                        }
                        ImportStateDialog(importState) { importState = ImportActionState.Idle }
                        exportNotification?.let { msg ->
                            AlertDialog(
                                onDismissRequest = { exportNotification = null },
                                confirmButton = {
                                    TextButton(onClick = { exportNotification = null }) {
                                        Text(strings.dialogOk)
                                    }
                                },
                                title = { Text(strings.backupDialogTitle) },
                                text = { Text(msg) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderDestination(
    destination: DesktopDestination.Reader,
    runtime: DesktopRuntime,
    mangaTitle: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onBorderless: () -> Unit,
    onEscape: () -> Unit,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var session by remember(destination) { mutableStateOf<mihon.reader.session.ReaderSession?>(null) }
    val factory = runtime.readerFactory
    if (factory == null) {
        Text(strings.readerUnavailable)
        return
    }
    LaunchedEffect(destination) {
        val isIncognito = runtime.preferences.load().incognitoMode
        session = withContext(Dispatchers.Default) {
            factory.createSession(isIncognito = isIncognito).also { it.open(destination.chapterId) }
        }
    }
    val activeSession = session
    if (activeSession == null) {
        Column(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(strings.readerOpeningChapter)
        }
    } else {
        ReaderScreen(
            session = activeSession,
            title = mangaTitle,
            chapterTitle = chapterTitle,
            settingsStore = DesktopReaderSettingsStore(runtime.preferences),
            onBack = onBack,
            onFullscreen = onFullscreen,
            onBorderless = onBorderless,
            onEscape = onEscape,
            pageContent = { page, _, modifier ->
                DecodedReaderPage(
                    factory = factory,
                    page = page,
                    foreground = true,
                    modifier = modifier,
                )
            },
        )
    }
}

@Composable
private fun ImportStateDialog(state: ImportActionState, onDismiss: () -> Unit) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    when (state) {
        ImportActionState.Idle -> Unit
        ImportActionState.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(strings.importDialogTitle) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(strings.importDialogProgress)
                }
            },
        )
        is ImportActionState.Completed -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogDone) } },
                title = { Text(strings.importDialogCompleteTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.importReportTitle(result.reportId))
                        Text(strings.importReportManga(result.counts.mangaInserted, result.counts.mangaMerged))
                        Text(
                            strings.importReportChapters(result.counts.chaptersInserted, result.counts.chaptersMerged),
                        )
                        Text(strings.importReportCategories(result.counts.categoriesLinked))
                        Text(
                            strings.importReportPreferences(result.counts.preferencesImported, result.counts.preferencesSkipped),
                        )
                        if (result.skipCategories.isNotEmpty()) {
                            Text(strings.importReportSkipCategories(result.skipCategories.joinToString()))
                        }
                    }
                },
            )
        }
        is ImportActionState.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
            title = { Text(strings.importDialogRejectedTitle) },
            text = { Text(strings.importReportCategory(state.category)) },
        )
        is ImportActionState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
            title = { Text(strings.importDialogFailedTitle) },
            text = { Text(state.message) },
        )
    }
}
