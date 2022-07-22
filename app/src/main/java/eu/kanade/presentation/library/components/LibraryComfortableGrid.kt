package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.TextButton
import eu.kanade.tachiyomi.R
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
        columns = columns,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (searchQuery.isNullOrEmpty().not()) {
                TextButton(onClick = onGlobalSearchClicked) {
                    Text(
                        text = stringResource(R.string.action_global_search_query, searchQuery!!),
                        modifier = Modifier.zIndex(99f),
                    )
                }
            }
        }
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
                modifier = Modifier.padding(4.dp),
                text = manga.title,
                maxLines = 2,
                style = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
