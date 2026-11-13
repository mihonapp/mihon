package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View

/**
 * Fallback backend for devices without a known e-ink implementation.
 */
object NoOpEinkRefreshBackend : EinkRefreshBackend {
    override val platform: String = "none"
    override val supportedModes: Set<EinkRefreshMode> = emptySet()

    override fun refresh(view: View?, mode: EinkRefreshMode) = Unit

    override fun onResume() = Unit

    override fun onPause() = Unit
}
