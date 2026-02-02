package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.library.components.MassImportDialog
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { BrowseSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        
        // Back confirmation state
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val confirmBackAfterPages by sourcePreferences.confirmBackAfterPages().changes().collectAsState(initial = 0)
        val showPageNumber by sourcePreferences.showPageNumber().changes().collectAsState(initial = false)
        val skipCoverLoading by sourcePreferences.skipCoverLoading().changes().collectAsState(initial = false)
        val currentPage by screenModel.currentPage.collectAsState()
        var showBackConfirmDialog by remember { mutableStateOf(false) }
        
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                // Check if we should show confirmation before going back
                confirmBackAfterPages > 0 && currentPage > confirmBackAfterPages -> {
                    showBackConfirmDialog = true
                }
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }
        var showMassImportDialog by remember { mutableStateOf(false) }
        var lastImportResult by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }

        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
        
        // Auto-load pages when page range loading is active
        val targetEndPage by screenModel.targetEndPage.collectAsState()
        LaunchedEffect(currentPage, targetEndPage, mangaList.loadState.append) {
            val endPage = targetEndPage
            if (endPage != null && currentPage < endPage) {
                // Wait for current page to finish loading
                if (mangaList.loadState.append is androidx.paging.LoadState.NotLoading) {
                    // Small delay between page loads to avoid overwhelming the source
                    kotlinx.coroutines.delay(500)
                    // Trigger next page load by accessing beyond current items
                    if (mangaList.itemCount > 0) {
                        // Access the last item to trigger append
                        mangaList[mangaList.itemCount - 1]
                    }
                }
            } else if (endPage != null && currentPage >= endPage) {
                // Range loading complete
                screenModel.clearTargetEndPage()
                snackbarHostState.showSnackbar(
                    message = "Finished loading pages up to $endPage",
                    duration = SnackbarDuration.Short,
                )
            }
        }
        
        // Show snackbar when import completes
        LaunchedEffect(lastImportResult) {
            lastImportResult?.let { (added, skipped, errored) ->
                snackbarHostState.showSnackbar(
                    message = "Imported: $added added, $skipped skipped, $errored errors",
                    duration = SnackbarDuration.Short,
                )
            }
        }

        val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source
            val url: String
            val name: String
            val id: Long
            
            when (source) {
                is HttpSource -> {
                    url = source.baseUrl
                    name = source.name
                    id = source.id
                }
                is eu.kanade.tachiyomi.jsplugin.source.JsSource -> {
                    url = source.baseUrl
                    name = source.name
                    id = source.id
                }
                else -> return@f
            }
            
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = name,
                    sourceId = id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .pointerInput(Unit) {},
                ) {
                    BrowseSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        showPageNumber = showPageNumber,
                        currentPage = currentPage,
                        onPageJump = { targetPage ->
                            screenModel.jumpToPage(targetPage)
                            scope.launchIO {
                                snackbarHostState.showSnackbar(
                                    message = "Jumping to page $targetPage...",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        onPageRangeLoad = { startPage, endPage ->
                            screenModel.loadPageRange(startPage, endPage)
                            scope.launchIO {
                                snackbarHostState.showSnackbar(
                                    message = "Loading pages $startPage to $endPage...",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(SourcePreferencesScreen(sourceId)) },
                        onSearch = screenModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as CatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                        // Translation chip
                        FilterChip(
                            selected = state.translateTitles,
                            onClick = screenModel::toggleTranslateTitles,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Translate,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.action_translate))
                            },
                        )
                        // Multi-select chip for mass import
                        FilterChip(
                            selected = state.selectionMode,
                            onClick = screenModel::toggleSelectionMode,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(
                                    text = if (state.selectionMode && state.selection.isNotEmpty()) {
                                        "${state.selection.size} selected"
                                    } else {
                                        "Select"
                                    },
                                )
                            },
                        )
                        // Select All chip (only visible in selection mode)
                        if (state.selectionMode) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    screenModel.selectAll(mangaList.itemSnapshotList.items.mapNotNull { it.value })
                                },
                                label = {
                                    Text(text = "Select All")
                                },
                            )
                        }
                        // Add to library button when in selection mode with items selected
                        if (state.selectionMode && state.selection.isNotEmpty()) {
                            FilterChip(
                                selected = true,
                                onClick = { showMassImportDialog = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_add_to_library))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = mangaList,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                selectionMode = state.selectionMode,
                selection = state.selection,
                translateTitles = state.translateTitles,
                translatedTitles = state.translatedTitles,
                onTranslateManga = screenModel::translateManga,
                onMangaClick = { manga ->
                    if (state.selectionMode) {
                        screenModel.toggleSelection(manga)
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                titleMaxLines = screenModel.titleMaxLines,
                skipCoverLoading = skipCoverLoading,
                onMangaLongClick = { manga ->
                    if (state.selectionMode) {
                        screenModel.toggleSelection(manga)
                    } else {
                        scope.launchIO {
                            val duplicates = screenModel.getDuplicateLibraryManga(manga)
                            when {
                                manga.favorite -> screenModel.setDialog(
                                    BrowseSourceScreenModel.Dialog.RemoveManga(manga),
                                )
                                duplicates.isNotEmpty() -> screenModel.setDialog(
                                    BrowseSourceScreenModel.Dialog.AddDuplicateManga(manga, duplicates),
                                )
                                else -> screenModel.addFavorite(manga)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseSourceScreenModel.Dialog.Filter -> {
                val presets by screenModel.filterPresets.collectAsState()
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.pendingFilters, // Use pendingFilters for editing
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.pendingFilters) }, // Apply pendingFilters on search
                    onUpdate = screenModel::setFilters,
                    onOpenPresets = screenModel::openPresetSheet,
                    presets = presets,
                    onSavePreset = screenModel::saveFilterPreset,
                    onLoadPreset = screenModel::loadFilterPreset,
                    onDeletePreset = screenModel::deleteFilterPreset,
                )
            }
            is BrowseSourceScreenModel.Dialog.FilterPresets -> {
                val presets by screenModel.filterPresets.collectAsState()
                val autoApplyEnabled by screenModel.autoApplyFilterPresets.collectAsState()
                FilterPresetsDialog(
                    onDismissRequest = onDismissRequest,
                    presets = presets,
                    currentFilters = state.filters,
                    autoApplyEnabled = autoApplyEnabled,
                    onSavePreset = { name, setAsDefault ->
                        screenModel.saveFilterPreset(name, setAsDefault)
                    },
                    onLoadPreset = { presetId ->
                        screenModel.loadFilterPreset(presetId)
                        // loadFilterPreset now opens the filter dialog automatically
                    },
                    onDeletePreset = screenModel::deleteFilterPreset,
                    onSetDefaultPreset = screenModel::setDefaultFilterPreset,
                    onToggleAutoApply = screenModel::setAutoApplyPresets,
                )
            }
            is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.setDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)) },
                )
            }

            is BrowseSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            is BrowseSourceScreenModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                        // Remember selected categories for next selection
                        screenModel.rememberCategorySelection(include)
                    },
                )
            }
            else -> {}
        }

        // Show the library's comprehensive mass import dialog for URL-based imports
        if (showMassImportDialog) {
            // Prefill dialog with selected novels' URLs (one per line)
            val selected = screenModel.state.value.selection
            val initialText = selected.joinToString("\n") { manga ->
                val url = manga.url
                if (url.startsWith("http")) url else ((screenModel.source as? HttpSource)?.baseUrl ?: "") + url
            }

            MassImportDialog(
                onDismissRequest = { showMassImportDialog = false },
                onImportComplete = { added, skipped, errored ->
                    // Clear selection after import
                    screenModel.clearSelection()
                    // Store result to trigger snackbar via LaunchedEffect
                    lastImportResult = Triple(added, skipped, errored)
                },
                initialText = initialText,
            )
        }

        // Back confirmation dialog when many pages loaded
        if (showBackConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBackConfirmDialog = false },
                title = { Text(text = "Leave browse?") },
                text = { Text(text = "You have loaded $currentPage pages. Are you sure you want to go back?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showBackConfirmDialog = false
                            navigator.pop()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showBackConfirmDialog = false },
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
