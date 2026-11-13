/* generic EPD Controller for Android devices,
 * based on https://github.com/unwmun/refreshU */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import eu.kanade.tachiyomi.ui.reader.eink.epd.rockchip.RK33xxEPDController

class RK3368EPDController : RK33xxEPDController(), EPDInterface {

    override fun getPlatform(): String {
        return "rockchip"
    }

    override fun getMode(): String {
        return "full-only"
    }

    override fun getWaveformFull(): Int {
        return EINK_MODE_FULL
    }

    override fun getWaveformPartial(): Int {
        return EINK_MODE_PARTIAL
    }

    override fun getWaveformFullUi(): Int {
        return EINK_MODE_FULL_UI
    }

    override fun getWaveformPartialUi(): Int {
        return EINK_MODE_PARTIAL_UI
    }

    override fun getWaveformFast(): Int {
        return EINK_MODE_FAST
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
        requestEpdMode(epdMode!!)
    }

    override fun resume() {}
    override fun pause() {}
}
