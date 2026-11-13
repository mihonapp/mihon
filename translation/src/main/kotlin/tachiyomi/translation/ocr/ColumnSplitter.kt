package tachiyomi.translation.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Splits a bubble crop into columns (vertical Japanese) or rows (horizontal Korean/English).
 *
 * Uses a projection profile: a text line is a run of pixels that differ from the crop's mean
 * luminance, and the gaps between runs are the line separators. Cheap and model free, which
 * matters because the user's box is the only layout information available.
 *
 * Falls back to the whole crop whenever the profile is not convincing (art lettering, coloured
 * bubbles, sound effects), so the caller always gets at least one unit to recognize.
 */
object ColumnSplitter {
    private const val ANALYSIS_MAX_SIDE = 720
    private const val MIN_GAP_RATIO = 0.012f
    private const val MAX_UNITS = 8

    data class Unit(val bitmap: Bitmap, val startFraction: Float, val endFraction: Float)

    fun split(source: Bitmap, vertical: Boolean, trySplit: Boolean = true): List<Unit> {
        if (!trySplit || source.width < 24 || source.height < 24) {
            return listOf(Unit(source, 0f, 1f))
        }

        val scale = min(
            1f,
            ANALYSIS_MAX_SIDE.toFloat() / max(source.width, source.height),
        )
        val analysis = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }

        val ranges = runCatching { findRuns(analysis, vertical) }.getOrNull()
        if (analysis !== source) analysis.recycle()

        if (ranges == null || ranges.size <= 1 || ranges.size > MAX_UNITS) {
            return listOf(Unit(source, 0f, 1f))
        }

        val length = if (vertical) source.width else source.height
        val units = ranges.mapNotNull { (start, end) ->
            val from = (start * length).roundToInt().coerceIn(0, length)
            val to = (end * length).roundToInt().coerceIn(0, length)
            if (to - from < 8) return@mapNotNull null
            val bitmap = if (vertical) {
                Bitmap.createBitmap(source, from, 0, to - from, source.height)
            } else {
                Bitmap.createBitmap(source, 0, from, source.width, to - from)
            }
            Unit(bitmap, from.toFloat() / length, to.toFloat() / length)
        }

        return units.ifEmpty { listOf(Unit(source, 0f, 1f)) }
    }

    /** Returns normalized runs of text along the split axis. */
    private fun findRuns(bitmap: Bitmap, vertical: Boolean): List<Pair<Float, Float>>? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val histogram = IntArray(256)
        val lumas = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            lumas[i] = luma
            histogram[luma]++
        }

        // Otsu adapts to shaded and coloured bubbles; the minority side is the ink.
        val threshold = OcrImagePreprocessor.otsu(histogram)
        var dark = 0
        for (i in 0..threshold) dark += histogram[i]
        val inkIsDark = dark <= pixels.size / 2

        val axisLength = if (vertical) bitmap.width else bitmap.height
        val crossLength = if (vertical) bitmap.height else bitmap.width
        val profile = IntArray(axisLength)

        for (y in 0 until bitmap.height) {
            val rowBase = y * bitmap.width
            for (x in 0 until bitmap.width) {
                val luma = lumas[rowBase + x]
                val isInk = if (inkIsDark) luma <= threshold else luma > threshold
                if (isInk) {
                    if (vertical) profile[x]++ else profile[y]++
                }
            }
        }

        val minInk = (crossLength * 0.02f).roundToInt().coerceAtLeast(1)
        val minGap = (axisLength * MIN_GAP_RATIO).roundToInt().coerceAtLeast(2)

        val runs = mutableListOf<Pair<Int, Int>>()
        var runStart = -1
        var gap = 0
        for (i in profile.indices) {
            if (profile[i] >= minInk) {
                if (runStart < 0) runStart = i
                gap = 0
            } else if (runStart >= 0) {
                gap++
                if (gap >= minGap) {
                    runs += runStart to (i - gap + 1)
                    runStart = -1
                    gap = 0
                }
            }
        }
        if (runStart >= 0) runs += runStart to axisLength

        return runs
            .filter { it.second - it.first >= 4 }
            .map { (start, end) -> start.toFloat() / axisLength to end.toFloat() / axisLength }
            .takeIf { it.isNotEmpty() }
    }
}

/**
 * Decodes only the requested region at full source resolution, so a box drawn on a zoomed-out page
 * still yields a sharp crop.
 */
object BitmapCropper {
    fun decodeRegion(
        filePath: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        maxSide: Int = 1600,
    ): Bitmap? {
        if (right <= left || bottom <= top) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val l = left.coerceIn(0, bounds.outWidth - 1)
        val t = top.coerceIn(0, bounds.outHeight - 1)
        val r = right.coerceIn(l + 1, bounds.outWidth)
        val b = bottom.coerceIn(t + 1, bounds.outHeight)
        val region = android.graphics.Rect(l, t, r, b)

        val shortest = min(r - l, b - t)
        val sample = if (shortest > maxSide) {
            max(1, shortest / maxSide)
        } else {
            1
        }

        return runCatching {
            val decoder = android.graphics.BitmapRegionDecoder.newInstance(filePath, false)
                ?: return@runCatching null
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                decoder.decodeRegion(region, options)
            } finally {
                decoder.recycle()
            }
        }.getOrNull()
    }

    /** Whole page at a bounded size, used by the full-page scanner. */
    fun decodeWhole(filePath: String, maxSide: Int = 1600): Bitmap? {
        val bounds = boundsOf(filePath) ?: return null
        val longest = max(bounds.first, bounds.second)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(filePath, options) }.getOrNull()
    }

    fun boundsOf(filePath: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }
}
