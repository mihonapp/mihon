package eu.kanade.tachiyomi.util.chapter

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class ChapterGetNextUnreadTest {

    private val manga = Manga.create().copy(
        chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC,
    )

    private fun chapters(read: Set<Int> = emptySet(), started: Set<Int> = emptySet()): List<Chapter> {
        return (1..10).map { number ->
            Chapter.create().copy(
                id = number.toLong(),
                chapterNumber = number.toDouble(),
                read = number in read,
                lastPageRead = if (number in started) 5 else 0,
            )
        }
    }

    private fun List<Chapter>.nextToRead(visible: List<Chapter> = this): Int? {
        return findNextToRead(manga, visible)?.chapterNumber?.toInt()
    }

    @Test
    fun `starts at the first chapter without any progress`() {
        chapters().nextToRead() shouldBe 1
    }

    @Test
    fun `continues after the furthest read chapter`() {
        chapters(read = setOf(1, 2, 3)).nextToRead() shouldBe 4
    }

    @Test
    fun `skips earlier unread chapters when starting in the middle`() {
        chapters(read = setOf(5)).nextToRead() shouldBe 6
    }

    @Test
    fun `reopens a started chapter`() {
        chapters(read = setOf(4), started = setOf(5)).nextToRead() shouldBe 5
    }

    @Test
    fun `falls back to the first unread chapter when the last one is read`() {
        chapters(read = setOf(2, 3, 10)).nextToRead() shouldBe 1
    }

    @Test
    fun `is null when everything is read`() {
        chapters(read = (1..10).toSet()).nextToRead() shouldBe null
    }

    @Test
    fun `keeps the reading position when a filter hides read chapters`() {
        val all = chapters(read = setOf(1, 2, 3, 4, 5))
        all.nextToRead(visible = all.filterNot { it.read }) shouldBe 6
    }

    @Test
    fun `picks the next visible chapter when a filter hides the next one`() {
        val all = chapters(read = setOf(5))
        all.nextToRead(visible = all.filterNot { it.chapterNumber == 6.0 }) shouldBe 7
    }
}
