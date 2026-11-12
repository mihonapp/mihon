package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.domain.translation.model.OcrScript
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrBlock(
    val text: String,
    val box: Rect,
)

@Inject
@SingleIn(AppScope::class)
class OcrEngine {

    suspend fun recognize(image: Bitmap, script: OcrScript): List<OcrBlock> {
        val recognizer = recognizerFor(script)
        return try {
            val text = recognizer.process(InputImage.fromBitmap(image, 0)).awaitResult()
            text.textBlocks
                .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                .mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val content = block.text.trim()
                    if (content.isEmpty()) null else OcrBlock(content, box)
                }
        } finally {
            recognizer.close()
        }
    }

    private fun recognizerFor(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
