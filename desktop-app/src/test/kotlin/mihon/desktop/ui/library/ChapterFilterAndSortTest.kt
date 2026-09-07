package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryChapter
import org.junit.jupiter.api.Test

class ChapterFilterAndSortTest {

    private fun sampleChapter(
        id: Long,
        name: String,
        number: Double,
        order: Long,
        dateUpload: Long,
        read: Boolean = false,
        bookmark: Boolean = false,
    ) = LibraryChapter(
        id = id,
        mangaId = 1L,
        url = "/chapter/$id",
        name = name,
        scanlator = "ScanGroup",
        read = read,
        bookmark = bookmark,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = dateUpload,
        chapterNumber = number,
        sourceOrder = order,
        lastModifiedAt = 0L,
        version = 1L,
        memoJson = "{}",
    )

    @Test
    fun `chapter filter state counts active filters`() {
        val empty = ChapterFilterState()
        empty.hasActiveFilters shouldBe false
        empty.activeCount shouldBe 0

        val withUnread = empty.copy(unread = TriStateFilter.Include)
        withUnread.hasActiveFilters shouldBe true
        withUnread.activeCount shouldBe 1

        val withAll = empty.copy(
            unread = TriStateFilter.Include,
            downloaded = TriStateFilter.Exclude,
            bookmarked = TriStateFilter.Include,
        )
        withAll.activeCount shouldBe 3
        withAll.reset() shouldBe ChapterFilterState()
    }

    @Test
    fun `filtering chapters by unread inclusion and exclusion`() {
        val chapters = listOf(
            sampleChapter(1, "Ch 1", 1.0, 1, 100, read = true),
            sampleChapter(2, "Ch 2", 2.0, 2, 200, read = false),
            sampleChapter(3, "Ch 3", 3.0, 3, 300, read = false),
        )

        // Include unread only
        val unreadOnly = chapters.filter { !it.read }
        unreadOnly.map { it.id } shouldContainExactly listOf(2L, 3L)

        // Exclude unread (read only)
        val readOnly = chapters.filter { it.read }
        readOnly.map { it.id } shouldContainExactly listOf(1L)
    }

    @Test
    fun `filtering chapters by bookmark and downloaded status`() {
        val downloadedIds = setOf(1L, 3L)
        val chapters = listOf(
            sampleChapter(1, "Ch 1", 1.0, 1, 100, bookmark = true),
            sampleChapter(2, "Ch 2", 2.0, 2, 200, bookmark = false),
            sampleChapter(3, "Ch 3", 3.0, 3, 300, bookmark = false),
        )

        // Include bookmarked
        chapters.filter { it.bookmark }.map { it.id } shouldContainExactly listOf(1L)

        // Include downloaded
        chapters.filter { downloadedIds.contains(it.id) }.map { it.id } shouldContainExactly listOf(1L, 3L)

        // Exclude downloaded (not downloaded)
        chapters.filter { !downloadedIds.contains(it.id) }.map { it.id } shouldContainExactly listOf(2L)
    }

    @Test
    fun `sorting chapters by number ascending and descending`() {
        val chapters = listOf(
            sampleChapter(1, "Ch 1", 1.0, 3, 100),
            sampleChapter(2, "Ch 10", 10.0, 1, 300),
            sampleChapter(3, "Ch 2.5", 2.5, 2, 200),
        )

        val asc = chapters.sortedBy { it.chapterNumber }
        asc.map { it.id } shouldContainExactly listOf(1L, 3L, 2L)

        val desc = chapters.sortedByDescending { it.chapterNumber }
        desc.map { it.id } shouldContainExactly listOf(2L, 3L, 1L)
    }

    @Test
    fun `sorting chapters by upload date ascending and descending`() {
        val chapters = listOf(
            sampleChapter(1, "Ch 1", 1.0, 1, 1000L),
            sampleChapter(2, "Ch 2", 2.0, 2, 5000L),
            sampleChapter(3, "Ch 3", 3.0, 3, 2000L),
        )

        val asc = chapters.sortedBy { it.dateUpload }
        asc.map { it.id } shouldContainExactly listOf(1L, 3L, 2L)

        val desc = chapters.sortedByDescending { it.dateUpload }
        desc.map { it.id } shouldContainExactly listOf(2L, 3L, 1L)
    }
}
