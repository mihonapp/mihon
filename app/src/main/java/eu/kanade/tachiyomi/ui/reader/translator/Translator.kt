package eu.kanade.tachiyomi.ui.reader.translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Translator(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val googleTranslate by lazy { GoogleTranslate(context) }

    data class TranslationResult(
        val original: String,
        val translated: String,
        val boundingBox: Rect,
    )

    suspend fun detectAndTranslate(bitmap: Bitmap, from: String, to: String): List<TranslationResult> {
        val visionText = suspendCoroutine { continuation ->
            textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    continuation.resume(null)
                }
        } ?: return emptyList()

        val textBlocks = visionText.textBlocks
        val originalTexts = textBlocks.map { it.text }

        if (originalTexts.isEmpty()) {
            return emptyList()
        }

        return try {
            val translatedTexts = withContext(Dispatchers.IO) {
                googleTranslate.translate(originalTexts, from, to)
            }

            textBlocks.mapIndexedNotNull { index, textBlock ->
                if (index < translatedTexts.size) {
                    TranslationResult(
                        original = textBlock.text,
                        translated = translatedTexts[index],
                        boundingBox = textBlock.boundingBox!!,
                    )
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                context.toast("Translation failed: ${e.message}")
            }
            emptyList()
        }
    }

    fun destroy() {
        googleTranslate.destroy()
    }
}
