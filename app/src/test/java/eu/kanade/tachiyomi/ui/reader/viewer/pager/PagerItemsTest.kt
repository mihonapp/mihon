package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class PagerItemsTest {

    @Test
    fun `inserts the second half of a page in reading order`() {
        val chapter = ReaderChapter(Chapter.create().copy(id = 1, mangaId = 1))
        val first = ReaderPage(0).apply { this.chapter = chapter }
        val second = ReaderPage(1).apply { this.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(listOf(first, second))
        val chapters = ViewerChapters(chapter, null, null)

        val pagerItems = PagerItems(isReversed = false)
        pagerItems.setChapters(chapters, forceTransition = false, currentPosition = -1)

        val insert = InsertPage(first)
        assertTrue(pagerItems.onPageSplit(first, insert))
        assertEquals(5, pagerItems.items.size)
        assertTrue(pagerItems.items[0] is ChapterTransition)
        assertEquals(first, pagerItems.items[1])
        assertTrue(pagerItems.items[2] is InsertPage)
        assertEquals(second, pagerItems.items[3])
        assertTrue(pagerItems.items[4] is ChapterTransition)
    }

    @Test
    fun `does not insert the same second half twice`() {
        val chapter = ReaderChapter(Chapter.create().copy(id = 1, mangaId = 1))
        val page = ReaderPage(0).apply { this.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(listOf(page))
        val chapters = ViewerChapters(chapter, null, null)

        val pagerItems = PagerItems(isReversed = false)
        pagerItems.setChapters(chapters, forceTransition = false, currentPosition = -1)

        val insert = InsertPage(page)
        assertTrue(pagerItems.onPageSplit(page, insert))
        assertEquals(4, pagerItems.items.size)
        assertEquals(false, pagerItems.onPageSplit(page, insert))
        assertEquals(4, pagerItems.items.size)
    }

    @Test
    fun `cleanup removes inserted pages`() {
        val chapter = ReaderChapter(Chapter.create().copy(id = 1, mangaId = 1))
        val first = ReaderPage(0).apply { this.chapter = chapter }
        val second = ReaderPage(1).apply { this.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(listOf(first, second))
        val chapters = ViewerChapters(chapter, null, null)

        val pagerItems = PagerItems(isReversed = false)
        pagerItems.setChapters(chapters, forceTransition = false, currentPosition = -1)

        val insert = InsertPage(first)
        assertTrue(pagerItems.onPageSplit(first, insert))
        pagerItems.cleanupPageSplit()

        assertEquals(4, pagerItems.items.size)
        assertTrue(pagerItems.items[0] is ChapterTransition)
        assertEquals(first, pagerItems.items[1])
        assertEquals(second, pagerItems.items[2])
        assertTrue(pagerItems.items[3] is ChapterTransition)
    }
}
