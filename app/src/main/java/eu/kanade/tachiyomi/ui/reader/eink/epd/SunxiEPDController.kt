/* Tested on Moaan W7 */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import java.lang.reflect.Method

class SunxiEPDController : EPDInterface {

    private var resetRunnable: Runnable? = null
    private var resetView: View? = null

    companion object {
        private const val TAG = "EPD"

        const val SUNXI_EINK_INIT_MODE = 0x01
        const val SUNXI_EINK_DU_MODE = 0x02
        const val SUNXI_EINK_GC16_MODE = 0x04
        const val SUNXI_EINK_GC4_MODE = 0x08
        const val SUNXI_EINK_A2_MODE = 0x10
        const val SUNXI_EINK_GL16_MODE = 0x20
        const val SUNXI_EINK_GLR16_MODE = 0x40
        const val SUNXI_EINK_GLD16_MODE = 0x80
        const val SUNXI_EINK_GU16_MODE = 0x84
        const val SUNXI_EINK_RUBBER_MODE = 0x88
        const val SUNXI_EINK_AUTO_MODE = 0x8000
        const val SUNXI_EINK_NO_MERGE = Integer.MIN_VALUE // 0x80000000

        const val SUNXI_EINK_RESET_MODE = 0
        private const val RESET_DELAY_MS = 150L

        @Volatile private var reflectionReady = false
        private var getViewRootImplFn: Method? = null
        private var setRefreshModeFn: Method? = null

        @SuppressLint("BlockedPrivateApi")
        private fun initReflection() {
            if (reflectionReady) return
            try {
                getViewRootImplFn = View::class.java.getMethod("getViewRootImpl")
                val method = Class.forName(
                    "android.view.ViewRootImpl",
                ).getDeclaredMethod("setRefreshMode", Int::class.javaPrimitiveType)
                method.isAccessible = true
                setRefreshModeFn = method
            } catch (e: Exception) {
                Log.w(TAG, "Sunxi EPD: reflection path unavailable: $e")
            }
            reflectionReady = true
        }

        private fun setRefreshMode(view: View, mode: Int): Boolean {
            initReflection()
            val getFn = getViewRootImplFn ?: return false
            val setFn = setRefreshModeFn ?: return false
            return try {
                val vri = getFn.invoke(view.rootView) ?: return false
                setFn.invoke(vri, mode)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Sunxi EPD: setRefreshMode failed: $e")
                false
            }
        }
    }

    override fun getPlatform(): String = "sunxi"
    override fun getMode(): String = "all"

    override fun getWaveformFull(): Int = SUNXI_EINK_NO_MERGE + SUNXI_EINK_GC16_MODE
    override fun getWaveformPartial(): Int = SUNXI_EINK_GU16_MODE
    override fun getWaveformFullUi(): Int = SUNXI_EINK_NO_MERGE + SUNXI_EINK_GLR16_MODE
    override fun getWaveformPartialUi(): Int = SUNXI_EINK_GU16_MODE
    override fun getWaveformFast(): Int = SUNXI_EINK_A2_MODE

    override fun getWaveformDelay(): Int = 0
    override fun getWaveformDelayUi(): Int = 0
    override fun getWaveformDelayFast(): Int = 0

    override fun needsView(): Boolean = false

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
        val rawMode = when {
            epdMode == "EPD_FULL" || mode == getWaveformFull() -> SUNXI_EINK_GC16_MODE
            mode == getWaveformFullUi() -> SUNXI_EINK_GLR16_MODE
            mode == getWaveformFast() -> SUNXI_EINK_A2_MODE
            else -> SUNXI_EINK_RESET_MODE
        }

        setRefreshMode(targetView, rawMode)
        if (rawMode != SUNXI_EINK_RESET_MODE) {
            // setRefreshMode is persistent, so reset to 0-mode shortly after the
            // refresh to avoid leaving the display in a flashing mode.
            resetRunnable?.let { runnable ->
                resetView?.removeCallbacks(runnable)
            }
            resetView = targetView
            val runnable = Runnable {
                setRefreshMode(targetView, SUNXI_EINK_RESET_MODE)
            }
            resetRunnable = runnable
            targetView.postDelayed(runnable, RESET_DELAY_MS)
        }
    }

    override fun resume() {}
    override fun pause() {}
}
