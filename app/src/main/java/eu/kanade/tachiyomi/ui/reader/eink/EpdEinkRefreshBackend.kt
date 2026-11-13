package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.AUTO
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FAST
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FULL
import eu.kanade.tachiyomi.ui.reader.eink.epd.EPDInterface

/**
 * Adapts a KOReader-derived [EPDInterface] controller to the semantic backend contract.
 */
class EpdEinkRefreshBackend(private val epd: EPDInterface) : EinkRefreshBackend {
    override val platform: String = epd.getPlatform()

    override val supportedModes: Set<EinkRefreshMode> = when (epd.getMode()) {
        "full-only" -> setOf(FULL)
        "all" -> setOf(FULL, FAST, AUTO)
        else -> emptySet()
    }

    internal fun waveformFor(mode: EinkRefreshMode): Int = when (mode) {
        FULL -> epd.getWaveformFull()
        FAST -> epd.getWaveformFast()
        AUTO -> epd.getWaveformPartial()
    }

    internal fun epdModeFor(mode: EinkRefreshMode): String = when (mode) {
        FULL -> "EPD_FULL"
        FAST -> "EPD_A2"
        AUTO -> "EPD_AUTO"
    }

    override fun refresh(view: View?, mode: EinkRefreshMode) {
        val target = view ?: return
        if (target.width <= 0 || target.height <= 0) return
        if (mode !in supportedModes) return

        // Delay is owned by EinkRefreshController, not the vendor controller.
        epd.setEpdMode(
            target,
            waveformFor(mode),
            0,
            0,
            0,
            target.width,
            target.height,
            epdModeFor(mode),
        )
    }

    override fun onResume() {
        epd.resume()
    }

    override fun onPause() {
        epd.pause()
    }
}
