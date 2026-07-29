package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import spreadfit.LumaPage
import spreadfit.Spreadfit
import java.io.InputStream
import kotlin.math.max

/**
 * The one image-touching piece of the auto-detect feature: the Android `BitmapFactory` decode that the
 * pure [Spreadfit] classifier can't do itself. Decodes a page to the small grayscale [LumaPage] the
 * classifier consumes, applying the same shape gate and downscale it expects. All the cue maths and the
 * decision live in the `spreadfit` library.
 */
object SpreadOffsetSampler {

    // Width the page is downsampled to before measuring: small enough to stay a cheap decode, large
    // enough to still resolve a thin gutter (mirrors LumaPage.SUGGESTED_WIDTH).
    private const val DOWNSCALE_WIDTH = 100

    /**
     * Decodes one page to the classifier's [LumaPage] input, or null for a page that doesn't contribute:
     * an ineligible shape ([Spreadfit.isEligibleShape]), an unreadable stream, or an image too small to
     * measure.
     *
     * The stream is read once into an in-memory byte array and everything is decoded from that. Decoding
     * straight from the page's stream on this background thread races the reader's own decode of the same
     * page and corrupts decodeStream's internal read buffer: a native crash (SIGSEGV in
     * GetByteArrayRegion). Decoding from a private, immutable byte array avoids that entirely; the bounds
     * pass is a cheap header decode off the same bytes, so the shape gate still skips pixel work on
     * out-of-band pages.
     */
    fun sample(openStream: () -> InputStream): LumaPage? {
        val bytes = try {
            openStream().use { it.readBytes() }
        } catch (e: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (!Spreadfit.isEligibleShape(bounds.outWidth, bounds.outHeight)) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, bounds.outWidth / DOWNSCALE_WIDTH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return null

        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < 2 || height < 1) {
                null
            } else {
                LumaPage(toGray(bitmap, width, height), width, height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun toGray(bitmap: Bitmap, width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (77 * r + 150 * g + 29 * b) shr 8
        }
        return gray
    }
}
