/* Tested on Onyx Boox Nova 2 */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import eu.kanade.tachiyomi.ui.reader.eink.epd.qualcomm.QualcommEPDController

class OnyxEPDController : QualcommEPDController(), EPDInterface {

    override fun getPlatform(): String {
        return "qualcomm"
    }

    override fun getMode(): String {
        return "full-only"
    }

    override fun getWaveformFull(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_WAIT + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformPartial(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformFullUi(): Int {
        return EINK_WAVEFORM_UPDATE_FULL + EINK_WAVEFORM_MODE_REAGL
    }

    override fun getWaveformPartialUi(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_GC16
    }

    override fun getWaveformFast(): Int {
        return EINK_WAVEFORM_UPDATE_PARTIAL + EINK_WAVEFORM_MODE_DU
    }

    override fun getWaveformDelay(): Int {
        return EINK_WAVEFORM_DELAY
    }

    override fun getWaveformDelayUi(): Int {
        return EINK_WAVEFORM_DELAY_UI
    }

    override fun getWaveformDelayFast(): Int {
        return EINK_WAVEFORM_DELAY_FAST
    }

    override fun needsView(): Boolean {
        return false
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
