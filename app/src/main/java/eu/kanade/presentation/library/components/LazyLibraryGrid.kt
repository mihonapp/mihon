package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.util.plus
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga

@Composable
internal fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    contentPadding: PaddingValues,
    content: LazyGridScope.() -> Unit,
) {
    FastScrollLazyVerticalGrid(
        columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
        content = content,
    )
}

internal fun LazyGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (!searchQuery.isNullOrEmpty()) {
        item(
            span = { GridItemSpan(maxLineSpan) },
            contentType = { "library_global_search_item" },
        ) {
            GlobalSearchItem(
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}

internal fun LazyGridScope.librarySpotlightItem(
    libraryItem: LibraryItem?,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onContinueReading: ((LibraryManga) -> Unit)?,
) {
    if (libraryItem == null) return
    item(
        key = "library_spotlight_${libraryItem.id}",
        span = { GridItemSpan(maxLineSpan) },
        contentType = { "library_spotlight" },
    ) {
        LibrarySpotlightCard(
            libraryItem = libraryItem,
            onClick = { onClick(libraryItem.libraryManga) },
            onLongClick = { onLongClick(libraryItem.libraryManga) },
            onContinueReading = onContinueReading
                ?.takeIf { libraryItem.unreadCount > 0 }
                ?.let { callback -> { callback(libraryItem.libraryManga) } },
            modifier = Modifier
                .animateItem()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}
