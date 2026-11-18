package mihon.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ReadingWindowTest {

    @Test
    fun `new chapter past the window is excluded`() {
        // 45 chapters, first 10 read, next 3 unread are 11..13. Chapter 46 is outside.
        val chapters = chapters(1..45, readThrough = 10) + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 3)

        window shouldBe setOf(11L, 12L, 13L)
        (46L in window) shouldBe false
    }

    @Test
    fun `new chapter is included when the previous chapter is read`() {
        val chapters = chapters(1..45, readThrough = 44) + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 3)

        window shouldBe setOf(45L, 46L)
    }

    @Test
    fun `page progress counts the same as a finished chapter`() {
        val chapters = chapters(1..45, readThrough = 10).map { existing ->
            if (existing.id == 44L) existing.copy(lastPageRead = 3) else existing
        } + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 3)

        window shouldBe setOf(45L, 46L)
    }

    @Test
    fun `opening a chapter on the first page counts as the reading position`() {
        val chapters = chapters(1..45, readThrough = 10) + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = setOf(44L), amount = 3)

        window shouldBe setOf(45L, 46L)
    }

    @Test
    fun `unread chapters between the position and the new chapter still consume the window`() {
        val chapters = chapters(1..45, readThrough = 44) + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 1)

        window shouldBe setOf(45L)
    }

    @Test
    fun `without a reading position only the first unread chapters are eligible`() {
        val chapters = chapters(1..45, readThrough = 0) + chapter(46)
        val window = readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 3)

        window shouldBe setOf(1L, 2L, 3L)
    }

    @Test
    fun `disabled window selects nothing`() {
        val chapters = chapters(1..45, readThrough = 44) + chapter(46)
        readingWindowChapterIds(chapters, openedChapterIds = emptySet(), amount = 0) shouldBe emptySet()
    }

    private fun chapters(numbers: IntRange, readThrough: Int): List<Chapter> {
        return numbers.map { number -> chapter(number, read = number <= readThrough) }
    }

    private fun chapter(number: Int, read: Boolean = false): Chapter {
        return Chapter.create().copy(
            id = number.toLong(),
            chapterNumber = number.toDouble(),
            read = read,
        )
    }
}
