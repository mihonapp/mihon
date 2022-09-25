package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.items
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.presentation.library.components.MangaListItemContent
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.verticalPadding
import eu.kanade.tachiyomi.R

@Composable
fun BrowseSourceList(
    mangaList: LazyPagingItems<Manga>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList) { initialManga ->
            initialManga ?: return@items
            val manga by getMangaState(initialManga)
            BrowseSourceListItem(
                manga = manga,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceListItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
    MangaListItem(
        coverContent = {
            MangaCover.Square(
                modifier = Modifier
                    .padding(vertical = verticalPadding)
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        if (manga.favorite) {
                            drawRect(overlayColor)
                        }
                    },
                data = manga.thumbnailUrl,
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        badges = {
            if (manga.favorite) {
                Badge(text = stringResource(R.string.in_library))
            }
        },
        content = {
            MangaListItemContent(text = manga.title)
        },
    )
}
