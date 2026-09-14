package eu.kanade.tachiyomi.ui.reader.cast

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import kotlin.math.roundToInt

/**
 * Drives the reader's viewer on its own: continuous viewers scroll at a steady speed, paged
 * viewers turn a page at a fixed interval. The preferences are read on every tick so a change
 * made in the remote sheet applies immediately. At the end of a chapter the next one is loaded
 * when there is one, otherwise scrolling stops. Independent of whether a cast session is active.
 */
class ReaderAutoScroller(
    private val activity: ReaderActivity,
    private val controller: CastController,
    private val bridgeProvider: () -> CastViewerBridge?,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density

    var isRunning: Boolean = false
        private set

    /** Sub-pixel distance left over from the previous tick, so slow speeds still add up. */
    private var pendingPx = 0f

    /** Uptime at which the viewer first refused to move forward; 0 while it moves. */
    private var blockedSince = 0L

    /** Set once [ReaderActivity.loadNextChapter] has been requested for the current block. */
    private var chapterRequested = false

    /** Set once the requested chapter finished loading, to give the viewer time to lay it out. */
    private var chapterSettled = false

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val bridge = bridgeProvider()
            val delayMs = when {
                bridge == null -> RETRY_MS
                bridge.isContinuous -> tickContinuous(bridge)
                else -> tickPaged(bridge)
            }
            if (isRunning) handler.postDelayed(this, delayMs)
        }
    }

    /** Starts scrolling. [silent] resumes after a configuration change without a toast. */
    fun start(silent: Boolean = false) {
        if (isRunning) return
        isRunning = true
        resetBlock()
        pendingPx = 0f
        controller.setAutoScrollRunning(true)
        if (!silent) activity.toast(MR.strings.cast_auto_scroll_started)
        // Paged viewers keep the current page on screen for a full interval first.
        val bridge = bridgeProvider()
        val initialDelay = if (bridge != null && !bridge.isContinuous) pageIntervalMs() else 0L
        handler.postDelayed(tick, initialDelay)
    }

    fun stop(silent: Boolean = false) {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tick)
        controller.setAutoScrollRunning(false)
        if (!silent) activity.toast(MR.strings.cast_auto_scroll_stopped)
    }

    /**
     * Stops ticking without ending the session: the recreated activity resumes it (see
     * [ReaderActivity]) because [CastState.autoScrollRunning] stays true.
     */
    fun pause() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tick)
    }

    fun toggle() {
        if (isRunning) stop() else start()
    }

    /** Scrolls one step; returns the delay until the next one. */
    private fun tickContinuous(bridge: CastViewerBridge): Long {
        val preferences = controller.preferences
        val stepMs = preferences.autoScrollStepMs.get().coerceAtLeast(MIN_STEP_MS)
        val speedDp = preferences.autoScrollSpeed.get()
            .coerceIn(CastPreferences.AUTO_SCROLL_SPEED_MIN, CastPreferences.AUTO_SCROLL_SPEED_MAX)
        pendingPx += speedDp * density * stepMs / 1000f
        val deltaPx = pendingPx.roundToInt()
        if (deltaPx == 0) return stepMs.toLong()
        pendingPx -= deltaPx
        val moved = bridge.canScrollForward() && bridge.scrollBy(deltaPx.toFloat())
        if (moved) onMoved() else onBlocked(END_GRACE_MS)
        return stepMs.toLong()
    }

    /** Turns a page; returns the delay until the next one. */
    private fun tickPaged(bridge: CastViewerBridge): Long {
        if (bridge.canScrollForward()) {
            onMoved()
            bridge.next()
        } else {
            onBlocked(graceMs = 0L)
        }
        return pageIntervalMs()
    }

    private fun pageIntervalMs(): Long {
        val seconds = controller.preferences.autoScrollPageIntervalSec.get()
            .coerceIn(CastPreferences.AUTO_SCROLL_PAGE_INTERVAL_MIN, CastPreferences.AUTO_SCROLL_PAGE_INTERVAL_MAX)
        return seconds * 1000L
    }

    private fun onMoved() {
        if (blockedSince != 0L) resetBlock()
    }

    private fun resetBlock() {
        blockedSince = 0L
        chapterRequested = false
        chapterSettled = false
    }

    /**
     * The viewer is at its end. Waits [graceMs] for transient states (a pending layout pass, a
     * chapter still being preloaded), then loads the next chapter once or stops.
     */
    private fun onBlocked(graceMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (blockedSince == 0L) blockedSince = now
        if (now - blockedSince < graceMs) return
        val state = activity.viewModel.state.value
        when {
            !chapterRequested -> {
                if (state.viewerChapters?.nextChapter == null) {
                    stop()
                } else {
                    chapterRequested = true
                    blockedSince = now
                    activity.loadNextChapter()
                }
            }
            state.isLoadingAdjacentChapter -> blockedSince = now
            !chapterSettled -> {
                // Loaded: one more grace period for the viewer to lay the new chapter out.
                chapterSettled = true
                blockedSince = now
            }
            else -> stop()
        }
    }

    companion object {
        private const val MIN_STEP_MS = 16
        private const val RETRY_MS = 250L
        private const val END_GRACE_MS = 1000L
    }
}
