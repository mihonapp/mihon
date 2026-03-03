package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.floor

class SubChapterFilterTest {

    private fun isSubChapter(chapterNumber: Double): Boolean {
        return chapterNumber >= 0 && chapterNumber != floor(chapterNumber)
    }

    private fun chapter(number: Double) = Chapter.create().copy(
        chapterNumber = number,
    )

    @Test
    fun `Whole chapter numbers are not sub-chapters`() {
        isSubChapter(1.0) shouldBe false
        isSubChapter(2.0) shouldBe false
        isSubChapter(100.0) shouldBe false
    }

    @Test
    fun `Fractional chapter numbers are sub-chapters`() {
        isSubChapter(1.1) shouldBe true
        isSubChapter(1.5) shouldBe true
        isSubChapter(5.99) shouldBe true
    }

    @Test
    fun `Unrecognized chapters (negative) are never sub-chapters`() {
        isSubChapter(-1.0) shouldBe false
    }

    @Test
    fun `Zero is not a sub-chapter`() {
        isSubChapter(0.0) shouldBe false
    }

    @Test
    fun `applyFilter with DISABLED shows all chapters`() {
        val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(-1.0))
        val filtered = chapters.filter { applyFilter(TriState.DISABLED) { isSubChapter(it.chapterNumber) } }
        filtered.size shouldBe 4
    }

    @Test
    fun `applyFilter with ENABLED_IS shows only sub-chapters`() {
        val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(5.99))
        val filtered = chapters.filter { applyFilter(TriState.ENABLED_IS) { isSubChapter(it.chapterNumber) } }
        filtered.size shouldBe 2
        filtered[0].chapterNumber shouldBe 1.5
        filtered[1].chapterNumber shouldBe 5.99
    }

    @Test
    fun `applyFilter with ENABLED_NOT hides sub-chapters`() {
        val chapters = listOf(chapter(1.0), chapter(1.5), chapter(2.0), chapter(5.99))
        val filtered = chapters.filter { applyFilter(TriState.ENABLED_NOT) { isSubChapter(it.chapterNumber) } }
        filtered.size shouldBe 2
        filtered[0].chapterNumber shouldBe 1.0
        filtered[1].chapterNumber shouldBe 2.0
    }
}
