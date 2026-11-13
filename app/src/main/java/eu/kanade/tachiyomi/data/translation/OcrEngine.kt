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

/**
 * A piece of the page handed to the recognizer. [release] tells the engine whether it owns the bitmap.
 */
class OcrTile(
    val bitmap: Bitmap,
    val release: Boolean,
)

private data class OcrLine(
    val text: String,
    val box: Rect,
) {
    /** Japanese columns are tall and narrow; Latin lines are the other way around. */
    val isTall: Boolean
        get() = box.height() > box.width() * 1.5f
}

@Inject
@SingleIn(AppScope::class)
class OcrEngine {

    suspend fun recognize(image: Bitmap, script: OcrScript): List<OcrBlock> {
        return recognizeTiles(image.width, image.height, script) { tile ->
            val bitmap = if (tile.width() == image.width && tile.height() == image.height) {
                image
            } else {
                Bitmap.createBitmap(image, tile.left, tile.top, tile.width(), tile.height())
            }
            OcrTile(bitmap, release = bitmap !== image)
        }
    }

    /**
     * Recognizes a page region by region.
     *
     * Long strip pages can be tens of thousands of pixels tall, so they are never loaded as a single bitmap: the
     * caller decodes one tile at a time at full resolution, which is what keeps small lettering readable.
     */
    suspend fun recognizeTiles(
        width: Int,
        height: Int,
        script: OcrScript,
        load: (Rect) -> OcrTile?,
    ): List<OcrBlock> {
        val recognizer = recognizerFor(script)
        val lines = mutableListOf<OcrLine>()
        try {
            for (tile in tilesFor(width, height)) {
                val image = load(tile) ?: continue
                try {
                    val result = recognizer.process(InputImage.fromBitmap(image.bitmap, 0)).awaitResult()
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val box = line.boundingBox ?: return@forEach
                            val text = line.text.trim()
                            if (text.isNotEmpty()) {
                                lines += OcrLine(
                                    text = text,
                                    box = Rect(
                                        box.left + tile.left,
                                        box.top + tile.top,
                                        box.right + tile.left,
                                        box.bottom + tile.top,
                                    ),
                                )
                            }
                        }
                    }
                } finally {
                    if (image.release) image.bitmap.recycle()
                }
            }
            return toBlocks(mergeLines(dedupe(lines)))
        } finally {
            recognizer.close()
        }
    }

    /**
     * Splits the page into overlapping tiles so nothing is lost on a tile boundary.
     */
    private fun tilesFor(width: Int, height: Int): List<Rect> {
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
                intersection(existing.box, candidate.box) >= 0.5f * minArea(existing.box, candidate.box)
            }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    /**
     * Rebuilds speech bubbles out of the recognized lines.
     *
     * The recognizer hands back one entry per line, and Japanese columns come back one per column, so translating
     * them as-is produced fragments like "أنا…" / "هنا…" and thin vertical strips of Arabic. Lines (or columns) that
     * sit right next to each other with no gap are joined back into the bubble they came from.
     */
    private fun mergeLines(lines: List<OcrLine>): List<List<OcrLine>> {
        val parent = IntArray(lines.size) { it }

        fun find(index: Int): Int {
            var root = index
            while (parent[root] != root) root = parent[root]
            var current = index
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootB] = rootA
        }

        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                if (isSameBubble(lines[i], lines[j])) union(i, j)
            }
        }

        val groups = mutableMapOf<Int, MutableList<OcrLine>>()
        lines.indices.forEach { index ->
            groups.getOrPut(find(index)) { mutableListOf() }.add(lines[index])
        }
        return groups.values.toList()
    }

    private fun isSameBubble(first: OcrLine, second: OcrLine): Boolean {
        val minHeight = min(first.box.height(), second.box.height()).coerceAtLeast(1)
        val minWidth = min(first.box.width(), second.box.width()).coerceAtLeast(1)
        val verticalGap = max(first.box.top, second.box.top) - min(first.box.bottom, second.box.bottom)
        val horizontalGap = max(first.box.left, second.box.left) - min(first.box.right, second.box.right)

        // Stacked: the next line of the same bubble, close and in the same column. Centred lettering has lines of
        // different widths, so comparing the line centres keeps a bubble together where plain overlap would not.
        val centresAligned = abs(first.box.centerX() - second.box.centerX()) <=
            max(first.box.width(), second.box.width()) * 0.4f
        val stacked = verticalGap >= -0.45f * minHeight &&
            verticalGap <= 0.6f * minHeight &&
            (horizontalOverlap(first.box, second.box) >= 0.45f || centresAligned) &&
            // Lines of one bubble have comparable lettering; this keeps a sound effect out of a bubble.
            max(first.box.height(), second.box.height()) <= minHeight * 2.5f
        if (stacked) return true

        // Side by side: the columns of vertical Japanese text. Only tall boxes can merge this way, otherwise two
        // neighbouring bubbles would be glued together and their translation would land between them.
        return first.isTall && second.isTall &&
            horizontalGap >= -0.6f * minWidth &&
            horizontalGap <= 1.1f * minWidth &&
            verticalOverlap(first.box, second.box) >= 0.5f
    }

    private fun toBlocks(groups: List<List<OcrLine>>): List<OcrBlock> {
        return groups.mapNotNull { group ->
            val box = group.drop(1).fold(Rect(group.first().box)) { acc, line -> acc.apply { union(line.box) } }
            if (box.width() < MIN_BOX_SIZE || box.height() < MIN_BOX_SIZE) return@mapNotNull null

            val vertical = group.count { it.isTall } * 2 >= group.size
            val text = if (vertical) textOfVerticalGroup(group) else textOfHorizontalGroup(group)
            if (text.count { it.isLetterOrDigit() } < 2) null else OcrBlock(text, box)
        }.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    private fun textOfHorizontalGroup(group: List<OcrLine>): String {
        val ordered = group.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        return joinLines(ordered.map { it.text })
    }

    /**
     * Japanese columns are read right to left, and top to bottom inside a column.
     */
    private fun textOfVerticalGroup(group: List<OcrLine>): String {
        val columns = mutableListOf<MutableList<OcrLine>>()
        group.sortedByDescending { it.box.centerX() }.forEach { line ->
            val column = columns.firstOrNull { existing ->
                existing.any { horizontalOverlap(it.box, line.box) >= 0.5f }
            }
            if (column != null) column.add(line) else columns.add(mutableListOf(line))
        }

        return columns.joinToString(separator = "") { column ->
            column.sortedBy { it.box.top }.joinToString(separator = "") { it.text }
        }
    }

    private fun joinLines(lines: List<String>): String {
        val total = lines.sumOf { it.length }.coerceAtLeast(1)
        val cjk = lines.sumOf { line -> line.count { it.isCjk() } }
        val separator = if (cjk * 3 >= total) "" else " "
        return lines.joinToString(separator)
    }

    private fun Char.isCjk(): Boolean =
        this in '\u3040'..'\u30FF' || this in '\u3400'..'\u9FFF' || this in '\uAC00'..'\uD7AF'

    private fun intersection(a: Rect, b: Rect): Int {
        val left = max(a.left, b.left)
        val right = min(a.right, b.right)
        if (right <= left) return 0
        val top = max(a.top, b.top)
        val bottom = min(a.bottom, b.bottom)
        if (bottom <= top) return 0
        return (right - left) * (bottom - top)
    }

    private fun horizontalOverlap(a: Rect, b: Rect): Float {
        val left = max(a.left, b.left)
        val right = min(a.right, b.right)
        if (right <= left) return 0f
        return (right - left).toFloat() / min(a.width(), b.width()).coerceAtLeast(1)
    }

    private fun verticalOverlap(a: Rect, b: Rect): Float {
        val top = max(a.top, b.top)
        val bottom = min(a.bottom, b.bottom)
        if (bottom <= top) return 0f
        return (bottom - top).toFloat() / min(a.height(), b.height()).coerceAtLeast(1)
    }

    private fun minArea(a: Rect, b: Rect): Int = min(a.width() * a.height(), b.width() * b.height())

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
