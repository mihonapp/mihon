package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SpreadPairingTest {

    // Opaque chapter identities: pairing only ever compares chapters for equality.
    private val chA = mockk<ReaderChapter>()
    private val chB = mockk<ReaderChapter>()

    private fun page(index: Int, chapter: ReaderChapter = chA): ReaderPage =
        ReaderPage(index).apply { this.chapter = chapter }

    private fun assertSpread(item: Any, first: ReaderPage, second: ReaderPage) {
        assertTrue(item is PageSpread, "expected a PageSpread but was ${item::class.simpleName}")
        item as PageSpread
        assertSame(first, item.firstPage)
        assertSame(second, item.secondPage)
    }

    // --- pairFlat ---

    @Test
    fun `pairFlat pairs consecutive pages from the first`() {
        val p = List(4) { page(it) }
        val r = SpreadPairing.pairFlat(p, shifted = false)
        assertEquals(2, r.size)
        assertSpread(r[0], p[0], p[1])
        assertSpread(r[1], p[2], p[3])
    }

    @Test
    fun `pairFlat leaves a trailing odd page solo`() {
        val p = List(5) { page(it) }
        val r = SpreadPairing.pairFlat(p, shifted = false)
        assertEquals(3, r.size)
        assertSpread(r[0], p[0], p[1])
        assertSpread(r[1], p[2], p[3])
        assertSame(p[4], r[2])
    }

    @Test
    fun `pairFlat shifted leaves the first page solo and pairs the rest`() {
        val p = List(5) { page(it) }
        val r = SpreadPairing.pairFlat(p, shifted = true)
        assertEquals(3, r.size)
        assertSame(p[0], r[0])
        assertSpread(r[1], p[1], p[2])
        assertSpread(r[2], p[3], p[4])
    }

    @Test
    fun `pairFlat never pairs across an InsertPage`() {
        val p0 = page(0)
        val insert = InsertPage(p0)
        val p1 = page(1)
        val r = SpreadPairing.pairFlat(listOf(p0, insert, p1), shifted = false)
        // p0 can't pair with the insert, the insert is solo, p1 has no partner.
        assertEquals(3, r.size)
        assertFalse(r.any { it is PageSpread })
    }

    // --- wide pages as run boundaries ("treat wide pages as spreads") ---

    @Test
    fun `pairFlat keeps the run after a wide paired at the pinned shift`() {
        // cover, 1, 2, [W = facing spread of 3-4], 5, 6: W at index 3 pins shift-1 (cover solo).
        val p = List(6) { page(it) }
        val wide = p[3]
        val r = SpreadPairing.pairFlat(p, shifted = true, isWide = { it === wide })
        assertEquals(4, r.size)
        assertSame(p[0], r[0]) // cover solo
        assertSpread(r[1], p[1], p[2]) // (1,2)
        assertSame(wide, r[2]) // wide shown solo (whole)
        assertSpread(r[3], p[4], p[5]) // (5,6): the parity flows across the wide, so this stays paired
    }

    @Test
    fun `pairFlat flows the shift across a wide so the run after it re-pairs too`() {
        val p = List(10) { page(it) }
        val wide = p[5]
        // Pinned shift (true): 0 solo, (1,2), (3,4), W, (6,7), (8,9): clean, post-wide paired.
        val pinned = SpreadPairing.pairFlat(p, shifted = true, isWide = { it === wide })
        assertSame(p[0], pinned[0])
        assertSpread(pinned[4], p[6], p[7])
        // Flipped (false): (0,1), (2,3), 4, W, 6, (7,8), 9: the run AFTER the wide flips too.
        val flipped = SpreadPairing.pairFlat(p, shifted = false, isWide = { it === wide })
        assertEquals(7, flipped.size)
        assertSpread(flipped[0], p[0], p[1])
        assertSpread(flipped[1], p[2], p[3])
        assertSame(p[4], flipped[2])
        assertSame(wide, flipped[3])
        assertSame(p[6], flipped[4]) // paired in the default; solo now, the shift reached across the wide
        assertSpread(flipped[5], p[7], p[8])
        assertSame(p[9], flipped[6])
    }

    @Test
    fun `pairFlat leaves the inconsistency's lone page and flows on without re-aligning`() {
        // Two wides with an ODD run of singles between them (indices 4,5,6): the wide-regular-wide
        // structural inconsistency. No single shift aligns both wides, so one page is unavoidably lone.
        // The parity flows past it uninterrupted; it does NOT reset at the second wide to re-pair the
        // tail, so the run after the inconsistency reads as it would with the feature off. Pinning this
        // keeps a future refactor from silently switching to a re-aligning (run-reset) tail.
        val p = List(14) { page(it) }
        val wides = setOf(p[3], p[7])
        // The first interior wide (index 3) pins shift-1.
        val shift = SpreadPairing.decisiveShiftFromWides(p, isWide = { it in wides })
        assertEquals(true, shift)

        val r = SpreadPairing.pairFlat(p, shifted = shift!!, isWide = { it in wides })
        assertSame(p[0], r[0]) // solo (shift)
        assertSpread(r[1], p[1], p[2])
        assertSame(p[3], r[2]) // wide
        assertSpread(r[3], p[4], p[5])
        assertSame(p[6], r[4]) // the unavoidable lone page of the odd run
        assertSame(p[7], r[5]) // wide
        assertSame(p[8], r[6]) // tail flows on: lone, not re-aligned to pair with p9
        assertSpread(r[7], p[9], p[10])
        assertSpread(r[8], p[11], p[12])
        assertSame(p[13], r[9])
        assertEquals(10, r.size)
    }

    @Test
    fun `pairFlat with the default never-wide predicate is unchanged`() {
        val p = List(6) { page(it) }
        assertEquals(
            SpreadPairing.pairFlat(p, shifted = false),
            SpreadPairing.pairFlat(p, shifted = false, isWide = {
                false
            }),
        )
    }

    // --- decisiveShiftFromWides ---

    @Test
    fun `decisiveShiftFromWides pins the shift from the first interior wide's parity`() {
        val p = List(6) { page(it) }
        // wide at an odd index -> shift-1; at an even index -> shift-0.
        assertEquals(true, SpreadPairing.decisiveShiftFromWides(p, isWide = { it === p[3] }))
        assertEquals(false, SpreadPairing.decisiveShiftFromWides(p, isWide = { it === p[2] }))
    }

    @Test
    fun `decisiveShiftFromWides pins a lone cover wide unshifted and is null with no wide`() {
        val p = List(6) { page(it) }
        // A lone cover wide pins false: it occupies a column the parity crosses, so the run after it pairs
        // cleanly only unshifted (post-cover pages pair from page 1, matching feature-off).
        assertEquals(false, SpreadPairing.decisiveShiftFromWides(p, isWide = { it === p[0] }))
        assertEquals(null, SpreadPairing.decisiveShiftFromWides(p, isWide = { false })) // no wide at all
        // A cover wide occupies a column, so the interior wide at 3 pins shift-0 (its column is 3 + 1).
        assertEquals(false, SpreadPairing.decisiveShiftFromWides(p, isWide = { it === p[0] || it === p[3] }))
    }

    @Test
    fun `a lone cover wide's pinned shift outranks the classifier`() {
        // A lone cover pins unshifted (decisiveShiftFromWides -> false); that decisive-wide parity sits above
        // the classifier's verdict in the ladder, so even a classifier that says "shift" can't offset the
        // post-cover pairing. Pins the precedence against a refactor quietly reordering the rungs.
        val p = List(4) { page(it) }
        val coverShift = SpreadPairing.decisiveShiftFromWides(p, isWide = { it === p[0] })
        assertEquals(false, coverShift)
        assertEquals(
            false,
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = coverShift,
                detected = true, // classifier says shift
                default = true,
            ),
        )
    }

    @Test
    fun `pairFlat pairs a cover-only chapter from the first page, not soloing page 1`() {
        // A wide cover with no interior wide: the shift must not double-apply (the cover already is the lone
        // first slot). decisiveShiftFromWides pins it unshifted, so the pages after the cover pair from 1,
        // matching feature-off.
        val p = List(6) { page(it) }
        val cover = p[0]
        val shift = SpreadPairing.decisiveShiftFromWides(p, isWide = { it === cover })
        assertEquals(false, shift)
        val r = SpreadPairing.pairFlat(p, shifted = shift!!, isWide = { it === cover })
        assertEquals(4, r.size)
        assertSame(cover, r[0]) // wide cover, solo
        assertSpread(r[1], p[1], p[2])
        assertSpread(r[2], p[3], p[4])
        assertSame(p[5], r[3]) // trailing odd page
    }

    @Test
    fun `pairingColumnIndex counts a wide as two columns, not a run break`() {
        val p = List(5) { page(it) }
        val items = listOf<Any>(PageSpread(p[0], p[1]), p[2], p[3], p[4]) // p2 is the wide (solo)
        // Wide-blind, p4's column is its index (4); wide-aware, the spread at p2 adds a column -> 5.
        assertEquals(4, SpreadPairing.pairingColumnIndex(items, p[4]))
        assertEquals(5, SpreadPairing.pairingColumnIndex(items, p[4], isWide = { it === p[2] }))
    }

    // --- pairInStoryOrder ---

    @Test
    fun `pairInStoryOrder never pairs across a chapter boundary`() {
        val a0 = page(0, chA)
        val a1 = page(1, chA)
        val b0 = page(2, chB)
        val r = SpreadPairing.pairInStoryOrder(listOf(a0, a1, b0), shifted = false)
        assertEquals(2, r.size)
        assertSpread(r[0], a0, a1)
        assertSame(b0, r[1]) // different chapter, no partner
    }

    // --- regroupChapterAware ---

    @Test
    fun `regroup L2R unshifted pairs from the first page`() {
        val p = List(5) { page(it) }
        val r = SpreadPairing.regroupChapterAware(p, isR2L = false, shiftFor = { false })
        assertEquals(3, r.size)
        assertSpread(r[0], p[0], p[1])
        assertSpread(r[1], p[2], p[3])
        assertSame(p[4], r[2])
    }

    @Test
    fun `regroup L2R shifted leaves the current chapter's first page solo`() {
        val p = List(5) { page(it) }
        val r = SpreadPairing.regroupChapterAware(p, isR2L = false, shiftFor = { it == chA })
        assertEquals(3, r.size)
        assertSame(p[0], r[0])
        assertSpread(r[1], p[1], p[2])
        assertSpread(r[2], p[3], p[4])
    }

    @Test
    fun `regroup R2L shifted actually changes an odd-length chapter's grouping`() {
        // For an odd-length R2L chapter, shifting must actually change the grouping: pairing in
        // display order without the story-order round-trip would leave it identical to unshifted, so
        // the toggle would have no visible effect. Source is in display order (R2L = story reversed).
        val story = List(5) { page(it) } // p0..p4 in story order
        val display = story.asReversed().toList() // [p4, p3, p2, p1, p0]

        val unshifted = SpreadPairing.regroupChapterAware(
            display,
            isR2L = true,
            shiftFor = { false },
        )
        val shifted = SpreadPairing.regroupChapterAware(
            display,
            isR2L = true,
            shiftFor = { it == chA },
        )

        // Unshifted pairs (0,1)(2,3) with p4 solo; in display order the solo p4 comes first.
        assertSame(story[4], unshifted[0])
        // Shifted pairs (1,2)(3,4) with p0 solo; in display order the solo p0 comes last.
        assertSame(story[0], shifted[shifted.size - 1])
        // The two groupings must differ, the whole point of the toggle.
        assertTrue(unshifted != shifted)
        // And the shift is coherent: the last display item is p0 alone, the rest are spreads.
        assertEquals(3, shifted.size)
        assertSpread(shifted[0], story[3], story[4])
        assertSpread(shifted[1], story[1], story[2])
    }

    @Test
    fun `regroup only shifts the current chapter, not others`() {
        // chA pages then chB pages, shifted, current = chA. Only chA's run shifts; chB pairs normally.
        val a = List(3) { page(it, chA) } // odd
        val b = List(2) { page(it + 10, chB) }
        val r = SpreadPairing.regroupChapterAware(a + b, isR2L = false, shiftFor = { it == chA })
        // chA shifted: p0 solo, (a1,a2). chB unshifted: (b0,b1).
        assertSame(a[0], r[0])
        assertSpread(r[1], a[1], a[2])
        assertSpread(r[2], b[0], b[1])
    }

    // --- generative invariants (random inputs, seeded for reproducibility) ---

    private val chC = mockk<ReaderChapter>()

    /** Unwrap spreads back to their two display-order pages; everything else passes through. */
    private fun flattenDisplay(items: List<Any>, isR2L: Boolean): List<Any> =
        items.flatMap { if (it is PageSpread) SpreadPairing.spreadOrder(it, isR2L).toList() else listOf(it) }

    @Test
    fun `pairFlat preserves every page once and in order, never pairing across an insert`() {
        val rnd = Random(1)
        repeat(1000) {
            val pages = (0 until rnd.nextInt(0, 12)).map { i ->
                if (i > 0 && rnd.nextInt(4) == 0) InsertPage(page(i)) else page(i)
            }
            for (shifted in booleanArrayOf(false, true)) {
                val r = SpreadPairing.pairFlat(pages, shifted)
                val flat = r.flatMap { if (it is PageSpread) listOf(it.firstPage, it.secondPage) else listOf(it) }
                assertEquals(pages, flat, "pairFlat must preserve the page sequence (shifted=$shifted)")
                assertFalse(
                    r.any { it is PageSpread && (it.firstPage is InsertPage || it.secondPage is InsertPage) },
                    "no spread may contain an InsertPage",
                )
            }
        }
    }

    @Test
    fun `regroupChapterAware preserves the display sequence and never spreads across a boundary`() {
        val rnd = Random(2)
        val chapters = listOf(chA, chB, chC)
        repeat(1000) {
            val source = mutableListOf<Any>()
            var idx = 0
            repeat(rnd.nextInt(1, 5)) {
                val ch = chapters[rnd.nextInt(chapters.size)]
                repeat(rnd.nextInt(0, 5)) {
                    source.add(if (rnd.nextInt(5) == 0) InsertPage(page(idx, ch)) else page(idx, ch))
                    idx++
                }
                if (rnd.nextInt(3) == 0) source.add(ChapterTransition.Next(ch, null))
            }
            for (isR2L in booleanArrayOf(false, true)) {
                for (shifted in booleanArrayOf(false, true)) {
                    val result = SpreadPairing.regroupChapterAware(source, isR2L, shiftFor = { shifted && it == chA })
                    assertEquals(
                        flattenDisplay(source, isR2L),
                        flattenDisplay(result, isR2L),
                        "regroup must preserve the display-order sequence (isR2L=$isR2L shifted=$shifted)",
                    )
                    result.filterIsInstance<PageSpread>().forEach { s ->
                        assertFalse(s.firstPage is InsertPage || s.secondPage is InsertPage, "no insert in a spread")
                        assertSame(s.firstPage.chapter, s.secondPage.chapter, "no spread across a chapter boundary")
                    }
                }
            }
        }
    }

    // --- soloSideIsLeft: which half a justified lone page justifies to ---

    @Test
    fun `soloSideIsLeft puts an L2R first-of-pair page left and a second-of-pair page right`() {
        // Unshifted L2R: even positions are first-of-pair (left), odd are second-of-pair (right).
        assertTrue(SpreadPairing.soloSideIsLeft(0, shifted = false, isR2L = false))
        assertFalse(SpreadPairing.soloSideIsLeft(1, shifted = false, isR2L = false))
        assertTrue(SpreadPairing.soloSideIsLeft(4, shifted = false, isR2L = false)) // trailing odd page
    }

    @Test
    fun `soloSideIsLeft moves the shifted L2R leading page to the recto (right)`() {
        assertFalse(SpreadPairing.soloSideIsLeft(0, shifted = true, isR2L = false))
    }

    @Test
    fun `soloSideIsLeft mirrors L2R for R2L`() {
        for (position in 0..6) {
            for (shifted in booleanArrayOf(false, true)) {
                assertEquals(
                    !SpreadPairing.soloSideIsLeft(position, shifted, isR2L = false),
                    SpreadPairing.soloSideIsLeft(position, shifted, isR2L = true),
                    "R2L must be the mirror of L2R (position=$position shifted=$shifted)",
                )
            }
        }
    }

    @Test
    fun `soloSideIsLeft flips with each page and with the shift`() {
        for (isR2L in booleanArrayOf(false, true)) {
            // adjacent positions land on opposite sides
            assertEquals(
                SpreadPairing.soloSideIsLeft(2, shifted = false, isR2L = isR2L),
                !SpreadPairing.soloSideIsLeft(3, shifted = false, isR2L = isR2L),
            )
            // shifting is equivalent to moving one position
            assertEquals(
                SpreadPairing.soloSideIsLeft(2, shifted = true, isR2L = isR2L),
                SpreadPairing.soloSideIsLeft(3, shifted = false, isR2L = isR2L),
            )
        }
    }

    // --- pairingColumnIndex: a lone page's pairing column within its run ---

    @Test
    fun `pairingColumnIndex equals the source index within a single run`() {
        val p = (0..4).map { page(it) }
        // [ (0,1) (2,3) 4 ]: one run, trailing lone page
        val trailing = listOf(PageSpread(p[0], p[1]), PageSpread(p[2], p[3]), p[4])
        assertEquals(4, SpreadPairing.pairingColumnIndex(trailing, p[4]))
        // [ 0 (1,2) (3,4) ]: shifted, leading lone page
        val leading = listOf(p[0], PageSpread(p[1], p[2]), PageSpread(p[3], p[4]))
        assertEquals(0, SpreadPairing.pairingColumnIndex(leading, p[0]))
    }

    @Test
    fun `pairingColumnIndex restarts after an insert page`() {
        val p = (0..5).map { page(it) }
        // a wide page 2 split into a lone half + an InsertPage breaks the chapter into two runs
        val items = listOf(PageSpread(p[0], p[1]), p[2], InsertPage(p[2]), PageSpread(p[3], p[4]), p[5])
        // page 5 is the third column of the second run (3,4,5), not the sixth of the chapter
        assertEquals(2, SpreadPairing.pairingColumnIndex(items, p[5]))
    }

    @Test
    fun `pairingColumnIndex restarts at a chapter boundary`() {
        val a = (0..1).map { page(it, chA) }
        val b = (0..2).map { page(it, chB) }
        // a transition and a bare chapter change both bound the run; b2 is column 2 of chB either way
        val viaTransition =
            listOf(PageSpread(a[0], a[1]), ChapterTransition.Next(chA, chB), PageSpread(b[0], b[1]), b[2])
        assertEquals(2, SpreadPairing.pairingColumnIndex(viaTransition, b[2]))
        val viaChapterChange = listOf(PageSpread(a[0], a[1]), PageSpread(b[0], b[1]), b[2])
        assertEquals(2, SpreadPairing.pairingColumnIndex(viaChapterChange, b[2]))
    }

    @Test
    fun `pairingColumnIndex falls back to the source index when the page is absent`() {
        assertEquals(7, SpreadPairing.pairingColumnIndex(emptyList(), page(7)))
    }

    // --- resolveShift: which shift a chapter pairs by
    //     (manual > per-series force > decisive wide > detected > default) ---

    @Test
    fun `resolveShift lets a remembered manual shift win over everything`() {
        // A per-chapter toggle wins against a per-series force, a decisive wide, a detection, and the default.
        assertTrue(
            SpreadPairing.resolveShift(
                remembered = true,
                perMangaForce = false,
                decisiveWide = false,
                detected = false,
                default = false,
            ),
        )
        assertFalse(
            SpreadPairing.resolveShift(
                remembered = false,
                perMangaForce = true,
                decisiveWide = true,
                detected = true,
                default = true,
            ),
        )
    }

    @Test
    fun `resolveShift lets a per-series force win over the wide, detection and the default`() {
        // A deliberate per-series override outranks the whole automatic stage (but not a manual toggle).
        assertTrue(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = true,
                decisiveWide = false,
                detected = false,
                default = false,
            ),
        )
        assertFalse(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = false,
                decisiveWide = true,
                detected = true,
                default = true,
            ),
        )
    }

    @Test
    fun `resolveShift lets a decisive wide win over detection and the default`() {
        // Within the automatic stage a genuine spread outranks the classifier's cue read; it yields to a
        // deliberate user choice, which is why the manual toggle stays a live override on a wide-governed chapter.
        assertTrue(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = true,
                detected = false,
                default = false,
            ),
        )
        assertFalse(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = false,
                detected = true,
                default = true,
            ),
        )
    }

    @Test
    fun `resolveShift falls to the detected shift when nothing outranks it`() {
        assertTrue(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = null,
                detected = true,
                default = false,
            ),
        )
        assertFalse(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = null,
                detected = false,
                default = true,
            ),
        )
    }

    @Test
    fun `resolveShift falls to the default when nothing else is known`() {
        assertTrue(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = null,
                detected = null,
                default = true,
            ),
        )
        assertFalse(
            SpreadPairing.resolveShift(
                remembered = null,
                perMangaForce = null,
                decisiveWide = null,
                detected = null,
                default = false,
            ),
        )
    }

    // --- the two pairing routes agree ---

    @Test
    fun `regroup toggle matches a pairFlat rebuild for the same chapter`() {
        // setChapters pairs a chapter with pairFlat (story order) then reverses the whole list for
        // R2L; toggleSpreadShift reflows the current *display* list with regroupChapterAware. For the
        // same chapter and shift the two routes must produce the same grouping, or toggling and then
        // any later rebuild (a routine preload reload) would visibly reshuffle the pages under the
        // reader. Spread mode shows wide pages whole and never splits one, so the reachable domain is
        // a single insert-free chapter.
        val rnd = Random(7)
        repeat(2000) {
            val story = List(rnd.nextInt(0, 12)) { page(it) }
            for (isR2L in booleanArrayOf(false, true)) {
                fun rebuild(shift: Boolean): List<Any> =
                    SpreadPairing.pairFlat(story, shift).let { if (isR2L) it.asReversed().toList() else it }

                for (shifted in booleanArrayOf(false, true)) {
                    val display = rebuild(shifted)
                    // regrouping the current display at the same shift is a no-op
                    assertEquals(
                        display,
                        SpreadPairing.regroupChapterAware(display, isR2L, shiftFor = { shifted }),
                        "regroup at the same shift reshuffled (isR2L=$isR2L shifted=$shifted n=${story.size})",
                    )
                    // toggling the shift via regroup matches a fresh rebuild at the flipped shift
                    assertEquals(
                        rebuild(!shifted),
                        SpreadPairing.regroupChapterAware(display, isR2L, shiftFor = { !shifted }),
                        "regroup toggle diverged from a pairFlat rebuild (isR2L=$isR2L from=$shifted n=${story.size})",
                    )
                }
            }
        }
    }
}
