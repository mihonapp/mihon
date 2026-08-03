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

    // Bytes read from the head of a page for the header-only wide check ([isWidePage]): enough to cover
    // the dimension fields of the formats the reader decodes, plus a moderate leading EXIF/thumbnail
    // block. A page whose dimensions sit past this falls back to a full read.
    private const val HEADER_BYTES = 64 * 1024

    /** One page's contribution to detection: [luma] for the classifier (null when the page doesn't
     *  contribute: ineligible shape, unreadable, or too small) and [isWide] from the same bounds read
     *  (`outWidth > outHeight`, matching `ImageUtil.isWideImage`) for the "treat wide pages as spreads"
     *  pairing, which needs the wide pages the classifier itself skips. */
    data class Sampled(val luma: LumaPage?, val isWide: Boolean)

    /**
     * Decodes one page to a [Sampled]. [Sampled.luma] is the classifier input (null for a page that
     * doesn't contribute: an ineligible shape ([Spreadfit.isEligibleShape]), an unreadable stream, or an
     * image too small to measure); [Sampled.isWide] is read off the same bounds decode.
     *
     * The stream is read once into an in-memory byte array and everything is decoded from that. Decoding
     * straight from the page's stream on this background thread races the reader's own decode of the same
     * page and corrupts decodeStream's internal read buffer: a native crash (SIGSEGV in
     * GetByteArrayRegion). Decoding from a private, immutable byte array avoids that entirely; the bounds
     * pass is a cheap header decode off the same bytes, so the shape gate still skips pixel work on
     * out-of-band pages.
     */
    fun sample(openStream: () -> InputStream): Sampled {
        val bytes = try {
            openStream().use { it.readBytes() }
        } catch (e: Exception) {
            return Sampled(null, isWide = false)
        }
        if (bytes.isEmpty()) return Sampled(null, isWide = false)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val isWide = bounds.outWidth > bounds.outHeight
        if (!Spreadfit.isEligibleShape(bounds.outWidth, bounds.outHeight)) return Sampled(null, isWide)

        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, bounds.outWidth / DOWNSCALE_WIDTH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return Sampled(null, isWide)

        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < 2 ||
                height < 1
            ) {
                Sampled(null, isWide)
            } else {
                Sampled(LumaPage(toGray(bitmap, width, height), width, height), isWide)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Whether a page is wide (`outWidth > outHeight`, matching [sample] and `ImageUtil.isWideImage`),
     * from a header-only read. For "treat wide pages as spreads" on pages past the gutter-sample window,
     * where only the wide flag is needed and a full [sample] would be wasteful.
     *
     * Reads a bounded prefix into a byte array and decodes the bounds from it, never `decodeStream` off
     * the page's stream, which races the reader's own decode of the same page (SIGSEGV), for the same
     * reason as [sample]. Falls back to the full stream when the dimensions aren't within the prefix (a
     * large leading metadata block). Returns false for an unreadable page, treated as not wide.
     */
    fun isWidePage(openStream: () -> InputStream): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val head = try {
            openStream().use { it.readPrefix(HEADER_BYTES) }
        } catch (e: Exception) {
            return false
        }
        if (head.isEmpty()) return false
        BitmapFactory.decodeByteArray(head, 0, head.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val full = try {
                openStream().use { it.readBytes() }
            } catch (e: Exception) {
                return false
            }
            BitmapFactory.decodeByteArray(full, 0, full.size, bounds)
        }
        return bounds.outWidth > bounds.outHeight
    }

    // Reads up to [max] bytes from the head of the stream into a right-sized array (shorter when the
    // stream is). One bounded read, so the bytes stay a private copy the way [sample]'s full read does.
    private fun InputStream.readPrefix(max: Int): ByteArray {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val read = read(buf, total, max - total)
            if (read < 0) break
            total += read
        }
        return if (total == max) buf else buf.copyOf(total)
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
