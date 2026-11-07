package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.util.plus

@Composable
internal fun LazyLibraryGrid(
    state: LazyGridState,
    userScrollEnabled: Boolean = true,
    stretchRows: Int = 0,
    stretchColumns: Boolean = false,
    modifier: Modifier = Modifier,
    columns: Int,
    contentPadding: PaddingValues,
    content: LazyGridScope.() -> Unit,
) {
    FastScrollLazyVerticalGrid(
        state = state,
        userScrollEnabled = userScrollEnabled,
        columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = remember(stretchRows) {
            val spacing = CommonMangaItemDefaults.GridVerticalSpacer
            if (stretchRows > 0) LibraryPageArrangement(spacing, stretchRows) else Arrangement.spacedBy(spacing)
        },
        horizontalArrangement = remember(stretchColumns) {
            val spacing = CommonMangaItemDefaults.GridHorizontalSpacer
            if (stretchColumns) LibraryPageArrangement(spacing) else Arrangement.spacedBy(spacing)
        },
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
