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
import kotlin.math.min

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
     * "Auto" is the one source that has to guess, so a couple of slices are read first with the Japanese model —
     * it picks up Latin and Korean text as well — and the script the page is actually written in is used for the
     * real pass. Guessing Japanese for an English page used to leave most bubbles untranslated.
     */
    private suspend fun recognize(bitmap: Bitmap, source: TranslationLanguage): List<OcrBlock> {
        source.ocrScript?.let { script -> return ocrEngine.recognize(bitmap, script) }

        if (source != TranslationLanguage.AUTO) {
            throw TranslationException(TranslationError.UNSUPPORTED_SOURCE_SCRIPT)
        }

        return ocrEngine.recognize(bitmap, detectScript(bitmap))
    }

    private suspend fun detectScript(bitmap: Bitmap): OcrScript {
        val sliceHeight = min(bitmap.height, DETECTION_SLICE_HEIGHT)
        val tops = if (bitmap.height <= sliceHeight) {
            listOf(0)
        } else {
            listOf(0, (bitmap.height - sliceHeight) / 2, bitmap.height - sliceHeight)
        }

        var cjk = 0
        var hangul = 0
        var latin = 0
        tops.distinct().forEach { top ->
            val slice = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, sliceHeight)
            val sample = if (slice.width > DETECTION_WIDTH) {
                Bitmap.createScaledBitmap(slice, DETECTION_WIDTH, (slice.height * DETECTION_WIDTH / slice.width).coerceAtLeast(1), true)
            } else {
                slice
            }
            try {
                ocrEngine.recognize(sample, OcrScript.JAPANESE).forEach { block ->
                    block.text.forEach { char ->
                        when {
                            char in '\uAC00'..'\uD7AF' -> hangul++
                            char.isCjk() -> cjk++
                            char in 'a'..'z' || char in 'A'..'Z' -> latin++
                        }
                    }
                }
            } finally {
                if (sample !== slice) sample.recycle()
                if (slice !== bitmap) slice.recycle()
            }
        }

        return when {
            hangul > (cjk + latin) -> OcrScript.KOREAN
            cjk >= latin -> OcrScript.JAPANESE
            else -> OcrScript.LATIN
        }
    }

    private fun Char.isCjk(): Boolean =
        this in '\u3040'..'\u30FF' || this in '\u3400'..'\u9FFF'

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
        private const val DETECTION_SLICE_HEIGHT = 900
        private const val DETECTION_WIDTH = 720
    }
}
