package eu.kanade.tachiyomi.data.translation.overlay

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Independent, on-device OCR used only to verify whether the image model missed visible text. */
@Inject
@SingleIn(AppScope::class)
class OverlayTextDetector {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Text regions, one per bubble or text box: the lines OCR finds are merged by [TextRegionGrouper], and fragments
     * that are not text are dropped. Recovering a missed bubble costs one request per region.
     */
    suspend fun detect(bitmap: Bitmap): List<DetectedTextRegion> {
        val lines = recognize(bitmap).textBlocks.flatMap { block ->
            val blockLines = block.lines.mapNotNull { line ->
                val rect = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                if (text.isBlank() || rect.width() <= 0 || rect.height() <= 0) return@mapNotNull null
                DetectedTextRegion(PixelRect(rect.left, rect.top, rect.right, rect.bottom), text)
            }
            if (blockLines.isNotEmpty()) {
                blockLines
            } else {
                val rect = block.boundingBox
                val text = block.text.trim()
                if (rect == null || text.isBlank() || rect.width() <= 0 || rect.height() <= 0) {
                    emptyList()
                } else {
                    listOf(DetectedTextRegion(PixelRect(rect.left, rect.top, rect.right, rect.bottom), text))
                }
            }
        }
        return TextRegionGrouper.regionsFrom(lines)
    }

    private suspend fun recognize(bitmap: Bitmap) = suspendCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                continuation.resume(result)
            }
            .addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
    }
}
