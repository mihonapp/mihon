package tachiyomi.translation.ocr

import android.graphics.Bitmap

data class OcrLine(
    val text: String,
    val confidence: Float,
)

data class OcrResult(
    val lines: List<OcrLine>,
) {
    val text: String get() = lines.joinToString("\n") { it.text }

    val confidence: Float
        get() = if (lines.isEmpty()) 0f else lines.sumOf { it.confidence.toDouble() }.toFloat() / lines.size

    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }
}

/**
 * Recognizes a crop that contains a single column (vertical text) or a single line (horizontal).
 * Splitting a bubble into those units is [ColumnSplitter]'s job, not the engine's.
 */
fun interface OcrEngine {
    suspend fun recognize(crop: Bitmap, vertical: Boolean): OcrResult
}
