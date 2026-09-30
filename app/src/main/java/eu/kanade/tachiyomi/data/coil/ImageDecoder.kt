package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
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
import coil3.size.Dimension
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import android.graphics.Canvas as AndroidCanvas

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

    override suspend fun decode(): DecodeResult {
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
        val sampledBitmap = if (sampleSize > 1) {
            val scaledWidth = (srcWidth / sampleSize).coerceAtLeast(1)
            val scaledHeight = (srcHeight / sampleSize).coerceAtLeast(1)
            val scaled = fullBitmap.scaleWithCanvas(scaledWidth, scaledHeight)
            fullBitmap.recycle()
            scaled
        } else {
            fullBitmap
        }

        // Webtoon: the sample size only ever shrinks by powers of two, so a page slightly wider than
        // the screen stays at full width. Scale it down to the exact view width to save GPU memory.
        val targetWidth = if (options.customDecoder) (options.size.width as? Dimension.Pixels)?.px else null
        val bitmap = if (targetWidth != null && targetWidth in 1 until sampledBitmap.width) {
            val targetHeight = (sampledBitmap.height.toLong() * targetWidth / sampledBitmap.width)
                .toInt()
                .coerceAtLeast(1)
            val scaled = sampledBitmap.scaleWithCanvas(targetWidth, targetHeight)
            sampledBitmap.recycle()
            scaled
        } else {
            sampledBitmap
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = bitmap.width != srcWidth,
        )
    }

    /**
     * Same as [Bitmap.createScaledBitmap], but drawn manually. `createScaledBitmap` calls
     * `prepareToDraw()` on the result, which makes the RenderThread upload the whole (huge) webtoon
     * page as one GPU texture, stalling rendering and evicting the strips that are actually visible.
     */
    private fun Bitmap.scaleWithCanvas(width: Int, height: Int): Bitmap {
        val dst = createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)
        AndroidCanvas(dst).drawBitmap(this, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.newDecoder || options.customDecoder || isApplicable(result.source.source())) {
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
