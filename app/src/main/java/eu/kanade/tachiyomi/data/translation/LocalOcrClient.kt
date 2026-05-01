package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocalOcrClient {

    suspend fun recognize(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.Auto,
    ): List<OcrTextBlock> {
        val scripts = if (script == OcrScript.Auto) {
            listOf(OcrScript.Latin, OcrScript.Japanese, OcrScript.Chinese, OcrScript.Korean, OcrScript.Devanagari)
        } else {
            listOf(script)
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val blocks = mutableListOf<Text.TextBlock>()
        scripts.forEach { scriptToRun ->
            blocks += recognizerFor(scriptToRun).process(image).await().textBlocks
        }
        return blocks
            .mapIndexedNotNull { index, block -> block.toOcrTextBlock(index, bitmap.width, bitmap.height) }
            .distinctBy { "${it.text}:${it.x}:${it.y}:${it.width}:${it.height}" }
    }

    private fun recognizerFor(script: OcrScript): TextRecognizer {
        return when (script) {
            OcrScript.Auto,
            OcrScript.Latin -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.Chinese -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.Devanagari -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrScript.Japanese -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.Korean -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }
}

enum class OcrScript(val preferenceValue: String) {
    Auto("auto"),
    Latin("latin"),
    Chinese("chinese"),
    Devanagari("devanagari"),
    Japanese("japanese"),
    Korean("korean"),
    ;

    companion object {
        fun fromPreference(value: String): OcrScript {
            return entries.firstOrNull { it.preferenceValue == value } ?: Auto
        }
    }
}

private fun Text.TextBlock.toOcrTextBlock(index: Int, imageWidth: Int, imageHeight: Int): OcrTextBlock? {
    val box = boundingBox ?: return null
    if (text.isBlank() || imageWidth <= 0 || imageHeight <= 0) return null
    return OcrTextBlock(
        id = "ocr-$index",
        text = text,
        x = box.left.toFloat() / imageWidth,
        y = box.top.toFloat() / imageHeight,
        width = box.width().toFloat() / imageWidth,
        height = box.height().toFloat() / imageHeight,
    )
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
