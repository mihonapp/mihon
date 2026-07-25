package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Pure pairing logic for the double-page (spread) layout, extracted from [PagerViewerAdapter] so it
 * can be unit-tested without the reader stack.
 *
 * Given a single chapter segment — one boolean per page: whether the page is too wide to be paired —
 * and whether the segment's pairing is shifted by one, it computes the spread [Slot]s plus which
 * pages end up shifted / isolated.
 */
internal object DoublePagePairer {

    /** A single spread slot referencing pages by their index within the segment. */
    data class Slot(
        /** Index of the page shown on its own or as the first of the spread; always present. */
        val first: Int,
        /** Index of the page paired with [first], or null when [first] sits alone. */
        val second: Int?,
    )

    data class Layout(
        val slots: List<Slot>,
        val shiftedIndices: Set<Int>,
        val isolatedIndices: Set<Int>,
    )

    /**
     * @param fullPages one entry per page in reading order; true means the page is too wide to be
     *   doubled up and must occupy a slot alone.
     * @param shift offset the whole segment's pairing by one (cover offset).
     */
    fun pair(fullPages: List<Boolean>, shift: Boolean): Layout {
        // Page indices in reading order, with null standing in for a blank inserted to solo a page.
        val items = fullPages.indices.toMutableList<Int?>()
        val shiftedIndices = mutableSetOf<Int>()
        val isolatedIndices = mutableSetOf<Int>()

        // Shift: move the pairing over by one by soloing the first non-full page.
        if (shift) {
            for (index in items) {
                if (index != null && !fullPages[index]) {
                    shiftedIndices.add(index)
                    break
                }
            }
        }

        // Insert blanks so a full or shifted page — and, for parity, the page before an even-indexed
        // full page — ends up alone in its two-slot chunk.
        var i = 0
        while (i < items.size) {
            val index = items[i]
            val isFull = index != null && fullPages[index]
            val isShifted = index != null && index in shiftedIndices
            if (isFull || isShifted) {
                items.add(i + 1, null)
                if (isFull && i > 0 && items[i - 1] != null && (i - 1) % 2 == 0) {
                    items[i - 1]?.let { isolatedIndices.add(it) }
                    items.add(i, null)
                    i++
                }
                i++
            }
            i++
        }

        // The first of each chunk is always a real page (blanks only land at odd positions).
        val slots = items.chunked(2).map { Slot(it.first()!!, it.getOrNull(1)) }
        return Layout(slots, shiftedIndices, isolatedIndices)
    }
}
