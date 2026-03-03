package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

class MangaChapterFlagsTest {

    @Test
    fun `Sub-chapter mask does not overlap with other masks`() {
        val otherMasks = listOf(
            Manga.CHAPTER_SORT_DIR_MASK,
            Manga.CHAPTER_UNREAD_MASK,
            Manga.CHAPTER_DOWNLOADED_MASK,
            Manga.CHAPTER_BOOKMARKED_MASK,
            Manga.CHAPTER_SORTING_MASK,
            Manga.CHAPTER_DISPLAY_MASK,
        )
        for (mask in otherMasks) {
            Manga.CHAPTER_SUB_CHAPTER_MASK and mask shouldBe 0L
        }
    }

    @Test
    fun `subChapterFilter returns DISABLED when flags are zero`() {
        val manga = Manga.create().copy(chapterFlags = Manga.SHOW_ALL)
        manga.subChapterFilter shouldBe TriState.DISABLED
    }

    @Test
    fun `subChapterFilter returns ENABLED_IS when CHAPTER_SHOW_SUB_CHAPTER is set`() {
        val manga = Manga.create().copy(chapterFlags = Manga.CHAPTER_SHOW_SUB_CHAPTER)
        manga.subChapterFilter shouldBe TriState.ENABLED_IS
    }

    @Test
    fun `subChapterFilter returns ENABLED_NOT when CHAPTER_SHOW_NOT_SUB_CHAPTER is set`() {
        val manga = Manga.create().copy(chapterFlags = Manga.CHAPTER_SHOW_NOT_SUB_CHAPTER)
        manga.subChapterFilter shouldBe TriState.ENABLED_NOT
    }

    @Test
    fun `subChapterFilterRaw extracts only the sub-chapter bits`() {
        val flags = Manga.CHAPTER_SHOW_SUB_CHAPTER or
            Manga.CHAPTER_SHOW_UNREAD or
            Manga.CHAPTER_SHOW_BOOKMARKED or
            Manga.CHAPTER_SORTING_NUMBER
        val manga = Manga.create().copy(chapterFlags = flags)
        manga.subChapterFilterRaw shouldBe Manga.CHAPTER_SHOW_SUB_CHAPTER
    }

    @Test
    fun `Existing filters are unaffected by sub-chapter bits`() {
        val manga = Manga.create().copy(
            chapterFlags = Manga.CHAPTER_SHOW_SUB_CHAPTER or Manga.CHAPTER_SHOW_NOT_SUB_CHAPTER,
        )
        manga.unreadFilter shouldBe TriState.DISABLED
        manga.bookmarkedFilter shouldBe TriState.DISABLED
    }
}
