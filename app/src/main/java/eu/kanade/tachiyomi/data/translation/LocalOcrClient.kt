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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocalOcrClient {

    suspend fun recognize(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.Auto,
    ): List<OcrTextBlock> {
        return recognizeDetailed(bitmap, script).blocks
    }

    suspend fun recognizeDetailed(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.Auto,
    ): OcrRecognitionReport {
        val scripts = if (script == OcrScript.Auto) {
            listOf(OcrScript.Latin, OcrScript.Japanese, OcrScript.Chinese, OcrScript.Korean, OcrScript.Devanagari)
        } else {
            listOf(script)
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val blocks = mutableListOf<Text.TextBlock>()
        val results = mutableListOf<OcrScriptResult>()
        scripts.forEach { scriptToRun ->
            val recognizer = recognizerFor(scriptToRun)
            try {
                val textBlocks = recognizer.process(image).await().textBlocks
                blocks += textBlocks
                results += OcrScriptResult(
                    script = scriptToRun.preferenceValue,
                    success = true,
                    blocks = textBlocks.size,
                    error = null,
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                results += OcrScriptResult(
                    script = scriptToRun.preferenceValue,
                    success = false,
                    blocks = 0,
                    error = e.message ?: e::class.simpleName.orEmpty(),
                )
                if (script != OcrScript.Auto) {
                    throw e
                }
            } finally {
                recognizer.close()
            }
        }
        if (script == OcrScript.Auto && results.isNotEmpty() && results.none { it.success }) {
            error(
                results.joinToString(
                    prefix = "All OCR scripts failed: ",
                    separator = "; ",
                ) { "${it.script}=${it.error.orEmpty()}" },
            )
        }
        return OcrRecognitionReport(
            blocks = blocks
                .mapIndexedNotNull { index, block -> block.toOcrTextBlock(index, bitmap.width, bitmap.height) }
                .distinctBy { "${it.text}:${it.x}:${it.y}:${it.width}:${it.height}" },
            scriptResults = results,
        )
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

data class OcrRecognitionReport(
    val blocks: List<OcrTextBlock>,
    val scriptResults: List<OcrScriptResult>,
)

data class OcrScriptResult(
    val script: String,
    val success: Boolean,
    val blocks: Int,
    val error: String?,
)

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

private suspend fun <T : Any> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                if (result == null) {
                    continuation.resumeWithException(NullPointerException("Task returned null result"))
                } else {
                    continuation.resume(result)
                }
            }
        }
        addOnFailureListener {
            if (continuation.isActive) {
                continuation.resumeWithException(it)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel(CancellationException("Task was cancelled"))
            }
        }
    }
}
