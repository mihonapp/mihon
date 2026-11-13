package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View

/**
 * [EinkRefreshScheduler] backed by a [View]'s message queue.
 *
 * Keeping a single [Runnable] means a rapid page turn replaces the pending refresh instead of
 * queueing a stale one.
 */
class ViewEinkRefreshScheduler(private val view: View) : EinkRefreshScheduler {
    private var pending: (() -> Unit)? = null

    private val runnable = Runnable {
        val action = pending
        pending = null
        action?.invoke()
    }

    override fun schedule(delayMs: Long, action: () -> Unit) {
        cancel()
        pending = action
        view.postDelayed(runnable, delayMs)
    }

    override fun cancel() {
        view.removeCallbacks(runnable)
        pending = null
    }
}
