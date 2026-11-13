package tachiyomi.translation.ocr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Preprocessing for comic lettering, following RapidOCR's pipeline before recognition: upscale
 * crops that are too small to survive the resize to 48px, and keep colour so outlines survive.
 */
object OcrImagePreprocessor {
    private const val CTC_MIN_SIDE = 64
    private const val MAX_UPSCALE = 3f
    private const val PADDING = 12

    /** Keeps colour but guarantees the crop is big enough to survive the resize to 48px. */
    fun forCtc(source: Bitmap): Bitmap {
        val shortest = minOf(source.width, source.height)
        if (shortest >= CTC_MIN_SIDE) return source
        val scale = (CTC_MIN_SIDE.toFloat() / shortest).coerceAtMost(MAX_UPSCALE)
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun upscaleFactor(currentHeight: Int, target: Int): Float {
        if (currentHeight >= target) return 1f
        return (target.toFloat() / currentHeight).coerceIn(1f, MAX_UPSCALE)
    }

    private class Gray(val bitmap: Bitmap, val histogram: IntArray)

    private fun toGrayscale(source: Bitmap): Gray {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            histogram[luma]++
            out[i] = Color.rgb(luma, luma, luma)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(out, 0, width, 0, 0, width, height)
        return Gray(bitmap, histogram)
    }

    private fun binarize(source: Bitmap, threshold: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val black = Color.BLACK
        val white = Color.WHITE
        for (i in pixels.indices) {
            val luma = pixels[i] and 0xFF
            pixels[i] = if (luma <= threshold) black else white
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun pad(source: Bitmap, padding: Int): Bitmap {
        val width = source.width + padding * 2
        val height = source.height + padding * 2
        val padded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        source.recycle()
        return padded
    }

    /** Otsu's method; far more reliable than a fixed threshold on shaded or coloured bubbles. */
    fun otsu(histogram: IntArray): Int {
        val total = histogram.sum()
        if (total == 0) return 127
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * histogram[i]
        var sumB = 0.0
        var weightB = 0
        var best = 0.0
        var threshold = 127
        for (i in 0..255) {
            weightB += histogram[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i.toDouble() * histogram[i]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold
    }
}
