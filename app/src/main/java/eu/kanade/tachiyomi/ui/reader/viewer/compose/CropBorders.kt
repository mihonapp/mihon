package eu.kanade.tachiyomi.ui.reader.viewer.compose

import androidx.compose.ui.unit.IntRect
import ca.mpreg.imagedecoder.ImageDecoder
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Border detection runs on a copy shrunk to about this many pixels (4 MB of RGBA)
private const val BORDER_PIXELS = 1_000_000.0

// A line is content once this share of its sampled pixels stands out from the margin
private const val FILLED_RATIO_LIMIT = 0.0025
private const val BLACK_THRESHOLD = 64
private const val WHITE_THRESHOLD = 191

/**
 * The content of the decoder's image inside its borders, found on a shrunk copy - the full page could be hundreds
 * of MB - then each cropped edge again at full size, over the few lines around it.
 */
internal fun ImageDecoder.findContent(): IntRect {
    val sample = ceil(sqrt(width.toDouble() * height / BORDER_PIXELS)).toInt().coerceAtLeast(1)
    val found = decode(null, sample).use { frame ->
        findCropBorders(frame.image, frame.width, frame.height)
    }
    if (sample == 1) return found
    // A sampled pixel wider each way, so gaps between samples never cut content
    val coarse = IntRect(
        left = (found.left - 1).coerceAtLeast(0) * sample,
        top = (found.top - 1).coerceAtLeast(0) * sample,
        right = ((found.right + 1) * sample).coerceAtMost(width),
        bottom = ((found.bottom + 1) * sample).coerceAtMost(height),
    )
    // The true edge lies within two samples inside the slack one
    val band = 3 * sample
    val left = if (found.left > 0) {
        val right = (coarse.left + band).coerceAtMost(coarse.right)
        edge(IntRect(coarse.left, coarse.top, right, coarse.bottom), columns = true, fromEnd = false)
            ?.let { coarse.left + it }
    } else {
        null
    } ?: coarse.left
    val right = if (found.right * sample < width) {
        val from = (coarse.right - band).coerceAtLeast(left)
        edge(IntRect(from, coarse.top, coarse.right, coarse.bottom), columns = true, fromEnd = true)
            ?.let { from + it }
    } else {
        null
    } ?: coarse.right
    val top = if (found.top > 0) {
        val bottom = (coarse.top + band).coerceAtMost(coarse.bottom)
        edge(IntRect(left, coarse.top, right, bottom), columns = false, fromEnd = false)?.let { coarse.top + it }
    } else {
        null
    } ?: coarse.top
    val bottom = if (found.bottom * sample < height) {
        val from = (coarse.bottom - band).coerceAtLeast(top)
        edge(IntRect(left, from, right, coarse.bottom), columns = false, fromEnd = true)?.let { from + it }
    } else {
        null
    } ?: coarse.bottom
    return IntRect(left, top, right, bottom)
}

/**
 * Where content starts in [region] at full size, counted from its left or top: the first column or row that holds
 * any, or one past the last when [fromEnd]. Null to keep the coarse edge.
 */
private fun ImageDecoder.edge(region: IntRect, columns: Boolean, fromEnd: Boolean): Int? {
    if (region.width <= 0 || region.height <= 0) return null
    val rect = android.graphics.Rect(region.left, region.top, region.right, region.bottom)
    return decode(rect, 1).use { frame ->
        val w = frame.width
        val h = frame.height
        // From the buffer, not w: a region the codec made to size can keep its padded rows
        val stride = frame.image.capacity() / h
        if (stride < w * 4) return@use null
        if (columns) {
            val lines = Lines(frame.image, lineStride = 4, sampleStride = stride * 2)
            if (fromEnd) {
                lines.findBorder(start = w - 1, end = -1, from = 0, count = h)?.plus(1)
            } else {
                lines.findBorder(start = 0, end = w, from = 0, count = h)
            }
        } else {
            val lines = Lines(frame.image, lineStride = stride, sampleStride = 8)
            if (fromEnd) {
                lines.findBorder(start = h - 1, end = -1, from = 0, count = w)?.plus(1)
            } else {
                lines.findBorder(start = 0, end = h, from = 0, count = w)
            }
        }
    }
}

/**
 * Finds the content of a page inside plain white or black margins, from tightly packed RGBA [pixels].
 *
 * Each edge is scanned inward for the first line where enough pixels stand out from the margin, whose colour
 * is whichever of black or white fills most of the outermost line. An edge whose outermost line isn't mostly one
 * of them, or that has no content line, is left alone. Returns the whole image when there is nothing to crop.
 */
internal fun findCropBorders(pixels: ByteBuffer, width: Int, height: Int): IntRect {
    val rows = Lines(pixels, lineStride = width * 4, sampleStride = 8)
    val columns = Lines(pixels, lineStride = 4, sampleStride = width * 8)

    val top = rows.findBorder(start = 0, end = height, from = 0, count = width) ?: 0
    val bottom = rows.findBorder(start = height - 1, end = top - 1, from = 0, count = width)?.plus(1) ?: height
    if (bottom <= top) return IntRect(0, 0, width, height)
    val left = columns.findBorder(start = 0, end = width, from = top, count = bottom - top) ?: 0
    val right = columns.findBorder(start = width - 1, end = left - 1, from = top, count = bottom - top)
        ?.plus(1) ?: width
    if (right <= left) return IntRect(0, 0, width, height)
    return IntRect(left, top, right, bottom)
}

/**
 * Rows or columns of the image, read every other pixel: line n starts [lineStride] bytes after line n - 1, and
 * its samples are [sampleStride] bytes apart.
 */
private class Lines(private val pixels: ByteBuffer, private val lineStride: Int, private val sampleStride: Int) {

    /**
     * The first line from [start] towards [end] that holds content, or null to leave this edge uncropped. Each
     * line is read over [count] pixels from pixel [from].
     */
    fun findBorder(start: Int, end: Int, from: Int, count: Int): Int? {
        val step = if (end > start) 1 else -1
        val samples = (count + 1) / 2
        val filledLimit = (count * FILLED_RATIO_LIMIT / 2).roundToInt()
        val offset = from * (sampleStride / 2)

        var black = 0
        var white = 0
        var i = start * lineStride + offset
        repeat(samples) {
            val value = luma(i)
            if (value < BLACK_THRESHOLD) {
                black++
            } else if (value > WHITE_THRESHOLD) {
                white++
            }
            i += sampleStride
        }
        // Content is whatever stands out from the margin, so the outermost line must be mostly one colour
        val whiteMargin = when {
            white > samples / 2 && black <= filledLimit -> true
            black > samples / 2 && white <= filledLimit -> false
            else -> return null
        }

        var line = start + step
        while (line != end) {
            var filled = 0
            i = line * lineStride + offset
            repeat(samples) {
                val value = luma(i)
                if (if (whiteMargin) value <= WHITE_THRESHOLD else value >= BLACK_THRESHOLD) filled++
                i += sampleStride
            }
            if (filled > filledLimit) return line
            line += step
        }
        return null
    }

    private fun luma(i: Int): Int {
        val r = pixels.get(i).toInt() and 0xFF
        val g = pixels.get(i + 1).toInt() and 0xFF
        val b = pixels.get(i + 2).toInt() and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
