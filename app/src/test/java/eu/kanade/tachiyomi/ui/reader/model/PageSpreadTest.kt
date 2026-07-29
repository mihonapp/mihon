package eu.kanade.tachiyomi.ui.reader.model

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PageSpreadTest {

    private val ch = mockk<ReaderChapter>()

    private fun page(index: Int): ReaderPage = ReaderPage(index).apply { chapter = ch }

    @Test
    fun `spreads wrapping the same two pages are equal`() {
        val a = page(0)
        val b = page(1)
        assertEquals(PageSpread(a, b), PageSpread(a, b))
        assertEquals(PageSpread(a, b).hashCode(), PageSpread(a, b).hashCode())
    }

    @Test
    fun `spreads differing in a page, or in order, are unequal`() {
        val a = page(0)
        val b = page(1)
        val c = page(2)
        assertNotEquals(PageSpread(a, b), PageSpread(a, c))
        assertNotEquals(PageSpread(a, b), PageSpread(b, a))
    }

    @Test
    fun `a freshly built spread is found in a list by value`() {
        // getItemPosition must locate a freshly built PageSpread over the same (stable) pages by value:
        // a list rebuild constructs a new instance that the pager still finds in the rebuilt list.
        val a = page(0)
        val b = page(1)
        val items = listOf<Any>(a, PageSpread(a, b))
        assertEquals(1, items.indexOf(PageSpread(a, b)))
    }
}
