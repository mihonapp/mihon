package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.model.OcrScript
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File
import java.io.InputStream
import kotlin.math.ceil
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
    private val context: Context,
    private val translator: Translator,
    private val ocrEngine: OcrEngine,
    private val preferences: TranslationPreferences,
) {

    suspend fun translate(
        pageIndex: Int,
        openStream: (() -> InputStream)?,
    ): TranslatedPage = withContext(Dispatchers.IO) {
        val open = openStream ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
        val source = translator.sourceLanguage
        val target = translator.targetLanguage

        val file = cacheFile(pageIndex)
        try {
            open().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file.setReadOnly()

            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val decoder = try {
                BitmapRegionDecoder.newInstance(descriptor.fileDescriptor, false)
                    ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
            } catch (e: Exception) {
                descriptor.close()
                throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
            }

            try {
                val nativeWidth = decoder.width
                val nativeHeight = decoder.height
                if (nativeWidth <= 0 || nativeHeight <= 0) {
                    throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
                }

                val bitmap = decodeForDisplay(file, nativeWidth, nativeHeight)
                    ?: throw TranslationException(TranslationError.PAGE_NOT_AVAILABLE)
                // Dots of an ellipsis, page numbers and sound effects are recognized as "0000" or "…" all the
                // time; translating those only paints boxes over the art.
                val blocks = recognize(decoder, nativeWidth, nativeHeight, source)
                    .filter { it.text.count { char -> char.isLetter() } >= MIN_LETTERS }
                if (blocks.isEmpty()) throw TranslationException(TranslationError.NO_TEXT_FOUND)

                // Text positions are read at the OCR scale and have to be drawn on the displayed bitmap.
                val ratio = bitmap.width.toFloat() / nativeWidth
                // Line breaks and repeated spacing from the recognizer break the sentences the service sees.
                val cleaned = blocks.map { it.text.normalizeForTranslation() }
                val translation = translateAll(cleaned, source, target)
                val translatedBlocks = blocks.mapIndexedNotNull { index, block ->
                    val translated = translation.getOrNull(index)?.trim()
                    val hasLetters = translated != null && translated.any { it.isLetter() }
                    if (translated.isNullOrEmpty() || !hasLetters) {
                        null
                    } else {
                        TranslatedTextBlock(
                            box = block.box.scaled(ratio),
                            original = cleaned[index],
                            translated = translated,
                        )
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
            } finally {
                decoder.recycle()
                descriptor.close()
            }
        } finally {
            file.delete()
        }
    }

    private fun cacheFile(pageIndex: Int): File {
        val dir = File(context.cacheDir, "translation").apply { mkdirs() }
        return File(dir, "page-$pageIndex-${System.nanoTime()}.img")
    }

    /**
     * The page is shown at a readable size while keeping memory in check; the recognizer doesn't use this bitmap,
     * so it may be coarser than what is read.
     */
    private fun decodeForDisplay(file: File, nativeWidth: Int, nativeHeight: Int): Bitmap? {
        var sampleSize = 1
        while ((nativeWidth / sampleSize).toLong() * (nativeHeight / sampleSize) > DISPLAY_PIXEL_BUDGET) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // Otherwise the platform rescales the bitmap by the device density and every position drifts.
            inScaled = false
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private suspend fun recognize(
        decoder: BitmapRegionDecoder,
        nativeWidth: Int,
        nativeHeight: Int,
        source: TranslationLanguage,
    ): List<OcrBlock> {
        val script = source.ocrScript ?: run {
            if (source != TranslationLanguage.AUTO) {
                throw TranslationException(TranslationError.UNSUPPORTED_SOURCE_SCRIPT)
            }
            detectScript(decoder, nativeWidth, nativeHeight)
        }

        val wantedWidth = when (preferences.ocrQuality.get()) {
            1 -> TranslationPreferences.OCR_FAST_WIDTH
            3 -> TranslationPreferences.OCR_ACCURATE_WIDTH
            else -> TranslationPreferences.OCR_BALANCED_WIDTH
        }
        val sampleSize = max(1, ceil(nativeWidth / wantedWidth.toFloat()).toInt())
        val width = nativeWidth / sampleSize
        val height = nativeHeight / sampleSize

        return ocrEngine.recognizeTiles(width, height, script) { tile ->
            val region = Rect(
                tile.left * sampleSize,
                tile.top * sampleSize,
                tile.right * sampleSize,
                tile.bottom * sampleSize,
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
            }
            decoder.decodeRegion(region, options)?.let { OcrTile(it, release = true) }
        }
    }

    /**
     * "Auto" is the one source that has to guess, so a couple of slices are read first and the script the page is
     * actually written in is used for the real pass. Assuming Japanese used to leave English pages untranslated.
     */
    private suspend fun detectScript(
        decoder: BitmapRegionDecoder,
        nativeWidth: Int,
        nativeHeight: Int,
    ): OcrScript {
        val sampleSize = max(1, ceil(nativeWidth / DETECTION_WIDTH.toFloat()).toInt())
        val sliceHeight = min(nativeHeight, DETECTION_SLICE_HEIGHT * sampleSize)
        val starts = if (nativeHeight <= sliceHeight) {
            listOf(0)
        } else {
            listOf(0, (nativeHeight - sliceHeight) / 2, nativeHeight - sliceHeight)
        }

        var cjk = 0
        var hangul = 0
        var latin = 0
        starts.distinct().forEach { top ->
            val region = Rect(0, top, nativeWidth, top + sliceHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
            }
            val sample = decoder.decodeRegion(region, options) ?: return@forEach
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
                sample.recycle()
            }
        }

        return when {
            hangul > cjk + latin -> OcrScript.KOREAN
            cjk >= latin -> OcrScript.JAPANESE
            else -> OcrScript.LATIN
        }
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

    /**
     * Recognized lettering arrives with artificial line breaks and repeated spacing, which translation services turn
     * into broken sentences.
     */
    private fun String.normalizeForTranslation(): String = this
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\.{2,}|…+"), "…")
        .trim()

    private fun Rect.scaled(ratio: Float): Rect = Rect(
        (left * ratio).toInt(),
        (top * ratio).toInt(),
        (right * ratio).toInt(),
        (bottom * ratio).toInt(),
    )

    private fun Char.isCjk(): Boolean = this in '\u3040'..'\u30FF' || this in '\u3400'..'\u9FFF'

    companion object {
        /** Blocks with fewer letters are ellipsis dots, page numbers or noise, not dialogue. */
        private const val MIN_LETTERS = 2

        private const val DISPLAY_PIXEL_BUDGET = 8_000_000L
        private const val DETECTION_SLICE_HEIGHT = 900
        private const val DETECTION_WIDTH = 720
    }
}
