package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

class PixelBuffer(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixels size must be width * height" }
    }

    fun luminanceAt(x: Int, y: Int): Int {
        val c = pixels[y * width + x]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}

fun Bitmap.toPixelBuffer(maxDimension: Int): PixelBuffer {
    val scale = min(1f, maxDimension.toFloat() / max(width, height))
    val targetWidth = max(1, (width * scale).toInt())
    val targetHeight = max(1, (height * scale).toInt())
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true) else this
    val pixels = IntArray(targetWidth * targetHeight)
    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    if (scaled !== this) scaled.recycle()
    return PixelBuffer(targetWidth, targetHeight, pixels)
}
