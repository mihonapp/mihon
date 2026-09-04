package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
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
) {
    val initialAnchor = state.viewportAnchor
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialAnchor.pageIndex.coerceIn(state.pages.indices),
        initialFirstVisibleItemScrollOffset = initialAnchor.offsetPixels,
    )
    val gap = if (state.mode == ReadingMode.WEBTOON) 0.dp else ReaderLayout.DEFAULT_CONTINUOUS_GAP_PIXELS.dp
    val scaleMode = if (state.mode == ReadingMode.WEBTOON) ScaleMode.FIT_WIDTH else state.scaleMode
    val density = LocalDensity.current
    val viewport = with(density) {
        ReaderViewport(
            viewportWidth.roundToPx().coerceAtLeast(1),
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

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        itemsIndexed(state.pages, key = { _, page -> page.id }) { pageIndex, page ->
            val transform = calculatePageTransform(
                page = page,
                viewport = viewport,
                scaleMode = scaleMode,
                zoom = state.zoom,
                requestedPan = ReaderPan(0f, 0f),
            )
            val itemHeight = with(density) { transform.heightPixels.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .then(if (pageIndex < state.pages.lastIndex) Modifier else Modifier),
            ) {
                ReaderPageFrame(
                    page = page,
                    pageIndex = pageIndex,
                    totalPages = state.pages.size,
                    scaleMode = scaleMode,
                    zoom = state.zoom,
                    requestedPan = ReaderPan(0f, 0f),
                    pageContent = pageContent,
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                )
            }
            if (pageIndex < state.pages.lastIndex && gap > 0.dp) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(gap))
            }
        }
    }
}
