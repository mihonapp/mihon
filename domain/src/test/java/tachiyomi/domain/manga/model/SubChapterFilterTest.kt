package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.Chapter

class SubChapterFilterTest {

    private fun chapter(number: Double) = Chapter.create().copy(chapterNumber = number)

    @Nested
    inner class IsSubChapterPredicate {

        @Test
        fun `Whole chapter numbers are not sub-chapters`() {
            chapter(1.0).isSubChapter shouldBe false
            chapter(2.0).isSubChapter shouldBe false
            chapter(100.0).isSubChapter shouldBe false
        }

        @Test
        fun `Fractional chapter numbers are sub-chapters`() {
            chapter(1.1).isSubChapter shouldBe true
            chapter(1.5).isSubChapter shouldBe true
            chapter(5.99).isSubChapter shouldBe true
        }

        @Test
        fun `Unrecognized chapters (negative) are never sub-chapters`() {
            chapter(-1.0).isSubChapter shouldBe false
            chapter(-0.5).isSubChapter shouldBe false
        }

        @Test
        fun `Zero is not a sub-chapter`() {
            chapter(0.0).isSubChapter shouldBe false
        }

        @Test
        fun `Zero point five is a sub-chapter`() {
            chapter(0.5).isSubChapter shouldBe true
        }

        @Test
        fun `Very small fractional part is a sub-chapter`() {
            chapter(1.001).isSubChapter shouldBe true
        }

        @Test
        fun `Large chapter number with fraction is a sub-chapter`() {
            chapter(999.5).isSubChapter shouldBe true
        }
    }

    @Nested
    inner class ApplyFilterIntegration {

        @Test
        fun `DISABLED shows all chapters`() {
            val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(-1.0))
            val filtered = chapters.filter { applyFilter(TriState.DISABLED) { it.isSubChapter } }
            filtered.size shouldBe 4
        }

        @Test
        fun `ENABLED_IS shows only sub-chapters`() {
            val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(5.99))
            val filtered = chapters.filter { applyFilter(TriState.ENABLED_IS) { it.isSubChapter } }
            filtered.size shouldBe 2
            filtered[0].chapterNumber shouldBe 1.5
            filtered[1].chapterNumber shouldBe 5.99
        }

        @Test
        fun `ENABLED_NOT hides sub-chapters`() {
            val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(5.99))
            val filtered = chapters.filter { applyFilter(TriState.ENABLED_NOT) { it.isSubChapter } }
            filtered.size shouldBe 2
            filtered[0].chapterNumber shouldBe 1.0
            filtered[1].chapterNumber shouldBe 2.0
        }

        @Test
        fun `ENABLED_IS with no sub-chapters returns empty`() {
            val chapters = listOf(chapter(1.0), chapter(2.0), chapter(3.0))
            val filtered = chapters.filter { applyFilter(TriState.ENABLED_IS) { it.isSubChapter } }
            filtered.size shouldBe 0
        }

        @Test
        fun `ENABLED_NOT with only sub-chapters returns empty`() {
            val chapters = listOf(chapter(1.5), chapter(2.5), chapter(3.5))
            val filtered = chapters.filter { applyFilter(TriState.ENABLED_NOT) { it.isSubChapter } }
            filtered.size shouldBe 0
        }

        @Test
        fun `Unrecognized chapters are kept by ENABLED_NOT`() {
            val chapters = listOf(chapter(-1.0), chapter(1.5), chapter(2.0))
            val filtered = chapters.filter { applyFilter(TriState.ENABLED_NOT) { it.isSubChapter } }
            filtered.size shouldBe 2
            filtered[0].chapterNumber shouldBe -1.0
            filtered[1].chapterNumber shouldBe 2.0
        }
    }
}
