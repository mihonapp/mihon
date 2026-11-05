package eu.kanade.tachiyomi.data.translation.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.translation.overlay.BoxFilter
import eu.kanade.tachiyomi.data.translation.overlay.DetectedTextRegion
import eu.kanade.tachiyomi.data.translation.overlay.PixelRect
import eu.kanade.tachiyomi.data.translation.overlay.TallPageCropper
import eu.kanade.tachiyomi.data.translation.overlay.TextRegionGrouper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import logcat.logcat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

data class OcrBlock(
    val id: Int,
    val text: String,
    val bounds: PixelRect,
    val yMin: Int,
    val xMin: Int,
    val yMax: Int,
    val xMax: Int,
)

@Inject
@SingleIn(AppScope::class)
class LocalOcrEngine(
    private val context: Context,
) {

    init {
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN) { "Failed to initialize MlKitContext explicitly: ${e.message}" }
        }
    }

    private val latinRecognizer: TextRecognizer by lazy {
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (_: Throwable) {}
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val japaneseRecognizer: TextRecognizer by lazy {
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (_: Throwable) {}
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (_: Throwable) {}
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (_: Throwable) {}
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private fun getRecognizer(script: String): TextRecognizer = when (script.lowercase()) {
        "japanese" -> japaneseRecognizer
        "korean" -> koreanRecognizer
        "chinese" -> chineseRecognizer
        "latin" -> latinRecognizer
        else -> japaneseRecognizer
    }

    /**
     * Extracts text blocks from a manga page bitmap using on-device ML Kit OCR.
     * Merges adjacent lines belonging to the same speech bubble.
     * Uses multi-script dual-recognition and webtoon slicing for maximum accuracy.
     */
    suspend fun processImage(bitmap: Bitmap, script: String = "auto"): List<OcrBlock> {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)

        val detected: List<DetectedTextRegion> = if (height > 3000 && height.toFloat() / width >= 2.2f) {
            // Slice tall webtoons so ML Kit doesn't downscale characters into unreadable pixels
            val crops = TallPageCropper.plan(width, height)
            val allRegions = mutableListOf<DetectedTextRegion>()
            for (crop in crops) {
                val cropHeight = (crop.bottom - crop.top).coerceAtLeast(1)
                val cropBmp = try {
                    Bitmap.createBitmap(bitmap, 0, crop.top, width, cropHeight)
                } catch (_: Throwable) {
                    null
                }
                if (cropBmp != null) {
                    try {
                        val cropDetected = processSingleBitmap(cropBmp, script)
                        val mapped = cropDetected.map { region ->
                            DetectedTextRegion(
                                bounds = PixelRect(
                                    region.bounds.left,
                                    region.bounds.top + crop.top,
                                    region.bounds.right,
                                    region.bounds.bottom + crop.top,
                                ),
                                text = region.text,
                            )
                        }
                        allRegions.addAll(mapped)
                    } finally {
                        cropBmp.recycle()
                    }
                }
            }
            mergeDetections(allRegions, emptyList())
        } else {
            processSingleBitmap(bitmap, script)
        }

        return detected.mapIndexed { index, region ->
            val left = region.bounds.left.coerceIn(0, width - 1)
            val top = region.bounds.top.coerceIn(0, height - 1)
            val right = region.bounds.right.coerceIn(left + 1, width)
            val bottom = region.bounds.bottom.coerceIn(top + 1, height)

            val yMin = (top.toFloat() / height * 1000f).roundToInt().coerceIn(0, 1000)
            val xMin = (left.toFloat() / width * 1000f).roundToInt().coerceIn(0, 1000)
            val yMax = (bottom.toFloat() / height * 1000f).roundToInt().coerceIn(0, 1000)
            val xMax = (right.toFloat() / width * 1000f).roundToInt().coerceIn(0, 1000)

            OcrBlock(
                id = index,
                text = region.text,
                bounds = PixelRect(left, top, right, bottom),
                yMin = yMin,
                xMin = xMin,
                yMax = yMax,
                xMax = xMax,
            )
        }
    }

    private suspend fun processSingleBitmap(bitmap: Bitmap, script: String): List<DetectedTextRegion> {
        val s = script.lowercase()
        return when (s) {
            "auto" -> {
                coroutineScope {
                    val latinJob = async { runRecognition(latinRecognizer, bitmap) }
                    val jpnJob = async { runRecognition(japaneseRecognizer, bitmap) }
                    val latinResults = latinJob.await()
                    val jpnResults = jpnJob.await()
                    mergeDetections(latinResults, jpnResults)
                }
            }
            "japanese" -> {
                coroutineScope {
                    val jpnJob = async { runRecognition(japaneseRecognizer, bitmap) }
                    val latinJob = async { runRecognition(latinRecognizer, bitmap) }
                    mergeDetections(jpnJob.await(), latinJob.await())
                }
            }
            "korean" -> {
                coroutineScope {
                    val korJob = async { runRecognition(koreanRecognizer, bitmap) }
                    val latinJob = async { runRecognition(latinRecognizer, bitmap) }
                    mergeDetections(korJob.await(), latinJob.await())
                }
            }
            "chinese" -> {
                coroutineScope {
                    val chnJob = async { runRecognition(chineseRecognizer, bitmap) }
                    val latinJob = async { runRecognition(latinRecognizer, bitmap) }
                    mergeDetections(chnJob.await(), latinJob.await())
                }
            }
            "latin" -> {
                runRecognition(latinRecognizer, bitmap)
            }
            else -> {
                coroutineScope {
                    val primaryJob = async { runRecognition(getRecognizer(script), bitmap) }
                    val latinJob = async { runRecognition(latinRecognizer, bitmap) }
                    mergeDetections(primaryJob.await(), latinJob.await())
                }
            }
        }
    }

    private fun mergeDetections(
        primary: List<DetectedTextRegion>,
        secondary: List<DetectedTextRegion>,
    ): List<DetectedTextRegion> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary

        val result = primary.toMutableList()
        for (sec in secondary) {
            val duplicateIndex = result.indexOfFirst { prim ->
                prim.bounds.overlapShare(sec.bounds) >= 0.35f
            }
            if (duplicateIndex < 0) {
                result.add(sec)
            } else {
                val existing = result[duplicateIndex]
                val secLetters = sec.text.count { it.isLetter() }
                val exLetters = existing.text.count { it.isLetter() }
                if (secLetters > exLetters * 1.3f) {
                    result[duplicateIndex] = sec
                }
            }
        }
        return result.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    private suspend fun runRecognition(recognizer: TextRecognizer, bitmap: Bitmap): List<DetectedTextRegion> {
        val result = try {
            recognize(recognizer, bitmap)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN) { "Recognition failed: ${e.message}" }
            return emptyList()
        }

        val lines = result.textBlocks.flatMap { block ->
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

        // Group lines into speech bubbles
        val grouped = TextRegionGrouper.group(lines)
        return grouped.filterNot { region ->
            if (BoxFilter.hasCjk(region.text)) {
                // For CJK languages, keep even short 1-2 character dialogue
                region.text.none { it.isLetterOrDigit() }
            } else {
                TextRegionGrouper.isLikelyNoise(region.text)
            }
        }
    }

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): Text =
        suspendCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
}
