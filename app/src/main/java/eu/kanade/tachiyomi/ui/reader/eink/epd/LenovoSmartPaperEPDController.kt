/* EPD Controller for Lenovo Smart Paper (RK3566).
 * Tested on Lenovo Smart Paper. sendOneFullFrame() does not work on this device,
 * so setEpdMode() uses screenRefresh(true, 1) for full refresh instead.
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import android.util.Log
import android.view.View
import eu.kanade.tachiyomi.ui.reader.eink.epd.rockchip.RK33xxEPDController

class LenovoSmartPaperEPDController : RK33xxEPDController(), EPDInterface {

    companion object {
        private const val TAG = "EPD"
    }

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
        return true
    }

    override fun setEpdMode(
        targetView: View,
        mode: Int,
        delay: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        epdMode: String?,
    ) {
        try {
            val einkManagerClass = Class.forName("android.os.EinkManager")
            val einkManager: Any? = targetView.context.getSystemService("eink")

            if (einkManager != null) {
                val screenRefresh = einkManagerClass.getMethod(
                    "screenRefresh",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                screenRefresh.invoke(einkManager, true, 1)
            } else {
                Log.w(TAG, "EinkManager service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setEpdMode failed: ${e.message}")
            Log.e(TAG, e.stackTraceToString())
        }
    }

    override fun resume() {}
    override fun pause() {}
}
