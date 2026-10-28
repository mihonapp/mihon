package eu.kanade.presentation.library.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryPaginationTest {

    @Test
    fun `automatic capacity excludes partial rows and includes spacing`() {
        libraryPageCapacity(height = 350, rowHeight = 100, spacing = 4, columns = 3, requestedRows = 0) shouldBe 9
        libraryPageCapacity(height = 308, rowHeight = 100, spacing = 4, columns = 3, requestedRows = 0) shouldBe 9
        libraryPageCapacity(height = 307, rowHeight = 100, spacing = 4, columns = 3, requestedRows = 0) shouldBe 6
    }

    @Test
    fun `requested rows are limited to complete rows that fit`() {
        libraryPageCapacity(height = 350, rowHeight = 100, spacing = 4, columns = 3, requestedRows = 2) shouldBe 6
        libraryPageCapacity(height = 350, rowHeight = 100, spacing = 4, columns = 3, requestedRows = 10) shouldBe 9
    }

    @Test
    fun `list capacity uses one entry per row`() {
        libraryPageCapacity(height = 330, rowHeight = 56, spacing = 0, columns = 1, requestedRows = 0) shouldBe 5
    }

    @Test
    fun `last page stays aligned when it is incomplete`() {
        libraryPageStart(anchorIndex = 12, pageSize = 6, itemCount = 14) shouldBe 12
        libraryPageStart(anchorIndex = 18, pageSize = 6, itemCount = 14) shouldBe 12
        libraryPageStart(anchorIndex = -6, pageSize = 6, itemCount = 14) shouldBe 0
    }

    @Test
    fun `capacity changes retain the page containing the anchor`() {
        libraryPageStart(anchorIndex = 12, pageSize = 9, itemCount = 40) shouldBe 9
        libraryPageStart(anchorIndex = 12, pageSize = 4, itemCount = 40) shouldBe 12
    }

    @Test
    fun `shrinking and empty results clamp the current page`() {
        libraryPageStart(anchorIndex = 24, pageSize = 6, itemCount = 8) shouldBe 6
        libraryPageStart(anchorIndex = 24, pageSize = 6, itemCount = 0) shouldBe 0
    }

    @Test
    fun `oversized covers shrink while retaining space for the title`() {
        libraryPageItemWidth(height = 350, rowHeight = 650, itemWidth = 400, padding = 8) shouldBe 200
        libraryPageItemWidth(height = 650, rowHeight = 650, itemWidth = 400, padding = 8) shouldBe 400
    }

    @Test
    fun `manual rows can exceed the natural auto capacity by fitting smaller covers`() {
        val auto = libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 0)
        val manual = libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 3)
        auto.capacity shouldBe 3
        auto.rows shouldBe 1
        manual.capacity shouldBe 9
        manual.rows shouldBe 3
        manual.maxRows shouldBe 5
        manual.itemWidth shouldBe 101
    }

    @Test
    fun `unsupported row counts use the numeric row limit`() {
        val layout = libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 99)
        layout.capacity shouldBe 15
        layout.maxRows shouldBe 5
    }

    @Test
    fun `column changes preserve compatible rows and clamp incompatible rows`() {
        val narrow = libraryGridPageLayout(600, 30, 10, 8, 56, 4, 48, 6)
        val wide = libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 6)
        narrow.maxRows shouldBe 6
        wide.maxRows shouldBe 5
        libraryCompatiblePageRows(3, wide.maxRows) shouldBe 3
        libraryCompatiblePageRows(6, wide.maxRows) shouldBe 5
        libraryCompatiblePageRows(0, wide.maxRows) shouldBe 0
    }

    @Test
    fun `vertical fitting disables stretch when rows exceed auto`() {
        libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 0).canStretchSpacing shouldBe true
        libraryGridPageLayout(600, 200, 3, 8, 56, 4, 48, 3).canStretchSpacing shouldBe false
        libraryGridPageLayout(344, 200, 3, 8, 56, 4, 48, 0).canStretchSpacing shouldBe false
        libraryCompatiblePageRows(-1, 5) shouldBe 5
    }
}
