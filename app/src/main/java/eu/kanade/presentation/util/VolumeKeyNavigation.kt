package eu.kanade.presentation.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Registry for screens that page their list with hardware volume keys, driven by
 * [eu.kanade.tachiyomi.ui.main.MainActivity]. The last registered *enabled* handler wins,
 * so gated-off pager pages don't shadow the visible one.
 */
object VolumeKeyNavigation {

    interface Handler {
        val enabled: Boolean

        fun pageScroll(up: Boolean)
    }

    private val handlers = mutableListOf<Handler>()

    val activeHandler: Handler?
        get() = handlers.lastOrNull { it.enabled }

    internal fun push(handler: Handler) {
        handlers += handler
    }

    internal fun pop(handler: Handler) {
        handlers -= handler
    }
}

@Composable
fun VolumeKeyPageScrollHandler(
    state: LazyListState,
    enabled: () -> Boolean = { true },
) {
    VolumeKeyPageScrollHandlerImpl(
        key = state,
        canScroll = { state.canScrollForward || state.canScrollBackward },
        pageLayout = { state.toPageScrollLayout() },
        scrollToIndex = { state.scrollToItem(it) },
        enabled = enabled,
    )
}

@Composable
fun VolumeKeyPageScrollHandler(
    state: LazyGridState,
    enabled: () -> Boolean = { true },
) {
    VolumeKeyPageScrollHandlerImpl(
        key = state,
        canScroll = { state.canScrollForward || state.canScrollBackward },
        pageLayout = { state.toPageScrollLayout() },
        scrollToIndex = { state.scrollToItem(it) },
        enabled = enabled,
    )
}

private class PageScrollLayout(
    val rowsPerPage: Int,
    val firstIndex: Int,
    val itemsPerRow: Int,
)

private fun LazyListState.toPageScrollLayout(): PageScrollLayout {
    val info = layoutInfo
    val wholeItems =
        info.visibleItemsInfo.count { it.offset >= info.viewportStartOffset - 1 && it.offset + it.size <= info.viewportEndOffset + 1 }
    return PageScrollLayout(
        rowsPerPage = wholeItems.coerceAtLeast(1),
        firstIndex = firstVisibleItemIndex,
        itemsPerRow = 1,
    )
}

private fun LazyGridState.toPageScrollLayout(): PageScrollLayout {
    val info = layoutInfo
    val rows = info.visibleItemsInfo.groupBy { it.row }
    val wholeRows =
        rows.count { (_, items) ->
            val top = items.minOf { it.offset.y }
            val bottom = items.maxOf { it.offset.y + it.size.height }
            top >= info.viewportStartOffset - 1 && bottom <= info.viewportEndOffset + 1
        }
    val itemsPerRow = rows.entries.firstOrNull { (row, _) -> row != LazyGridItemInfo.UnknownRow }
        ?.let { (_, items) -> items.size }
        ?: 1
    return PageScrollLayout(
        rowsPerPage = wholeRows.coerceAtLeast(1),
        firstIndex = firstVisibleItemIndex,
        itemsPerRow = itemsPerRow.coerceAtLeast(1),
    )
}

/**
 * Index the new first row should land on: one page is however many whole rows fit in the
 * viewport (3.7 visible rows means pages of 3 rows), and pages always start at a row top.
 */
private fun PageScrollLayout.targetPageIndex(up: Boolean): Int {
    val target = if (up) {
        firstIndex - rowsPerPage * itemsPerRow
    } else {
        firstIndex + rowsPerPage * itemsPerRow
    }
    return if (up) target.coerceAtLeast(0) else target
}

@Composable
private fun VolumeKeyPageScrollHandlerImpl(
    key: Any,
    canScroll: () -> Boolean,
    pageLayout: () -> PageScrollLayout,
    scrollToIndex: suspend (Int) -> Unit,
    enabled: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled())

    val handler = remember(key) {
        object : VolumeKeyNavigation.Handler {
            override val enabled: Boolean
                get() = currentEnabled && canScroll()

            override fun pageScroll(up: Boolean) {
                scope.launch { scrollToIndex(pageLayout().targetPageIndex(up)) }
            }
        }
    }

    DisposableEffect(handler) {
        VolumeKeyNavigation.push(handler)
        onDispose { VolumeKeyNavigation.pop(handler) }
    }
}
