package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.ui.library.ChapterFilterState
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.ChapterSortMode
import mihon.desktop.ui.library.ChapterSortState
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailScreen
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.library.TriStateFilter
import mihon.extension.ipc.findNetworkFailure
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import java.nio.file.Files

sealed interface BrowseNavigationState {
    data object Home : BrowseNavigationState
    data class SourceView(
        val source: SourceDescriptor,
        val mode: SourceListingMode = SourceListingMode.Popular,
    ) : BrowseNavigationState
    data class MangaDetail(
        val source: SourceDescriptor,
        val manga: SManga,
    ) : BrowseNavigationState
    data class ExtensionDetails(
        val pkg: String,
    ) : BrowseNavigationState
    data class SourcePreferences(
        val sourceId: Long,
        val returnToExtensionPkg: String? = null,
    ) : BrowseNavigationState
}

@Composable
fun BrowseContentView(
    runtime: DesktopRuntime,
    detailState: MangaDetailUiState = MangaDetailUiState(),
    detailActions: MangaDetailActions = MangaDetailActions(),
    onReadChapter: (Long) -> Unit = detailActions.onReadChapter,
    onOpenMangaDetail: (Long) -> Unit = {},
    onCloseMangaDetail: () -> Unit = {},
    onRetryMangaDetail: () -> Unit = {},
    onImportLocal: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var navState by remember { mutableStateOf<BrowseNavigationState>(BrowseNavigationState.Home) }

    val presenter = remember(runtime) {
        BrowsePresenter(
            sourceManager = runtime.sourceManager,
            installer = runtime.extensionInstaller,
            storeService = runtime.extensionStoreService,
            libraryRepository = runtime.library,
            preferenceStore = runtime.preferences,
            scope = scope,
            onExtensionUpdatesAvailable = { count ->
                runtime.notificationService?.notifyExtensionUpdatePending(count)
            },
        )
    }
    val browseState by presenter.state.collectAsState()

    val importActions = remember(runtime) {
        val importController = LibraryImportController(
            backupImporter = runtime.backupImporter,
            localImporter = runtime.localImporter,
            localLibraryRoot = runtime.localLibraryRoot,
        )
        LibraryImportActions(
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }

    when (val nav = navState) {
        BrowseNavigationState.Home -> {
            BrowseScreen(
                state = browseState,
                onTabSelected = presenter::setTab,
                onSearchQueryChange = presenter::setSearchQuery,
                onSourceSelected = { source, mode ->
                    navState = BrowseNavigationState.SourceView(source, mode)
                },
                onTogglePinSource = presenter::togglePinSource,
                onInstallExtension = presenter::installExtension,
                onInstallFromFile = presenter::installFromFile,
                onUninstallExtension = presenter::uninstallExtension,
                onToggleExtensionEnabled = presenter::toggleExtensionEnabled,
                onExtensionSelected = { extension ->
                    presenter.setTab(BrowseTab.Extensions)
                    navState = BrowseNavigationState.ExtensionDetails(extension.pkg)
                },
                onAddRepository = presenter::addRepository,
                onRemoveRepository = presenter::removeRepository,
                onUpdateAllPending = presenter::updateAllPending,
                onRefresh = presenter::refresh,
                onOpenGlobalSearch = presenter::openGlobalSearch,
                onCloseGlobalSearch = presenter::closeGlobalSearch,
                onGlobalSearchQueryChange = presenter::setGlobalSearchQuery,
                onPerformGlobalSearch = presenter::performGlobalSearch,
                onGlobalMangaSelected = { source, manga ->
                    navState = BrowseNavigationState.MangaDetail(source, manga)
                },
                onSelectMigrationSource = presenter::selectMigrationSource,
                onSearchTargetMigrationSource = presenter::searchTargetMigrationSource,
                onPerformMigration = presenter::performMigration,
            )
        }
        is BrowseNavigationState.SourceView -> {
            var webPageOpen by remember(nav.source.id) { mutableStateOf(false) }
            var pageJob by remember(nav.source.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            androidx.compose.runtime.DisposableEffect(nav.source.id) {
                onDispose { pageJob?.cancel() }
            }
            var sourceUiState by remember(nav.source.id, nav.mode) {
                mutableStateOf(
                    BrowseSourceUiState(
                        source = nav.source,
                        mode = nav.mode,
                        isLoading = true,
                        filterList = mihon.extension.source.model.FilterList(),
                    ),
                )
            }

            fun loadSourcePage(
                mode: SourceListingMode,
                page: Int,
                query: String,
                filters: mihon.extension.source.model.FilterList = sourceUiState.filterList,
            ) {
                pageJob?.cancel()
                sourceUiState =
                    sourceUiState.copy(
                        isLoading = true,
                        isLoadingMore = false,
                        errorMessage = null,
                        networkFailure = null,
                        mode = mode,
                        page = page,
                    )
                pageJob = scope.launch {
                    try {
                        val mangasPage = when (mode) {
                            SourceListingMode.Popular -> runtime.sourceManager.getPopular(nav.source.id, page)
                            SourceListingMode.Latest -> runtime.sourceManager.getLatest(nav.source.id, page)
                            SourceListingMode.Search -> runtime.sourceManager.searchManga(
                                nav.source.id,
                                page,
                                query,
                                filters,
                            )
                        }
                        val inLibrary = withContext(Dispatchers.IO) {
                            val all = runtime.library.librarySnapshot(null)
                            all.filter { it.sourceId == nav.source.id }.map { it.url }.toSet()
                        }
                        val chapterCounts = loadLocalChapterCounts(runtime, nav.source)
                        sourceUiState = sourceUiState.copy(
                            isLoading = false,
                            mangas = mangasPage.mangas,
                            hasNextPage = mangasPage.hasNextPage,
                            inLibraryUrls = inLibrary,
                            chapterCounts = chapterCounts,
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        sourceUiState = sourceUiState.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load source page",
                            networkFailure = e.findNetworkFailure(),
                        )
                    }
                }
            }

            fun loadMore() {
                if (sourceUiState.isLoading || sourceUiState.isLoadingMore || !sourceUiState.hasNextPage) return
                val nextPage = sourceUiState.page + 1
                sourceUiState = sourceUiState.copy(isLoadingMore = true, errorMessage = null, networkFailure = null)
                pageJob = scope.launch {
                    try {
                        val mangasPage = when (sourceUiState.mode) {
                            SourceListingMode.Popular -> runtime.sourceManager.getPopular(nav.source.id, nextPage)
                            SourceListingMode.Latest -> runtime.sourceManager.getLatest(nav.source.id, nextPage)
                            SourceListingMode.Search -> runtime.sourceManager.searchManga(
                                nav.source.id,
                                nextPage,
                                sourceUiState.query,
                                sourceUiState.filterList,
                            )
                        }
                        val inLibrary = withContext(Dispatchers.IO) {
                            val all = runtime.library.librarySnapshot(null)
                            all.filter { it.sourceId == nav.source.id }.map { it.url }.toSet()
                        }
                        val chapterCounts = sourceUiState.chapterCounts + loadLocalChapterCounts(runtime, nav.source)
                        sourceUiState = sourceUiState.copy(
                            isLoadingMore = false,
                            page = nextPage,
                            mangas = (sourceUiState.mangas + mangasPage.mangas).distinctBy { it.url },
                            hasNextPage = mangasPage.hasNextPage,
                            inLibraryUrls = sourceUiState.inLibraryUrls + inLibrary,
                            chapterCounts = chapterCounts,
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        sourceUiState = sourceUiState.copy(
                            isLoadingMore = false,
                            errorMessage = e.message ?: "Failed to load more manga",
                            networkFailure = e.findNetworkFailure(),
                        )
                    }
                }
            }

            LaunchedEffect(nav.source.id) {
                val filters = runtime.sourceManager.loadFilterList(nav.source.id)
                if (filters.isNotEmpty()) {
                    sourceUiState = sourceUiState.copy(filterList = filters)
                }
            }

            LaunchedEffect(nav.source.id, nav.mode) {
                loadSourcePage(nav.mode, 1, "")
            }

            BrowseSourceScreen(
                state = sourceUiState,
                onOpenWebPage = if (nav.source.isLocalSource()) null else ({ webPageOpen = true }),
                onBack = { navState = BrowseNavigationState.Home },
                onModeChange = { newMode ->
                    loadSourcePage(newMode, 1, sourceUiState.query)
                },
                onQueryChange = { newQuery ->
                    sourceUiState = sourceUiState.copy(query = newQuery)
                },
                onSearch = {
                    val mode = if (sourceUiState.query.isBlank() && sourceUiState.activeFilterCount == 0) {
                        SourceListingMode.Popular
                    } else {
                        SourceListingMode.Search
                    }
                    loadSourcePage(mode, 1, sourceUiState.query)
                },
                onPageChange = { newPage ->
                    if (newPage > sourceUiState.page) {
                        loadMore()
                    } else {
                        loadSourcePage(sourceUiState.mode, newPage, sourceUiState.query)
                    }
                },
                onLoadMore = ::loadMore,
                onMangaSelected = { manga ->
                    navState = BrowseNavigationState.MangaDetail(nav.source, manga)
                },
                onRetry = {
                    loadSourcePage(sourceUiState.mode, sourceUiState.page, sourceUiState.query)
                },
                onOpenFilters = {
                    sourceUiState = sourceUiState.copy(isFilterDialogOpen = true)
                },
                onCloseFilters = {
                    sourceUiState = sourceUiState.copy(isFilterDialogOpen = false)
                },
                onResetFilters = {
                    scope.launch {
                        val reset = runtime.sourceManager.loadFilterList(nav.source.id)
                        sourceUiState = sourceUiState.copy(filterList = reset)
                    }
                },
                onApplyFilters = { applied ->
                    sourceUiState = sourceUiState.copy(filterList = applied, isFilterDialogOpen = false)
                    loadSourcePage(SourceListingMode.Search, 1, sourceUiState.query, applied)
                },
                onImportLocal = onImportLocal ?: {
                    scope.launch {
                        when (val result = importActions.chooseAndImportLocal()) {
                            is ImportActionState.Completed -> {
                                loadSourcePage(SourceListingMode.Popular, 1, "")
                            }
                            is ImportActionState.Failed -> {
                                sourceUiState = sourceUiState.copy(errorMessage = result.message)
                            }
                            is ImportActionState.Rejected -> {
                                sourceUiState = sourceUiState.copy(errorMessage = "Import rejected: ${result.category}")
                            }
                            ImportActionState.Idle, ImportActionState.Running -> Unit
                        }
                    }
                    Unit
                },
            )
            if (webPageOpen) {
                SourceWebPageDialog(
                    runtime,
                    nav.source,
                    onDismiss = { webPageOpen = false },
                    onRetry = { loadSourcePage(sourceUiState.mode, sourceUiState.page, sourceUiState.query) },
                )
            }
        }
        is BrowseNavigationState.MangaDetail -> {
            if (nav.source.isLocalSource()) {
                LocalMangaDetailContent(
                    runtime = runtime,
                    source = nav.source,
                    manga = nav.manga,
                    onBack = { navState = BrowseNavigationState.SourceView(nav.source) },
                    onReadChapter = onReadChapter,
                )
            } else {
                var loadState by remember(nav.source.id, nav.manga.url) {
                    mutableStateOf(OnlineDetailLoadState(loading = true))
                }

                fun loadMangaDetails(forceRefresh: Boolean = false) {
                    loadState = loadState.copy(loading = true, errorMessage = null)
                    scope.launch {
                        try {
                            val mangaId = runtime.onlineMangaSyncService.prepareOnlineMangaForReading(
                                sourceId = nav.source.id,
                                manga = nav.manga,
                                forceRefresh = forceRefresh,
                            )
                            onOpenMangaDetail(mangaId)
                            onRetryMangaDetail()
                            loadState = loadState.copy(loading = false, mangaId = mangaId)
                        } catch (e: Exception) {
                            loadState = loadState.copy(
                                loading = false,
                                errorMessage = e.message ?: "Failed to fetch manga details",
                            )
                        }
                    }
                }

                LaunchedEffect(nav.source.id, nav.manga.url) {
                    loadMangaDetails()
                }

                val displayedState = when {
                    loadState.loading -> MangaDetailUiState(loading = true)
                    loadState.errorMessage != null -> MangaDetailUiState(
                        loading = false,
                        errorMessage = loadState.errorMessage,
                    )
                    else -> detailState
                }
                MangaDetailScreen(
                    state = displayedState,
                    actions = detailActions.copy(onReadChapter = onReadChapter),
                    onBack = {
                        onCloseMangaDetail()
                        navState = BrowseNavigationState.SourceView(nav.source)
                    },
                    onRetry = { loadMangaDetails(forceRefresh = true) },
                    onToggleLibrary = {
                        scope.launch {
                            loadState = loadState.copy(syncingLibrary = true, errorMessage = null)
                            try {
                                val adding = detailState.manga?.favorite != true
                                if (adding) {
                                    runtime.onlineMangaSyncService.addOrUpdateOnlineManga(
                                        nav.source.id,
                                        nav.manga,
                                    )
                                } else {
                                    runtime.onlineMangaSyncService.removeFromLibrary(
                                        nav.source.id,
                                        nav.manga.url,
                                    )
                                }
                                onRetryMangaDetail()
                                loadState = loadState.copy(syncingLibrary = false)
                            } catch (e: Exception) {
                                loadState = loadState.copy(
                                    syncingLibrary = false,
                                    errorMessage = "Failed to update library: ${e.message}",
                                )
                            }
                        }
                    },
                    onRefreshSource = { loadMangaDetails(forceRefresh = true) },
                    isLibraryActionRunning = loadState.syncingLibrary,
                    isRefreshingSource = loadState.loading,
                )
            }
        }
        is BrowseNavigationState.ExtensionDetails -> {
            val extension = browseState.installedExtensions.find { it.pkg == nav.pkg }
            if (extension == null) {
                LaunchedEffect(nav.pkg) {
                    navState = BrowseNavigationState.Home
                }
            } else {
                val sourceStates = browseState.extensionSourceStates[extension.pkg]
                    ?: extension.manifest.sources.map { source ->
                        SourceState(source = source, extensionPackage = extension.pkg)
                    }
                val updateItem = browseState.availableExtensions
                    .filter { it.pkg == extension.pkg && it.versionCode > extension.manifest.versionCode }
                    .maxByOrNull { it.versionCode }

                ExtensionDetailsScreen(
                    extension = extension,
                    sources = sourceStates,
                    isExtensionIncognito = extension.pkg in browseState.incognitoExtensionPackages,
                    updateItem = updateItem,
                    onBack = { navState = BrowseNavigationState.Home },
                    onToggleEnabled = { enabled ->
                        presenter.toggleExtensionEnabled(extension.pkg, enabled)
                    },
                    onUpdate = { item -> presenter.installExtension(item) },
                    onUninstall = {
                        presenter.uninstallExtension(extension.pkg)
                        navState = BrowseNavigationState.Home
                    },
                    onClearCookies = { presenter.clearExtensionCookies(extension.pkg) },
                    onToggleIncognito = { incognito ->
                        presenter.setExtensionIncognito(extension.pkg, incognito)
                    },
                    onToggleSourceEnabled = presenter::setSourceEnabled,
                    onToggleSourceIncognito = presenter::setSourceIncognito,
                    onOpenSourcePreferences = { sourceId ->
                        navState = BrowseNavigationState.SourcePreferences(sourceId, extension.pkg)
                    },
                    onOpenSource = { source ->
                        navState = BrowseNavigationState.SourceView(source)
                    },
                )
            }
        }
        is BrowseNavigationState.SourcePreferences -> {
            val sourceState = browseState.sourceStates.find { it.source.id == nav.sourceId }
                ?: browseState.extensionSourceStates.values.flatten()
                    .find { it.source.id == nav.sourceId }

            if (sourceState == null) {
                LaunchedEffect(nav.sourceId) {
                    navState = BrowseNavigationState.Home
                }
            } else {
                SourcePreferencesScreen(
                    source = sourceState.source,
                    definitions = browseState.sourcePreferenceDefinitions[nav.sourceId].orEmpty(),
                    values = browseState.sourcePreferenceValues[nav.sourceId].orEmpty(),
                    onBack = {
                        navState = nav.returnToExtensionPkg
                            ?.let { pkg -> BrowseNavigationState.ExtensionDetails(pkg) }
                            ?: BrowseNavigationState.Home
                    },
                    onValueChange = { key, value ->
                        presenter.setSourcePreferenceValue(nav.sourceId, key, value)
                    },
                )
            }
        }
    }
}

private data class OnlineDetailLoadState(
    val mangaId: Long? = null,
    val loading: Boolean = false,
    val syncingLibrary: Boolean = false,
    val errorMessage: String? = null,
)

private suspend fun loadLocalChapterCounts(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
): Map<String, Long> {
    if (!source.isLocalSource()) return emptyMap()
    return withContext(Dispatchers.IO) {
        runtime.library.librarySnapshot(null)
            .filter { it.sourceId == source.id }
            .associate { it.url to it.chapterCount }
    }
}

private data class LocalMangaDetailState(
    val mangaId: Long? = null,
    val manga: MangaDetails? = null,
    val chapters: List<LibraryChapter> = emptyList(),
    val readerAvailability: Map<Long, ChapterReaderAvailability> = emptyMap(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@Composable
private fun LocalMangaDetailContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    manga: SManga,
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember(manga.url) { mutableStateOf(0) }
    var detailState by remember(manga.url) { mutableStateOf(LocalMangaDetailState()) }
    var filterState by remember(manga.url) { mutableStateOf(ChapterFilterState()) }
    var sortState by remember(manga.url) { mutableStateOf(ChapterSortState()) }

    LaunchedEffect(manga.url, refreshKey) {
        detailState = detailState.copy(loading = true, errorMessage = null)
        try {
            val localMangaId = withContext(Dispatchers.IO) {
                runtime.library.librarySnapshot(null)
                    .firstOrNull { it.sourceId == source.id && it.url == manga.url }
                    ?.id
                    ?: runtime.library.allMangaSnapshot()
                        .firstOrNull { it.sourceId == source.id && it.url == manga.url }
                        ?.id
            } ?: error("Local manga is no longer in the library")

            val details = withContext(Dispatchers.IO) { runtime.library.mangaSnapshot(localMangaId) }
            val localStoragePath = withContext(Dispatchers.IO) {
                manga.url.substringAfter("local:", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                    ?.let { runtime.library.findLocalMangaByManifest(it)?.storagePath }
            }
            val detailsWithCover = details?.let { value ->
                if (value.thumbnailUrl.isNullOrBlank() && localStoragePath != null) {
                    value.copy(thumbnailUrl = localStoragePath)
                } else {
                    value
                }
            }
            val chapters = withContext(Dispatchers.IO) { runtime.library.chapterSnapshot(localMangaId) }
            val availability = chapters.associate { chapter ->
                chapter.id to chapter.resolveReaderAvailability(runtime.library)
            }
            detailState = LocalMangaDetailState(
                mangaId = localMangaId,
                manga = detailsWithCover,
                chapters = chapters,
                readerAvailability = availability,
                loading = false,
            )
        } catch (e: Exception) {
            detailState = LocalMangaDetailState(
                loading = false,
                errorMessage = e.message ?: "Failed to load local manga",
            )
        }
    }

    val filteredChapters = remember(detailState.chapters, filterState) {
        detailState.chapters.filter { chapter ->
            val unreadMatch = when (filterState.unread) {
                TriStateFilter.Disabled -> true
                TriStateFilter.Include -> !chapter.read
                TriStateFilter.Exclude -> chapter.read
            }
            val bookmarkMatch = when (filterState.bookmarked) {
                TriStateFilter.Disabled -> true
                TriStateFilter.Include -> chapter.bookmark
                TriStateFilter.Exclude -> !chapter.bookmark
            }
            unreadMatch && bookmarkMatch
        }
    }
    val displayedChapters = remember(filteredChapters, sortState) {
        when (sortState.mode) {
            ChapterSortMode.SourceOrder -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.sourceOrder }
            } else {
                filteredChapters.sortedByDescending { it.sourceOrder }
            }
            ChapterSortMode.ChapterNumber -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.chapterNumber }
            } else {
                filteredChapters.sortedByDescending { it.chapterNumber }
            }
            ChapterSortMode.UploadDate -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.dateUpload }
            } else {
                filteredChapters.sortedByDescending { it.dateUpload }
            }
        }
    }

    MangaDetailScreen(
        state = MangaDetailUiState(
            manga = detailState.manga,
            chapters = displayedChapters,
            allChapters = detailState.chapters,
            loading = detailState.loading,
            errorMessage = detailState.errorMessage,
            readerAvailability = detailState.readerAvailability,
            chapterFilterState = filterState,
            chapterSortState = sortState,
        ),
        onBack = onBack,
        onReadChapter = onReadChapter,
        onRetry = { refreshKey++ },
        onChapterFilterChange = { filterState = it },
        onChapterSortChange = { sortState = it },
        onToggleBookmark = { chapterId ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runtime.library.allChaptersSnapshot()
                        .firstOrNull { it.id == chapterId }
                        ?.let { record -> runtime.library.updateChapter(record.copy(bookmark = !record.bookmark)) }
                }
                refreshKey++
            }
        },
        onToggleRead = { chapterId ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runtime.library.allChaptersSnapshot()
                        .firstOrNull { it.id == chapterId }
                        ?.let { record -> runtime.library.updateChapter(record.copy(read = !record.read)) }
                }
                refreshKey++
            }
        },
        onMarkPreviousRead = { chapterId ->
            detailState.mangaId?.let { mangaId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val records = runtime.library.allChaptersSnapshot()
                            .filter { it.mangaId == mangaId }
                            .sortedBy { it.sourceOrder }
                        val targetIndex = records.indexOfFirst { it.id == chapterId }
                        if (targetIndex >= 0) {
                            records.take(targetIndex + 1).forEach { record ->
                                if (!record.read) {
                                    runtime.library.updateChapter(record.copy(read = true))
                                }
                            }
                        }
                    }
                    refreshKey++
                }
            }
        },
    )
}

private fun LibraryChapter.resolveReaderAvailability(
    readerLibrary: ReaderLibraryPort,
): ChapterReaderAvailability {
    val asset = readerLibrary.chapterAsset(id)
    if (asset != null) {
        val path = asset.storageRoot.resolve(asset.relativePath)
        val readable = if (asset.assetKind == "DIRECTORY") {
            Files.isDirectory(path)
        } else {
            Files.isRegularFile(path)
        }
        return if (readable) ChapterReaderAvailability.Readable else ChapterReaderAvailability.MissingLocalContent
    }
    return if (readerLibrary.onlineChapter(id) != null) {
        ChapterReaderAvailability.Readable
    } else {
        ChapterReaderAvailability.RemoteOnly
    }
}
