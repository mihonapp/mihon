package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.library.components.MangaGridCompactText
import eu.kanade.presentation.library.components.MangaGridCover
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

@Composable
fun BrowseSourceCompactGrid(
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(mangaList.itemCount) { index ->
            val initialManga = mangaList[index] ?: return@items
            val manga by getMangaState(initialManga)
            BrowseSourceCompactGridItem(
                manga = manga,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceCompactGridItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
    MangaGridCover(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        cover = {
            MangaCover.Book(
                modifier = Modifier
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        if (manga.favorite) {
                            drawRect(overlayColor)
                        }
                    },
                data = eu.kanade.domain.manga.model.MangaCover(
                    manga.id,
                    manga.source,
                    manga.favorite,
                    manga.thumbnailUrl,
                    manga.coverLastModified,
                ),
            )
        },
        badgesStart = {
            if (manga.favorite) {
                Badge(text = stringResource(R.string.in_library))
            }
        },
        content = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xAA000000),
                        ),
                    )
                    .fillMaxHeight(0.33f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            MangaGridCompactText(manga.title)
        },
    )
}
