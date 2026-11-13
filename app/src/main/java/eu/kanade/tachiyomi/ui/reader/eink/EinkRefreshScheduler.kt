package eu.kanade.tachiyomi.ui.reader.eink

/**
 * Schedules a single delayed refresh action.
 *
 * Implementations must replace any previously scheduled action when [schedule] is called again.
 */
interface EinkRefreshScheduler {
    fun schedule(delayMs: Long, action: () -> Unit)

    fun cancel()
}
