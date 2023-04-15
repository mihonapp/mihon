package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LibraryFlagsTest {

    @Test
    fun `Check the amount of flags`() {
        LibraryDisplayMode.values.size shouldBe 4
        LibrarySort.types.size shouldBe 8
        LibrarySort.directions.size shouldBe 2
    }

    @Test
    fun `Test Flag plus operator (LibraryDisplayMode)`() {
        val current = LibraryDisplayMode.List
        val new = LibraryDisplayMode.CoverOnlyGrid
        val flag = current + new

        flag shouldBe 0b00000011
    }

    @Test
    fun `Test Flag plus operator (LibrarySort)`() {
        val current = LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Ascending)
        val new = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = current + new

        flag shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator`() {
        val display = LibraryDisplayMode.CoverOnlyGrid
        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = display + sort

        flag shouldBe 0b01011111
    }

    @Test
    fun `Test Flag plus operator with old flag as base`() {
        val currentDisplay = LibraryDisplayMode.List
        val currentSort = LibrarySort(LibrarySort.Type.UnreadCount, LibrarySort.Direction.Descending)
        val currentFlag = currentDisplay + currentSort

        val display = LibraryDisplayMode.CoverOnlyGrid
        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = currentFlag + display + sort

        currentFlag shouldBe 0b00001110
        flag shouldBe 0b01011111
        flag shouldNotBe currentFlag
    }

    @Test
    fun `Test default flags`() {
        val sort = LibrarySort.default
        val display = LibraryDisplayMode.default
        val flag = display + sort.type + sort.direction

        flag shouldBe 0b01000000
    }
}
