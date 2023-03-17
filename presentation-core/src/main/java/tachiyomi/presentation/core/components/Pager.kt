package tachiyomi.presentation.core.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastSumBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

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
            flingBehavior = rememberLazyListSnapFlingBehavior(lazyListState = state.lazyListState),
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
            flingBehavior = rememberLazyListSnapFlingBehavior(lazyListState = state.lazyListState),
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

    private val pageSize: Int
        get() = visiblePages.firstOrNull()?.size ?: 0

    private var _currentPage by mutableStateOf(currentPage)

    private val layoutInfo: LazyListLayoutInfo
        get() = lazyListState.layoutInfo

    private val visiblePages: List<LazyListItemInfo>
        get() = layoutInfo.visibleItemsInfo

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

    private val closestPageToSnappedPosition: LazyListItemInfo?
        get() = visiblePages.fastMaxBy {
            -abs(
                calculateDistanceToDesiredSnapPosition(
                    layoutInfo,
                    it,
                    SnapAlignmentStartToStart,
                ),
            )
        }

    val currentPageOffsetFraction: Float by derivedStateOf {
        val currentPagePositionOffset = closestPageToSnappedPosition?.offset ?: 0
        val pageUsedSpace = pageSize.toFloat()
        if (pageUsedSpace == 0f) {
            // Default to 0 when there's no info about the page size yet.
            0f
        } else {
            ((-currentPagePositionOffset) / (pageUsedSpace)).coerceIn(
                MinPageOffset,
                MaxPageOffset,
            )
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

private const val MinPageOffset = -0.5f
private const val MaxPageOffset = 0.5f
internal val SnapAlignmentStartToStart: (layoutSize: Float, itemSize: Float) -> Float =
    { _, _ -> 0f }

// https://android.googlesource.com/platform/frameworks/support/+/refs/changes/78/2160778/35/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/snapping/LazyListSnapLayoutInfoProvider.kt
private fun lazyListSnapLayoutInfoProvider(
    lazyListState: LazyListState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        layoutSize / 2f - itemSize / 2f
    },
) = object : SnapLayoutInfoProvider {

    private val layoutInfo: LazyListLayoutInfo
        get() = lazyListState.layoutInfo

    // Single page snapping is the default
    override fun Density.calculateApproachOffset(initialVelocity: Float): Float = 0f

    override fun Density.calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val offset =
                calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

            // Find item that is closest to the center
            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }

            // Find item that is closest to center, but after it
            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }

        return lowerBoundOffset.rangeTo(upperBoundOffset)
    }

    override fun Density.calculateSnapStepSize(): Float = with(layoutInfo) {
        if (visibleItemsInfo.isNotEmpty()) {
            visibleItemsInfo.fastSumBy { it.size } / visibleItemsInfo.size.toFloat()
        } else {
            0f
        }
    }
}

@Composable
private fun rememberLazyListSnapFlingBehavior(lazyListState: LazyListState): FlingBehavior {
    val snappingLayout = remember(lazyListState) { lazyListSnapLayoutInfoProvider(lazyListState) }
    return rememberSnapFlingBehavior(snappingLayout)
}

private fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyListLayoutInfo,
    item: LazyListItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        with(layoutInfo) { singleAxisViewportSize - beforeContentPadding - afterContentPadding }

    val desiredDistance =
        positionInLayout(containerSize.toFloat(), item.size.toFloat())

    val itemCurrentPosition = item.offset
    return itemCurrentPosition - desiredDistance
}

private val LazyListLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
