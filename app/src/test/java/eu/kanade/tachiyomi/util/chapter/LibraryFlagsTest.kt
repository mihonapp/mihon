package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.domain.library.model.plus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LibraryFlagsTest {

    @Test
    fun `Check the amount of flags`() {
        assertEquals(4, LibraryDisplayMode.values.size)
        assertEquals(8, LibrarySort.types.size)
        assertEquals(2, LibrarySort.directions.size)
    }

    @Test
    fun `Test Flag plus operator (LibraryDisplayMode)`() {
        val current = LibraryDisplayMode.List
        val new = LibraryDisplayMode.CoverOnlyGrid
        val flag = current + new

        assertEquals(0b00000011, flag)
    }

    @Test
    fun `Test Flag plus operator (LibrarySort)`() {
        val current = LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Ascending)
        val new = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = current + new

        assertEquals(0b01011100, flag)
    }

    @Test
    fun `Test Flag plus operator`() {
        val display = LibraryDisplayMode.CoverOnlyGrid
        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = display + sort

        assertEquals(0b01011111, flag)
    }

    @Test
    fun `Test Flag plus operator with old flag as base`() {
        val currentDisplay = LibraryDisplayMode.List
        val currentSort = LibrarySort(LibrarySort.Type.UnreadCount, LibrarySort.Direction.Descending)
        val currentFlag = currentDisplay + currentSort

        val display = LibraryDisplayMode.CoverOnlyGrid
        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = currentFlag + display + sort

        assertEquals(0b00001110, currentFlag)
        assertEquals(0b01011111, flag)
        assertNotEquals(currentFlag, flag)
    }

    @Test
    fun `Test default flags`() {
        val sort = LibrarySort.default
        val display = LibraryDisplayMode.default
        val flag = display + sort.type + sort.direction

        assertEquals(0b01000000, flag)
    }
}
