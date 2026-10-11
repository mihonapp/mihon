package eu.kanade.tachiyomi.data.translation.overlay

/**
 * Gemini sometimes returns several boxes for one bubble (one per line or sentence). Drawing each separately would
 * erase and overlap the others, so boxes whose centre lies inside another box's bubble are merged into one group.
 */
object BubbleGrouping {

    /**
     * @return groups of indexes into [boxes], each group sorted ascending and groups ordered by first index.
     */
    fun group(boxes: List<PixelRect>, regions: List<BubbleRegion>): List<List<Int>> {
        require(boxes.size == regions.size)
        val parent = IntArray(boxes.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                // A rectangular open-text extent is only an erasure safety limit, not evidence that two boxes
                // belong to the same bubble. Group only boxes independently traced inside a closed bubble.
                if (!regions[i].fromFloodFill || !regions[j].fromFloodFill) continue
                val iHoldsJ = regions[i].contains(boxes[j].centerX, boxes[j].centerY)
                val jHoldsI = regions[j].contains(boxes[i].centerX, boxes[i].centerY)
                if (iHoldsJ || jHoldsI) parent[find(j)] = find(i)
            }
        }
        return boxes.indices.groupBy { find(it) }.values.sortedBy { it.first() }
    }

    /**
     * A speech bubble that cannot be traced must not be erased or lettered over nearby artwork. It is still safe when
     * its lettering sits on a flat page colour (for example a dashed bubble). Lettering that could not be erased
     * (logos, huge stylised titles) is never drawn over, because the translation would overlap the original.
     */
    fun canRenderSafely(kind: BoxKind, region: BubbleRegion): Boolean {
        if (region.fromFloodFill) return true
        if (region.inkErasedShare < MIN_ERASED_SHARE) return false
        return kind != BoxKind.BUBBLE || region.flatSurroundings
    }

    /**
     * Joins the translations of the boxes of one bubble. The model (or the recovery pass) sometimes reports the same
     * sentence twice, for example once for the whole bubble and once for a single line of it. Such repeats are
     * dropped; of two duplicates the longer translation is kept.
     */
    fun mergedText(boxes: List<TextBox>): String {
        val kept = mutableListOf<TextBox>()
        for (box in boxes) {
            if (box.text.isBlank()) continue
            val index = kept.indexOfFirst { isDuplicate(it, box) }
            if (index < 0) {
                kept += box
            } else if (box.text.trim().length > kept[index].text.trim().length) {
                kept[index] = box
            }
        }
        return kept.joinToString(" ") { it.text.trim() }
    }

    internal fun isDuplicate(a: TextBox, b: TextBox): Boolean {
        val sourceA = normalize(a.sourceText)
        val sourceB = normalize(b.sourceText)
        if (sourceA.isNotEmpty() && sourceB.isNotEmpty()) {
            return contains(sourceA, sourceB) || tokensOverlap(a.sourceText, b.sourceText)
        }
        return contains(normalize(a.text), normalize(b.text)) || tokensOverlap(a.text, b.text)
    }

    private fun contains(a: String, b: String): Boolean {
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= MIN_CONTAINED_LENGTH && longer.contains(shorter)
    }

    private fun tokensOverlap(a: String, b: String): Boolean {
        val left = tokens(a)
        val right = tokens(b)
        val smaller = minOf(left.size, right.size)
        if (smaller < MIN_OVERLAP_TOKENS) return false
        return left.intersect(right).size.toFloat() / smaller >= MIN_TOKEN_OVERLAP
    }

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private fun tokens(text: String): Set<String> =
        text.lowercase().split(WORD_SEPARATOR).filter { it.isNotBlank() }.toSet()

    private const val MIN_ERASED_SHARE = 0.6f
    private const val MIN_CONTAINED_LENGTH = 8
    private const val MIN_OVERLAP_TOKENS = 3
    private const val MIN_TOKEN_OVERLAP = 0.7f
    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
