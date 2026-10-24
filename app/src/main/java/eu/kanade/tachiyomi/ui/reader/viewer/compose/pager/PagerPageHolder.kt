package eu.kanade.tachiyomi.ui.reader.viewer.compose.pager

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageError
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.compose.PageImageSource
import eu.kanade.tachiyomi.ui.reader.viewer.processPagedImage
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.DynamicZoomSpec
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomFocalPoint
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import me.saket.telephoto.zoomable.zoomable
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import kotlin.coroutines.cancellation.CancellationException

/**
 * A page of the pager: loads its image and shows it zoomable, or its progress or error.
 */
@Stable
class PagerPageHolder(
    val viewer: ComposePagerViewer,
    val page: ReaderPage,
) {

    private var image by mutableStateOf<PagerImage?>(null)

    private var error by mutableStateOf<Throwable?>(null)

    private var progress by mutableFloatStateOf(0f)

    private var isImageShown by mutableStateOf(false)

    private var zoomableState: ZoomableState? = null

    private var scope: CoroutineScope? = null

    /**
     * Whether each selection of this page moved forward, kept until the image can take it, as SSIV's ready listener.
     */
    private val selections = Channel<Boolean>(Channel.CONFLATED)

    private val zoomDuration: Int
        get() = (viewer.general.doubleTapAnimDuration * viewer.activity.animatorDurationScale).toInt()
            .coerceAtLeast(1)

    @Composable
    fun Content() {
        val context = LocalContext.current
        scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { loadPageAndProcessStatus(context) }

        // The pager would restore a page's zoom when it comes back, where the view pager always made it anew
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
            PageContent()
        }
    }

    @Composable
    private fun PageContent() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val image = image) {
                is PagerImage.Static -> StaticImage(image)
                is PagerImage.Animated -> AnimatedImage(image)
                null -> {}
            }

            val error = error
            when {
                error != null -> {
                    ReaderPageError(
                        page = page,
                        error = error,
                        sourceId = viewer.activity.viewModel.manga?.source,
                        onButtonPressChange = { pressed -> viewer.setGestureDetectorEnabled(!pressed) },
                    )
                }
                !isImageShown -> CombinedCircularProgressIndicator(progress = { progress })
            }
        }
    }

    @Composable
    private fun StaticImage(image: PagerImage.Static) {
        val zoomableState = rememberPageZoomableState(
            contentScale = pageContentScale(viewer.paged.page.imageScaleType),
            alignment = ZoomStartAlignment(viewer.imageZoomType),
        )
        val imageState = rememberSubSamplingImageState(image.source, zoomableState)
        LaunchedEffect(image.source) {
            image.source.onError = { error = it }
            snapshotFlow { imageState.isImageDisplayed }.collect { isImageShown = it }
        }
        LaunchedEffect(zoomableState) {
            for (forward in selections) {
                landscapeZoom(zoomableState, image.source.size, forward)
            }
        }
        HideMenuOnZoomEffect(zoomableState)

        val background = image.background?.takeIf { isImageShown }
        val colorFilter = LocalPageColorFilter.current
        SubSamplingImage(
            state = imageState,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // Over the background too: it continues the page's own edge.
                .then(if (colorFilter != null) Modifier.graphicsLayer { this.colorFilter = colorFilter } else Modifier)
                .then(if (background != null) Modifier.background(background) else Modifier)
                .zoomable(zoomableState, onDoubleClick = doubleTapZoom),
        )
    }

    @Composable
    private fun AnimatedImage(image: PagerImage.Animated) {
        val context = LocalContext.current
        val zoomableState = rememberPageZoomableState(contentScale = ContentScale.Fit, alignment = Alignment.Center)
        val painter = rememberAsyncImagePainter(
            ImageRequest.Builder(context)
                .data(image.source)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .crossfade(false)
                .build(),
        )
        LaunchedEffect(painter) {
            painter.state.collect { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        zoomableState.setContentLocation(
                            ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize),
                        )
                        isImageShown = true
                    }
                    is AsyncImagePainter.State.Error -> error = state.result.throwable
                    else -> {}
                }
            }
        }
        HideMenuOnZoomEffect(zoomableState)

        // Unscaled, as the content location says: the zoomable state fits it to the screen
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Inside,
            colorFilter = LocalPageColorFilter.current,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomableState, onDoubleClick = doubleTapZoom),
        )
    }

    @Composable
    private fun rememberPageZoomableState(contentScale: ContentScale, alignment: Alignment): ZoomableState {
        return rememberZoomableState(PageZoomSpec)
            .also {
                it.contentScale = contentScale
                it.contentAlignment = alignment
                zoomableState = it
            }
    }

    @Composable
    private fun HideMenuOnZoomEffect(zoomableState: ZoomableState) {
        LaunchedEffect(zoomableState) {
            snapshotFlow { zoomableState.zoomFraction }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { viewer.activity.hideMenu() }
        }
    }

    /**
     * Zooms in to SSIV's double tap scale, bringing the tapped point to the center, or back out.
     */
    @OptIn(ExperimentalTelephotoApi::class)
    private val doubleTapZoom = DoubleClickToZoomListener { state, centroid ->
        val animationSpec = tween<Float>(zoomDuration)
        // Relative to the fitted page, as is the zoom spec; zoomTo would take it relative to the image's own size
        val zoom = 1 + (state.zoomFraction ?: 0f) * (MAX_ZOOM - 1)
        if (zoom <= DOUBLE_TAP_ZOOM * 0.9f) {
            state.zoomBy(
                zoomFactor = DOUBLE_TAP_ZOOM / zoom,
                focal = ZoomFocalPoint.moveToViewportCenter(SpatialOffset(centroid, CoordinateSpace.Viewport)),
                animationSpec = animationSpec,
            )
        } else {
            state.resetZoom(animationSpec)
        }
    }

    /**
     * Zooms a wide page fitted to the screen to the screen's height, starting at the edge the reader comes from.
     */
    @OptIn(ExperimentalTelephotoApi::class)
    private suspend fun landscapeZoom(zoomableState: ZoomableState, imageSize: IntSize, forward: Boolean) {
        if (
            !viewer.paged.page.landscapeZoom ||
            viewer.paged.page.imageScaleType != ReaderPreferences.ScaleType.FIT_SCREEN ||
            imageSize.width <= imageSize.height
        ) {
            return
        }
        snapshotFlow { isImageShown }.first { it }
        if (zoomableState.zoomFraction != 0f) return

        delay(LANDSCAPE_ZOOM_DELAY)
        // As SSIV's, only a page still on screen zooms: passed in a burst, it zooms from the side the reader comes back from
        if (!viewer.isCurrentPage(page)) return
        val viewport = zoomableState.coordinateSystem.viewportSize
        val x = when (viewer.imageZoomType) {
            ZoomStartPosition.LEFT -> if (forward) 0f else viewport.width
            ZoomStartPosition.RIGHT -> if (forward) viewport.width else 0f
            ZoomStartPosition.CENTER -> viewport.width / 2
        }
        zoomableState.zoomTo(
            zoomFactor = viewport.height / imageSize.height,
            centroid = Offset(x, viewport.height / 2),
            animationSpec = tween(LANDSCAPE_ZOOM_DURATION, easing = EaseInOutQuad),
        )
    }

    fun onPageSelected(forward: Boolean) {
        selections.trySend(forward)
    }

    /**
     * Whether the image can be panned to the left.
     */
    fun canPanLeft(): Boolean = panRemaining { left, _ -> left }

    /**
     * Whether the image can be panned to the right.
     */
    fun canPanRight(): Boolean = panRemaining { _, right -> right }

    @OptIn(ExperimentalTelephotoApi::class)
    private fun panRemaining(side: (left: Float, right: Float) -> Float): Boolean {
        val state = zoomableState?.takeIf { isImageShown } ?: return false
        val (bounds, viewport) = with(state.coordinateSystem) {
            contentBounds(clipToViewport = false).rectIn(CoordinateSpace.Viewport) to viewportSize
        }
        return side(-bounds.left, bounds.right - viewport.width) > 1
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() = pan(1f)

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() = pan(-1f)

    @OptIn(ExperimentalTelephotoApi::class)
    private fun pan(direction: Float) {
        val state = zoomableState ?: return
        scope?.launch {
            state.panBy(
                offset = Offset(direction * state.coordinateSystem.viewportSize.width, 0f),
                animationSpec = tween(PAN_DURATION, easing = EaseOutQuad),
            )
        }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus(context: Context) {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launch(Dispatchers.IO) {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue, Page.State.LoadPage -> error = null
                    Page.State.DownloadImage -> {
                        error = null
                        page.progressFlow.collectLatest { value -> progress = value / 100f }
                    }
                    Page.State.Ready -> setImage(context)
                    is Page.State.Error -> error = state.error
                }
            }
        }
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage(context: Context) {
        progress = 0f

        val streamFn = page.stream ?: return

        try {
            val buffer = withContext(Dispatchers.IO) { streamFn().use { Buffer().readFrom(it) } }
            // Splitting, merging, sampling the background and finding the borders decode the image, which is CPU work
            image = withContext(Dispatchers.Default) {
                val source = viewer.simplePaged.processPagedImage(page, buffer, viewer is ComposeL2RPagerViewer) {
                    viewer.onPageSplit(page, it)
                }
                if (ImageUtil.isAnimatedAndSupported(source)) {
                    PagerImage.Animated(source)
                } else {
                    val background = if (viewer.general.automaticBackground) {
                        ImageUtil.chooseBackground(context, source.peek().inputStream()).toBrush()
                    } else {
                        null
                    }
                    PagerImage.Static(
                        PageImageSource.open(source, viewer.paged.page.cropBorders, viewer.graph.appScope),
                        background,
                    )
                }
            }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            error = e
        }
    }
}

