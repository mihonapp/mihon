package eu.kanade.presentation.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HorizontalPager(
    count: Int,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState(),
    key: ((page: Int) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    userScrollEnabled: Boolean = true,
    content: @Composable BoxScope.(page: Int) -> Unit,
) {
    Pager(
        count = count,
        modifier = modifier,
        state = state,
        isVertical = false,
        key = key,
        contentPadding = contentPadding,
        verticalAlignment = verticalAlignment,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

@Composable
private fun Pager(
    count: Int,
    modifier: Modifier,
    state: PagerState,
    isVertical: Boolean,
    key: ((page: Int) -> Any)?,
    contentPadding: PaddingValues,
    userScrollEnabled: Boolean,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable BoxScope.(page: Int) -> Unit,
) {
    LaunchedEffect(count) {
        state.currentPage = minOf(count - 1, state.currentPage).coerceAtLeast(0)
    }

    LaunchedEffect(state) {
        snapshotFlow { state.mostVisiblePageLayoutInfo?.index }
            .distinctUntilChanged()
            .collect { state.updateCurrentPageBasedOnLazyListState() }
    }

    if (isVertical) {
        LazyColumn(
            modifier = modifier,
            state = state.lazyListState,
            contentPadding = contentPadding,
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.aligned(verticalAlignment),
            userScrollEnabled = userScrollEnabled,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state.lazyListState),
        ) {
            items(
                count = count,
                key = key,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .wrapContentSize(),
                ) {
                    content(this, page)
                }
            }
        }
    } else {
        LazyRow(
            modifier = modifier,
            state = state.lazyListState,
            contentPadding = contentPadding,
            verticalAlignment = verticalAlignment,
            horizontalArrangement = Arrangement.aligned(horizontalAlignment),
            userScrollEnabled = userScrollEnabled,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state.lazyListState),
        ) {
            items(
                count = count,
                key = key,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .wrapContentSize(),
                ) {
                    content(this, page)
                }
            }
        }
    }
}

@Composable
fun rememberPagerState(
    initialPage: Int = 0,
) = rememberSaveable(saver = PagerState.Saver) {
    PagerState(currentPage = initialPage)
}

@Stable
class PagerState(
    currentPage: Int = 0,
) {
    init { check(currentPage >= 0) { "currentPage cannot be less than zero" } }

    val lazyListState = LazyListState(firstVisibleItemIndex = currentPage)

    private var _currentPage by mutableStateOf(currentPage)

    var currentPage: Int
        get() = _currentPage
        set(value) {
            if (value != _currentPage) {
                _currentPage = value
            }
        }

    val mostVisiblePageLayoutInfo: LazyListItemInfo?
        get() {
            val layoutInfo = lazyListState.layoutInfo
            return layoutInfo.visibleItemsInfo.fastMaxBy {
                val start = maxOf(it.offset, 0)
                val end = minOf(
                    it.offset + it.size,
                    layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding,
                )
                end - start
            }
        }

    fun updateCurrentPageBasedOnLazyListState() {
        mostVisiblePageLayoutInfo?.let {
            currentPage = it.index
        }
    }

    suspend fun animateScrollToPage(page: Int) {
        lazyListState.animateScrollToItem(index = page)
    }

    suspend fun scrollToPage(page: Int) {
        lazyListState.scrollToItem(index = page)
        updateCurrentPageBasedOnLazyListState()
    }

    companion object {
        val Saver: Saver<PagerState, *> = listSaver(
            save = { listOf(it.currentPage) },
            restore = { PagerState(it[0]) },
        )
    }
}
