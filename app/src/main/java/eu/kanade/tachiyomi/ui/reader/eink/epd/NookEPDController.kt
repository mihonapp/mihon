/* Tested on Nook Glowlight 3. */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import eu.kanade.tachiyomi.ui.reader.eink.epd.freescale.NTXEPDController

class NookEPDController : NTXEPDController(), EPDInterface {

    override fun getPlatform(): String {
        return "freescale"
    }

    override fun getMode(): String {
        return "full-only"
    }

    override fun getWaveformFull(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformPartial(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformFullUi(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_GLR16
    }

    override fun getWaveformPartialUi(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GLR16
    }

    override fun getWaveformFast(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_DU
    }

    override fun getWaveformDelay(): Int {
        // we're racing against system driver. Let the system win
        return 500
    }

    override fun getWaveformDelayUi(): Int {
        return EINK_WAVEFORM_DELAY
    }

    override fun getWaveformDelayFast(): Int {
        return EINK_WAVEFORM_DELAY
    }

    override fun needsView(): Boolean {
        return true
    }

    override fun setEpdMode(
        targetView: android.view.View,
        mode: Int,
        delay: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        epdMode: String?,
    ) {
        requestEpdMode(targetView, mode, delay, x, y, width, height)
    }

    override fun resume() {}
    override fun pause() {}
}
