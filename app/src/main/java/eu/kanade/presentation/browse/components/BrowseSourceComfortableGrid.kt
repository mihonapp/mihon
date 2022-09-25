package eu.kanade.presentation.browse.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.library.components.MangaGridComfortableText
import eu.kanade.presentation.library.components.MangaGridCover
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

@Composable
fun BrowseSourceComfortableGrid(
    mangaList: LazyPagingItems<Manga>,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(8.dp, 4.dp) + contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mangaList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList.itemCount) { index ->
            val initialManga = mangaList[index] ?: return@items
            val manga by getMangaState(initialManga)
            BrowseSourceComfortableGridItem(
                manga = manga,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceComfortableGridItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        MangaGridCover(
            cover = {
                MangaCover.Book(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            if (manga.favorite) {
                                drawRect(overlayColor)
                            }
                        },
                    data = manga.thumbnailUrl,
                )
            },
            badgesStart = {
                if (manga.favorite) {
                    Badge(text = stringResource(R.string.in_library))
                }
            },
        )
        MangaGridComfortableText(
            text = manga.title,
        )
    }
}
