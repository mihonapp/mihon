package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.model.OcrScript
import tachiyomi.domain.translation.model.TranslationLanguage
import java.io.InputStream
import kotlin.math.max

data class TranslatedTextBlock(
    val box: Rect,
    val original: String,
    val translated: String,
)

class TranslatedPage(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val blocks: List<TranslatedTextBlock>,
    val source: TranslationLanguage,
    val target: TranslationLanguage,
)

@Inject
@SingleIn(AppScope::class)
class PageTranslator(
    private val translator: Translator,
    private val ocrEngine: OcrEngine,
) {

    suspend fun translate(
        pageIndex: Int,
        openStream: (() -> InputStream)?,
    ): TranslatedPage = withContext(Dispatchers.IO) {
        val stream = openStream ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
        val source = translator.sourceLanguage
        val target = translator.targetLanguage
        val script = source.ocrScript ?: when (source) {
            // Reading the text is the only step that needs the language up front, and the Japanese model
            // recognizes Latin characters too, so it's the safest guess when the source isn't set.
            TranslationLanguage.AUTO -> OcrScript.JAPANESE
            else -> throw TranslationException(TranslationError.UNSUPPORTED_SOURCE_SCRIPT)
        }

        val bitmap = decode(stream) ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
        val blocks = ocrEngine.recognize(bitmap, script)
        if (blocks.isEmpty()) throw TranslationException(TranslationError.NO_TEXT_FOUND)

        val translations = translator.translate(blocks.map { it.text }, source, target)
        TranslatedPage(
            pageIndex = pageIndex,
            bitmap = bitmap,
            blocks = blocks.mapIndexed { index, block ->
                TranslatedTextBlock(
                    box = block.box,
                    original = block.text,
                    translated = translations.getOrElse(index) { "" },
                )
            },
            source = source,
            target = target,
        )
    }

    private fun decode(openStream: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return openStream().use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        // More pixels don't help the models and cost a lot of memory on long strip pages.
        private const val MAX_DIMENSION = 2048
    }
}
