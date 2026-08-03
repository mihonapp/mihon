package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the user-facing invariant behind "land on the leading page when collapsing a spread"
 * (ReaderViewModel.setMangaSpread: requestedPage = currentPage - 1, where currentPage is the
 * leading page number of the current spread; see updateChapterProgress: currentPage =
 * displayIndex + 1, displayIndex = the nearer/first page's index).
 *
 * The collapse target is therefore the spread's leading page ([PagerNavigation.leadingPage]). These
 * tests pin the properties that landing choice must uphold.
 */
class SpreadToggleLandingTest {

    private val ch = mockk<ReaderChapter>()

    private fun page(index: Int): ReaderPage = ReaderPage(index).apply { chapter = ch }

    private fun spreadContaining(items: List<Any>, p: ReaderPage): PageSpread =
        items.filterIsInstance<PageSpread>().first { it.firstPage === p || it.secondPage === p }

    /** Invariant NO-SKIP: collapsing a spread never advances the reader past a page they were viewing. */
    @Test
    fun `collapsing a spread never skips forward past a viewed page`() {
        val pages = List(6) { page(it) }
        for (shifted in listOf(false, true)) {
            val items = SpreadPairing.pairFlat(pages, shifted)
            pages.forEach { p ->
                // A page may be solo (shifted first page, or an odd trailing page); collapsing a solo
                // page is a no-op. Only the pairing case is interesting.
                val spread = items.filterIsInstance<PageSpread>().firstOrNull {
                    it.firstPage === p ||
                        it.secondPage === p
                }
                if (spread != null) {
                    val landing = PagerNavigation.leadingPage(spread)!!
                    assertTrue(
                        landing.index <= p.index,
                        "collapse landed on ${landing.index}, skipping past viewed page ${p.index} (shifted=$shifted)",
                    )
                }
            }
        }
    }

    /** An opening 1-2 spread collapses to page 1, so the reader can still read from the start. */
    @Test
    fun `opening spread collapses to the first page`() {
        val items = SpreadPairing.pairFlat(List(4) { page(it) }, shifted = false)
        assertEquals(
            0,
            PagerNavigation.leadingPage(
                spreadContaining(items, items.filterIsInstance<PageSpread>().first().firstPage),
            )!!.index,
        )
        // Explicitly: page 0 and page 1 are the opening pair; collapsing it lands on page 0.
        assertEquals(0, PagerNavigation.leadingPage(items[0] as PageSpread)!!.index)
    }

    /**
     * Documents the round-trip: enabling then disabling is *not* strict identity, and cannot be for
     * any stateless rule: viewing a spread touches both its pages (progress advances to the trailing
     * one, the display shows the leading one). This rule breaks identity in the SAFE direction: a page
     * that pairs as the *trailing* half round-trips back to the leading half (a re-read), never forward
     * (a skip). A leading-half page round-trips to itself.
     */
    @Test
    fun `round-trip retreats at most one page, never advances`() {
        val pages = List(6) { page(it) }
        val items = SpreadPairing.pairFlat(pages, shifted = false) // {0,1} {2,3} {4,5}
        pages.forEach { p ->
            val landing = PagerNavigation.leadingPage(spreadContaining(items, p))!!
            val delta = p.index - landing.index // enable-from-p then disable lands here
            assertTrue(delta == 0 || delta == 1, "round-trip moved page ${p.index} by $delta (want 0 or 1 back)")
        }
        // Concretely: leading halves (0,2,4) round-trip to themselves; trailing halves (1,3,5) retreat one.
        assertEquals(0, PagerNavigation.leadingPage(spreadContaining(items, pages[0]))!!.index)
        assertEquals(0, PagerNavigation.leadingPage(spreadContaining(items, pages[1]))!!.index)
        assertEquals(2, PagerNavigation.leadingPage(spreadContaining(items, pages[2]))!!.index)
        assertEquals(2, PagerNavigation.leadingPage(spreadContaining(items, pages[3]))!!.index)
    }
}
