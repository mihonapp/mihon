package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

class MangaChapterFlagsTest {

    private fun manga(flags: Long = 0L) = Manga.create().copy(chapterFlags = flags)

    @Nested
    inner class MaskOverlap {

        @Test
        fun `All masks are non-overlapping`() {
            val masks = listOf(
                Manga.CHAPTER_SORT_DIR_MASK,
                Manga.CHAPTER_UNREAD_MASK,
                Manga.CHAPTER_DOWNLOADED_MASK,
                Manga.CHAPTER_BOOKMARKED_MASK,
                Manga.CHAPTER_SORTING_MASK,
                Manga.CHAPTER_SUB_CHAPTER_MASK,
                Manga.CHAPTER_DISPLAY_MASK,
            )
            for (i in masks.indices) {
                for (j in i + 1 until masks.size) {
                    (masks[i] and masks[j]) shouldBe 0L
                }
            }
        }

        @Test
        fun `SHOW_ALL is zero`() {
            Manga.SHOW_ALL shouldBe 0L
        }
    }

    @Nested
    inner class SortDirection {

        @Test
        fun `Default flags give descending sort`() {
            manga(Manga.CHAPTER_SORT_DESC).sortDescending() shouldBe true
        }

        @Test
        fun `ASC flag gives ascending sort`() {
            manga(Manga.CHAPTER_SORT_ASC).sortDescending() shouldBe false
        }

        @Test
        fun `Sort direction is isolated from other flags`() {
            val flags = Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SHOW_UNREAD or Manga.CHAPTER_SORTING_NUMBER
            manga(flags).sortDescending() shouldBe false
        }
    }

    @Nested
    inner class UnreadFilter {

        @Test
        fun `Default is DISABLED`() {
            manga().unreadFilter shouldBe TriState.DISABLED
        }

        @Test
        fun `SHOW_UNREAD maps to ENABLED_IS`() {
            manga(Manga.CHAPTER_SHOW_UNREAD).unreadFilter shouldBe TriState.ENABLED_IS
        }

        @Test
        fun `SHOW_READ maps to ENABLED_NOT`() {
            manga(Manga.CHAPTER_SHOW_READ).unreadFilter shouldBe TriState.ENABLED_NOT
        }

        @Test
        fun `unreadFilterRaw extracts only unread bits`() {
            val flags = Manga.CHAPTER_SHOW_UNREAD or Manga.CHAPTER_SHOW_BOOKMARKED or Manga.CHAPTER_SORT_ASC
            manga(flags).unreadFilterRaw shouldBe Manga.CHAPTER_SHOW_UNREAD
        }
    }

