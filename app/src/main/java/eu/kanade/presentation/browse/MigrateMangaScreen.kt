package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaPresenter
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaPresenter.Event
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String?,
    presenter: MigrateMangaPresenter,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AppBar(
                title = title,
                navigateUp = navigateUp,
            )
        },
    ) { paddingValues ->
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(textResource = R.string.empty_screen)
            else -> {
                MigrateMangaContent(
                    paddingValues = paddingValues,
                    state = presenter,
                    onClickItem = onClickItem,
                    onClickCover = onClickCover,
                )
            }
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
    paddingValues: PaddingValues,
    state: MigrateMangaState,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + WindowInsets.navigationBars.asPaddingValues(),
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
