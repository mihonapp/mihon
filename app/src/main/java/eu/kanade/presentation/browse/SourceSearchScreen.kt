package eu.kanade.presentation.browse

import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.webview.WebViewActivity

@Composable
fun SourceSearchScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onFabClick: () -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    val context = LocalContext.current

    BrowseSourceScreen(
        presenter = presenter,
        navigateUp = navigateUp,
        onDisplayModeChange = { presenter.displayMode = (it) },
        onFabClick = onFabClick,
        onMangaClick = onClickManga,
        onMangaLongClick = onClickManga,
        onWebViewClick = f@{
            val source = presenter.source as? HttpSource ?: return@f
            val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
            context.startActivity(intent)
        },
    )
}
