package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

/**
 * Owns the page-interval policy for hardware e-ink refreshes.
 *
 * The backend performs the panel update; this class decides whether a page should trigger one,
 * applies the configured delay, and replaces pending refreshes on rapid page turns.
 */
class EinkRefreshController(
    private val backend: EinkRefreshBackend,
    private val preferences: ReaderPreferences,
) {
    private var pageCounter = 0

    /**
     * @return `true` when hardware refresh owns this page change, even if the interval skipped it.
     *         Callers should only run the software flash when this returns `false`.
     */
    fun onPageChanged(view: View?, scheduler: EinkRefreshScheduler): Boolean {
        if (!preferences.einkHardwareRefresh.get()) return false

        val mode = preferences.einkRefreshMode.get()
        if (mode !in backend.supportedModes) return false

        val interval = preferences.einkRefreshInterval.get().coerceAtLeast(1)
        pageCounter++
        if (pageCounter >= interval) {
            pageCounter = 0
            val delayMs = preferences.einkRefreshDelayMillis.get().coerceAtLeast(0).toLong()
            scheduler.cancel()
            scheduler.schedule(delayMs) {
                backend.refresh(view, mode)
            }
        }

        return true
    }

    fun onPause() {
        backend.onPause()
    }

    fun onResume() {
        pageCounter = 0
        backend.onResume()
    }
}
