package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Matrix
import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.decoder.ImageDecoder
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A [Decoder] that uses built-in [ImageDecoder] to decode images that is not supported by the system.
 */
class ImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    /**
     * A page cut into immutable strips of about [STRIP_BYTES] each. The renderer uploads an
     * immutable bitmap only when it is drawn, so only the strips on screen reach the GPU instead of
     * the whole page as one texture.
     */
    class StripsImage(val strips: List<Bitmap>) : Image {
        override val size: Long = strips.sumOf { it.allocationByteCount.toLong() }
        override val width: Int = strips.firstOrNull()?.width ?: 0
        override val height: Int = strips.sumOf { it.height }
        override val shareable: Boolean get() = false
        override fun draw(canvas: Canvas) {}
    }

    override suspend fun decode(): DecodeResult {
        if (options.webtoonStrips) return decodeStrips()

        val decoder = resources.sourceOrNull()?.use {
            ImageDecoder.newInstance(it.inputStream(), options.cropBorders, displayProfile)
        }

        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder" }

        val srcWidth = decoder.width
        val srcHeight = decoder.height

        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }

        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        var bitmap = decoder.decode(sampleSize = sampleSize)
        decoder.recycle()

        check(bitmap != null) { "Failed to decode image" }

        if (options.bitmapConfig == Bitmap.Config.HARDWARE && ImageUtil.canUseHardwareBitmap(bitmap)) {
            val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
            if (hwBitmap != null) {
                bitmap.recycle()
                bitmap = hwBitmap
            }
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    /**
     * Decodes a webtoon page and cuts it into strips, freeing the decoded page once they're copied.
     *
     * Strips keep the resolution a power-of-two sample size leaves, like the other paths, so zooming
     * in still shows the page's detail, unless that would make the page take more than
     * [MAX_PAGE_BYTES]. They are never narrower than the view, so the page stays sharp unzoomed.
     */
    private suspend fun decodeStrips(): DecodeResult = stripDecodes.withPermit {
        val decoder = resources.sourceOrNull()?.use {
            ImageDecoder.newInstance(it.inputStream(), options.cropBorders, displayProfile)
        }
        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder" }

        val srcWidth = decoder.width
        val viewWidth = options.size.widthPx(options.scale) { srcWidth }
        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = decoder.width,
            srcHeight = decoder.height,
            dstWidth = viewWidth,
            dstHeight = options.size.heightPx(options.scale) { decoder.height },
            scale = options.scale,
        )
        val page = decoder.decode(sampleSize = sampleSize)
        decoder.recycle()
        check(page != null) { "Failed to decode image" }

        val config = page.config ?: Bitmap.Config.ARGB_8888
        val bytesPerPixel = page.rowBytes / page.width
        val budgetWidth = sqrt(MAX_PAGE_BYTES.toDouble() / bytesPerPixel * page.width / page.height).toInt()
        val stripWidth = maxOf(viewWidth, min(page.width, budgetWidth))
            // Older GPUs can't draw a texture wider than this
            .coerceAtMost(MAX_STRIP_WIDTH)
            .coerceIn(1, page.width)
        val scale = stripWidth.toFloat() / page.width

        // Strips of about the same size in bytes, so each takes about as long to upload
        val stripRows = (STRIP_BYTES / (stripWidth * bytesPerPixel)).coerceAtLeast(1)
        val bandRows = (stripRows / scale).toInt().coerceIn(1, page.height)
        val matrix = Matrix().apply { setScale(scale, scale) }
        val strips = (0..<page.height step bandRows).map { top ->
            val rows = min(bandRows, page.height - top)
            val band = if (stripWidth < page.width) {
                Bitmap.createBitmap(page, 0, top, page.width, rows, matrix, true)
            } else {
                Bitmap.createBitmap(page, 0, top, page.width, rows)
            }
            // createBitmap returns a mutable bitmap despite its docs, and copy is the only way to an immutable one
            band.copy(config, false).also { if (band !== page) band.recycle() }
        }
        page.recycle()
        DecodeResult(image = StripsImage(strips), isSampled = stripWidth < srcWidth)
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.webtoonStrips || isApplicable(result.source.source())) {
                ImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().use {
                ImageUtil.findImageType(it)
            }
            return when (type) {
                ImageUtil.ImageType.AVIF, ImageUtil.ImageType.JXL, ImageUtil.ImageType.HEIF -> true
                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }

    companion object {
        var displayProfile: ByteArray? = null
    }
}

private const val STRIP_BYTES = 4 * 1024 * 1024

private const val MAX_PAGE_BYTES = 128 * 1024 * 1024

private const val MAX_STRIP_WIDTH = 4096

// Each decode holds the whole page until its strips are copied, and the reader loads pages ahead in parallel
private val stripDecodes = Semaphore(1)
