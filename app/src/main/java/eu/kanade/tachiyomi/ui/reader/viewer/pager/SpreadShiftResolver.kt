package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import spreadfit.Analysis
import spreadfit.LumaPage
import spreadfit.Spreadfit
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat

/**
 * Session-scoped cache of a chapter's spread page-offset (which page starts the pairing). The offset is
 * a property of the chapter's content, so it is decided once and survives every viewer rebuild. The
 * cached [Analysis] is direction-independent; the direction-dependent verdict is resolved on demand, so
 * an L2R/R2L flip re-decides without re-decoding. The viewer reads decisions from here and never applies
 * a shift under a live pager (decide before display). Owned by [ReaderViewModel]; [scope] bounds
 * detection to the session. Main-thread only.
 */
class SpreadShiftResolver(
    private val scope: CoroutineScope,
    private val onSettled: () -> Unit,
    // Runs the bounded sampling; IO in production, injectable for tests.
    private val samplingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val analyses = HashMap<Long, Analysis>()

    // Chapters whose detection has finished (decided or abstained). A settled chapter never
    // re-detects; [decisionFor] reports it as decided, with a `null` shift for an abstain / no-analysis
    // so the caller falls back to its own default (the user's preference) rather than being forced off.
    private val settled = HashSet<Long>()
    private val inFlight = HashSet<Long>()

    // Per-chapter set of wide page indices (the pages the classifier's shape gate skips: a wide page is a
    // spread, not a single page it can measure). Consumed by the "treat wide pages as spreads" pairing,
    // which is why the scan runs even when only that feature (not auto-detect) is on.
    private val wideIndices = HashMap<Long, Set<Int>>()

    private data class ScanResult(val analysis: Analysis?, val wideIndices: Set<Int>)

    sealed interface Decision {
        /** Detection hasn't finished for this chapter yet; hold before displaying it as a spread. */
        data object Pending : Decision

        /**
         * Detection has finished. [shift] is the detected offset, or `null` for an abstain
         * (no confident signal): the caller then falls back to its own default (the user's global or
         * per-manga preference) rather than being forced to `false`.
         */
        data class Decided(val shift: Boolean?) : Decision
    }

    /** The decision for [chapterId] in the current reading direction; [Decision.Pending] until it settles. */
    fun decisionFor(chapterId: Long, leftToRight: Boolean): Decision =
        if (chapterId in settled) {
            Decision.Decided(analyses[chapterId]?.shifted(leftToRight))
        } else {
            Decision.Pending
        }

    /**
     * The wide page indices detected in [chapterId]'s sampled prefix, or empty when detection hasn't
     * settled or found none. Used by the "treat wide pages as spreads" pairing.
     */
    fun wideIndicesFor(chapterId: Long): Set<Int> = wideIndices[chapterId].orEmpty()

    /**
     * Starts detection for [chapter] unless it's already settled or in flight. On completion it
     * caches the (direction-independent) analysis and fires [onSettled], which triggers a viewer
     * rebuild that reads the now-settled decision. Idempotent per chapter; the caller gates on source
     * eligibility (local only) and on a remembered manual shift taking precedence. [scanAllForWides]
     * carries the wide-page scan past the gutter window to the whole chapter (header-only) for "treat
     * wide pages as spreads"; off, only the gutter window contributes wides.
     */
    fun ensureDetected(chapter: ReaderChapter, scanAllForWides: Boolean) {
        val id = chapter.chapter.id ?: return
        if (id in settled || id in inFlight) return
        val pages = chapter.pages ?: return
        inFlight.add(id)
        scope.launch(samplingDispatcher) {
            // Detection must always settle: whatever analyse does, the chapter has to leave the Pending
            // state and fire onSettled, or the viewer's decide-before-display hold never releases and the
            // reader is stranded on a blank page. A failure (decode, classifier, out-of-memory) is
            // treated as an abstain (the same safe default as no signal), so one bad page can't wedge
            // the reader. Cancellation (reader closed) is left to propagate.
            val result = try {
                analyse(pages, scanAllForWides)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Qualified receiver so the tag stays this class, not this launch's coroutine scope.
                this@SpreadShiftResolver.logcat(LogPriority.WARN, e) { "Spread offset detection failed; abstaining" }
                ScanResult(analysis = null, wideIndices = emptySet())
            }
            withUIContext {
                if (result.analysis != null) analyses[id] = result.analysis
                wideIndices[id] = result.wideIndices
                inFlight.remove(id)
                settled.add(id)
                onSettled()
            }
        }
    }

    /**
     * Drops [chapterId]'s cached detection so the next [ensureDetected] re-scans it, for when a setting
     * that reshapes the scan itself (wide pairing) changes mid-session, since a chapter scanned before
     * the change keeps its stale result otherwise. Main-thread only like the rest of this class, so it
     * doesn't cancel an in-flight scan: with cache mutation all serialized on the main thread, a scan
     * that lands after a forget can only re-cache a stale result (last-writer-wins), never corrupt the
     * maps; and a scan settles well before a setting can be toggled.
     */
    fun forget(chapterId: Long) {
        settled.remove(chapterId)
        inFlight.remove(chapterId)
        analyses.remove(chapterId)
        wideIndices.remove(chapterId)
    }

    /**
     * Scans [pages] up front and returns the direction-independent analysis plus every wide page index,
     * bounded by [BUDGET_MS] wall-clock so no chapter can make it run away. Two passes run concurrently
     * under one deadline: a Ready-gated full sample of the gutter window ([sampleGutter], the classifier's
     * luma input, at most [PAGE_CAP] pages) and, when [scanAllForWides], a Ready-free header-only wide
     * check across the whole chapter ([scanWides]). They run in parallel because the wide check needs only
     * a page's bounds, not a loaded page, so it must not queue behind the sampler's per-page load waits for
     * a share of the budget. The two wide sets are unioned: the gutter sample's full-decode flags are the
     * reliable signal where it reached, the whole-chapter check extends coverage past the window, and a
     * page is wide if either flags it, so the cheap pass never drops a wide the reliable one caught.
     */
    private suspend fun analyse(pages: List<ReaderPage>, scanAllForWides: Boolean): ScanResult = coroutineScope {
        val startNanos = System.nanoTime()
        val deadline = startNanos + BUDGET_MS * 1_000_000L

        val gutterDeferred = async { sampleGutter(pages, deadline) }
        val wideDeferred = if (scanAllForWides) async { scanWides(pages, deadline) } else null

        val gutter = gutterDeferred.await()
        val wide = wideDeferred?.await()
        val wideIndices = gutter.wides + wide?.wides.orEmpty()

        val wallMs = (System.nanoTime() - startNanos) / 1_000_000.0
        // Qualified receiver so the tag stays this class, not the coroutineScope. The classifier verdict is
        // built lazily inside the message (a stripped log then costs nothing) and is the raw detection,
        // canonicalised to L2R; the effective pairing shift is logged where it resolves, in the adapter.
        this@SpreadShiftResolver.logcat {
            val classifier = gutter.analysis?.resolve(leftToRight = true)?.let {
                "${it.source}${it.decision.shifted?.let { s -> " shift@L2R=$s" }.orEmpty()}"
            } ?: "no-sample"
            "Spread offset: sampled ${gutter.sampled}, wide-scanned ${wide?.scanned ?: 0} " +
                "(${wideIndices.size} wide: ${wideIndices.sorted()}), classifier=$classifier " +
                "in ${"%.0f".format(wallMs)} ms"
        }
        ScanResult(gutter.analysis, wideIndices)
    }

    private data class GutterScan(val analysis: Analysis?, val wides: Set<Int>, val sampled: Int)

    private data class WideScan(val wides: Set<Int>, val scanned: Int)

    /**
     * The classifier pass: for the gutter window, a full [SpreadOffsetSampler.sample] per page (the luma
     * raster plus the wide flag off the same bounds decode), gated on the page being Ready and bounded by
     * [deadline]. The wide flags it produces are the reliable full-decode signal [analyse] unions with the
     * whole-chapter check.
     */
    private suspend fun sampleGutter(pages: List<ReaderPage>, deadline: Long): GutterScan {
        val gutterLimit = minOf(PAGE_CAP, pages.size)
        val samples = arrayOfNulls<LumaPage>(gutterLimit)
        val wide = mutableSetOf<Int>()
        var sampled = 0
        for (i in 0 until gutterLimit) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMs <= 0) break
            val page = pages[i]
            val state = withTimeoutOrNull(remainingMs) {
                page.statusFlow.first { it is Page.State.Ready || it is Page.State.Error }
            }
            val stream = page.stream
            if (state is Page.State.Ready && stream != null) {
                val s = SpreadOffsetSampler.sample(stream)
                samples[i] = s.luma
                if (s.isWide) wide.add(page.index)
            }
            sampled = i + 1
        }
        val analysis = if (sampled == 0) null else Spreadfit.analyze(samples.copyOf(sampled).asList())
        return GutterScan(analysis, wide, sampled)
    }

    /**
     * The wide pass: a header-only [SpreadOffsetSampler.isWidePage] check across the whole chapter, bounded
     * by [deadline]. Needs no Ready wait (a local page's stream factory is live at getPages, before the
     * page loads), so it runs alongside [sampleGutter] instead of competing for its budget. Its own
     * accumulator and a fresh stream per page mean it shares no mutable state with the gutter pass, and
     * [SpreadOffsetSampler] decodes from a private byte copy, so a page both passes read is safe to read
     * concurrently. The [ensureActive] check stops a closed reader from being scanned to the deadline.
     */
    private suspend fun scanWides(pages: List<ReaderPage>, deadline: Long): WideScan {
        val wide = mutableSetOf<Int>()
        var scanned = 0
        for (i in pages.indices) {
            currentCoroutineContext().ensureActive()
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMs <= 0) break
            val stream = pages[i].stream ?: continue
            if (SpreadOffsetSampler.isWidePage(stream)) wide.add(pages[i].index)
            scanned++
        }
        return WideScan(wide, scanned)
    }

    private companion object {
        // Bounded work, fixed by design. PAGE_CAP reads past a colour/splash opening and still rests
        // on a dozen-plus real-signal pages; the confidence gate ignores the dead ones. BUDGET_MS is a
        // conservative runaway guard: a device too slow to finish simply leaves the default pairing.
        private const val PAGE_CAP = 20
        private const val BUDGET_MS = 400L
    }
}
