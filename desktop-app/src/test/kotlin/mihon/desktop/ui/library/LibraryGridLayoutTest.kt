package mihon.desktop.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryGridLayoutTest {
    @Test
    fun `comfortable grid keeps readable cells across desktop widths`() {
        val narrow = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 360f, 180f, 16f)
        val standard = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 1180f, 180f, 16f)
        val ultrawide = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 2200f, 180f, 16f)

        assertEquals(2, narrow.columns)
        assertEquals(6, standard.columns)
        assertTrue(ultrawide.columns > standard.columns)
        listOf(narrow, standard, ultrawide).forEach { metrics ->
            assertTrue(metrics.cellWidthDp in 168f..224f, "Unexpected metrics: $metrics")
        }
    }

    @Test
    fun `very narrow content uses one column without overflowing its width`() {
        val metrics = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 140f, 180f, 16f)

        assertEquals(LibraryGridMetrics(columns = 1, cellWidthDp = 140f), metrics)
    }

    @Test
    fun `invalid measurements fall back to one safe comfortable card`() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -1f, 0f).forEach { width ->
            assertEquals(
                LibraryGridMetrics(columns = 1, cellWidthDp = 180f),
                calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, width, Float.NaN, -1f),
            )
        }
    }

    @Test
    fun `columns never decrease as width grows`() {
        val columns = (140..2400 step 20).map { width ->
            calculateLibraryGridMetrics(LibraryDisplayMode.CompactGrid, width.toFloat(), 180f, 12f).columns
        }

        columns.zipWithNext().forEach { (left, right) ->
            assertTrue(right >= left, "Column count decreased from $left to $right")
        }
    }

    @Test
    fun `display modes use distinct desktop density bounds`() {
        val comfortable = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 1180f, 180f, 16f)
        val compact = calculateLibraryGridMetrics(LibraryDisplayMode.CompactGrid, 1180f, 180f, 12f)
        val coverOnly = calculateLibraryGridMetrics(LibraryDisplayMode.CoverOnly, 1180f, 180f, 10f)

        assertTrue(compact.columns > comfortable.columns)
        assertTrue(coverOnly.columns > compact.columns)
        assertEquals(1, calculateLibraryGridMetrics(LibraryDisplayMode.List, 1180f, 180f, 8f).columns)
    }
}
