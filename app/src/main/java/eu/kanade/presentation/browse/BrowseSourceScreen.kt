package eu.kanade.presentation.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.data.source.NoResultsException
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.presentation.browse.components.BrowseSourceComfortableGrid
import eu.kanade.presentation.browse.components.BrowseSourceCompactGrid
import eu.kanade.presentation.browse.components.BrowseSourceList
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.widget.EmptyView

@Composable
fun BrowseSourceScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onFabClick: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onWebViewClick: () -> Unit,
) {
    val columns by presenter.getColumnsPreferenceForCurrentOrientation()

    val mangaList = presenter.getMangaList().collectAsLazyPagingItems()

    val snackbarHostState = remember { SnackbarHostState() }

    val uriHandler = LocalUriHandler.current

    val onHelpClick = {
        uriHandler.openUri(LocalSource.HELP_URL)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            BrowseSourceToolbar(
                state = presenter,
                source = presenter.source!!,
                displayMode = presenter.displayMode,
                onDisplayModeChange = { presenter.displayMode = it },
                navigateUp = navigateUp,
                onWebViewClick = onWebViewClick,
                onHelpClick = onHelpClick,
                onSearch = { presenter.search() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            BrowseSourceFloatingActionButton(
                isVisible = presenter.filters.isNotEmpty(),
                onFabClick = onFabClick,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { paddingValues ->
        BrowseSourceContent(
            state = presenter,
            mangaList = mangaList,
            getMangaState = { presenter.getManga(it) },
            columns = columns,
            displayMode = presenter.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(MoreController.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaLongClick,
            header = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = presenter.currentQuery == GetRemoteManga.QUERY_POPULAR,
                        onClick = {
                            presenter.resetFilter()
                            presenter.search(GetRemoteManga.QUERY_POPULAR)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Favorite,
                                contentDescription = "",
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(id = R.string.popular))
                        },
                    )
                    if (presenter.source?.supportsLatest == true) {
                        FilterChip(
                            selected = presenter.currentQuery == GetRemoteManga.QUERY_LATEST,
                            onClick = {
                                presenter.resetFilter()
                                presenter.search(GetRemoteManga.QUERY_LATEST)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.NewReleases,
                                    contentDescription = "",
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.latest))
                            },
                        )
                    }
                    if (presenter.filters.isNotEmpty()) {
                        FilterChip(
                            selected = presenter.currentQuery != GetRemoteManga.QUERY_POPULAR && presenter.currentQuery != GetRemoteManga.QUERY_LATEST,
                            onClick = onFabClick,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = "",
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.action_filter))
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun BrowseSourceFloatingActionButton(
    modifier: Modifier = Modifier.navigationBarsPadding(),
    isVisible: Boolean,
    onFabClick: () -> Unit,
) {
    AnimatedVisibility(visible = isVisible) {
        ExtendedFloatingActionButton(
            modifier = modifier,
            text = { Text(text = stringResource(id = R.string.action_filter)) },
            icon = { Icon(Icons.Outlined.FilterList, contentDescription = "") },
            onClick = onFabClick,
        )
    }
}

@Composable
fun BrowseSourceContent(
    state: BrowseSourceState,
    mangaList: LazyPagingItems<Manga>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    header: (@Composable () -> Unit)? = null,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLocalSourceHelpClick: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val context = LocalContext.current

    val errorState = mangaList.loadState.refresh.takeIf { it is LoadState.Error }
        ?: mangaList.loadState.append.takeIf { it is LoadState.Error }

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        when {
            state.error is NoResultsException -> context.getString(R.string.no_results_found)
            state.error.message == null -> ""
            state.error.message!!.startsWith("HTTP error") -> "${state.error.message}: ${context.getString(R.string.http_error_hint)}"
            else -> state.error.message!!
        }
    }

    LaunchedEffect(errorState) {
        if (mangaList.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.getString(R.string.action_webview_refresh),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> mangaList.refresh()
            }
        }
    }

    if (mangaList.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        EmptyScreen(
            message = getErrorMessage(errorState),
            actions = if (state.source is LocalSource) {
                listOf(
                    EmptyView.Action(R.string.local_source_help_guide, R.drawable.ic_help_24dp) { onLocalSourceHelpClick() },
                )
            } else {
                listOf(
                    EmptyView.Action(R.string.action_retry, R.drawable.ic_refresh_24dp) { mangaList.refresh() },
                    EmptyView.Action(R.string.action_open_in_web_view, R.drawable.ic_public_24dp) { onWebViewClick() },
                    EmptyView.Action(R.string.label_help, R.drawable.ic_help_24dp) { onHelpClick() },
                )
            },
        )

        return
    }

    if (mangaList.itemCount == 0 && mangaList.loadState.refresh is LoadState.Loading) {
        LoadingScreen()
        return
    }

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> {
            BrowseSourceComfortableGrid(
                mangaList = mangaList,
                getMangaState = getMangaState,
                columns = columns,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                header = header,
            )
        }
        LibraryDisplayMode.List -> {
            BrowseSourceList(
                mangaList = mangaList,
                getMangaState = getMangaState,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                header = header,
            )
        }
        else -> {
            BrowseSourceCompactGrid(
                mangaList = mangaList,
                getMangaState = getMangaState,
                columns = columns,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
                header = header,
            )
        }
    }
}
