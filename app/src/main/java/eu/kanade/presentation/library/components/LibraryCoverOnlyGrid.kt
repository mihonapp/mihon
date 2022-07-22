package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import eu.kanade.presentation.components.TextButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryCoverOnlyGrid(
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
            LibraryCoverOnlyGridItem(
                item = libraryItem,
                isSelected = libraryItem.manga in selection,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
fun LibraryCoverOnlyGridItem(
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
    )
}
