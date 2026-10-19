package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ca.mpreg.imagedecoder.ImageDecoder
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A [Decoder] that uses [ImageDecoder] to decode image formats not supported
 * by the Android system decoder (AVIF, JXL, HEIF, etc.).
 */
class ImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    /**
     * Wraps a raw [ImageDecoder.Frame] as a Coil [Image] for callers that want
     * direct access to the RGBA [java.nio.ByteBuffer] (e.g. the new-decoder path).
     */
    class DecodeResultImage(
        val frame: ImageDecoder.Frame,
        val isHdr: Boolean,
        val hdrHeadroom: Float,
        val gainmap: ImageDecoder.Gainmap?,
    ) : Image {
        val image: java.nio.ByteBuffer get() = frame.image

        // Taken now: a caller may close the frame once it has the pixels.
        override val size: Long = frame.image.capacity().toLong()
        override val width: Int get() = frame.width
        override val height: Int get() = frame.height
        override val shareable: Boolean get() = true
        override fun draw(canvas: Canvas) {}
    }

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

        val res = resources.source().use {
            ImageDecoder.open(it.inputStream()).use { dec ->
                DecodeResultImage(
                    dec.decodeNext(),
                    dec.isHdr,
                    dec.hdrHeadroom,
                    if (dec.hdrKind == ImageDecoder.HdrKind.GAINMAP) dec.getGainmap() else null,
                )
            }
        }

        val srcWidth = res.width
        val srcHeight = res.height

        // newDecoder path: caller wants the raw DecodeResult (e.g. for custom rendering).
        // Hand it back as-is; sampling is the caller's responsibility.
        if (options.newDecoder) {
            return DecodeResult(
                image = res,
                isSampled = false,
            )
        }

        // Normal path: produce a Bitmap scaled to the requested output size.
        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }
        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        // Copy RGBA pixels from the native buffer into a full-resolution bitmap.
        // We must do this while `res` (and its native memory) is still alive.
        // HDR frames are half-float RGBA.
        val config = if (res.isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
        val fullBitmap = createBitmap(srcWidth, srcHeight, config)
        res.image.rewind()
        fullBitmap.copyPixelsFromBuffer(res.image)
        res.frame.close()

        // Downsample if needed. sampleSize is a power-of-two factor; the target
        // dimensions are src / sampleSize, matching BitmapFactory inSampleSize behaviour.
        val bitmap = if (sampleSize > 1) {
            val scaledWidth = (srcWidth / sampleSize).coerceAtLeast(1)
            val scaledHeight = (srcHeight / sampleSize).coerceAtLeast(1)
            val scaled = fullBitmap.scale(scaledWidth, scaledHeight)
            fullBitmap.recycle()
            scaled
        } else {
            fullBitmap
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    /**
     * Copies bands of rows straight from the native frame into strips, so the page never exists as
     * one Java bitmap on top of the frame.
     *
     * Strips keep the resolution a power-of-two sample size leaves, like the other paths, so zooming
     * in still shows the page's detail, unless that would make the page take more than
     * [MAX_PAGE_BYTES]. They are never narrower than the view, so the page stays sharp unzoomed.
     */
    private suspend fun decodeStrips(): DecodeResult = stripDecodes.withPermit {
        val (frame, isHdr) = resources.source().use {
            ImageDecoder.open(it.inputStream()).use { dec -> dec.decodeNext() to dec.isHdr }
        }
        frame.use {
            val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
            val bytesPerPixel = if (isHdr) 8 else 4
            val rowBytes = frame.width * bytesPerPixel
            val viewWidth = options.size.widthPx(options.scale) { frame.width }
            val sampleSize = DecodeUtils.calculateInSampleSize(
                srcWidth = frame.width,
                srcHeight = frame.height,
                dstWidth = viewWidth,
                dstHeight = options.size.heightPx(options.scale) { frame.height },
                scale = options.scale,
            )
            val budgetWidth = sqrt(MAX_PAGE_BYTES.toDouble() / bytesPerPixel * frame.width / frame.height).toInt()
            val stripWidth = maxOf(viewWidth, min(frame.width / sampleSize, budgetWidth))
                // Older GPUs can't draw a texture wider than this
                .coerceAtMost(MAX_STRIP_WIDTH)
                .coerceIn(1, frame.width)
            val scale = stripWidth.toFloat() / frame.width

            // Strips of about the same size in bytes, so each takes about as long to upload
            val stripRows = (STRIP_BYTES / (stripWidth * bytesPerPixel)).coerceAtLeast(1)
            val bandRows = (stripRows / scale).toInt().coerceIn(1, frame.height)
            val matrix = Matrix().apply { setScale(scale, scale) }
            val band = createBitmap(frame.width, bandRows, config)
            val strips = (0..<frame.height step bandRows).map { top ->
                val rows = min(bandRows, frame.height - top)
                if (rows != band.height) band.reconfigure(frame.width, rows, config)
                band.copyPixelsFromBuffer(
                    frame.image.duplicate().apply {
                        position(top * rowBytes)
                        limit((top + rows) * rowBytes)
                    },
                )
                val strip = if (stripWidth < frame.width) {
                    Bitmap.createBitmap(band, 0, 0, frame.width, rows, matrix, true)
                } else {
                    band
                }
                // createBitmap returns a mutable bitmap despite its docs, and copy is the only way to an immutable one
                strip.copy(config, false).also { if (strip !== band) strip.recycle() }
            }
            band.recycle()
            DecodeResult(image = StripsImage(strips), isSampled = stripWidth < frame.width)
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.newDecoder || options.webtoonStrips || isApplicable(result.source.source())) {
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
                ImageUtil.ImageType.AVIF,
                ImageUtil.ImageType.JXL,
                ImageUtil.ImageType.HEIF,
                ImageUtil.ImageType.JP2,
                -> true

                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}

private const val STRIP_BYTES = 4 * 1024 * 1024

private const val MAX_PAGE_BYTES = 128 * 1024 * 1024

private const val MAX_STRIP_WIDTH = 4096

// Each decode holds the whole native frame until its strips are copied, and the reader loads pages ahead in parallel
private val stripDecodes = Semaphore(1)
