package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
) {
    LazyLibraryGrid(
        columns = columns,
    ) {
        items(
            items = items,
            key = {
                it.manga.id!!
            },
        ) { libraryItem ->
            LibraryComfortableGridItem(
                libraryItem,
                libraryItem.manga in selection,
                onClick,
                onLongClick,
            )
        }
    }
}

@Composable
fun LibraryComfortableGridItem(
    item: LibraryItem,
    isSelected: Boolean,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
) {
    val manga = item.manga
    LibraryGridItemSelectable(isSelected = isSelected) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        onClick(manga)
                    },
                    onLongClick = {
                        onLongClick(manga)
                    },
                ),
        ) {
            LibraryGridCover(
                mangaCover = MangaCover(
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
            )
            Text(
                text = manga.title,
                maxLines = 2,
                style = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
