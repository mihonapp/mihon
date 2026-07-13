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
internal fun LibraryCompactGrid(
    items: List<LibraryItem>,
    showTitle: Boolean,
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
    hopperOffsetX: androidx.compose.animation.core.Animatable<Float, *>,
    hopperInitialized: Boolean,
    onHopperInitialized: () -> Unit,
) {
    val cell: @Composable (LibraryItem) -> Unit = { libraryItem ->
        val manga = libraryItem.libraryManga.manga
        MangaCompactGridItem(
            isSelected = manga.id in selection,
            title = manga.title.takeIf { showTitle },
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
        // Cover keeps a fixed 2:3 width:height ratio (MangaCover.Book.ratio),
        // so cover height = cover width * 1.5. GridItemSelectable adds 4.dp
        // padding on all sides (8.dp total vertical). The compact layout has
        // no extra space below the cover — the title overlays the cover.
        PagedLibraryGrid(
            items = items,
            columns = columns,
            manualRows = manualRows,
            contentPadding = contentPadding,
            cellHeightForWidth = { cellWidth -> (cellWidth * 1.5f) - 4.dp },
            cell = cell,
            categories = categories,
            categoryIndex = categoryIndex,
            onSelectCategory = onSelectCategory,
            showHopper = showHopper,
            hopperOffsetX = hopperOffsetX,
            hopperInitialized = hopperInitialized,
            onHopperInitialized = onHopperInitialized,
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
            contentType = { "library_compact_grid_item" },
        ) { libraryItem -> cell(libraryItem) }
    }
}
