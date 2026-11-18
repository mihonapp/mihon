package eu.kanade.presentation.library.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import eu.kanade.tachiyomi.ui.library.LibraryNavigation
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScrollNavigation(
    navigation: LibraryNavigation,
    listState: LazyListState,
    gridState: LazyGridState,
    isList: Boolean,
    active: Boolean,
) {
    val scope = rememberCoroutineScope()
    val onPageChange: (Int) -> Unit = remember(listState, gridState, isList) {
        { direction ->
            scope.launch {
                if (isList) {
                    val info = listState.layoutInfo
                    val rows = info.visibleItemsInfo.filter { it.contentType == "library_list_item" }
                        .map { LibraryRow(it.offset, it.offset + it.size) }
                    listState.scrollBy(
                        direction * libraryPageDistance(rows, info.viewportStartOffset, info.viewportEndOffset),
                    )
                } else {
                    val info = gridState.layoutInfo
                    val rows = info.visibleItemsInfo.filter { it.contentType != "library_global_search_item" }
                        .groupBy { it.row }.values.map { row ->
                            LibraryRow(row.minOf { it.offset.y }, row.maxOf { it.offset.y + it.size.height })
                        }
                    gridState.scrollBy(
                        direction *
                            libraryPageDistance(
                                rows,
                                info.viewportStartOffset,
                                info.viewportEndOffset,
                                info.mainAxisItemSpacing,
                            ),
                    )
                }
            }
        }
    }
    DisposableEffect(navigation, onPageChange, active) {
        if (active) navigation.onPageChange = onPageChange
        onDispose {
            if (navigation.onPageChange === onPageChange) navigation.onPageChange = null
        }
    }
}

internal data class LibraryRow(val start: Int, val end: Int)

internal fun libraryPageDistance(
    rows: List<LibraryRow>,
    viewportStart: Int,
    viewportEnd: Int,
    spacing: Int = 0,
): Float {
    val visible = rows.filter { it.end > viewportStart && it.start < viewportEnd && it.end > it.start }
    val whole = visible.filter { it.start >= viewportStart && it.end <= viewportEnd }
    val page = whole.ifEmpty { visible.take(1) }
    return page.sumOf { it.end - it.start + spacing }.toFloat()
}