private sealed interface PagerImage {
    class Static(val source: PageImageSource, val background: Brush?) : PagerImage
    class Animated(val source: BufferedSource) : PagerImage
}

/**
 * The page's unzoomed scale for each of the reader's scale types, as SSIV's minimum scale was.
 */
private fun pageContentScale(scaleType: ReaderPreferences.ScaleType): ContentScale = when (scaleType) {
    ReaderPreferences.ScaleType.FIT_SCREEN -> ContentScale.Fit
    ReaderPreferences.ScaleType.STRETCH -> ContentScale.Crop
    ReaderPreferences.ScaleType.FIT_WIDTH -> ContentScale.FillWidth
    ReaderPreferences.ScaleType.FIT_HEIGHT -> ContentScale.FillHeight
    ReaderPreferences.ScaleType.ORIGINAL_SIZE -> ContentScale.None
    ReaderPreferences.ScaleType.SMART_FIT -> SmartFit
}

/**
 * Fits the width of a tall page and the height of a wide one.
 */
private object SmartFit : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size) = if (srcSize.height > srcSize.width) {
        ContentScale.FillWidth.computeScaleFactor(srcSize, dstSize)
    } else {
        ContentScale.FillHeight.computeScaleFactor(srcSize, dstSize)
    }
}

/**
 * Centers a page that fits, and starts one that overflows at its top and at the [position] side, as SSIV did.
 */
