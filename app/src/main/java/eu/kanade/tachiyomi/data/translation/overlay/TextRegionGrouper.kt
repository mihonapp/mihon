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
        if (visible == 0 || letters == 0) return true
        if (BoxFilter.hasCjk(text)) return false

        val clean = text.lowercase().filter { it.isLetter() }
        if (clean in COMMON_SHORT_DIALOGUE) return false

        val words = text.split(WORD_SEPARATOR).filter { it.isNotBlank() }
        if (words.any { it.lowercase() in COMMON_SHORT_DIALOGUE }) return false

        // Punctuation heavy (e.g. "|| -- //")
        if (letters.toFloat() / visible < MIN_LETTER_SHARE) return true

        // Extremely short fragments like "a1", "x", single non-word consonants
        if (letters <= 2 && visible > letters) return true
        if (letters == 1) {
            val single = clean.uppercase()
            return single !in listOf("A", "I", "O")
        }

        // Random unpronounceable consonant clusters like "dsle", "xzq" with 0 or bad vowels
        val hasVowel = clean.any { it in "aeiouy" }
        if (!hasVowel && letters in 2..5) return true

        // If it's a single word of 3-4 chars starting with unnatural consonant clusters (e.g. "dsle")
        if (words.size == 1 && clean.length in 3..4 && !hasVowelLikeFlow(clean)) {
            return true
        }

        return false
    }

    private fun hasVowelLikeFlow(s: String): Boolean {
        if (s.startsWith("dsl") || s.startsWith("xz") || s.startsWith("qj")) return false
        return s.any { it in "aeiouy" }
    }

    private val COMMON_SHORT_DIALOGUE = hashSetOf(
        "no", "yes", "why", "wait", "stop", "help", "what", "who", "how", "run", "die",
        "look", "hey", "huh", "damn", "sure", "fine", "okay", "ok", "good", "yeah", "yea",
        "oh", "ah", "ha", "ouch", "wow", "go", "hi", "bye", "man", "bro", "sir", "mom",
        "dad", "son", "kill", "shut", "up", "let", "see", "come", "on", "back", "away",
        "out", "now", "here", "them", "him", "her", "you", "me", "us", "he", "she", "it",
        "they", "we", "am", "is", "are", "was", "were", "be", "do", "did", "can", "will",
    )

    private const val MAX_HEIGHT_RATIO = 2.4f
    private const val MAX_GAP_IN_LINE_HEIGHTS = 1.4f
    private const val MAX_CENTER_SHIFT = 0.55f
    private const val MIN_EDGE_OVERLAP = 0.35f
    private const val MIN_LETTER_SHARE = 0.5f
    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
