package eu.kanade.tachiyomi.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition

import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Model representation of detected text with its bounding box
data class OcrResultBlock(val text: String, val boundingBox: Rect?)

class RecognizeTextUseCase {

    // ── Lazy-initialised recognisers ─────────────────────────────────────────
    private var recognizerJa: com.google.mlkit.vision.text.TextRecognizer? = null
    private var recognizerKo: com.google.mlkit.vision.text.TextRecognizer? = null
    private var recognizerZh: com.google.mlkit.vision.text.TextRecognizer? = null
    private var recognizerDe: com.google.mlkit.vision.text.TextRecognizer? = null  // Devanagari (hi/bn)
    private var recognizerLatin: com.google.mlkit.vision.text.TextRecognizer? = null

    private fun getJa(): com.google.mlkit.vision.text.TextRecognizer {
        if (recognizerJa == null) recognizerJa = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        return recognizerJa!!
    }

    private fun getKo(): com.google.mlkit.vision.text.TextRecognizer {
        if (recognizerKo == null) recognizerKo = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        return recognizerKo!!
    }

    private fun getZh(): com.google.mlkit.vision.text.TextRecognizer {
        if (recognizerZh == null) recognizerZh = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return recognizerZh!!
    }

    private fun getDe(): com.google.mlkit.vision.text.TextRecognizer {
        if (recognizerDe == null) recognizerDe = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        return recognizerDe!!
    }

    private fun getLatin(): com.google.mlkit.vision.text.TextRecognizer {
        if (recognizerLatin == null) recognizerLatin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return recognizerLatin!!
    }

    /**
     * Maps a language code to the appropriate ML Kit recogniser(s).
     *
     * ML Kit recogniser coverage:
     *  Japanese  → JapaneseTextRecognizer
     *  Korean    → KoreanTextRecognizer
     *  Chinese   → ChineseTextRecognizer  (zh, zh-CN, zh-TW)
     *  Hindi/Bengali → DevanagariTextRecognizer (hi, bn, mr, ne)
     *  Arabic/Russian/Latin languages → Latin recogniser (best available fallback)
     *  auto      → run all 5 recognisers and deduplicate
     */
    private fun recognisersFor(langCode: String): List<com.google.mlkit.vision.text.TextRecognizer> =
        when (langCode.lowercase().take(2)) {
            "ja" -> listOf(getJa())
            "ko" -> listOf(getKo())
            "zh" -> listOf(getZh())
            "hi", "bn", "mr", "ne" -> listOf(getDe())
            "au" -> listOf(getJa(), getKo(), getZh(), getDe(), getLatin()) // "auto"
            else -> listOf(getLatin()) // en, es, fr, de, pt, ru, ar, tr, id, vi, it, …
        }

    /**
     * Unloads native ML Kit models to reclaim memory.
     */
    fun close() {
        listOf(recognizerJa, recognizerKo, recognizerZh, recognizerDe, recognizerLatin)
            .forEach { it?.close() }
        recognizerJa = null; recognizerKo = null; recognizerZh = null
        recognizerDe = null; recognizerLatin = null
    }

