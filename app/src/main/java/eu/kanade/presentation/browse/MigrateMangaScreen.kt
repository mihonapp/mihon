package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaPresenter
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaPresenter.Event
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MigrateMangaScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: MigrateMangaPresenter,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    val context = LocalContext.current
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.isEmpty -> EmptyScreen(textResource = R.string.empty_screen)
        else -> {
            MigrateMangaContent(
                nestedScrollInterop = nestedScrollInterop,
                state = presenter,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
    LaunchedEffect(Unit) {
        presenter.events.collectLatest { event ->
            when (event) {
                Event.FailedFetchingFavorites -> {
                    context.toast(R.string.internal_error)
                }
            }
        }
    }
}

@Composable
fun MigrateMangaContent(
    nestedScrollInterop: NestedScrollConnection,
    state: MigrateMangaState,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    ScrollbarLazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(state.items) { manga ->
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
