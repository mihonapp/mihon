package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaState
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaPresenter

@Composable
fun MigrateMangaScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: MigrationMangaPresenter,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    val state by presenter.state.collectAsState()

    when (state) {
        MigrateMangaState.Loading -> LoadingScreen()
        is MigrateMangaState.Error -> Text(text = (state as MigrateMangaState.Error).error.message!!)
        is MigrateMangaState.Success -> {
            MigrateMangaContent(
                nestedScrollInterop = nestedScrollInterop,
                list = (state as MigrateMangaState.Success).list,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
fun MigrateMangaContent(
    nestedScrollInterop: NestedScrollConnection,
    list: List<Manga>,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    if (list.isEmpty()) {
        EmptyScreen(textResource = R.string.empty_screen)
        return
    }
    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(list) { manga ->
            MigrateMangaItem(
                manga = manga,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
fun MigrateMangaItem(
    modifier: Modifier = Modifier,
    manga: Manga,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    BaseMangaListItem(
        modifier = modifier,
        manga = manga,
        onClickItem = { onClickItem(manga) },
        onClickCover = { onClickCover(manga) },
    )
}
