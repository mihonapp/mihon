package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.SourcePreferencesRoute
import mihon.core.navigation.WebViewRoute
import mihon.core.navigation.util.LocalAssistContentManager
import mihon.core.navigation.util.LocalBackStack
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.NewReleases
import mihon.icons.materialsymbols.roundedfilled.Favorite
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

@Composable
fun BrowseSourceScreen(
    sourceId: Long,
    listingQuery: String?,
) {
    val viewModel =
        assistedMetroViewModel<BrowseSourceViewModel, BrowseSourceViewModel.Factory> {
            create(sourceId = sourceId, listingQuery = listingQuery)
        }
    val state by viewModel.state.collectAsState()

    val backStack = LocalBackStack.current
    val assistContentManager = LocalAssistContentManager.current
    val navigateUp: () -> Unit = {
        when {
            !state.isUserQuery && state.toolbarQuery != null -> viewModel.setToolbarQuery(null)
            else -> backStack.removeLastOrNull()
        }
    }

    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }

    if (source is StubSource) {
        MissingSourceScreen(
            source = source,
            navigateUp = navigateUp,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
    val onWebViewClick = f@{
        val httpSource = source as? HttpSource ?: return@f
        backStack.add(
            WebViewRoute(
                url = httpSource.getHomeUrl(),
                initialTitle = httpSource.name,
                sourceId = httpSource.id,
            ),
        )
    }

    LaunchedEffect(source) {
        assistContentManager.currentAssistUrl = (source as? HttpSource)?.getHomeUrl()
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
                    onSearchQueryChange = viewModel::setToolbarQuery,
                    source = source,
                    displayMode = viewModel.displayMode,
                    onDisplayModeChange = { viewModel.displayMode = it },
                    navigateUp = navigateUp,
                    onWebViewClick = onWebViewClick,
                    onHelpClick = onHelpClick,
                    onSettingsClick = { backStack.add(SourcePreferencesRoute(sourceId)) },
                    onSearch = viewModel::search,
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
                            viewModel.resetFilters()
                            viewModel.setListing(Listing.Popular)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = MaterialSymbols.RoundedFilled.Favorite,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(MR.strings.popular))
                        },
                    )
                    if (source.supportsLatest) {
                        FilterChip(
                            selected = state.listing == Listing.Latest,
                            onClick = {
                                viewModel.resetFilters()
                                viewModel.setListing(Listing.Latest)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.NewReleases,
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
                            onClick = viewModel::openFilterSheet,
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.FilterList,
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
                }

                HorizontalDivider()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = { backStack.add(MangaRoute(it.id, true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicates = viewModel.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> viewModel.setDialog(BrowseSourceViewModel.Dialog.RemoveManga(manga))
                        duplicates.isNotEmpty() -> viewModel.setDialog(
                            BrowseSourceViewModel.Dialog.AddDuplicateManga(manga, duplicates),
                        )
                        else -> viewModel.addFavorite(manga)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
        )
    }

    val onDismissRequest = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is BrowseSourceViewModel.Dialog.Filter -> {
            SourceFilterDialog(
                onDismissRequest = onDismissRequest,
                filters = state.filters,
                onReset = viewModel::resetFilters,
                onFilter = { viewModel.search(filters = state.filters) },
                onUpdate = viewModel::setFilters,
            )
        }
        is BrowseSourceViewModel.Dialog.AddDuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.addFavorite(dialog.manga) },
                onOpenManga = { backStack.add(MangaRoute(it.id)) },
                onMigrate = { viewModel.setDialog(BrowseSourceViewModel.Dialog.Migrate(dialog.manga, it)) },
            )
        }

        is BrowseSourceViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                // Initiated from the context of [dialog.target] so we show [dialog.current].
                onClickTitle = { backStack.add(MangaRoute(dialog.current.id)) },
                onDismissRequest = onDismissRequest,
            )
        }
        is BrowseSourceViewModel.Dialog.RemoveManga -> {
            RemoveMangaDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    viewModel.changeMangaFavorite(dialog.manga)
                },
                mangaToRemove = dialog.manga,
            )
        }
        is BrowseSourceViewModel.Dialog.ChangeMangaCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { backStack.add(CategoryRoute) },
                onConfirm = { include, _ ->
                    viewModel.changeMangaFavorite(dialog.manga)
                    viewModel.moveMangaToCategories(dialog.manga, include)
                },
            )
        }
        else -> {}
    }

    ResultEffect<String>(resultKey = BrowseSearchEventKey) {
        viewModel.search(it)
    }

    ResultEffect<String>(resultKey = browseSearchGenreEventKey) {
        viewModel.searchGenre(it)
    }
}

@Suppress("ConstPropertyName")
const val BrowseSearchEventKey = "BrowseSearchEventKey"

@Suppress("ConstPropertyName")
const val browseSearchGenreEventKey = "BrowseSearchGenreEventKey"