    /**
     * Extracts text blocks and bounding boxes from [bitmap].
     * When [langCode] is "auto", all recognisers run sequentially and results are deduplicated.
     */
    suspend fun await(bitmap: Bitmap, langCode: String): List<OcrResultBlock> {
        return withContext(Dispatchers.Default) {
            val allBlocks = mutableListOf<OcrResultBlock>()
            val seenTexts = mutableSetOf<String>()

            // Convert to grayscale and apply threshold for better OCR on stylized text
            val processedBitmap = toGrayscaleAndThreshold(bitmap)

            for (recognizer in recognisersFor(langCode)) {
                try {
                    val image = InputImage.fromBitmap(processedBitmap, 0)
                    val mlKitText = suspendCancellableCoroutine<Text> { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }

                    mlKitText.textBlocks.forEach { block ->
                        val normalised = block.text.trim()
                        if (normalised.isNotBlank() && seenTexts.add(normalised)) {
                            allBlocks.add(OcrResultBlock(text = normalised, boundingBox = block.boundingBox))
                        }
                    }
                } catch (_: Exception) {
                    // Skip failing recognisers silently; another one may succeed
                }
            }
            
            if (processedBitmap != bitmap && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
            
            // Merge blocks that are close to each other (same speech bubble)
            groupProximityBlocks(allBlocks)
        }
    }
    
    /**
     * Converts bitmap to grayscale and applies adaptive threshold for better OCR.
     * Makes text more distinct from background, especially for italic/bold fonts.
     */
    private fun toGrayscaleAndThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                
                // Convert to grayscale
                val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                
                // Apply threshold: make dark pixels black, light pixels white
                val threshold = if (gray < 128) 0 else 255
                val newPixel = android.graphics.Color.rgb(threshold, threshold, threshold)
                result.setPixel(x, y, newPixel)
            }
        }
        
        return result
    }

    /**
     * Merges bounding boxes that are vertically or horizontally close to each other
     * (using a threshold based on their height). This reconstructs shattered speech bubbles.
     */
    private fun groupProximityBlocks(blocks: List<OcrResultBlock>): List<OcrResultBlock> {
        if (blocks.isEmpty()) return emptyList()

        val grouped = mutableListOf<MutableList<OcrResultBlock>>()
        val sortedBlocks = blocks.sortedBy { it.boundingBox?.top ?: 0 }

        for (block in sortedBlocks) {
            val rect = block.boundingBox ?: continue
            val blockHeight = rect.bottom - rect.top
            var addedToGroup = false

            for (group in grouped) {
                val groupRect = getGroupRect(group)
                val groupHeight = groupRect.bottom - groupRect.top
                val groupWidth = groupRect.right - groupRect.left

                // Strict threshold: blocks must be very close to merge
                val refHeight = minOf(blockHeight, groupHeight)
                val thresholdX = refHeight * 0.2f
                val thresholdY = refHeight * 0.1f

                val expandedGroup = Rect(
                    (groupRect.left - thresholdX).toInt(),
                    (groupRect.top - thresholdY).toInt(),
                    (groupRect.right + thresholdX).toInt(),
                    (groupRect.bottom + thresholdY).toInt()
                )

                // Must intersect expanded rect AND be vertically aligned
                val centerX = rect.left + (rect.right - rect.left) / 2
                val groupCenterX = groupRect.left + groupWidth / 2
                val centerY = rect.top + blockHeight / 2
                val groupCenterY = groupRect.top + groupHeight / 2

                val horizontalAligned = kotlin.math.abs(centerX - groupCenterX) < groupWidth * 0.6f
                val verticalGapSmall = kotlin.math.abs(rect.top - groupRect.bottom) < refHeight * 0.5f ||
                                      kotlin.math.abs(rect.bottom - groupRect.top) < refHeight * 0.5f

                if (Rect.intersects(expandedGroup, rect) && (horizontalAligned && verticalGapSmall)) {
                    group.add(block)
                    addedToGroup = true
                    break
                }
            }

            if (!addedToGroup) {
                grouped.add(mutableListOf(block))
            }
        }

        return grouped.map { group ->
            val mergedRect = getGroupRect(group)
            // Sort blocks top-to-bottom for proper reading order within each bubble
            val mergedText = group.sortedBy { it.boundingBox?.top ?: 0 }
                .joinToString(" ") { it.text }
            OcrResultBlock(text = mergedText, boundingBox = mergedRect)
        }
    }

    private fun getGroupRect(group: List<OcrResultBlock>): Rect {
        val left = group.minOf { it.boundingBox?.left ?: Int.MAX_VALUE }
        val top = group.minOf { it.boundingBox?.top ?: Int.MAX_VALUE }
        val right = group.maxOf { it.boundingBox?.right ?: Int.MIN_VALUE }
        val bottom = group.maxOf { it.boundingBox?.bottom ?: Int.MIN_VALUE }
        return Rect(left, top, right, bottom)
    }


}
