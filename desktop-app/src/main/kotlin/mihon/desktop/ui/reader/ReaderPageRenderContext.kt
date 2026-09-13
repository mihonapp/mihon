package mihon.desktop.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import mihon.reader.image.IntRect
import mihon.reader.model.PageDescriptor
import mihon.reader.model.ReaderViewport
import kotlin.math.ceil
import kotlin.math.floor

data class ReaderPageRenderContext(
    val imageWidth: Int,
    val imageHeight: Int,
    val visibleImageBounds: IntRect,
    val sampleSize: Int,
)

val LocalReaderPageRenderContext = staticCompositionLocalOf<ReaderPageRenderContext?> { null }

fun calculatePageRenderContext(
    page: PageDescriptor,
    viewport: ReaderViewport,
    transform: ReaderPageTransform,
): ReaderPageRenderContext {
    val originX = (viewport.width - transform.widthPixels) / 2f + transform.pan.x
    val originY = (viewport.height - transform.heightPixels) / 2f + transform.pan.y
    val scaleX = page.width.toFloat() / transform.widthPixels
    val scaleY = page.height.toFloat() / transform.heightPixels
    val left = floor((0f - originX).coerceAtLeast(0f) * scaleX).toInt().coerceIn(0, page.width - 1)
    val top = floor((0f - originY).coerceAtLeast(0f) * scaleY).toInt().coerceIn(0, page.height - 1)
    val right = ceil((viewport.width - originX).coerceAtMost(transform.widthPixels.toFloat()) * scaleX)
        .toInt()
        .coerceIn(left + 1, page.width)
    val bottom = ceil((viewport.height - originY).coerceAtMost(transform.heightPixels.toFloat()) * scaleY)
        .toInt()
        .coerceIn(top + 1, page.height)
    val renderedScale = minOf(
        transform.widthPixels.toFloat() / page.width,
        transform.heightPixels.toFloat() / page.height,
    )
    var sampleSize = 1
    while (sampleSize <= Int.MAX_VALUE / 2 && 1f / (sampleSize * 2) >= renderedScale) {
        sampleSize *= 2
    }
    return ReaderPageRenderContext(
        imageWidth = page.width,
        imageHeight = page.height,
        visibleImageBounds = IntRect(left, top, right, bottom),
        sampleSize = sampleSize,
    )
}
