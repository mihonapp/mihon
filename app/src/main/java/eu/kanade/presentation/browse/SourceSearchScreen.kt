package eu.kanade.presentation.browse

import androidx.compose.runtime.Composable
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter

@Composable
fun SourceSearchScreen(
    presenter: BrowseSourcePresenter,
    navigateUp: () -> Unit,
    onFabClick: () -> Unit,
    onClickManga: (Manga) -> Unit,
) {
    BrowseSourceScreen(
        presenter = presenter,
        navigateUp = navigateUp,
        onDisplayModeChange = { presenter.displayMode = (it) },
        onFabClick = onFabClick,
        onMangaClick = onClickManga,
        onMangaLongClick = onClickManga,
    )
}
