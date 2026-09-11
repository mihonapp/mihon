package mihon.desktop.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.ReaderColorFilter
import mihon.reader.model.PageDescriptor

val LocalReaderColorFilter = staticCompositionLocalOf { ReaderColorFilter.NONE }
val LocalReaderCropBorders = staticCompositionLocalOf { false }

/** Each composed page owns its display lease; leaving a spread/list releases that lease. */
@Composable
fun DecodedReaderPage(
    factory: DesktopReaderFactory,
    page: PageDescriptor,
    foreground: Boolean,
    modifier: Modifier,
    colorFilter: ReaderColorFilter = LocalReaderColorFilter.current,
    cropBorders: Boolean = LocalReaderCropBorders.current,
) {
    val pageSizeSink = LocalReaderPageSizeSink.current
    val imageStore = LocalReaderPageImageStore.current
    var frame by remember(page.id, cropBorders) { mutableStateOf<DesktopReaderFactory.PageFrame?>(null) }
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
    LaunchedEffect(factory, page.id, pageSizeSink) {
        val sink = pageSizeSink ?: return@LaunchedEffect
        factory.pageSizes.sizes.collect { sizes ->
            sizes[page.id]?.let { size -> sink.onPageSize(page.id, size) }
        }
    }

    LaunchedEffect(factory, page.id, foreground, cropBorders) {
        var owned: DesktopReaderFactory.PageFrame? = null
        try {
            var index = 0
            while (true) {
                val replacement = factory.loadFrame(page.id, index, cropBorders)
                pageSizeSink?.onPageSize(
                    page.id,
                    PageSize(replacement.metadata.width, replacement.metadata.height),
                )
                val previous = owned
                owned = replacement
                frame = replacement
                previous?.tile?.close()
                if (!foreground || replacement.metadata.frameCount == 1) awaitCancellation()
                delay(replacement.metadata.frameDurationsMillis[index].coerceAtLeast(20L))
                index = (index + 1) % replacement.metadata.frameCount
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message ?: "Unable to decode page"
        } finally {
            frame = null
            owned?.tile?.close()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = frame
        when {
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
