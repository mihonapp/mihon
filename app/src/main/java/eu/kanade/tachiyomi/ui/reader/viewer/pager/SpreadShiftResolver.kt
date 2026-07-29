package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
     * Starts detection for [chapter] unless it's already settled or in flight. On completion it
     * caches the (direction-independent) analysis and fires [onSettled], which triggers a viewer
     * rebuild that reads the now-settled decision. Idempotent per chapter; the caller gates on source
     * eligibility (local only) and on a remembered manual shift taking precedence.
     */
    fun ensureDetected(chapter: ReaderChapter) {
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
            val analysis = try {
                analyse(pages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Qualified receiver so the tag stays this class, not this launch's coroutine scope.
                this@SpreadShiftResolver.logcat(LogPriority.WARN, e) { "Spread offset detection failed; abstaining" }
                null
            }
            withUIContext {
                if (analysis != null) analyses[id] = analysis
                inFlight.remove(id)
                settled.add(id)
                onSettled()
            }
        }
    }

    /**
     * Drops [chapterId]'s cached detection so the next [ensureDetected] re-scans it. Main-thread only
     * like the rest of this class, so it doesn't cancel an in-flight scan: with cache mutation all
     * serialized on the main thread, a scan that lands after a forget can only re-cache a stale result
     * (last-writer-wins), never corrupt the maps; and a scan settles well before it matters.
     */
    fun forget(chapterId: Long) {
        settled.remove(chapterId)
        inFlight.remove(chapterId)
        analyses.remove(chapterId)
    }

    /**
     * Scans the gutter window of [pages] up front and returns the direction-independent analysis,
     * bounded by [BUDGET_MS] wall-clock so no chapter can make it run away. Gates each page on Ready and
     * feeds at most [PAGE_CAP] pages to the classifier.
     */
    private suspend fun analyse(pages: List<ReaderPage>): Analysis? {
        val startNanos = System.nanoTime()
        val deadline = startNanos + BUDGET_MS * 1_000_000L

        val gutterLimit = minOf(PAGE_CAP, pages.size)
        val samples = arrayOfNulls<LumaPage>(gutterLimit)
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
                samples[i] = SpreadOffsetSampler.sample(stream)
            }
            sampled = i + 1
        }
        val analysis = if (sampled == 0) null else Spreadfit.analyze(samples.copyOf(sampled).asList())

        val wallMs = (System.nanoTime() - startNanos) / 1_000_000.0
        // Qualified receiver so the tag stays this class. The classifier verdict is built lazily inside
        // the message (a stripped log then costs nothing) and is the raw detection, canonicalised to L2R;
        // the effective pairing shift is logged where it resolves, in the adapter.
        this@SpreadShiftResolver.logcat {
            val classifier = analysis?.resolve(leftToRight = true)?.let {
                "${it.source}${it.decision.shifted?.let { s -> " shift@L2R=$s" }.orEmpty()}"
            } ?: "no-sample"
            "Spread offset: sampled $sampled, classifier=$classifier in ${"%.0f".format(wallMs)} ms"
        }
        return analysis
    }

    private companion object {
        // Bounded work, fixed by design. PAGE_CAP reads past a colour/splash opening and still rests
        // on a dozen-plus real-signal pages; the confidence gate ignores the dead ones. BUDGET_MS is a
        // conservative runaway guard: a device too slow to finish simply leaves the default pairing.
        private const val PAGE_CAP = 20
        private const val BUDGET_MS = 400L
    }
}
