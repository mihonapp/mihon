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

        val bitmap = decode(stream) ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
        val blocks = recognize(bitmap, source)
        if (blocks.isEmpty()) throw TranslationException(TranslationError.NO_TEXT_FOUND)

        val translations = translateAll(blocks.map { it.text }, source, target)
        val translatedBlocks = blocks.mapIndexedNotNull { index, block ->
            val translated = translations.getOrNull(index)?.trim()
            if (translated.isNullOrEmpty()) {
                null
            } else {
                TranslatedTextBlock(box = block.box, original = block.text, translated = translated)
            }
        }
        if (translatedBlocks.isEmpty()) throw TranslationException(TranslationError.NO_TRANSLATION)

        TranslatedPage(
            pageIndex = pageIndex,
            bitmap = bitmap,
            blocks = translatedBlocks,
            source = source,
            target = target,
        )
    }

    /**
     * "Auto" is the one source that has to guess: the Japanese model reads Latin too, so it's used first and the
     * Latin model is only tried when the guess came back nearly empty.
     */
    private suspend fun recognize(bitmap: Bitmap, source: TranslationLanguage): List<OcrBlock> {
        source.ocrScript?.let { script -> return ocrEngine.recognize(bitmap, script) }

        if (source != TranslationLanguage.AUTO) {
            throw TranslationException(TranslationError.UNSUPPORTED_SOURCE_SCRIPT)
        }

        val japanese = ocrEngine.recognize(bitmap, OcrScript.JAPANESE)
        if (japanese.sumOf { it.text.length } >= AUTO_SCRIPT_ENOUGH_TEXT) return japanese

        val latin = ocrEngine.recognize(bitmap, OcrScript.LATIN)
        return if (latin.sumOf { it.text.length } > japanese.sumOf { it.text.length }) latin else japanese
    }

    /**
     * Translates every block, but doesn't let one bad response cost the whole page — anything that fails is simply
     * left untranslated instead of aborting the page.
     */
    private suspend fun translateAll(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String?> {
        return try {
            translator.translate(texts, source, target)
        } catch (e: TranslationException) {
            if (texts.size <= 1) throw e

            // If the first block fails as well the provider is unreachable, so there's nothing to salvage.
            val first = runCatching { translator.translate(listOf(texts.first()), source, target).first() }
                .getOrElse { throw e }
            listOf(first) + texts.drop(1).map { text ->
                runCatching { translator.translate(listOf(text), source, target).first() }.getOrNull()
            }
        }
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
        // Kept high enough that small lettering survives; the OCR step reads the page in tiles anyway.
        private const val MAX_DIMENSION = 4096
        private const val AUTO_SCRIPT_ENOUGH_TEXT = 80
    }
}
