package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import spreadfit.Analysis
import spreadfit.Spreadfit
import java.io.ByteArrayInputStream

/**
 * The resolver's job is to decide a chapter's spread offset once and gate display on it: report
 * [SpreadShiftResolver.Decision.Pending] until detection settles, then a fixed decision, the crux of
 * decide-before-display. These pin that contract without a live pager. Chapters with no decodable pages
 * settle to an abstain (`Decided(shift = null)`), standing in for a no-signal detection without a real
 * bitmap decode; the decodable-chapter tests stub the classifier ([Spreadfit]) and sampler so they pin
 * the resolver's own contract: caching the analysis, resolving per direction, unioning the wide scans,
 * and invalidating on [SpreadShiftResolver.forget], while the classifier's accuracy stays the concern of
 * spreadfit's own tests. A `null` shift means "no opinion": the caller then falls back to its own default
 * (see the abstain-honours-default test).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpreadShiftResolverTest {

    private fun chapter(id: Long, pages: List<ReaderPage>?): ReaderChapter {
        val dbChapter = mockk<Chapter>()
        every { dbChapter.id } returns id
        val rc = mockk<ReaderChapter>()
        every { rc.chapter } returns dbChapter
        every { rc.pages } returns pages
        return rc
    }

    // A page the sampler can read: Ready with a (dummy) stream. The sampler and classifier are mocked, so
    // the stream's contents don't matter, only that a Ready page with a non-null stream reaches them.
    private fun readyPage(index: Int): ReaderPage {
        val p = mockk<ReaderPage>()
        every { p.index } returns index
        every { p.statusFlow } returns MutableStateFlow(Page.State.Ready)
        every { p.stream } returns { ByteArrayInputStream(ByteArray(0)) }
        return p
    }

    @Test
    fun `a chapter is Pending until it settles, then Decided`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var settledCount = 0
            val resolver = SpreadShiftResolver(
                scope = this,
                onSettled = { settledCount++ },
                samplingDispatcher = dispatcher,
            )

            // Before detection runs, an unknown chapter is Pending: the signal to hold its layout.
            assertEquals(SpreadShiftResolver.Decision.Pending, resolver.decisionFor(1L, leftToRight = true))

            resolver.ensureDetected(chapter(1L, emptyList()), scanAllForWides = false)
            advanceUntilIdle()

            // No signal settles to an abstain (null shift, so the caller's default applies), and fires
            // the settle callback exactly once.
            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = null),
                resolver.decisionFor(1L, leftToRight = true),
            )
            assertEquals(1, settledCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a settled chapter is never re-analysed`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var settledCount = 0
            val resolver = SpreadShiftResolver(
                scope = this,
                onSettled = { settledCount++ },
                samplingDispatcher = dispatcher,
            )

            resolver.ensureDetected(chapter(2L, emptyList()), scanAllForWides = false)
            advanceUntilIdle()
            resolver.ensureDetected(chapter(2L, emptyList()), scanAllForWides = false)
            advanceUntilIdle()

            assertEquals(1, settledCount, "a settled chapter must not detect again")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the decision is stable across reading direction for a no-signal chapter`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val resolver = SpreadShiftResolver(scope = this, onSettled = {}, samplingDispatcher = dispatcher)
            resolver.ensureDetected(chapter(3L, emptyList()), scanAllForWides = false)
            advanceUntilIdle()

            // An abstain is null (no opinion) whichever way it's read, stable across direction.
            assertEquals(SpreadShiftResolver.Decision.Decided(null), resolver.decisionFor(3L, leftToRight = true))
            assertEquals(SpreadShiftResolver.Decision.Decided(null), resolver.decisionFor(3L, leftToRight = false))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a failure during analysis settles as an abstain instead of stranding the chapter`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        mockkObject(SpreadOffsetSampler)
        try {
            // A page that's ready to sample, but sampling blows up, a decode/classifier failure. Detection
            // must not leave the chapter Pending forever (which would hold the pager on a blank page); it
            // has to fall back to the safe default and settle.
            val page = mockk<ReaderPage> {
                every { statusFlow } returns MutableStateFlow(Page.State.Ready)
                every { stream } returns { ByteArrayInputStream(ByteArray(0)) }
            }
            every { SpreadOffsetSampler.sample(any()) } throws RuntimeException("decode boom")

            var settledCount = 0
            val resolver = SpreadShiftResolver(
                scope = this,
                onSettled = { settledCount++ },
                samplingDispatcher = dispatcher,
            )

            resolver.ensureDetected(chapter(9L, listOf(page)), scanAllForWides = false)
            advanceUntilIdle()

            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = null),
                resolver.decisionFor(9L, leftToRight = true),
            )
            assertEquals(1, settledCount, "a failed analysis must still settle exactly once")
        } finally {
            unmockkObject(SpreadOffsetSampler)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an abstain honours the caller's default instead of forcing unshifted`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val resolver = SpreadShiftResolver(scope = this, onSettled = {}, samplingDispatcher = dispatcher)
            // no pages -> settles as an abstain
            resolver.ensureDetected(chapter(4L, emptyList()), scanAllForWides = false)
            advanceUntilIdle()

            // Read the detected shift exactly as PagerViewerAdapter.decidedShift does.
            val detected = (
                resolver.decisionFor(4L, leftToRight = false)
                    as? SpreadShiftResolver.Decision.Decided
                )?.shift

            // An abstain must leave the global "first page" default (true) in effect, not force false.
            assertEquals(
                true,
                SpreadPairing.resolveShift(
                    remembered = null,
                    perMangaForce = null,
                    decisiveWide = null,
                    detected = detected,
                    default = true,
                ),
            )
            // Symmetric check: with the default off, an abstain stays unshifted.
            assertEquals(
                false,
                SpreadPairing.resolveShift(
                    remembered = null,
                    perMangaForce = null,
                    decisiveWide = null,
                    detected = detected,
                    default = false,
                ),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a decodable chapter reports the classifier's verdict per direction and its wide pages`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        mockkObject(SpreadOffsetSampler, Spreadfit)
        try {
            // Two Ready pages the sampler reads; the classifier is stubbed to a definite, direction-split
            // verdict so this pins the resolver's own contract (cache the analysis once, resolve per
            // direction, union the wides), not the classifier's accuracy (spreadfit's own tests cover that).
            val pages = listOf(readyPage(0), readyPage(1))
            every { SpreadOffsetSampler.sample(any()) } returns SpreadOffsetSampler.Sampled(luma = null, isWide = false)
            every { SpreadOffsetSampler.isWidePage(any()) } returns true
            val analysis = mockk<Analysis> {
                every { shifted(true) } returns true
                every { shifted(false) } returns false
            }
            every { Spreadfit.analyze(any()) } returns analysis

            val resolver = SpreadShiftResolver(scope = this, onSettled = {}, samplingDispatcher = dispatcher)
            resolver.ensureDetected(chapter(1L, pages), scanAllForWides = true)
            advanceUntilIdle()

            // One cached analysis, resolved each way without re-scanning.
            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = true),
                resolver.decisionFor(1L, leftToRight = true),
            )
            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = false),
                resolver.decisionFor(1L, leftToRight = false),
            )
            // The whole-chapter wide scan is unioned into the wide set.
            assertEquals(setOf(0, 1), resolver.wideIndicesFor(1L))
        } finally {
            unmockkObject(SpreadOffsetSampler, Spreadfit)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the gutter sample's own wide flags reach the wide set (the union's reliable half)`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        mockkObject(SpreadOffsetSampler, Spreadfit)
        try {
            // Complements the test above: there the whole-chapter header scan supplied the wides and the
            // gutter sample none; here only the full-decode gutter pass flags them (the header scan finds
            // none). Both must reach the wide set, or the union in analyse() drops the reliable half, so a
            // page the classifier's own decode saw as wide would be paired as a single page.
            val pages = listOf(readyPage(0), readyPage(1))
            every { SpreadOffsetSampler.sample(any()) } returns SpreadOffsetSampler.Sampled(luma = null, isWide = true)
            every { SpreadOffsetSampler.isWidePage(any()) } returns false
            every { Spreadfit.analyze(any()) } returns mockk<Analysis> { every { shifted(any()) } returns null }

            val resolver = SpreadShiftResolver(scope = this, onSettled = {}, samplingDispatcher = dispatcher)
            resolver.ensureDetected(chapter(1L, pages), scanAllForWides = true)
            advanceUntilIdle()

            assertEquals(setOf(0, 1), resolver.wideIndicesFor(1L))
        } finally {
            unmockkObject(SpreadOffsetSampler, Spreadfit)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a cancelled scope (reader closed mid-detection) never settles or fires onSettled`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            // A page that never becomes Ready, so the gutter sample suspends waiting for it. Cancelling the
            // resolver's scope stands in for the reader closing mid-detection: the scan must abort cleanly,
            // leaving the chapter Pending and never firing onSettled, or a closed reader triggers a stale
            // reload.
            val neverReady = mockk<ReaderPage> {
                every { index } returns 0
                every { statusFlow } returns MutableStateFlow(Page.State.Queue)
                every { stream } returns { ByteArrayInputStream(ByteArray(0)) }
            }
            var settledCount = 0
            val resolverScope = CoroutineScope(dispatcher)
            val resolver = SpreadShiftResolver(
                scope = resolverScope,
                onSettled = { settledCount++ },
                samplingDispatcher = dispatcher,
            )

            resolver.ensureDetected(chapter(1L, listOf(neverReady)), scanAllForWides = false)
            resolverScope.cancel() // the reader closes before the sample completes
            advanceUntilIdle()

            assertEquals(0, settledCount, "a cancelled detection must not fire onSettled")
            assertEquals(SpreadShiftResolver.Decision.Pending, resolver.decisionFor(1L, leftToRight = true))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `forget drops a chapter's caches so the next detect re-scans it`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        mockkObject(SpreadOffsetSampler, Spreadfit)
        try {
            val pages = listOf(readyPage(0), readyPage(1))
            every { SpreadOffsetSampler.sample(any()) } returns SpreadOffsetSampler.Sampled(luma = null, isWide = false)
            every { SpreadOffsetSampler.isWidePage(any()) } returns true
            every { Spreadfit.analyze(any()) } returns mockk<Analysis> { every { shifted(any()) } returns true }

            val resolver = SpreadShiftResolver(scope = this, onSettled = {}, samplingDispatcher = dispatcher)
            resolver.ensureDetected(chapter(1L, pages), scanAllForWides = true)
            advanceUntilIdle()
            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = true),
                resolver.decisionFor(1L, leftToRight = true),
            )
            assertEquals(setOf(0, 1), resolver.wideIndicesFor(1L))

            // forget clears every per-chapter cache: back to Pending, no remembered wides.
            resolver.forget(1L)
            assertEquals(SpreadShiftResolver.Decision.Pending, resolver.decisionFor(1L, leftToRight = true))
            assertEquals(emptySet<Int>(), resolver.wideIndicesFor(1L))

            // The re-scan actually re-runs (a fresh verdict, not the stale cache served back).
            every { Spreadfit.analyze(any()) } returns mockk<Analysis> { every { shifted(any()) } returns false }
            resolver.ensureDetected(chapter(1L, pages), scanAllForWides = true)
            advanceUntilIdle()
            assertEquals(
                SpreadShiftResolver.Decision.Decided(shift = false),
                resolver.decisionFor(1L, leftToRight = true),
            )
        } finally {
            unmockkObject(SpreadOffsetSampler, Spreadfit)
            Dispatchers.resetMain()
        }
    }
}
