package eu.kanade.tachiyomi.ui.reader.viewer.compose.webtoon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageError
import eu.kanade.tachiyomi.ui.reader.viewer.compose.PageImageSource
import eu.kanade.tachiyomi.ui.reader.viewer.processStripImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import kotlin.coroutines.cancellation.CancellationException

// How much of the viewer a page that failed takes, and how far down a loading page's spinner sits
private const val ERROR_HEIGHT_FRACTION = 0.8f
private const val PROGRESS_TOP_FRACTION = 0.25f

/**
 * A page of the webtoon viewer: loads its image and shows it fitted to the width, or its progress or error.
 */
@Stable
class WebtoonPageHolder(
    private val viewer: ComposeWebtoonViewer,
    val page: ReaderPage,
    private val key: String,
) {

    private var image by mutableStateOf<WebtoonImage?>(null)

    private var error by mutableStateOf<Throwable?>(null)

    private var progress by mutableFloatStateOf(0f)

    private var isImageShown by mutableStateOf(false)

    @Composable
    fun Content(modifier: Modifier) {
        LaunchedEffect(Unit) { loadPageAndProcessStatus() }

        val viewportHeight = with(LocalDensity.current) { viewer.viewportHeight.toDp() }
        val error = error
        // A page made again after leaving the list's cache keeps the size it had, so nothing below it moves.
        val ratio = viewer.pageRatios[key]?.takeIf { error == null }
        Box(modifier = modifier.then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)) {
            when (val image = image.takeIf { error == null }) {
                is WebtoonImage.Static -> BandedPageImage(
                    source = image.source,
                    window = { viewer.windowOf(key) },
                    onShown = { isImageShown = true },
                )
                is WebtoonImage.Animated -> AnimatedImage(image)
                null -> {}
            }

            when {
                error != null -> {
                    ReaderPageError(
                        page = page,
                        error = error,
                        sourceId = viewer.activity.viewModel.manga?.source,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight * ERROR_HEIGHT_FRACTION),
                        onButtonPressChange = { pressed -> viewer.setGestureDetectorEnabled(!pressed) },
                    )
                }
                // The viewer's height while loading, so a screen holds one loading page and not all of them
                !isImageShown -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (ratio != null) Modifier.fillMaxHeight() else Modifier.height(viewportHeight))
                        .padding(top = viewportHeight * PROGRESS_TOP_FRACTION),
                    contentAlignment = Alignment.Center,
                ) {
                    CombinedCircularProgressIndicator(progress = { progress })
                }
            }
        }
    }

    @Composable
    private fun AnimatedImage(image: WebtoonImage.Animated) {
        val context = LocalContext.current
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
                        painter.intrinsicSize.let { viewer.pageRatios[key] = it.width / it.height }
                        isImageShown = true
                    }
                    is AsyncImagePainter.State.Error -> error = state.result.throwable
                    else -> {}
                }
            }
        }
        val size = painter.intrinsicSize
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            colorFilter = LocalPageColorFilter.current,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isImageShown) Modifier.aspectRatio(size.width / size.height) else Modifier),
        )
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
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
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> error = state.error
                }
            }
        }
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progress = 0f

        val streamFn = page.stream ?: return

        try {
            val buffer = withContext(Dispatchers.IO) { streamFn().use { Buffer().readFrom(it) } }
            // Splitting, merging and finding the borders decode the image, which is CPU work
            image = withContext(Dispatchers.Default) {
                val source = viewer.simpleLongStrip.page.processStripImage(buffer)
                if (ImageUtil.isAnimatedAndSupported(source)) {
                    WebtoonImage.Animated(source)
                } else {
                    WebtoonImage.Static(
                        PageImageSource.open(source, viewer.longStrip.cropBorders, viewer.graph.appScope)
                            .also { it.onError = { e -> error = e } },
                    )
                }
            }
            (image as? WebtoonImage.Static)?.source?.size?.let {
                viewer.pageRatios[key] = it.width.toFloat() / it.height
            }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            error = e
        }
    }

    /**
     * Closes the decoder once the page leaves the list.
     */
    fun recycle() {
        (image as? WebtoonImage.Static)?.source?.close()
        image = null
    }
}

private sealed interface WebtoonImage {
    class Static(val source: PageImageSource) : WebtoonImage
    class Animated(val source: BufferedSource) : WebtoonImage
}
