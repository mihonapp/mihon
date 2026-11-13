package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View

/**
 * A device-specific implementation of e-ink panel refreshes.
 *
 * Implementations must fail safely: a missing vendor API, an unsupported device, or a reflection
 * error should turn into a no-op rather than a crash.
 */
interface EinkRefreshBackend {
    /** Human-readable platform name, used for logging. */
    val platform: String

    /** Semantic modes this backend can actually perform. */
    val supportedModes: Set<EinkRefreshMode>

    /** Perform a full-screen hardware refresh of [view] using [mode]. */
    fun refresh(view: View?, mode: EinkRefreshMode)

    /** Called when the reader resumes. */
    fun onResume()

    /** Called when the reader pauses. */
    fun onPause()
}
