package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.window.ReaderWindowController
import mihon.desktop.reader.window.ReaderWindowEscape
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.DesktopAppLockGate
import mihon.desktop.security.UnlockResult
import mihon.desktop.track.toDesktopTrackRecord
import mihon.desktop.track.toTrackingRecord
import mihon.desktop.ui.category.EditMangaCategoriesDialog
import mihon.desktop.ui.category.ManageCategoriesDialog
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.chapterDisplayLabel
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.LibraryChapterBookmarkStore
import mihon.desktop.ui.reader.ReaderChapterTransitionChapter
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.track.TrackingDialog
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Frame
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val appLockController = remember(runtime.preferences) {
        DesktopAppLockController(runtime.preferences)
    }
    val appLocked by appLockController.isLocked.collectAsState()
    LaunchedEffect(appLockController) {
        appLockController.onStartup()
    }
    LaunchedEffect(appLockController, appLocked) {
        while (!appLocked) {
            delay(1_000)
            appLockController.checkIdleTimeout()
        }
    }
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

    fun handleReaderEscape(): Boolean = when (readerWindowController.onEscape(currentWindowPlacement())) {
        ReaderWindowEscape.ReturnedToNormal -> {
            applyReaderWindowMode(ReaderWindowMode.NORMAL)
            false
        }
        ReaderWindowEscape.CloseReader -> true
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
    val sourceNames = remember(runtime.sourceManager, libraryState.items) {
        runCatching {
            runtime.sourceManager.getSources().associate { it.id to it.name }
        }.getOrDefault(emptyMap())
    }
    val upcomingPresenter = remember(runtime.library) {
        UpcomingPresenter(
            repository = runtime.library,
            scope = presenterScope,
            preferences = runtime.preferences,
        )
    }
    val upcomingState by upcomingPresenter.state.collectAsState()
    DisposableEffect(upcomingPresenter) {
        onDispose(upcomingPresenter::close)
    }
    var isUpcomingOpen by remember { mutableStateOf(false) }
    LaunchedEffect(navigator.current) {
        if (navigator.current != DesktopDestination.Updates) {
            isUpcomingOpen = false
        }
    }
    val categoryService = runtime.categoryService
    val categories by (
        categoryService?.categories ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList<DesktopCategory>())
        }
        ).collectAsState()
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
    var isManageCategoriesDialogOpen by remember { mutableStateOf(false) }
    var isEditMangaCategoriesDialogOpen by remember { mutableStateOf(false) }
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
    val lastReport by (
        runtime.libraryUpdateScheduler?.lastReport
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }
        ).collectAsState()
    val lastUpdateResult = remember(lastReport) {
        lastReport?.let { rep ->
            mihon.desktop.updates.LibraryUpdateResult(
                totalMangaChecked = rep.totalMangaChecked,
                mangaWithNewChapters = rep.updatedMangaCount,
                newChaptersFound = rep.newChaptersTotal,
                updatedMangaTitles = rep.results.filter { it.newChapters.isNotEmpty() }.map { it.title },
                errors = rep.errors,
            )
        }
    }
    val allMangaList = libraryState.items
    val updatedChapters = remember(lastReport, allMangaList) {
        lastReport?.results?.flatMap { res ->
            val mangaCover = allMangaList.firstOrNull { it.id == res.mangaId }?.thumbnailUrl
            res.newChapters.map { ch ->
                mihon.desktop.updates.UpdatedChapterItem(
                    mangaId = res.mangaId,
                    chapterId = ch.id,
                    mangaTitle = res.title,
                    chapterName = ch.name,
                    chapterNumber = ch.chapterNumber,
                    dateFetch = ch.dateFetch,
                    mangaThumbnailUrl = mangaCover,
                )
            }
        } ?: emptyList()
    }

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

    val appIcon = remember {
        try {
            val stream = DesktopNavigator::class.java.getResourceAsStream("/icon.png")
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
            stream?.use {
                val bytes = it.readAllBytes()
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap(),
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

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
            icon = appIcon,
            undecorated = readerWindowMode == ReaderWindowMode.BORDERLESS,
            onPreviewKeyEvent = { event ->
                appLockController.recordActivity()
                false
            },
        ) {
            SideEffect { composeWindow = window }
            MihonDesktopTheme(
                themeMode = preferences.themeMode,
                appTheme = preferences.appTheme,
                isAmoled = preferences.themeDarkAmoled,
            ) {
                mihon.desktop.i18n.ProvideDesktopStrings(preferences.language) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        mihon.desktop.image.LocalImageLoader provides runtime.imageLoader,
                        mihon.desktop.image.LocalCustomCoverManager provides runtime.customCoverManager,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().pointerInput(appLockController) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        appLockController.recordActivity()
                                    }
                                }
                            },
                        ) {
                            DesktopAppLockGate(
                                isLocked = appLocked,
                                onUnlock = { pin ->
                                    appLockController.unlock(pin) == UnlockResult.Success
                                },
                                onForgotPinConfirmed = appLockController::disableLock,
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
                                    if (destination != DesktopDestination.Library) {
                                        isManageCategoriesDialogOpen = false
                                        isEditMangaCategoriesDialogOpen = false
                                    }
                                }
                                if (destination is DesktopDestination.Reader) {
                                    val currentChapterId = destination.chapterId
                                    val navigationChapters = if (mangaDetailState.readerChapters.isNotEmpty()) {
                                        mangaDetailState.readerChapters
                                    } else {
                                        mangaDetailState.allChapters
                                    }
                                    val readableChapters = remember(
                                        navigationChapters,
                                        mangaDetailState.readerAvailability,
                                        currentChapterId,
                                    ) {
                                        val available = if (mangaDetailState.readerAvailability.isEmpty()) {
                                            navigationChapters
                                        } else {
                                            navigationChapters.filter {
                                                mangaDetailState.readerAvailability[it.id] ==
                                                    ChapterReaderAvailability.Readable
                                            }
                                        }
                                        if (mangaDetailState.readerChapters.isNotEmpty()) {
                                            available
                                        } else {
                                            available.sortedWith(
                                                compareBy<mihon.desktop.library.model.LibraryChapter> {
                                                    it.chapterNumber
                                                }.thenBy { it.sourceOrder },
                                            )
                                        }
                                    }
                                    val currentChapterIdx = readableChapters.indexOfFirst { it.id == currentChapterId }
                                    val prevChapter = if (currentChapterIdx >
                                        0
                                    ) {
                                        readableChapters[currentChapterIdx - 1]
                                    } else {
                                        null
                                    }
                                    val nextChapter = if (currentChapterIdx in 0 until readableChapters.size - 1) {
                                        readableChapters[currentChapterIdx + 1]
                                    } else {
                                        null
                                    }
                                    val sortedAllChapters = remember(navigationChapters) { navigationChapters }
                                    val transitionCatalog = remember(
                                        sortedAllChapters,
                                        mangaDetailState.readerAvailability,
                                        mangaDetailState.downloadedChapterIds,
                                        mangaDetailState.chapterSettings.displayMode,
                                    ) {
                                        sortedAllChapters.map { chapter ->
                                            ReaderChapterTransitionChapter(
                                                id = chapter.id,
                                                title = chapterDisplayLabel(
                                                    chapter,
                                                    mangaDetailState.chapterSettings.displayMode,
                                                ),
                                                chapterNumber = chapter.chapterNumber,
                                                scanlator = chapter.scanlator,
                                                downloaded = mangaDetailState.downloadedChapterIds.contains(chapter.id),
                                                available = mangaDetailState.readerAvailability.isEmpty() ||
                                                    mangaDetailState.readerAvailability[chapter.id] ==
                                                    ChapterReaderAvailability.Readable,
                                                read = chapter.read,
                                            )
                                        }
                                    }
                                    val transitionCurrent = transitionCatalog.firstOrNull { it.id == currentChapterId }
                                    val transitionPrevious = prevChapter?.let { prev ->
                                        transitionCatalog.firstOrNull { it.id == prev.id }
                                    }
                                    val transitionNext = nextChapter?.let { next ->
                                        transitionCatalog.firstOrNull { it.id == next.id }
                                    }
                                    val readerBookmarkStore: ReaderChapterBookmarkStore? = remember(
                                        runtime.library,
                                        mangaDetailState.manga?.id,
                                    ) {
                                        mangaDetailState.manga?.id?.let { mangaId ->
                                            LibraryChapterBookmarkStore(
                                                findChapter = { chapterId ->
                                                    runtime.library.chapterSnapshot(mangaId)
                                                        .firstOrNull { it.id == chapterId }
                                                },
                                                mutationPort = runtime.library,
                                            )
                                        }
                                    }

                                    ReaderDestination(
                                        destination = destination,
                                        runtime = runtime,
                                        mangaTitle = mangaDetailState.manga?.title ?: "Reader",
                                        chapterTitle = mangaDetailState.allChapters.firstOrNull {
                                            it.id == destination.chapterId
                                        }?.let { chapter ->
                                            chapterDisplayLabel(
                                                chapter,
                                                mangaDetailState.chapterSettings.displayMode,
                                            )
                                        } ?: "Chapter ${destination.chapterId}",
                                        mangaId = mangaDetailState.manga?.id,
                                        chapterCatalog = transitionCatalog,
                                        previousChapter = transitionPrevious,
                                        nextChapter = transitionNext,
                                        currentChapter = transitionCurrent,
                                        currentChapterDownloaded =
                                        mangaDetailState.downloadedChapterIds.contains(currentChapterId),
                                        bookmarkStore = readerBookmarkStore,
                                        onBack = {
                                            val chapter = mangaDetailState.allChapters.firstOrNull {
                                                it.id == destination.chapterId
                                            }
                                            val manga = mangaDetailState.manga
                                            if (chapter != null && manga != null) {
                                                presenterScope.launch {
                                                    val prefs = runtime.preferences.load()
                                                    if (!prefs.incognitoMode) {
                                                        runtime.trackSyncService?.onChapterRead(
                                                            manga.id,
                                                            chapter.chapterNumber,
                                                        )
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
                                        onPreviousChapter = {
                                            if (prevChapter != null) {
                                                navigator.navigate(DesktopDestination.Reader(prevChapter.id))
                                            }
                                        },
                                        onNextChapter = {
                                            if (nextChapter != null) {
                                                navigator.navigate(DesktopDestination.Reader(nextChapter.id))
                                            }
                                        },
                                        onChapterSelected = { chapterId ->
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                        hasPreviousChapter = prevChapter != null,
                                        hasNextChapter = nextChapter != null,
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
                                    val upcomingContent: (@Composable () -> Unit)? = if (isUpcomingOpen) {
                                        {
                                            UpcomingScreen(
                                                state = upcomingState,
                                                onBack = { isUpcomingOpen = false },
                                                onPreviousMonth = upcomingPresenter::previousMonth,
                                                onNextMonth = upcomingPresenter::nextMonth,
                                                onSelectDate = upcomingPresenter::selectDate,
                                                onOpenFilter = { upcomingPresenter.setFilterDialogOpen(true) },
                                                onDismissFilter = { upcomingPresenter.setFilterDialogOpen(false) },
                                                onCycleCategory = upcomingPresenter::cycleCategory,
                                                onClearFilters = upcomingPresenter::clearFilters,
                                                onOpenManga = { mangaId ->
                                                    isUpcomingOpen = false
                                                    navigator.navigate(DesktopDestination.Library)
                                                    libraryPresenter.selectManga(mangaId)
                                                },
                                                onRetry = upcomingPresenter::retry,
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                    DesktopShell(
                                        selected = destination as DesktopDestination,
                                        onDestinationSelected = { selectedDestination ->
                                            isUpcomingOpen = false
                                            navigator.navigate(selectedDestination)
                                        },
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
                                        onCategorySelected = libraryPresenter::selectCategory,
                                        onManageCategories = { isManageCategoriesDialogOpen = true },
                                        onEditMangaCategories = { isEditMangaCategoriesDialogOpen = true },
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
                                        onBatchCloseCategoryDialog = {
                                            libraryPresenter.setBatchCategoryDialogOpen(false)
                                        },
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
                                        updatedChapters = updatedChapters,
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
                                        readerSettingsStore = remember {
                                            DesktopReaderSettingsStore(runtime.preferences)
                                        },
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
                                        onDownloadBatch = { amount ->
                                            if (amount == -1) {
                                                libraryPresenter.downloadNextChapters(null, unreadOnly = false)
                                            } else {
                                                libraryPresenter.downloadNextChapters(amount, unreadOnly = true)
                                            }
                                        },
                                        onBatchBookmarkChapters = libraryPresenter::batchBookmarkChapters,
                                        onBatchMarkChaptersRead = libraryPresenter::batchMarkChaptersRead,
                                        onBatchDownloadChapters = libraryPresenter::batchDownloadChapters,
                                        onBatchDeleteDownloads = libraryPresenter::batchDeleteChapterDownloads,
                                        onOpenChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(true)
                                        },
                                        onDismissChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(false)
                                        },
                                        onChapterDisplayModeChange = libraryPresenter::setChapterDisplayMode,
                                        onExcludedScanlatorsChange = libraryPresenter::setExcludedScanlators,
                                        onShowMissingChaptersChange = libraryPresenter::setShowMissingChapters,
                                        onSetChapterSettingsAsDefault = libraryPresenter::setChapterSettingsAsDefault,
                                        onResetChapterSettingsToDefault =
                                        libraryPresenter::resetChapterSettingsToDefault,
                                        onDuplicateOpenManga = libraryPresenter::openDuplicateManga,
                                        onDuplicateMigrate = libraryPresenter::migrateDuplicateTo,
                                        onDuplicateAddAnyway = libraryPresenter::addDuplicateAnyway,
                                        onDuplicateDismiss = libraryPresenter::dismissDuplicateDialog,
                                        sourceNameFor = { sourceId ->
                                            sourceNames[sourceId] ?: "Source #$sourceId"
                                        },
                                        downloadCacheCleaner = runtime.downloadCacheCleaner,
                                        downloadsDir = runtime.downloader?.diskProvider?.downloadsDir
                                            ?: runtime.directories.root.resolve("media").resolve("downloads"),
                                        diskCacheDir = runtime.directories.cache,
                                        onOpenTracking = { isTrackingDialogOpen = true },
                                        onExportBackup = {
                                            presenterScope.launch {
                                                val path =
                                                    mihon.desktop.ui.library.chooseExportBackup() ?: return@launch
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
                                        onPreferencesChanged = { updated ->
                                            preferences = updated
                                            appLockController.refresh()
                                        },
                                        // Phase 14: Stats & Incognito
                                        statsData = statsData,
                                        onRefreshStats = { presenterScope.launch { statsService?.refresh() } },
                                        incognitoMode = preferences.incognitoMode,
                                        onToggleIncognito = {
                                            val updated = preferences.copy(incognitoMode = !preferences.incognitoMode)
                                            preferences = updated
                                            runtime.preferences.save(updated)
                                        },
                                        // Upcoming calendar (transient view opened from Updates)
                                        isUpcomingOpen = isUpcomingOpen,
                                        upcomingContent = upcomingContent,
                                        onOpenUpcoming = { isUpcomingOpen = true },
                                        onCloseUpcoming = { isUpcomingOpen = false },
                                        // App lock (desktop security)
                                        appLockController = appLockController,
                                        onLockNow = if (preferences.appLockEnabled) {
                                            appLockController::lockNow
                                        } else {
                                            null
                                        },
                                    )
                                }
                                if (isManageCategoriesDialogOpen) {
                                    categoryService?.let { service ->
                                        ManageCategoriesDialog(
                                            categories = categories.sortedBy { it.order },
                                            onDismiss = { isManageCategoriesDialogOpen = false },
                                            onCreateCategory = service::createCategory,
                                            onRenameCategory = service::renameCategory,
                                            onDeleteCategory = { categoryId ->
                                                if (libraryState.selectedCategoryId == categoryId) {
                                                    libraryPresenter.selectCategory(SYSTEM_ALL_CATEGORY.id)
                                                }
                                                service.deleteCategory(categoryId)
                                            },
                                            onMoveCategory = { category, newIndex ->
                                                val ordered = categories.sortedBy { it.order }
                                                val currentIndex = ordered.indexOfFirst { it.id == category.id }
                                                if (currentIndex != -1 &&
                                                    newIndex in ordered.indices &&
                                                    currentIndex != newIndex
                                                ) {
                                                    val reordered = ordered.toMutableList()
                                                    reordered.add(newIndex, reordered.removeAt(currentIndex))
                                                    reordered.forEachIndexed { index, item ->
                                                        val newOrder = index.toLong()
                                                        if (item.order != newOrder) {
                                                            service.reorderCategory(item.id, newOrder)
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                val editManga = mangaDetailState.manga
                                if (isEditMangaCategoriesDialogOpen && editManga != null) {
                                    categoryService?.let { service ->
                                        EditMangaCategoriesDialog(
                                            allCategories = categories.sortedBy { it.order },
                                            assignedCategoryIds = editManga.categories.map { it.id }.toSet(),
                                            onDismiss = { isEditMangaCategoriesDialogOpen = false },
                                            onSave = { categoryIds ->
                                                service.setMangaCategories(editManga.id, categoryIds)
                                            },
                                        )
                                    }
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
                                            presenterScope.launch {
                                                val tracker = trackerMgr.get(track.trackerId)
                                                val local = track.copy(mangaId = currentTrackingManga.id)
                                                val resolved = if (tracker != null && tracker.isLoggedIn) {
                                                    try {
                                                        tracker.updateRemote(local)
                                                    } catch (_: Exception) {
                                                        runtime.trackingQueue?.enqueue(local)
                                                        local
                                                    }
                                                } else {
                                                    runtime.trackingQueue?.enqueue(local)
                                                    local
                                                }
                                                val record = resolved.toTrackingRecord()
                                                val existing = runtime.library.findTracking(
                                                    currentTrackingManga.id,
                                                    track.trackerId,
                                                )
                                                if (existing != null) {
                                                    runtime.library.updateTracking(record.copy(id = existing.id))
                                                } else {
                                                    runtime.library.insertTracking(record)
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
    onEscape: () -> Boolean,
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: ((Long) -> Unit)? = null,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    mangaId: Long? = null,
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    previousChapter: ReaderChapterTransitionChapter? = null,
    nextChapter: ReaderChapterTransitionChapter? = null,
    currentChapter: ReaderChapterTransitionChapter? = null,
    currentChapterDownloaded: Boolean = false,
    bookmarkStore: ReaderChapterBookmarkStore? = null,
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
            onPreviousChapter = onPreviousChapter,
            onNextChapter = onNextChapter,
            onChapterSelected = onChapterSelected,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            onRetryChapter = { activeSession.open(destination.chapterId) },
            mangaId = mangaId,
            chapterCatalog = chapterCatalog,
            previousChapter = previousChapter,
            nextChapter = nextChapter,
            currentChapter = currentChapter,
            currentChapterDownloaded = currentChapterDownloaded,
            bookmarkStore = bookmarkStore,
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
                            strings.importReportPreferences(
                                result.counts.preferencesImported,
                                result.counts.preferencesSkipped,
                            ),
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
