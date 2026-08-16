package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sub-stops for a wide panel based on where its dialogue text is, using on-device OCR
 * to find text-block bounding boxes rather than a trained bubble-detection model. Falls
 * back to [GeometricPanelSubStopGenerator] whenever OCR finds nothing or times out.
 */
class TextAwarePanelSubStopGenerator(
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : PanelSubStopGenerator {

    override suspend fun generate(
        panel: PanelRect,
        direction: PanelDirection,
        cropPanel: suspend () -> Bitmap?,
    ): List<PanelRect> {
        if (panel.height <= 0f || panel.width / panel.height < WIDE_ASPECT_THRESHOLD) return emptyList()

        val bitmap = cropPanel() ?: return GeometricPanelSubStopGenerator.generate(panel, direction, cropPanel)
        val textBlocks = detectTextBlocks(bitmap, panel)
        if (textBlocks.isEmpty()) return GeometricPanelSubStopGenerator.generate(panel, direction, cropPanel)

        val clusters = PanelTextClustering.clusterByGap(textBlocks, panel.width).take(MAX_STOPS)
        val stops = clusters.map { cluster -> boundingRect(cluster, panel) }
        val ordered = if (direction == PanelDirection.RTL) stops.reversed() else stops
        return ordered + panel
    }

    /** Releases the native resources held by the OCR recognizer. */
    fun close() {
        recognizer.close()
    }

    private suspend fun detectTextBlocks(bitmap: Bitmap, panel: PanelRect): List<PanelRect> {
        val text = withTimeoutOrNull(OCR_TIMEOUT_MS) { recognize(bitmap) } ?: return emptyList()
        return text.textBlocks.mapNotNull { block ->
            block.boundingBox?.let { toPanelLocalRect(it, bitmap.width, bitmap.height, panel) }
        }
    }

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun toPanelLocalRect(box: Rect, bitmapWidth: Int, bitmapHeight: Int, panel: PanelRect): PanelRect {
        return PanelRect(
            left = panel.left + panel.width * (box.left / bitmapWidth.toFloat()),
            top = panel.top + panel.height * (box.top / bitmapHeight.toFloat()),
            right = panel.left + panel.width * (box.right / bitmapWidth.toFloat()),
            bottom = panel.top + panel.height * (box.bottom / bitmapHeight.toFloat()),
        )
    }

    private fun boundingRect(cluster: List<PanelRect>, panel: PanelRect): PanelRect {
        val padding = 0.05f * panel.width
        return PanelRect(
            left = (cluster.minOf { it.left } - padding).coerceAtLeast(panel.left),
            top = panel.top,
            right = (cluster.maxOf { it.right } + padding).coerceAtMost(panel.right),
            bottom = panel.bottom,
        )
    }

    companion object {
        private const val WIDE_ASPECT_THRESHOLD = 2f
        private const val OCR_TIMEOUT_MS = 3000L
        private const val MAX_STOPS = 4
    }
}
