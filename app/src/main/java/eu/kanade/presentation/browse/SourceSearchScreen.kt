package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.more.MoreController

@Composable
fun SourceSearchScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onFabClick: () -> Unit,
    onMangaClick: (Manga) -> Unit,
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
            SearchToolbar(
                searchQuery = presenter.searchQuery ?: "",
                onChangeSearchQuery = { presenter.searchQuery = it },
                onClickCloseSearch = navigateUp,
                onSearch = { presenter.search(it) },
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
            onMangaLongClick = onMangaClick,
        )
    }
}
