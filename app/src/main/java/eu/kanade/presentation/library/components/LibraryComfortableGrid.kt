package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    pagedBrowsing: Boolean,
    manualRows: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    categories: List<Category>,
    categoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    showHopper: Boolean,
) {
    val cell: @Composable (LibraryItem) -> Unit = { libraryItem ->
        val manga = libraryItem.libraryManga.manga
        MangaComfortableGridItem(
            isSelected = manga.id in selection,
            title = manga.title,
            coverData = MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            ),
            coverBadgeStart = {
                DownloadsBadge(count = libraryItem.badges.downloadCount)
                UnreadBadge(count = libraryItem.badges.unreadCount)
            },
            coverBadgeEnd = {
                LanguageBadge(
                    isLocal = libraryItem.badges.isLocal,
                    sourceLanguage = libraryItem.badges.sourceLanguage,
                )
            },
            onLongClick = { onLongClick(libraryItem.libraryManga) },
            onClick = { onClick(libraryItem.libraryManga) },
            onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                { onClickContinueReading(libraryItem.libraryManga) }
            } else {
                null
            },
        )
    }

    if (pagedBrowsing) {
        // Same cover math as compact grid, plus the title block rendered
        // below the cover: ~2 lines at 18sp line-height (~36.dp) + 8.dp of
        // padding (4.dp top/bottom) = ~44.dp, on top of the 8.dp from
        // GridItemSelectable's own padding.
        PagedLibraryGrid(
            items = items,
            columns = columns,
            manualRows = manualRows,
            contentPadding = contentPadding,
            cellHeightForWidth = { cellWidth -> (cellWidth * 1.5f) - 4.dp + 45.dp },
            cell = cell,
            categories = categories,
            categoryIndex = categoryIndex,
            onSelectCategory = onSelectCategory,
            showHopper = showHopper,
        )
        return
    }

    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "library_comfortable_grid_item" },
        ) { libraryItem -> cell(libraryItem) }
    }
}
