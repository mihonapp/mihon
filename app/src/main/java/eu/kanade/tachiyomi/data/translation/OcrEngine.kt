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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OcrBlock(
    val text: String,
    val box: Rect,
)

private data class OcrLine(
    val text: String,
    val box: Rect,
)

private data class OcrGroup(
    val lines: MutableList<OcrLine> = mutableListOf(),
) {
    val box: Rect
        get() = lines.drop(1).fold(Rect(lines.first().box)) { acc, line -> acc.apply { union(line.box) } }

    val bottom: Int
        get() = lines.maxOf { it.box.bottom }
}

@Inject
@SingleIn(AppScope::class)
class OcrEngine {

    suspend fun recognize(image: Bitmap, script: OcrScript): List<OcrBlock> {
        return clusterIntoBlocks(recognizeLines(image, script))
    }

    /**
     * Reads the page in horizontal tiles.
     *
     * Long strip pages are much taller than the recognizer is comfortable with, and shrinking them to a single
     * manageable bitmap blurts small lettering away — that is what used to leave most speech bubbles untranslated.
     */
    private suspend fun recognizeLines(image: Bitmap, script: OcrScript): List<OcrLine> {
        val recognizer = recognizerFor(script)
        return try {
            val lines = mutableListOf<OcrLine>()
            for (tile in tilesOf(image)) {
                val bitmap = if (tile.width() == image.width && tile.height() == image.height) {
                    image
                } else {
                    Bitmap.createBitmap(image, tile.left, tile.top, tile.width(), tile.height())
                }
                try {
                    val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val box = line.boundingBox ?: return@forEach
                            val text = line.text.trim()
                            if (text.isNotEmpty()) {
                                lines += OcrLine(
                                    text = text,
                                    box = Rect(box.left + tile.left, box.top + tile.top, box.right + tile.left, box.bottom + tile.top),
                                )
                            }
                        }
                    }
                } finally {
                    if (bitmap !== image) bitmap.recycle()
                }
            }
            dedupe(lines)
        } finally {
            recognizer.close()
        }
    }

    /**
     * Splits the page into overlapping tiles so nothing is lost on a tile boundary.
     */
    private fun tilesOf(image: Bitmap): List<Rect> {
        val width = image.width
        val height = image.height
        if (height <= TILE_HEIGHT) return listOf(Rect(0, 0, width, height))

        val tiles = mutableListOf<Rect>()
        var top = 0
        while (true) {
            val bottom = min(top + TILE_HEIGHT, height)
            tiles += Rect(0, top, width, bottom)
            if (bottom == height) break
            top = max(bottom - TILE_OVERLAP, top + 1)
        }
        return tiles
    }

    /**
     * Tiles share an overlap band, so the same line is often read twice. Keep the longer reading.
     */
    private fun dedupe(lines: List<OcrLine>): List<OcrLine> {
        val kept = mutableListOf<OcrLine>()
        lines.sortedByDescending { it.text.length }.forEach { candidate ->
            val duplicate = kept.any { existing ->
                intersection(existing.box, candidate.box) >= 0.5f * min(existing.box.area(), candidate.box.area())
            }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    /**
     * Speech bubbles arrive as one line per block, so translating them one by one produced fragments like
     * "أنا..." / "هنا...". Lines that sit on top of each other are joined back into a single block instead.
     */
    private fun clusterIntoBlocks(lines: List<OcrLine>): List<OcrBlock> {
        if (lines.isEmpty()) return emptyList()

        val groups = mutableListOf<OcrGroup>()
        lines.sortedWith(compareBy({ it.box.top }, { it.box.left })).forEach { line ->
            val group = groups.firstOrNull { it.fits(line) }
            if (group != null) group.lines += line else groups += OcrGroup(mutableListOf(line))
        }

        return groups.mapNotNull { group ->
            val box = group.box
            if (box.width() < MIN_BOX_SIZE || box.height() < MIN_BOX_SIZE) return@mapNotNull null

            val ordered = group.lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
            val text = joinLines(ordered.map { it.text })
            if (text.length < 2) null else OcrBlock(text, box)
        }.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    private fun OcrGroup.fits(line: OcrLine): Boolean {
        val anchor = lines.maxByOrNull { it.box.bottom } ?: return false
        val lineHeight = anchor.box.height().coerceAtLeast(1)
        val gap = line.box.top - anchor.box.bottom

        // The next line of the same bubble follows closely; a different bubble is further away.
        if (gap > lineHeight * 0.9 || gap < -lineHeight * 0.7) return false
        if (line.box.height() > anchor.box.height() * 2.5) return false

        val overlap = intersection(anchor.box, line.box)
        val narrowest = min(anchor.box.width(), line.box.width()).coerceAtLeast(1)
        return overlap >= 0.2f * narrowest || abs(anchor.box.centerX() - line.box.centerX()) <= narrowest * 0.45f
    }

    private fun joinLines(lines: List<String>): String {
        val total = lines.sumOf { it.length }.coerceAtLeast(1)
        val cjk = lines.sumOf { line -> line.count { it.isCjk() } }
        val separator = if (cjk * 3 >= total) "" else " "
        return lines.joinToString(separator)
    }

    private fun Char.isCjk(): Boolean = this in '\u3040'..'\u30FF' || this in '\u3400'..'\u9FFF' || this in '\uAC00'..'\uD7AF'

    private fun intersection(a: Rect, b: Rect): Int {
        val left = max(a.left, b.left)
        val right = min(a.right, b.right)
        if (right <= left) return 0
        val top = max(a.top, b.top)
        val bottom = min(a.bottom, b.bottom)
        if (bottom <= top) return 0
        return (right - left) * (bottom - top)
    }

    private fun Rect.area(): Int = width() * height()

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

    companion object {
        private const val TILE_HEIGHT = 1600
        private const val TILE_OVERLAP = 200
        private const val MIN_BOX_SIZE = 6
    }
}
