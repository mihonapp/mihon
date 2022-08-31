package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.components.BrowseLatestToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity

@Composable
fun BrowseLatestScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val columns by presenter.getColumnsPreferenceForCurrentOrientation()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val onHelpClick = {
        uriHandler.openUri(LocalSource.HELP_URL)
    }

    val onWebViewClick = f@{
        val source = presenter.source as? HttpSource ?: return@f
        val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            BrowseLatestToolbar(
                navigateUp = navigateUp,
                source = presenter.source!!,
                displayMode = presenter.displayMode,
                onDisplayModeChange = { presenter.displayMode = it },
                onHelpClick = onHelpClick,
                onWebViewClick = onWebViewClick,
            )
        },
    ) { paddingValues ->
        BrowseSourceContent(
            source = presenter.source,
            mangaList = presenter.getMangaList().collectAsLazyPagingItems(),
            getMangaState = { presenter.getManga(it) },
            columns = columns,
            displayMode = presenter.displayMode,
            snackbarHostState = remember { SnackbarHostState() },
            contentPadding = paddingValues,
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(MoreController.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = onMangaClick,
            onMangaLongClick = onMangaLongClick,
        )
    }
}
