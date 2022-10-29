package eu.kanade.presentation.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.presentation.browse.components.BrowseSourceComfortableGrid
import eu.kanade.presentation.browse.components.BrowseSourceCompactGrid
import eu.kanade.presentation.browse.components.BrowseSourceList
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.EmptyScreenAction
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.more.MoreController

@Composable
fun BrowseSourceScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    openFilterSheet: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onWebViewClick: () -> Unit,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
) {
    val columns by presenter.getColumnsPreferenceForCurrentOrientation()

    val mangaList = presenter.getMangaList().collectAsLazyPagingItems()

    val snackbarHostState = remember { SnackbarHostState() }

    val uriHandler = LocalUriHandler.current

    val onHelpClick = {
        uriHandler.openUri(LocalSource.HELP_URL)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                BrowseSourceToolbar(
                    state = presenter,
                    source = presenter.source,
                    displayMode = presenter.displayMode,
                    onDisplayModeChange = { presenter.displayMode = it },
                    navigateUp = navigateUp,
                    onWebViewClick = onWebViewClick,
                    onHelpClick = onHelpClick,
                    onSearch = { presenter.search(it) },
                )

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = presenter.currentFilter == BrowseSourcePresenter.Filter.Popular,
                        onClick = {
                            presenter.reset()
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
                            Text(text = stringResource(R.string.popular))
                        },
                    )
                    if (presenter.source?.supportsLatest == true) {
                        FilterChip(
                            selected = presenter.currentFilter == BrowseSourcePresenter.Filter.Latest,
                            onClick = {
                                presenter.reset()
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
                                Text(text = stringResource(R.string.latest))
                            },
                        )
                    }
                    if (presenter.filters.isNotEmpty()) {
                        FilterChip(
                            selected = presenter.currentFilter is BrowseSourcePresenter.Filter.UserInput,
                            onClick = openFilterSheet,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = "",
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(R.string.action_filter))
                            },
                        )
                    }
                }

                Divider()

                AppStateBanners(downloadedOnlyMode, incognitoMode)
            }
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
            text = { Text(text = stringResource(R.string.action_filter)) },
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
            state.error.message.isNullOrEmpty() -> ""
            state.error.message.orEmpty().startsWith("HTTP error") -> "${state.error.message}: ${context.getString(R.string.http_error_hint)}"
            else -> state.error.message.orEmpty()
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
                    EmptyScreenAction(
                        stringResId = R.string.local_source_help_guide,
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onLocalSourceHelpClick,
                    ),
                )
            } else {
                listOf(
                    EmptyScreenAction(
                        stringResId = R.string.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = mangaList::refresh,
                    ),
                    EmptyScreenAction(
                        stringResId = R.string.action_open_in_web_view,
                        icon = Icons.Outlined.Public,
                        onClick = onWebViewClick,
                    ),
                    EmptyScreenAction(
                        stringResId = R.string.label_help,
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onHelpClick,
                    ),
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
            )
        }
        LibraryDisplayMode.List -> {
            BrowseSourceList(
                mangaList = mangaList,
                getMangaState = getMangaState,
                contentPadding = contentPadding,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaLongClick,
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
            )
        }
    }
}
