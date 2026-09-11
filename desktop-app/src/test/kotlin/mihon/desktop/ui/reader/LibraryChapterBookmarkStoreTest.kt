package mihon.desktop.ui.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import org.junit.jupiter.api.Test

class LibraryChapterBookmarkStoreTest {

    @Test
    fun `repository store reads and writes the library bookmark`() {
        var chapter = libraryChapter(bookmark = false)
        var updated: ChapterRecord? = null
        val store = LibraryChapterBookmarkStore(
            findChapter = { chapter },
            updateChapter = { record ->
                updated = record
                chapter = chapter.copy(bookmark = record.bookmark)
            },
        )

        store.isBookmarked(7L) shouldBe false
        store.setBookmarked(7L, true)
        store.isBookmarked(7L) shouldBe true
        updated?.id shouldBe 7L
        updated?.mangaId shouldBe 42L
        updated?.bookmark shouldBe true
    }

    @Test
    fun `repository store ignores unknown chapters and no-op writes`() {
        var writes = 0
        val store = LibraryChapterBookmarkStore(
            findChapter = { null },
            updateChapter = { writes++ },
        )

        store.isBookmarked(99L) shouldBe false
        store.setBookmarked(99L, true)
        writes shouldBe 0
    }

    private fun libraryChapter(bookmark: Boolean): LibraryChapter = LibraryChapter(
        id = 7L,
        mangaId = 42L,
        url = "/chapter/7",
        name = "Chapter 7",
        scanlator = "Scanlator",
        read = false,
        bookmark = bookmark,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = 0L,
        chapterNumber = 7.0,
        sourceOrder = 7L,
        lastModifiedAt = 0L,
        version = 1L,
        memoJson = "{}",
    )
}
