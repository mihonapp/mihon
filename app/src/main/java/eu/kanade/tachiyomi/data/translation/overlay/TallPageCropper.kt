package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Geometry and overlap handling for vertically cropped tall manga pages. */
object TallPageCropper {

    data class Crop(val top: Int, val bottom: Int) {
        val height: Int get() = bottom - top
    }

    data class Detection(val crop: Crop, val box: TextBox)

    /**
     * Pages are split only when the whole-page upload would make dialogue unusually small. Crops stay below the
     * upload side limit after preparation and overlap enough to keep bubbles near crop edges intact.
     */
    fun plan(width: Int, height: Int): List<Crop> {
        require(width > 0 && height > 0)
        if (height < MIN_PAGE_HEIGHT || height.toFloat() / width < MIN_ASPECT_RATIO) {
            return listOf(Crop(0, height))
        }

        val strideCapacity = MAX_CROP_HEIGHT - OVERLAP_PIXELS
        val count = ceilDiv(height - OVERLAP_PIXELS, strideCapacity).coerceAtLeast(2)
        val cropHeight = ceilDiv(height + (count - 1) * OVERLAP_PIXELS, count)
        val stride = cropHeight - OVERLAP_PIXELS
        val result = mutableListOf<Crop>()
        var top = 0
        while (top + cropHeight < height) {
            result += Crop(top, top + cropHeight)
            top += stride
        }
        val lastTop = max(0, height - cropHeight)
        if (result.lastOrNull()?.top != lastTop) result += Crop(lastTop, height)
        return result
    }

    /** Map a crop-relative normalized box back to the original full-page normalized coordinates. */
    fun mapToPage(box: TextBox, crop: Crop, pageHeight: Int): TextBox {
        require(pageHeight > 0 && crop.top >= 0 && crop.bottom <= pageHeight && crop.height > 0)
        val yMin = ((crop.top + crop.height * (box.yMin / TextBox.GRID)) / pageHeight * TextBox.GRID)
            .roundToInt().coerceIn(0, 999)
        val yMax = ((crop.top + crop.height * (box.yMax / TextBox.GRID)) / pageHeight * TextBox.GRID)
            .roundToInt().coerceIn(yMin + 1, 1000)
        return box.copy(yMin = yMin, yMax = yMax)
    }

    /**
     * Merge duplicate detections of the same translated text from overlapping crops. When either crop only sees a
     * bubble near its edge, prefer the detection farther from the crop boundary, where more context was available.
     */
    fun mapAndDeduplicate(detections: List<Detection>, pageWidth: Int, pageHeight: Int): List<TextBox> {
        data class Mapped(val detection: Detection, val box: TextBox)
        val kept = mutableListOf<Mapped>()
        for (detection in detections) {
            val mappedBox = mapToPage(detection.box, detection.crop, pageHeight)
            val duplicateIndex = kept.indexOfFirst { previous ->
                similarText(duplicateKey(previous.box), duplicateKey(mappedBox)) &&
                    overlapCoverage(previous.box, mappedBox, pageWidth, pageHeight) >= MIN_DUPLICATE_OVERLAP
            }
            if (duplicateIndex < 0) {
                kept += Mapped(detection, mappedBox)
            } else if (boundaryClearance(detection.box) > boundaryClearance(kept[duplicateIndex].detection.box)) {
                kept[duplicateIndex] = Mapped(detection, mappedBox)
            }
        }
        return kept.map { it.box }
    }

    private fun duplicateKey(box: TextBox): String = box.sourceText.takeIf { it.isNotBlank() } ?: box.text

    private fun boundaryClearance(box: TextBox): Int = min(box.yMin, 1000 - box.yMax)

    private fun overlapCoverage(a: TextBox, b: TextBox, width: Int, height: Int): Double {
        val ar = a.toPixelRect(width, height)
        val br = b.toPixelRect(width, height)
        val intersectionWidth = max(0, min(ar.right, br.right) - max(ar.left, br.left))
        val intersectionHeight = max(0, min(ar.bottom, br.bottom) - max(ar.top, br.top))
        val intersection = intersectionWidth.toLong() * intersectionHeight
        val smallerArea = min(ar.area, br.area)
        return if (smallerArea <= 0L) 0.0 else intersection.toDouble() / smallerArea
    }

    private fun similarText(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        val longest = max(left.length, right.length)
        if (min(left.length, right.length).toDouble() / longest < MIN_TEXT_LENGTH_RATIO) return false
        return 1.0 - editDistance(left, right).toDouble() / longest >= MIN_TEXT_SIMILARITY
    }

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), substitution)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

    private const val MIN_PAGE_HEIGHT = 3_000
    private const val MIN_ASPECT_RATIO = 4.5f
    private const val MAX_CROP_HEIGHT = 2_400
    private const val OVERLAP_PIXELS = 320
    private const val MIN_DUPLICATE_OVERLAP = 0.45
    private const val MIN_TEXT_LENGTH_RATIO = 0.65
    private const val MIN_TEXT_SIMILARITY = 0.75
}
