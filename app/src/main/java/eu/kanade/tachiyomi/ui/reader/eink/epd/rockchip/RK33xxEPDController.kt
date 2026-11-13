/* generic EPD Controller for Android devices
 *
 * interface for boyue rk33xx clones, like the Likebook Mimas, Mars, Muses...
 * based on https://github.com/koreader/koreader/issues/4595
 *
 * Thanks to @carlinux
 *
 * Only full mode was tested, so 'getEpdMode' returns that.
 * Device owners will want to expand this to return other well known modes:
 *
 * val EPD_A2 = 2
 * val EPD_PART = 3
 * val EPD_BLACK_WHITE = 6
 * val EPD_FORCE_FULL = 11
 * val EPD_REGAL = 15
 * val EPD_ADAPTATIVE = 17
 * val EPD_FAST = 18
 * val EPD_DITHER = 100
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd.rockchip

import android.util.Log
import java.util.*

abstract class RK33xxEPDController {
    companion object {
        private const val TAG = "EPD"

        const val EINK_MODE_FULL = 1
        const val EINK_MODE_PARTIAL = 2
        const val EINK_MODE_FULL_UI = 4
        const val EINK_MODE_PARTIAL_UI = 4
        const val EINK_MODE_FAST = 3
        const val EINK_WAVEFORM_DELAY = 0

        fun requestEpdMode(epdMode: String): Boolean {
            return try {
                Class.forName("android.view.View").getMethod(
                    "setByEinkUpdateMode",
                    Integer.TYPE,
                ).invoke(null, getEpdMode(epdMode))
                true
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                false
            }
        }

        private fun getEpdMode(epdMode: String): Int {
            val mode = EINK_MODE_FULL
            Log.v(TAG, String.format(Locale.US, "Requesting %s: %d", epdMode, mode))
            return mode
        }
    }
}
