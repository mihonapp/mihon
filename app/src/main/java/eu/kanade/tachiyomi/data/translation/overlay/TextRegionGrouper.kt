package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns the text lines found by OCR into one region per bubble or text box. Recovering a missed bubble costs one
 * request per region, so a bubble of six lines must be one region, not six. Lines that clearly are not text
 * (fragments recognised in artwork) are dropped, so they do not use up the recovery budget.
 *
 * Pure geometry and string handling, so it can be tested without Android.
 */
object TextRegionGrouper {

    /** Lines of one bubble are grouped, merged into regions, and OCR noise is removed. */
    fun regionsFrom(lines: List<DetectedTextRegion>): List<DetectedTextRegion> =
        group(lines).filterNot { isLikelyNoise(it.text) }

    /**
     * Merges lines that are stacked closely and centred on each other (or share an edge) and have a similar height.
     * The result is sorted from top to bottom; the text of a region is its lines in reading order.
     */
    fun group(lines: List<DetectedTextRegion>): List<DetectedTextRegion> {
        val usable = lines.filter { !it.bounds.isEmpty && it.text.isNotBlank() }
        if (usable.isEmpty()) return emptyList()

        val parent = IntArray(usable.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in usable.indices) {
            for (j in i + 1 until usable.size) {
                if (belongTogether(usable[i].bounds, usable[j].bounds)) parent[find(j)] = find(i)
            }
        }

        return usable.indices.groupBy { find(it) }.values
            .map { indexes ->
                val members = indexes.map { usable[it] }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
                DetectedTextRegion(
                    bounds = members.map { it.bounds }.reduce(PixelRect::union),
                    text = members.joinToString(" ") { it.text.trim() },
                )
            }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    internal fun belongTogether(a: PixelRect, b: PixelRect): Boolean {
        val smaller = max(1, min(a.height, b.height))
        val larger = max(a.height, b.height)
        if (larger > smaller * MAX_HEIGHT_RATIO) return false

        // Negative when the two lines overlap vertically.
        val gap = max(a.top, b.top) - min(a.bottom, b.bottom)
        if (gap > (a.height + b.height) / 2f * MAX_GAP_IN_LINE_HEIGHTS) return false

        val widest = max(a.width, b.width)
        val overlap = min(a.right, b.right) - max(a.left, b.left)
        val centred = abs(a.centerX - b.centerX) <= widest * MAX_CENTER_SHIFT
        val sharesEdge = overlap >= min(a.width, b.width) * MIN_EDGE_OVERLAP
        return centred || sharesEdge
    }

    /**
     * True for OCR output that is very unlikely to be dialogue: almost no letters, mostly symbols, or one or two
     * tiny fragments such as "dsle" that OCR finds in artwork.
     */
    fun isLikelyNoise(text: String): Boolean {
        val visible = text.count { !it.isWhitespace() }
        val letters = text.count { it.isLetter() }
        if (visible == 0 || letters < MIN_LETTERS) return true
        if (letters.toFloat() / visible < MIN_LETTER_SHARE) return true
        val words = text.split(WORD_SEPARATOR).count { it.isNotBlank() }
        return words <= 2 && letters < MIN_LETTERS_FEW_WORDS
    }

    private const val MAX_HEIGHT_RATIO = 1.8f
    private const val MAX_GAP_IN_LINE_HEIGHTS = 1.0f
    private const val MAX_CENTER_SHIFT = 0.35f
    private const val MIN_EDGE_OVERLAP = 0.5f
    private const val MIN_LETTERS = 3
    private const val MIN_LETTERS_FEW_WORDS = 5
    private const val MIN_LETTER_SHARE = 0.6f
    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
