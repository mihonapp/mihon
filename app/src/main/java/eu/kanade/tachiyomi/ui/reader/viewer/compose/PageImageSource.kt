package eu.kanade.tachiyomi.ui.reader.viewer.compose

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import ca.mpreg.imagedecoder.ImageDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import okio.BufferedSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A page image decoded a region at a time by [ImageDecoder], so only the shrunk regions on screen are ever held:
 * Telephoto's tiles in the pager, bands in the webtoon viewer. With borders cropped, the image is [content] and
 * regions are offset into it.
 */
class PageImageSource private constructor(
    private val decoder: ImageDecoder,
    private val content: IntRect,
    private val closeScope: CoroutineScope,
) : SubSamplingImageSource {

    val size: IntSize get() = content.size

    /**
     * Told when a region fails to decode. Telephoto runs decodes in the composition's scope, where a thrown
     * exception would crash the app, so the region is left blank instead.
     */
    var onError: ((Throwable) -> Unit)? = null

    private var isClosed = false

    // The decoder runs one decode at a time; this queues them in the order they're asked for, which a monitor doesn't
    private val decodeQueue = Mutex()

    override val preview: ImageBitmap? = null

    override suspend fun decoder() = ImageRegionDecoder.Factory { RegionDecoder() }

    // ImageDecoder synchronizes its decodes on itself, and close must wait for one in flight
    override fun close() {
        closeScope.launch(Dispatchers.IO) {
            synchronized(decoder) {
                if (!isClosed) {
                    isClosed = true
                    decoder.close()
                }
            }
        }
    }

    /**
     * Decodes [region] of the content, shrunk by [sampleSize] as it decodes and then to [width] when that's narrower,
     * or null when it fails.
     */
    suspend fun decode(region: IntRect, sampleSize: Int, width: Int = 0): Bitmap? = decodeQueue.withLock {
        withContext(Dispatchers.IO) {
            try {
                synchronized(decoder) {
                    if (isClosed) throw CancellationException("Page image closed")
                    val source = region.translate(content.topLeft).intersect(content)
                    decodeAveraged(source, sampleSize.coerceAtLeast(1))
                }.let { if (width in 1..<it.width) it.resampled(width) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onError?.invoke(e)
                null
            }
        }
    }

    /**
     * [region] shrunk by [sample] with every source pixel counted. The decoder's own sampling skips pixels, which
     * turns screentone into moiré, so it decodes at half the sample and halves that with filtering, which averages
     * each 2x2 block.
     *
     * Revisit: averaging each whole block in the decoder, then a Catmull-Rom resize to the exact width, cut the long
     * strip's moiré from 831 to 530 with no 2x2 pass. Left out of imagedecoder 18; the commits are kept on its
     * decoder-experiments-sampling branch.
     */
    private fun decodeAveraged(region: IntRect, sample: Int): Bitmap {
        val rect = region.toAndroidRect()
        if (sample == 1) return decoder.decodeToBitmap(rect, 1)
        val fine = decoder.decodeToBitmap(rect, sample / 2)
        val width = ceil(region.width.toFloat() / sample).toInt()
        val height = ceil(region.height.toFloat() / sample).toInt()
        return Bitmap.createScaledBitmap(fine, width, height, true).also { if (it !== fine) fine.recycle() }
    }

    /**
     * This shrunk to [width]. It starts at most twice as wide, so filtering in one step still sees every pixel.
     */
    private fun Bitmap.resampled(width: Int): Bitmap {
        val height = (this.height * width.toFloat() / this.width).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, width, height, true).also { if (it !== this) recycle() }
    }

    private inner class RegionDecoder : ImageRegionDecoder {

        override val imageSize: IntSize get() = content.size

        override suspend fun decodeRegion(region: IntRect, sampleSize: Int) = ImageRegionDecoder.DecodeResult(
            painter = decode(region, sampleSize)?.let { BitmapPainter(it.asImageBitmap()) }
                ?: ColorPainter(Color.Transparent),
            hasUltraHdrContent = false,
        )
    }

    companion object {
        /**
         * Opens the image in [source], finding its content inside the borders when [cropBorders]. Decodes a shrunk
         * copy of the whole page to find them, so call it off the main thread. The decoder is closed on [closeScope],
         * which must outlive the page so a close waiting on a decode isn't lost.
         */
        fun open(source: BufferedSource, cropBorders: Boolean, closeScope: CoroutineScope): PageImageSource {
            val decoder = ImageDecoder.open(source.inputStream())
            try {
                val whole = IntRect(0, 0, decoder.width, decoder.height)
                // Border detection reads 8-bit RGBA, so HDR (half-float) images are left uncropped
                val content = if (cropBorders && !decoder.isHdr) decoder.findContent() else whole
                return PageImageSource(decoder, content, closeScope)
            } catch (e: Throwable) {
                decoder.close()
                throw e
            }
        }
    }
}
