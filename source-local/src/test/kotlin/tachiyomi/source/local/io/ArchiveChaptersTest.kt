package tachiyomi.source.local.io

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test

class ArchiveChaptersTest {

    @Test
    fun `returns a folder per chapter`() {
        getChapterFolders(
            listOf("Chapter 001/001.jpg", "Chapter 001/002.jpg", "Chapter 002/001.jpg"),
        ) shouldContainExactlyInAnyOrder listOf("Chapter 001", "Chapter 002")
    }

    @Test
    fun `returns chapter folders inside a volume folder`() {
        getChapterFolders(
            listOf("Vol 01/Chapter 001/001.jpg", "Vol 01/Chapter 002/001.jpg"),
        ) shouldContainExactlyInAnyOrder listOf("Vol 01/Chapter 001", "Vol 01/Chapter 002")
    }

    @Test
    fun `keeps an archive without folders as one chapter`() {
        getChapterFolders(listOf("001.jpg", "002.jpg")).shouldBeNull()
    }

    @Test
    fun `keeps an archive with a single folder as one chapter`() {
        getChapterFolders(listOf("Vol 01/001.jpg", "Vol 01/002.jpg")).shouldBeNull()
    }

    @Test
    fun `keeps an archive with pages outside the folders as one chapter`() {
        getChapterFolders(listOf("cover.jpg", "Chapter 001/001.jpg", "Chapter 002/001.jpg")).shouldBeNull()
    }

    @Test
    fun `keeps an archive with pages in a folder holding another folder as one chapter`() {
        getChapterFolders(
            listOf("Vol 01/cover.jpg", "Vol 01/Chapter 001/001.jpg", "Vol 01/Chapter 002/001.jpg"),
        ).shouldBeNull()
    }

    @Test
    fun `keeps an empty archive as one chapter`() {
        getChapterFolders(emptyList()).shouldBeNull()
    }
}
