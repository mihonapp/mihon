package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderState
import kotlin.math.roundToInt

typealias ReaderPageContent = @Composable (PageDescriptor, Int, Modifier) -> Unit

data class ReaderPageTransform(
    val widthPixels: Int,
    val heightPixels: Int,
    val zoom: Float,
    val pan: ReaderPan,
)

fun calculatePageTransform(
    page: PageDescriptor,
    viewport: ReaderViewport,
    scaleMode: ScaleMode,
    zoom: Float,
    requestedPan: ReaderPan,
): ReaderPageTransform {
    val baseScale = when (scaleMode) {
        ScaleMode.ORIGINAL -> 1f
        ScaleMode.FIT_WIDTH -> viewport.width.toFloat() / page.width
        ScaleMode.FIT_HEIGHT -> viewport.height.toFloat() / page.height
    }
    val appliedZoom = ReaderLayout.clampZoom(zoom)
    val width = (page.width * baseScale * appliedZoom).roundToInt().coerceAtLeast(1)
    val height = (page.height * baseScale * appliedZoom).roundToInt().coerceAtLeast(1)
    return ReaderPageTransform(
        widthPixels = width,
        heightPixels = height,
        zoom = appliedZoom,
        pan = ReaderLayout.clampPostLayoutPan(viewport, width, height, requestedPan),
    )
}

@Composable
fun ReaderCanvas(
    state: ReaderState,
    onAction: (ReaderAction) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: ReaderBackgroundColor = ReaderBackgroundColor.DARK_GRAY,
    webtoonMaxWidth: Int = 800,
    webtoonSidePadding: Int = 0,
    pageSizes: Map<PageId, PageSize> = emptyMap(),
    pageContent: ReaderPageContent = { _, pageIndex, contentModifier ->
        DefaultReaderPage(pageIndex, contentModifier)
    },
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor.toComposeColor())
            .testTag("reader-canvas")
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    onAction(ReaderAction.SetViewport(ReaderViewport(size.width, size.height)))
                }
            },
    ) {
        if (state.loadState is ReaderLoadState.Ready && state.pages.isNotEmpty()) {
            if (state.mode == ReadingMode.VERTICAL || state.mode == ReadingMode.WEBTOON) {
                ContinuousReader(
                    state = state,
                    viewportWidth = maxWidth,
                    viewportHeight = maxHeight,
                    onAction = onAction,
                    pageContent = pageContent,
                    webtoonMaxWidth = webtoonMaxWidth,
                    webtoonSidePadding = webtoonSidePadding,
                    pageSizes = pageSizes,
                    modifier = Modifier.fillMaxSize().testTag("reader-continuous"),
                )
            } else {
                PagedReader(
                    state = state,
                    viewportWidth = maxWidth,
                    onAction = onAction,
                    pageSizes = pageSizes,
                    pageContent = pageContent,
                    modifier = Modifier.fillMaxSize().testTag("reader-paged"),
                )
            }
        }
    }
}

@Composable
internal fun ReaderPageFrame(
    page: PageDescriptor,
    pageIndex: Int,
    totalPages: Int,
    scaleMode: ScaleMode,
    zoom: Float,
    requestedPan: ReaderPan,
    pageContent: ReaderPageContent,
    modifier: Modifier = Modifier,
    intrinsicSize: PageSize? = null,
    viewportTiling: Boolean = true,
) {
    val effectivePage = page.withIntrinsicSize(intrinsicSize)
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewport = remember(constraints.maxWidth, constraints.maxHeight) {
            ReaderViewport(constraints.maxWidth.coerceAtLeast(1), constraints.maxHeight.coerceAtLeast(1))
        }
        val transform = calculatePageTransform(effectivePage, viewport, scaleMode, zoom, requestedPan)
        val width = with(density) { transform.widthPixels.toDp() }
        val height = with(density) { transform.heightPixels.toDp() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(pageTag(pageIndex))
                .semantics {
                    contentDescription = "Page ${pageIndex + 1} of $totalPages"
                    readerPageIndex = pageIndex
                    readerScaleMode = scaleMode
                    readerZoom = transform.zoom
                    readerPanX = transform.pan.x
                    readerPanY = transform.pan.y
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalReaderPageRenderContext provides if (viewportTiling) {
                    calculatePageRenderContext(effectivePage, viewport, transform)
                } else {
                    null
                },
            ) {
                pageContent(
                    effectivePage,
                    pageIndex,
                    Modifier
                        .requiredSize(width, height)
                        .graphicsLayer(
                            translationX = transform.pan.x,
                            translationY = transform.pan.y,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DefaultReaderPage(pageIndex: Int, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (pageIndex + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

val READER_SURFACE_COLOR = Color(0xff101010)
