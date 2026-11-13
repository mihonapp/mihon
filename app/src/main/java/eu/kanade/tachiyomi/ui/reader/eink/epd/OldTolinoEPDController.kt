/* Tolino-like without REGAL */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import eu.kanade.tachiyomi.ui.reader.eink.epd.freescale.NTXEPDController

class OldTolinoEPDController : NTXEPDController(), EPDInterface {

    override fun getPlatform(): String {
        return "freescale"
    }

    override fun getMode(): String {
        return "all"
    }

    override fun getWaveformFull(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformPartial(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformFullUi(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_GL16
    }

    override fun getWaveformPartialUi(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GL16
    }

    override fun getWaveformFast(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_DU
    }

    override fun getWaveformDelay(): Int {
        return EINK_WAVEFORM_DELAY
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
