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
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

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
}

@Composable
fun BrowseContentView(
    runtime: DesktopRuntime,
    onReadChapter: (Long) -> Unit,
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
        )
    }
    val browseState by presenter.state.collectAsState()

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
            var sourceUiState by remember(nav.source.id, nav.mode) {
                mutableStateOf(
                    BrowseSourceUiState(
                        source = nav.source,
                        mode = nav.mode,
                        isLoading = true,
                        filterList = runtime.sourceManager.getFilterList(nav.source.id),
                    ),
                )
            }

            fun loadSourcePage(
                mode: SourceListingMode,
                page: Int,
                query: String,
                filters: mihon.extension.source.model.FilterList = sourceUiState.filterList,
            ) {
                sourceUiState = sourceUiState.copy(isLoading = true, errorMessage = null, mode = mode, page = page)
                scope.launch {
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
                        sourceUiState = sourceUiState.copy(
                            isLoading = false,
                            mangas = mangasPage.mangas,
                            hasNextPage = mangasPage.hasNextPage,
                            inLibraryUrls = inLibrary,
                        )
                    } catch (e: Exception) {
                        sourceUiState = sourceUiState.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load source page",
                        )
                    }
                }
            }

            LaunchedEffect(nav.source.id, nav.mode) {
                loadSourcePage(nav.mode, 1, "")
            }

            BrowseSourceScreen(
                state = sourceUiState,
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
                    loadSourcePage(sourceUiState.mode, newPage, sourceUiState.query)
                },
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
                    val reset = runtime.sourceManager.getFilterList(nav.source.id)
                    sourceUiState = sourceUiState.copy(filterList = reset)
                },
                onApplyFilters = { applied ->
                    sourceUiState = sourceUiState.copy(filterList = applied, isFilterDialogOpen = false)
                    loadSourcePage(SourceListingMode.Search, 1, sourceUiState.query, applied)
                },
            )
        }
        is BrowseNavigationState.MangaDetail -> {
            var detailUiState by remember(nav.source.id, nav.manga.url) {
                mutableStateOf(
                    OnlineMangaDetailUiState(
                        source = nav.source,
                        manga = nav.manga,
                        isLoading = true,
                        inLibrary = runtime.onlineMangaSyncService.isMangaInLibrary(nav.source.id, nav.manga.url),
                    ),
                )
            }

            fun loadMangaDetails() {
                detailUiState = detailUiState.copy(isLoading = true, errorMessage = null)
                scope.launch {
                    try {
                        val detailed = runtime.sourceManager.getMangaDetails(nav.source.id, nav.manga)
                        val chapters = runtime.sourceManager.getChapterList(nav.source.id, detailed)
                        val inLib = runtime.onlineMangaSyncService.isMangaInLibrary(nav.source.id, nav.manga.url)
                        detailUiState = detailUiState.copy(
                            isLoading = false,
                            manga = detailed,
                            chapters = chapters,
                            inLibrary = inLib,
                        )
                    } catch (e: Exception) {
                        detailUiState = detailUiState.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to fetch manga details",
                        )
                    }
                }
            }

            LaunchedEffect(nav.source.id, nav.manga.url) {
                loadMangaDetails()
            }

            OnlineMangaDetailScreen(
                state = detailUiState,
                onBack = { navState = BrowseNavigationState.SourceView(nav.source) },
                onAddToLibrary = {
                    scope.launch {
                        detailUiState = detailUiState.copy(isSyncingLibrary = true)
                        try {
                            val adding = !detailUiState.inLibrary
                            if (adding) {
                                runtime.onlineMangaSyncService.addOrUpdateOnlineManga(
                                    nav.source.id,
                                    detailUiState.manga,
                                )
                            } else {
                                runtime.onlineMangaSyncService.removeFromLibrary(
                                    nav.source.id,
                                    detailUiState.manga.url,
                                )
                            }
                            detailUiState = detailUiState.copy(
                                isSyncingLibrary = false,
                                inLibrary = adding,
                            )
                        } catch (e: Exception) {
                            detailUiState = detailUiState.copy(
                                isSyncingLibrary = false,
                                errorMessage = "Failed to update library: ${e.message}",
                            )
                        }
                    }
                },
                onReadChapter = { chapter: SChapter ->
                    scope.launch {
                        detailUiState = detailUiState.copy(isSyncingLibrary = true)
                        try {
                            val mangaId = runtime.onlineMangaSyncService.addOrUpdateOnlineManga(
                                nav.source.id,
                                detailUiState.manga,
                            )
                            val chapters = withContext(Dispatchers.IO) {
                                runtime.library.chapterSnapshot(mangaId)
                            }
                            val targetChapter = chapters.find { it.url == chapter.url }
                                ?: chapters.firstOrNull()
                            if (targetChapter != null) {
                                onReadChapter(targetChapter.id)
                            }
                        } catch (e: Exception) {
                            detailUiState = detailUiState.copy(
                                isSyncingLibrary = false,
                                errorMessage = "Failed to start reader: ${e.message}",
                            )
                        }
                    }
                },
                onRefresh = ::loadMangaDetails,
            )
        }
    }
}