private class ZoomStartAlignment(private val position: ZoomStartPosition) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val x = when {
            size.width <= space.width -> (space.width - size.width) / 2
            position == ZoomStartPosition.LEFT -> 0
            position == ZoomStartPosition.RIGHT -> space.width - size.width
            else -> (space.width - size.width) / 2
        }
        val y = if (size.height <= space.height) (space.height - size.height) / 2 else 0
        return IntOffset(x, y)
    }
}

/**
 * The page background [ImageUtil.chooseBackground] picked: a fill of one colour, or a gradient from top to bottom.
 */
private fun Drawable.toBrush(): Brush? = when (this) {
    is ColorDrawable -> SolidColor(Color(color))
    is GradientDrawable -> colors?.let { colors -> Brush.verticalGradient(colors.map(::Color)) }
    else -> null
}

/**
 * SSIV's limit of five times the page as first shown. Telephoto's maximum is relative to the image's own size.
 */
private val PageZoomSpec = DynamicZoomSpec { inputs ->
    val initialScale = inputs.scaledContentBounds.width / inputs.unscaledContentSize.width
    ZoomSpec(maxZoomFactor = MAX_ZOOM * initialScale)
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2f
private const val LANDSCAPE_ZOOM_DELAY = 500L
private const val LANDSCAPE_ZOOM_DURATION = 500
private const val PAN_DURATION = 250