    @Nested
    inner class DownloadedFilter {

        @Test
        fun `Default downloadedFilterRaw is zero`() {
            manga().downloadedFilterRaw shouldBe 0L
        }

        @Test
        fun `SHOW_DOWNLOADED is extracted correctly`() {
            manga(Manga.CHAPTER_SHOW_DOWNLOADED).downloadedFilterRaw shouldBe Manga.CHAPTER_SHOW_DOWNLOADED
        }

        @Test
        fun `SHOW_NOT_DOWNLOADED is extracted correctly`() {
            manga(Manga.CHAPTER_SHOW_NOT_DOWNLOADED).downloadedFilterRaw shouldBe Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        @Test
        fun `downloadedFilterRaw is isolated from other flags`() {
            val flags = Manga.CHAPTER_SHOW_DOWNLOADED or Manga.CHAPTER_SHOW_UNREAD or Manga.CHAPTER_SORTING_ALPHABET
            manga(flags).downloadedFilterRaw shouldBe Manga.CHAPTER_SHOW_DOWNLOADED
        }
    }

    @Nested
    inner class BookmarkedFilter {

        @Test
        fun `Default is DISABLED`() {
            manga().bookmarkedFilter shouldBe TriState.DISABLED
        }

        @Test
        fun `SHOW_BOOKMARKED maps to ENABLED_IS`() {
            manga(Manga.CHAPTER_SHOW_BOOKMARKED).bookmarkedFilter shouldBe TriState.ENABLED_IS
        }

        @Test
        fun `SHOW_NOT_BOOKMARKED maps to ENABLED_NOT`() {
            manga(Manga.CHAPTER_SHOW_NOT_BOOKMARKED).bookmarkedFilter shouldBe TriState.ENABLED_NOT
        }

        @Test
        fun `bookmarkedFilterRaw extracts only bookmarked bits`() {
            val flags = Manga.CHAPTER_SHOW_BOOKMARKED or Manga.CHAPTER_SHOW_UNREAD
            manga(flags).bookmarkedFilterRaw shouldBe Manga.CHAPTER_SHOW_BOOKMARKED
        }
    }

    @Nested
    inner class SubChapterFilter {

        @Test
        fun `Default is DISABLED`() {
            manga().subChapterFilter shouldBe TriState.DISABLED
        }

        @Test
        fun `SHOW_SUB_CHAPTER maps to ENABLED_IS`() {
            manga(Manga.CHAPTER_SHOW_SUB_CHAPTER).subChapterFilter shouldBe TriState.ENABLED_IS
        }

        @Test
        fun `SHOW_NOT_SUB_CHAPTER maps to ENABLED_NOT`() {
            manga(Manga.CHAPTER_SHOW_NOT_SUB_CHAPTER).subChapterFilter shouldBe TriState.ENABLED_NOT
        }

        @Test
        fun `subChapterFilterRaw extracts only sub-chapter bits`() {
            val flags = Manga.CHAPTER_SHOW_SUB_CHAPTER or
                Manga.CHAPTER_SHOW_UNREAD or
                Manga.CHAPTER_SHOW_BOOKMARKED or
                Manga.CHAPTER_SORTING_NUMBER
            manga(flags).subChapterFilterRaw shouldBe Manga.CHAPTER_SHOW_SUB_CHAPTER
        }
    }

    @Nested
    inner class Sorting {

        @Test
        fun `Default sorting is SOURCE`() {
            manga().sorting shouldBe Manga.CHAPTER_SORTING_SOURCE
        }

        @Test
        fun `SORTING_NUMBER is extracted correctly`() {
            manga(Manga.CHAPTER_SORTING_NUMBER).sorting shouldBe Manga.CHAPTER_SORTING_NUMBER
        }

        @Test
        fun `SORTING_UPLOAD_DATE is extracted correctly`() {
            manga(Manga.CHAPTER_SORTING_UPLOAD_DATE).sorting shouldBe Manga.CHAPTER_SORTING_UPLOAD_DATE
        }

        @Test
        fun `SORTING_ALPHABET is extracted correctly`() {
            manga(Manga.CHAPTER_SORTING_ALPHABET).sorting shouldBe Manga.CHAPTER_SORTING_ALPHABET
        }

        @Test
        fun `Sorting is isolated from other flags`() {
            val flags = Manga.CHAPTER_SORTING_UPLOAD_DATE or Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SHOW_UNREAD
            manga(flags).sorting shouldBe Manga.CHAPTER_SORTING_UPLOAD_DATE
        }
    }

    @Nested
    inner class DisplayMode {

        @Test
        fun `Default displayMode is NAME`() {
            manga().displayMode shouldBe Manga.CHAPTER_DISPLAY_NAME
        }

        @Test
        fun `DISPLAY_NUMBER is extracted correctly`() {
            manga(Manga.CHAPTER_DISPLAY_NUMBER).displayMode shouldBe Manga.CHAPTER_DISPLAY_NUMBER
        }

        @Test
        fun `Display mode is isolated from other flags`() {
            val flags = Manga.CHAPTER_DISPLAY_NUMBER or Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SHOW_BOOKMARKED
            manga(flags).displayMode shouldBe Manga.CHAPTER_DISPLAY_NUMBER
        }
    }

    @Nested
    inner class FlagIsolation {

        @Test
        fun `Setting sub-chapter flags does not affect unread or bookmarked`() {
            val m = manga(Manga.CHAPTER_SHOW_SUB_CHAPTER or Manga.CHAPTER_SHOW_NOT_SUB_CHAPTER)
            m.unreadFilter shouldBe TriState.DISABLED
            m.bookmarkedFilter shouldBe TriState.DISABLED
        }

        @Test
        fun `All flags combined extract correctly`() {
            val flags = Manga.CHAPTER_SORT_ASC or
                Manga.CHAPTER_SHOW_UNREAD or
                Manga.CHAPTER_SHOW_DOWNLOADED or
                Manga.CHAPTER_SHOW_BOOKMARKED or
                Manga.CHAPTER_SORTING_ALPHABET or
                Manga.CHAPTER_SHOW_SUB_CHAPTER or
                Manga.CHAPTER_DISPLAY_NUMBER
            val m = manga(flags)

            m.sortDescending() shouldBe false
            m.unreadFilter shouldBe TriState.ENABLED_IS
            m.downloadedFilterRaw shouldBe Manga.CHAPTER_SHOW_DOWNLOADED
            m.bookmarkedFilter shouldBe TriState.ENABLED_IS
            m.sorting shouldBe Manga.CHAPTER_SORTING_ALPHABET
            m.subChapterFilter shouldBe TriState.ENABLED_IS
            m.displayMode shouldBe Manga.CHAPTER_DISPLAY_NUMBER
        }

        @Test
        fun `Zero flags give all defaults`() {
            val m = manga(0L)
            m.sortDescending() shouldBe true
            m.unreadFilter shouldBe TriState.DISABLED
            m.downloadedFilterRaw shouldBe 0L
            m.bookmarkedFilter shouldBe TriState.DISABLED
            m.sorting shouldBe Manga.CHAPTER_SORTING_SOURCE
            m.subChapterFilter shouldBe TriState.DISABLED
            m.displayMode shouldBe Manga.CHAPTER_DISPLAY_NAME
        }
    }
}
