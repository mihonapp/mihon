package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.reader.model.PageId
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState

@Composable
internal fun ContinuousReader(
    state: ReaderState,
    viewportWidth: Dp,
    viewportHeight: Dp,
    onAction: (ReaderAction) -> Unit,
    pageContent: ReaderPageContent,
    modifier: Modifier = Modifier,
    webtoonMaxWidth: Int = 800,
    webtoonSidePadding: Int = 0,
    pageSizes: Map<PageId, PageSize> = emptyMap(),
) {
    val initialAnchor = state.viewportAnchor
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialAnchor.pageIndex.coerceIn(state.pages.indices),
        initialFirstVisibleItemScrollOffset = initialAnchor.offsetPixels,
    )
    val gap = if (state.mode == ReadingMode.WEBTOON) 0.dp else ReaderLayout.DEFAULT_CONTINUOUS_GAP_PIXELS.dp
    val scaleMode = if (state.mode == ReadingMode.WEBTOON) ScaleMode.FIT_WIDTH else state.scaleMode

    val isWebtoonOrVertical = state.mode == ReadingMode.WEBTOON || state.mode == ReadingMode.VERTICAL
    val maxWidthConstraint = if (isWebtoonOrVertical && webtoonMaxWidth > 0) {
        minOf(viewportWidth, webtoonMaxWidth.dp)
    } else {
        viewportWidth
    }
    val sidePaddingFraction = if (isWebtoonOrVertical && webtoonSidePadding > 0) {
        webtoonSidePadding.coerceIn(0, 30) / 100f
    } else {
        0f
    }
    val contentWidth = maxWidthConstraint * (1f - 2f * sidePaddingFraction)

    val density = LocalDensity.current
    val viewport = with(density) {
        ReaderViewport(
            contentWidth.roundToPx().coerceAtLeast(1),
            viewportHeight.roundToPx().coerceAtLeast(1),
        )
    }

    LaunchedEffect(listState, state.chapterId, state.pages.size) {
        snapshotFlow {
            ReaderPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.distinctUntilChanged().collect { position ->
            onAction(ReaderAction.SetViewportAnchor(position))
        }
    }

    LaunchedEffect(listState, state.chapterId, state.pages) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item -> state.pages.getOrNull(item.index)?.id }
        }.distinctUntilChanged().collect { visiblePages ->
            onAction(ReaderAction.SetVisiblePages(visiblePages))
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.width(contentWidth).fillMaxHeight(),
        ) {
            itemsIndexed(state.pages, key = { _, page -> page.id }) { pageIndex, page ->
                val intrinsicSize = pageSizes[page.id]
                val effectivePage = page.withIntrinsicSize(intrinsicSize)
                val transform = calculatePageTransform(
                    page = effectivePage,
                    viewport = viewport,
                    scaleMode = scaleMode,
                    zoom = state.zoom,
                    requestedPan = state.pan,
                )
                val itemHeight = with(density) { transform.heightPixels.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .then(if (pageIndex < state.pages.lastIndex) Modifier else Modifier),
                ) {
                    ReaderPageFrame(
                        page = effectivePage,
                        pageIndex = pageIndex,
                        totalPages = state.pages.size,
                        scaleMode = scaleMode,
                        zoom = state.zoom,
                        requestedPan = state.pan,
                        pageContent = pageContent,
                        modifier = Modifier.fillMaxWidth().height(itemHeight),
                        intrinsicSize = intrinsicSize,
                    )
                }
                if (pageIndex < state.pages.lastIndex && gap > 0.dp) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(gap))
                }
            }
        }
    }
}
