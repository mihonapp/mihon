package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.viewer.pager.DoublePagePairer.Slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DoublePagePairerTest {

    private fun slots(vararg pairs: Pair<Int, Int?>) = pairs.map { Slot(it.first, it.second) }

    @Test
    fun `pairs consecutive pages two by two`() {
        val layout = DoublePagePairer.pair(fullPages = listOf(false, false, false, false), shift = false)
        assertEquals(slots(0 to 1, 2 to 3), layout.slots)
        assertEquals(emptySet<Int>(), layout.isolatedIndices)
        assertEquals(emptySet<Int>(), layout.shiftedIndices)
    }

    @Test
    fun `leaves a trailing odd page alone`() {
        val layout = DoublePagePairer.pair(fullPages = listOf(false, false, false), shift = false)
        assertEquals(slots(0 to 1, 2 to null), layout.slots)
    }

    @Test
    fun `an empty segment produces no slots`() {
        val layout = DoublePagePairer.pair(fullPages = emptyList(), shift = false)
        assertEquals(emptyList<Slot>(), layout.slots)
    }

    @Test
    fun `a leading wide page is soloed and following pages pair normally`() {
        val layout = DoublePagePairer.pair(fullPages = listOf(true, false, false), shift = false)
        assertEquals(slots(0 to null, 1 to 2), layout.slots)
        assertEquals(emptySet<Int>(), layout.isolatedIndices)
    }

    @Test
    fun `a wide page at an even boundary isolates the page before it`() {
        // Page 1 is wide, so page 0 can't pair with it and is isolated; all three end up alone.
        val layout = DoublePagePairer.pair(fullPages = listOf(false, true, false), shift = false)
        assertEquals(slots(0 to null, 1 to null, 2 to null), layout.slots)
        assertEquals(setOf(0), layout.isolatedIndices)
    }

    @Test
    fun `shift solos the first page so the rest pair offset by one`() {
        val layout = DoublePagePairer.pair(fullPages = listOf(false, false, false), shift = true)
        assertEquals(slots(0 to null, 1 to 2), layout.slots)
        assertEquals(setOf(0), layout.shiftedIndices)
    }

    @Test
    fun `a single page is soloed`() {
        assertEquals(slots(0 to null), DoublePagePairer.pair(listOf(false), shift = false).slots)
        assertEquals(slots(0 to null), DoublePagePairer.pair(listOf(true), shift = false).slots)
    }

    @Test
    fun `two normal pages form one spread`() {
        assertEquals(slots(0 to 1), DoublePagePairer.pair(listOf(false, false), shift = false).slots)
    }
}
