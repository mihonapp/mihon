package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            contentType = { "library_comfortable_grid_item" },
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
                modifier = Modifier.padding(4.dp),
                text = manga.title,
                fontSize = 12.sp,
                maxLines = 2,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
