/* generic EPD Controller for Android,
 *
 * interface for Boyue T61/T62 clones
 * based on https://github.com/unwmun/refreshU
 *
 * Thanks to @unwmun
 *
 * val EPD_NULL = "EPD_NULL"
 * val EPD_AUTO = "EPD_AUTO"
 * val EPD_FULL = "EPD_FULL"
 * val EPD_A2 = "EPD_A2"
 * val EPD_PART = "EPD_PART"
 * val EPD_FULL_DITHER = "EPD_FULL_DITHER"
 * val EPD_RESET = "EPD_RESET"
 * val EPD_BLACK_WHITE = "EPD_BLACK_WHITE"
 * val EPD_TEXT = "EPD_TEXT"
 * val EPD_BLOCK = "EPD_BLOCK"
 * val EPD_FULL_WIN = "EPD_FULL_WIN"
 * val EPD_OED_PART = "EPD_OED_PART"
 * val EPD_DIRECT_PART = "EPD_DIRECT_PART"
 * val EPD_DIRECT_A2 = "EPD_DIRECT_A2"
 * val EPD_STANDBY = "EPD_STANDBY"
 * val EPD_POWEROFF = "EPD_POWEROFF"
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd.rockchip

import android.util.Log
import android.view.View
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

abstract class RK30xxEPDController {
    companion object {
        private const val TAG = "EPD"
        private var eInkEnum: Class<Enum<*>>? = null
        private var updateEpdMethod: Method? = null

        const val EINK_MODE_FULL = 1
        const val EINK_MODE_PARTIAL = 2
        const val EINK_MODE_FULL_UI = 4
        const val EINK_MODE_PARTIAL_UI = 4
        const val EINK_MODE_FAST = 3
        const val EINK_WAVEFORM_DELAY = 0

        init {
            try {
                @Suppress("UNCHECKED_CAST")
                eInkEnum = Class.forName("android.view.View\$EINK_MODE") as Class<Enum<*>>
                updateEpdMethod = View::class.java.getMethod(
                    "requestEpdMode",
                    eInkEnum,
                    Boolean::class.javaPrimitiveType,
                )
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, e.toString())
            } catch (e: NoSuchMethodException) {
                Log.e(TAG, e.toString())
            }
        }

        fun requestEpdMode(view: View, mode: String, flag: Boolean): Boolean {
            return try {
                updateEpdMethod!!.invoke(view, stringToEnum(mode), flag)
                true
            } catch (e: IllegalAccessException) {
                Log.e(TAG, e.toString())
                false
            } catch (e: InvocationTargetException) {
                Log.e(TAG, e.toString())
                false
            }
        }

        private fun stringToEnum(str: String): Any {
            val values = eInkEnum!!.enumConstants as Array<Enum<*>>
            return values.first { it.name == str }
        }
    }
}
