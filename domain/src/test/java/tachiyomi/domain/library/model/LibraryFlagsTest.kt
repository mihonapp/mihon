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
        LibrarySort.types.size shouldBe 9
        LibrarySort.directions.size shouldBe 2
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
        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)

        sort.flag shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator with old flag as base`() {
        val currentSort = LibrarySort(
            LibrarySort.Type.UnreadCount,
            LibrarySort.Direction.Descending,
        )
        currentSort.flag shouldBe 0b00001100

        val sort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        val flag = currentSort.flag + sort

        flag shouldBe 0b01011100
        flag shouldNotBe currentSort.flag
    }

    @Test
    fun `Test default flags`() {
        val sort = LibrarySort.default
        val flag = sort.type + sort.direction

        flag shouldBe 0b01000000
    }
}
