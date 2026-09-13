package mihon.desktop.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import mihon.desktop.reader.DesktopReaderContent
import mihon.desktop.reader.DesktopReaderPageFrame
import mihon.desktop.reader.ReaderColorFilter
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId

val LocalReaderColorFilter = staticCompositionLocalOf { ReaderColorFilter.NONE }
val LocalReaderCropBorders = staticCompositionLocalOf { false }
val LocalReaderForeground = staticCompositionLocalOf { true }
val LocalReaderSelectedPage = staticCompositionLocalOf<PageId?> { null }

/** Each composed page owns its display lease; leaving a spread/list releases that lease. */
@Composable
fun DecodedReaderPage(
    content: DesktopReaderContent,
    page: PageDescriptor,
    modifier: Modifier,
    colorFilter: ReaderColorFilter = LocalReaderColorFilter.current,
    cropBorders: Boolean = LocalReaderCropBorders.current,
) {
    val pageSizeSink = LocalReaderPageSizeSink.current
    val imageStore = LocalReaderPageImageStore.current
    val readerForeground = LocalReaderForeground.current
    val selectedPage = LocalReaderSelectedPage.current
    val renderContext = LocalReaderPageRenderContext.current
    val shouldAnimate = readerForeground && selectedPage == page.id
    val selectedAnimationFrame by content.selectedAnimationFrame.collectAsState()
    var frame by remember(page.id, cropBorders) { mutableStateOf<DesktopReaderPageFrame?>(null) }
    var failure by remember(page.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(frame, imageStore) {
        val image = frame?.tile?.image
        if (image != null) {
            imageStore?.put(page.id, image)
        } else {
            imageStore?.remove(page.id)
        }
    }
    DisposableEffect(page.id, imageStore) {
        onDispose { imageStore?.remove(page.id) }
    }

    // Promote probed intrinsic dimensions into reader layout even before the full frame decodes.
    LaunchedEffect(content, page.id, pageSizeSink) {
        val sink = pageSizeSink ?: return@LaunchedEffect
        content.pageSizes.sizes.collect { sizes ->
            sizes[page.id]?.let { size -> sink.onPageSize(page.id, size) }
        }
    }

    LaunchedEffect(content, page.id, cropBorders) {
        var owned: DesktopReaderPageFrame? = null
        try {
            val replacement = content.loadFrame(page.id, frameIndex = 0, cropBorders)
            pageSizeSink?.onPageSize(
                page.id,
                PageSize(replacement.metadata.width, replacement.metadata.height),
            )
            owned = replacement
            frame = replacement
            kotlinx.coroutines.awaitCancellation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failure = error.message ?: "Unable to decode page"
        } finally {
            frame = null
            owned?.tile?.close()
        }
    }

    val metadata = frame?.metadata
    LaunchedEffect(content, page.id, shouldAnimate, metadata) {
        if (shouldAnimate && metadata?.isAnimated == true) {
            content.startAnimation(page.id, metadata)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                content.stopAnimation(page.id)
            }
        } else {
            content.stopAnimation(page.id)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = frame
        val animatedFrame = selectedAnimationFrame?.takeIf { it.pageId == page.id && shouldAnimate }
        when {
            animatedFrame != null -> AnimatedPage(
                selectedFrame = animatedFrame,
                loadFrame = content::loadAnimatedFrame,
                bridge = content.bridge,
                contentVisible = true,
                foreground = readerForeground,
                visibilityReporter = content,
                modifier = Modifier.matchParentSize(),
            )
            current != null && renderContext != null &&
                current.tile.key.sampleSize > renderContext.sampleSize &&
                !current.metadata.isAnimated && !cropBorders -> TiledReaderPage(
                content = content,
                pageId = page.id,
                fallback = current,
                context = renderContext,
                colorFilter = colorFilter.toComposeColorFilter(),
                modifier = Modifier.matchParentSize().testTag("reader-decoded-${page.id.entryName}"),
            )
            current != null -> Image(
                bitmap = current.tile.image,
                contentDescription = "Decoded page ${page.id.entryName}",
                modifier = Modifier.matchParentSize().testTag("reader-decoded-${page.id.entryName}"),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter.toComposeColorFilter(),
            )
            failure != null -> Text(requireNotNull(failure))
            else -> CircularProgressIndicator()
        }
    }
}
