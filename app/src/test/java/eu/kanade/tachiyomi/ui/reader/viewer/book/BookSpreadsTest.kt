package eu.kanade.tachiyomi.ui.reader.viewer.book

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class BookSpreadsTest {

    private fun chapter(id: Long, pageCount: Int): List<ReaderPage> {
        val chapter = ReaderChapter(Chapter.create().copy(id = id))
        return List(pageCount) { index -> ReaderPage(index).also { it.chapter = chapter } }
    }

    private val notWide: (ReaderPage) -> Boolean = { false }

    @Test
    fun `single layout shows every item alone`() {
        val pages = chapter(1, 3)
        buildSpreads(pages, double = false, firstPageAlone = true, isWide = notWide) shouldBe
            pages.map { Spread.Full(it) }
    }

    @Test
    fun `first page alone then pages pair up`() {
        val pages = chapter(1, 6)
        buildSpreads(pages, double = true, firstPageAlone = true, isWide = notWide) shouldBe listOf(
            Spread.Facing(null, pages[0]),
            Spread.Facing(pages[1], pages[2]),
            Spread.Facing(pages[3], pages[4]),
            Spread.Facing(pages[5], null),
        )
    }

    @Test
    fun `pages pair from the first without the cover alone`() {
        val pages = chapter(1, 4)
        buildSpreads(pages, double = true, firstPageAlone = false, isWide = notWide) shouldBe listOf(
            Spread.Facing(pages[0], pages[1]),
            Spread.Facing(pages[2], pages[3]),
        )
    }

    @Test
    fun `a wide page stands alone and leaves the page before it unpaired`() {
        val pages = chapter(1, 6)
        val wide = pages[3]
        buildSpreads(pages, double = true, firstPageAlone = true, isWide = { it === wide }) shouldBe listOf(
            Spread.Facing(null, pages[0]),
            Spread.Facing(pages[1], pages[2]),
            Spread.Full(wide),
            Spread.Facing(pages[4], pages[5]),
        )
    }

    @Test
    fun `pages never pair across chapters or transitions`() {
        val first = chapter(1, 3)
        val second = chapter(2, 2)
        val transition = ChapterTransition.Next(first[0].chapter, second[0].chapter)
        val items = first + transition + second
        buildSpreads(items, double = true, firstPageAlone = false, isWide = notWide) shouldBe listOf(
            Spread.Facing(first[0], first[1]),
            Spread.Facing(first[2], null),
            Spread.Full(transition),
            Spread.Facing(second[0], second[1]),
        )
    }

    @Test
    fun `facing pages swap sides right to left`() {
        val pages = chapter(1, 2)
        val spread = Spread.Facing(pages[0], pages[1])
        spread.left(isRightToLeft = false) shouldBe pages[0]
        spread.right(isRightToLeft = true) shouldBe pages[0]
    }

    @Test
    fun `facing pages meet at the spine`() {
        val pages = chapter(1, 2)
        val placer = SpreadPlacer(Size(2000f, 1000f), 1000f..1000f, isRightToLeft = false) { IntSize(700, 1000) }
        val rects = placer.place(Spread.Facing(pages[0], pages[1]))
        rects.getValue(pages[0]).right shouldBe 1000f
        rects.getValue(pages[1]).left shouldBe 1000f
        rects.getValue(pages[0]).width shouldBe 700f
    }
}
