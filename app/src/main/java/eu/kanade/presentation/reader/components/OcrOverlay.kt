package eu.kanade.presentation.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.ocr.OcrResultBlock
import kotlin.math.min

/**
 * Renders translated text overlays covering original manga speech bubbles.
 *
 * Coordinate mapping modes:
 *
 *  A) SSIV mode — [ssivScale] > 0:
 *     SubsamplingScaleImageView exposes [scale] and [center] (source-space point shown
 *     at the viewport center). We replicate its internal vTranslate formula:
 *
 *       vTranslate.x = viewWidth  / 2  -  center.x * ssivScale
 *       vTranslate.y = viewHeight / 2  -  center.y * ssivScale
 *       screenX      = sourceX * ssivScale + vTranslate.x
 *       screenY      = sourceY * ssivScale + vTranslate.y
 *
 *     This is identical to SubsamplingScaleImageView.sourceToViewCoord() and therefore
 *     the overlay boxes are ALWAYS pixel-perfect regardless of zoom or pan.
 *
 *  B) Fallback mode — [ssivScale] == 0:
 *     For animated images / PhotoView / unavailable SSIV, we fall back to a simple
 *     fitCenter calculation so there is always something visible.
 */
@Composable
fun OcrOverlay(
    ocrResult: List<OcrResultBlock>,
    imageSize: IntSize,
    /** Full SSIV scale — same value as SubsamplingScaleImageView.scale. 0 = use fallback. */
    ssivScale: Float = 0f,
    /** Source-image X coordinate currently shown at the horizontal center of the view. */
    ssivCenterX: Float = 0f,
    /** Source-image Y coordinate currently shown at the vertical center of the view. */
    ssivCenterY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height

        if (imageSize.width <= 0 || imageSize.height <= 0) return@Canvas

        // ── Compute image-to-screen transform ────────────────────────────────
        val (imgLeft, imgTop, drawScale) = if (ssivScale > 0f) {
            // Mode A: replicate SubsamplingScaleImageView.sourceToViewCoord()
            val vTranslateX = viewW / 2f - ssivCenterX * ssivScale
            val vTranslateY = viewH / 2f - ssivCenterY * ssivScale
            Triple(vTranslateX, vTranslateY, ssivScale)
        } else {
            // Mode B: fitCenter fallback
            val fitScale = min(viewW / imageSize.width, viewH / imageSize.height)
            val fittedW = imageSize.width * fitScale
            val fittedH = imageSize.height * fitScale
            Triple((viewW - fittedW) / 2f, (viewH - fittedH) / 2f, fitScale)
        }

        // ── Draw one box per OCR block ────────────────────────────────────────
        ocrResult.forEach { block ->
            val box = block.boundingBox ?: return@forEach
            if (block.text.isBlank()) return@forEach

            // Map bounding box from source-image pixels → screen pixels
            val left   = imgLeft + box.left   * drawScale
            val top    = imgTop  + box.top    * drawScale
            val right  = imgLeft + box.right  * drawScale
            val bottom = imgTop  + box.bottom * drawScale

            // Clamp to at least 8px so tiny boxes are still visible
            val rectW = (right  - left).coerceAtLeast(8f)
            val rectH = (bottom - top ).coerceAtLeast(8f)

            // Solid black fill — completely covers the original bubble text
            drawRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
            )

            // Auto-fit translated text inside the bounding box with minimal padding
            val padding = 2f
            val availW = (rectW - padding * 2).coerceAtLeast(1f)
            val availH = (rectH - padding * 2).coerceAtLeast(1f)

            val fittedMeasure = fitTextToBox(
                textMeasurer = textMeasurer,
                text = block.text,
                availableWidth = availW.toInt(),
                availableHeight = availH.toInt(),
            )

            drawText(
                textLayoutResult = fittedMeasure,
                topLeft = Offset(left + padding, top + padding),
            )
        }
    }
}

/**
 * Finds the largest font size where [text] fits inside ([availableWidth] x [availableHeight]).
 * Starts at 16sp and decrements until it fits, stopping at a minimum of 5sp.
 * Uses tighter line spacing for long text and allows text wrapping.
 */
private fun fitTextToBox(
    textMeasurer: TextMeasurer,
    text: String,
    availableWidth: Int,
    availableHeight: Int,
): androidx.compose.ui.text.TextLayoutResult {
    val constraints = Constraints(maxWidth = availableWidth, maxHeight = Int.MAX_VALUE)

    // Try sizes from large to tiny until text fits height
    for (size in 18 downTo 4) {
        val style = TextStyle(
            color = Color.White,
            fontSize = size.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = (size * 1.15f).sp,
        )
        val measured = textMeasurer.measure(
            text = text,
            style = style,
            softWrap = true,
            constraints = constraints,
        )
        if (measured.size.height <= availableHeight) {
            return measured
        }
    }

    // Last resort: smallest possible
    return textMeasurer.measure(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = 4.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 4.5.sp,
        ),
        softWrap = true,
        constraints = constraints,
    )
}
