package tachiyomi.core.common.util.system.panel

/** Luminance image, row-major, one unsigned byte (0 = black, 255 = white) per pixel. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
    }
    fun lum(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF
}

/** Axis-aligned region normalised to the image, 0..1, left < right, top < bottom. */
data class PanelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** Finds regions of interest on a page. Implementations must be pure and thread-safe. A future ML bubble detector is expected to implement this interface. */
fun interface PanelDetector {
    fun detect(image: GrayImage): List<PanelRect>
}
