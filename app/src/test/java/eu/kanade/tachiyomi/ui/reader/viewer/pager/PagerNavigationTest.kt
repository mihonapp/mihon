package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagerNavigationTest {

    private val chA = mockk<ReaderChapter>()
    private val chB = mockk<ReaderChapter>()

    private fun page(index: Int, chapter: ReaderChapter = chA): ReaderPage =
        ReaderPage(index).apply { this.chapter = chapter }

    // --- comparablePage ---

    @Test
    fun `comparablePage maps a lone page to itself and a spread to its later page`() {
        val p = page(3)
        assertSame(p, PagerNavigation.comparablePage(p))

        val a = page(4)
        val b = page(5)
        assertSame(b, PagerNavigation.comparablePage(PageSpread(a, b)))
    }

    @Test
    fun `comparablePage has no page for a transition or null`() {
        assertNull(PagerNavigation.comparablePage(ChapterTransition.Next(chA, chB)))
        assertNull(PagerNavigation.comparablePage(null))
    }

    // --- screenOrder (the on-screen side↔page mapping a per-half page action reads back) ---

    @Test
    fun `screenOrder puts the first page on the left in L2R and on the right in R2L`() {
        val first = page(4)
        val second = page(5)
        val spread = PageSpread(first, second)

        val (l2rLeft, l2rRight) = PagerNavigation.screenOrder(spread, isL2r = true)
        assertSame(first, l2rLeft)
        assertSame(second, l2rRight)

        // R2L flips the on-screen order: the earlier page sits on the right, so a press on the *left*
        // half of an R2L spread targets the second (higher-index) page.
        val (r2lLeft, r2lRight) = PagerNavigation.screenOrder(spread, isL2r = false)
        assertSame(second, r2lLeft)
        assertSame(first, r2lRight)
    }

    // --- leadingPage (the shift anchor) ---

    @Test
    fun `leadingPage maps a lone page to itself and a spread to its first page`() {
        val p = page(3)
        assertSame(p, PagerNavigation.leadingPage(p))

        val a = page(4)
        val b = page(5)
        assertSame(a, PagerNavigation.leadingPage(PageSpread(a, b)))
    }

    @Test
    fun `shifting keeps the reader on the leading page instead of skipping to the next spread`() {
        // Chapter open on the (1,2) spread; the classifier picks shift-1, so pairing becomes
        // [1] (2,3) (4,5) [6]. Anchoring on the leading page (1) lands on the now-solo page 1: the
        // reader stays on page 1. Anchoring on the later page (2) would skip to the (2,3) spread,
        // bypassing page 1, the skip the leading-page anchor exists to prevent.
        val pages = (1..6).map { page(it) }
        val currentSpread = PageSpread(pages[0], pages[1])
        val shifted = listOf(
            pages[0],
            PageSpread(pages[1], pages[2]),
            PageSpread(pages[3], pages[4]),
            pages[5],
        )
        assertEquals(0, PagerNavigation.positionOfAnchor(shifted, PagerNavigation.leadingPage(currentSpread)!!))
        // Contrast: the later-page anchor lands on the (2,3) spread, the skip.
        assertEquals(1, PagerNavigation.positionOfAnchor(shifted, PagerNavigation.comparablePage(currentSpread)!!))
    }

    // --- positionOfPage ---

    @Test
    fun `positionOfPage finds a standalone page`() {
        val p = List(3) { page(it) }
        assertEquals(2, PagerNavigation.positionOfPage(p, p[2]))
    }

    @Test
    fun `positionOfPage finds a page nested as either half of a spread`() {
        val a = page(0)
        val b = page(1)
        val c = page(2)
        val items = listOf<Any>(page(9), PageSpread(a, b), c)
        assertEquals(1, PagerNavigation.positionOfPage(items, a)) // first half
        assertEquals(1, PagerNavigation.positionOfPage(items, b)) // second half
        assertEquals(2, PagerNavigation.positionOfPage(items, c))
    }

    @Test
    fun `positionOfPage returns -1 for a page not present`() {
        val items = listOf<Any>(page(0), page(1))
        assertEquals(-1, PagerNavigation.positionOfPage(items, page(0))) // a different instance
    }

    // --- positionOfAnchor ---

    @Test
    fun `positionOfAnchor matches a transition structurally across a fresh instance`() {
        val items = listOf<Any>(page(0), ChapterTransition.Prev(chA, chB), page(1, chB))
        // A rebuild would hand back a *different* instance carrying the same chapters.
        assertEquals(1, PagerNavigation.positionOfAnchor(items, ChapterTransition.Prev(chA, chB)))
    }

    @Test
    fun `positionOfAnchor matches a page anchor nested in a spread by reference`() {
        val a = page(0)
        val b = page(1)
        val items = listOf<Any>(ChapterTransition.Prev(chA, null), PageSpread(a, b))
        assertEquals(1, PagerNavigation.positionOfAnchor(items, b))
    }

    @Test
    fun `positionOfAnchor returns -1 when the anchor is gone`() {
        val items = listOf<Any>(page(0), page(1))
        assertEquals(-1, PagerNavigation.positionOfAnchor(items, ChapterTransition.Next(chA, chB)))
    }

    // --- isForwardTurn ---

    @Test
    fun `isForwardTurn compares page numbers`() {
        assertTrue(PagerNavigation.isForwardTurn(page(2), page(5), cameFromPrevTransition = false))
        assertFalse(PagerNavigation.isForwardTurn(page(5), page(2), cameFromPrevTransition = false))
    }

    @Test
    fun `isForwardTurn treats a same-numbered InsertPage as the forward half of a split`() {
        val original = page(5)
        val insert = InsertPage(original) // same index, so same number
        assertTrue(PagerNavigation.isForwardTurn(original, insert, cameFromPrevTransition = false))
        // Two plain pages sharing a number (not an insert) is not a forward move.
        assertFalse(PagerNavigation.isForwardTurn(page(5), page(5), cameFromPrevTransition = false))
    }

    @Test
    fun `isForwardTurn without a previous page reads a prev-chapter transition as backward`() {
        assertFalse(PagerNavigation.isForwardTurn(previous = null, current = page(0), cameFromPrevTransition = true))
        assertTrue(PagerNavigation.isForwardTurn(previous = null, current = page(0), cameFromPrevTransition = false))
    }

    // --- layoutAction: decide-before-display for the whole window build ---

    @Test
    fun `layoutAction holds whenever detection is pending, on any pager state`() {
        // The crux: an awaiting current chapter holds even on the live (not-gone) path (a chapter
        // reached by chapter-forward), so it is never displayed at a default pairing that then reflows.
        assertEquals(
            PagerNavigation.LayoutAction.HOLD,
            PagerNavigation.layoutAction(isGone = true, isAwaitingDetection = true),
        )
        assertEquals(
            PagerNavigation.LayoutAction.HOLD,
            PagerNavigation.layoutAction(isGone = false, isAwaitingDetection = true),
        )
    }

    @Test
    fun `layoutAction lays out a fresh window and relocates a live one once decided`() {
        assertEquals(
            PagerNavigation.LayoutAction.LAYOUT_FIRST,
            PagerNavigation.layoutAction(isGone = true, isAwaitingDetection = false),
        )
        assertEquals(
            PagerNavigation.LayoutAction.RELOCATE,
            PagerNavigation.layoutAction(isGone = false, isAwaitingDetection = false),
        )
    }
}
