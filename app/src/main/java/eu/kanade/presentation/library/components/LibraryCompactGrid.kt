package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryCompactGrid(
    items: List<LibraryItem>,
    columns: Int,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "library_compact_grid_item" },
        ) { libraryItem ->
            LibraryCompactGridItem(
                item = libraryItem,
                isSelected = libraryItem.manga in selection,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
fun LibraryCompactGridItem(
    item: LibraryItem,
    isSelected: Boolean,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
) {
    val manga = item.manga
    LibraryGridCover(
        modifier = Modifier
            .selectedOutline(isSelected)
            .combinedClickable(
                onClick = {
                    onClick(manga)
                },
                onLongClick = {
                    onLongClick(manga)
                },
            ),
        mangaCover = eu.kanade.domain.manga.model.MangaCover(
            manga.id!!,
            manga.source,
            manga.favorite,
            manga.thumbnail_url,
            manga.cover_last_modified,
        ),
        downloadCount = item.downloadCount,
        unreadCount = item.unreadCount,
        isLocal = item.isLocal,
        language = item.sourceLanguage,
    ) {
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
        Text(
            text = manga.title,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomStart),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            style = MaterialTheme.typography.titleSmall.copy(
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f,
                ),
            ),
        )
    }
}
