/* EPD controller for Nook Glowlight 4 Plus (bnrv1300, "Emperor" platform, Android 8.1).
 *
 * Waveform control — three paths, tried in order:
 *
 * PATH 1: ViewRootImpl.setRefreshMode (preferred, no root required)
 *   B&N's Android 8.1 framework adds setRefreshMode(int) directly to ViewRootImpl.
 *   Confirmed by runtime reflection — ViewRootImpl has no mSurfaceControl field but does
 *   have setRefreshMode(int). The call routes through SurfaceFlinger → HWC →
 *   hwc_set_layer_refresh_mode → layer->refreshMode → /dev/disp ioctl.
 *   Waveform lands on the correct (incoming) buffer because HWC reads layer->refreshMode
 *   during composition, after the buffer is queued.
 *
 *   GC16 (0x4) is confirmed working: dmesg shows mode=0x200004 on page turns without
 *   the Magisk epd_gc16 module. The Magisk module is no longer required.
 *
 *   GLR16 (0x40): setRefreshMode(0x40) is accepted without error but HWC silently maps
 *   it to GU16 (0x84) during composition. dmesg confirms mode=0x200084 on UI refreshes.
 *   GLR16 is effectively unavailable via this path on the shipping firmware.
 *
 * PATH 2: SurfaceControl.setRefreshMode (AOSP Android 10+, no root required)
 *   On AOSP firmware that adds mSurfaceControl to ViewRootImpl, this path is tried as
 *   a fallback to the VRI direct path above.
 *
 * PATH 3: sysfs force_update_mode (last resort, requires Magisk epd_gc16 module)
 *   Writes EINK_GC16_MODE (4) to:
 *     /sys/devices/virtual/disp/disp/waveform/force_update_mode
 *   chmod 666 applied at boot by the epd_gc16 Magisk module (SELinux rules also required).
 *   The driver adds EINK_GAMMA_CORRECT (0x200000) automatically.
 *   GLR16 (0x40) is silently ignored by the sysfs node; only GC16 is used here.
 *
 *   The com.nook.action.full_refresh broadcast intent and Surface.einkChangeQuickUpdateMode
 *   were ruled out: both fire GC16 against the currently-displayed buffer, not the queued
 *   one, causing the old page to flash. The sysfs flag is a persistent register read by
 *   the driver at buffer-commit time, which correctly arms it for the incoming page.
 *
 * needsView() = false: NativeSurfaceView causes a surface setup crash on Emperor hardware.
 *
 * Constants from sunxi-kobo.h / EpdDisplayControllerImpl.
 * see https://github.com/koreader/koreader/issues/14574
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import eu.kanade.tachiyomi.ui.reader.eink.epd.freescale.NTXEPDController
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*

class NookEmperorEPDController : EPDInterface {

    // Cached view for resume(), which has no view parameter.
    private var lastView = WeakReference<View>(null)

    companion object {
        private const val TAG = "EPD"

        const val EMPEROR_EINK_DU_MODE = 0x02
        const val EMPEROR_EINK_GC16_MODE = 0x04
        const val EMPEROR_EINK_GL16_MODE = 0x20
        const val EMPEROR_EINK_GLR16_MODE = 0x40
        const val EMPEROR_EINK_GU16_MODE = 0x84
        const val EMPEROR_EINK_NO_MERGE = Integer.MIN_VALUE // 0x80000000

        private const val FORCE_UPDATE_MODE =
            "/sys/devices/virtual/disp/disp/waveform/force_update_mode"

        // Reflection objects — initialized once, null if the path is unavailable.
        @Volatile private var reflectionReady = false
        private var getViewRootImplFn: Method? = null
        private var surfaceControlField: Field? = null
        private var setRefreshModeFn: Method? = null

        @SuppressLint("BlockedPrivateApi")
        private fun initReflection() {
            if (reflectionReady) return
            try {
                getViewRootImplFn = View::class.java.getMethod("getViewRootImpl")
                val vriClass = Class.forName("android.view.ViewRootImpl")
                try {
                    // B&N Android 8.1: setRefreshMode lives directly on ViewRootImpl.
                    val method = vriClass.getDeclaredMethod("setRefreshMode", Int::class.javaPrimitiveType)
                    method.isAccessible = true
                    setRefreshModeFn = method
                    // surfaceControlField stays null — VRI direct path.
                    Log.i(TAG, "Emperor EPD: ViewRootImpl.setRefreshMode path active")
                } catch (_: NoSuchMethodException) {
                    // AOSP Android 10+: setRefreshMode lives on SurfaceControl.
                    val scClass = Class.forName("android.view.SurfaceControl")
                    val field = vriClass.getDeclaredField("mSurfaceControl")
                    field.isAccessible = true
                    surfaceControlField = field
                    setRefreshModeFn = scClass.getMethod("setRefreshMode", Int::class.javaPrimitiveType)
                    Log.i(TAG, "Emperor EPD: SurfaceControl path active (GLR16 capable)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Emperor EPD: reflection path unavailable, using sysfs: $e")
            }
            reflectionReady = true
        }

        // Returns true if the reflection path handled the call.
        fun surfaceControlSetMode(view: View, mode: Int): Boolean {
            initReflection()
            val getFn = getViewRootImplFn ?: return false
            val setFn = setRefreshModeFn ?: return false
            return try {
                val vri = getFn.invoke(view.rootView) ?: return false
                val target = surfaceControlField?.get(vri) ?: vri
                setFn.invoke(target, mode)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Emperor EPD: setRefreshMode failed: $e")
                false
            }
        }

        fun sysfsSetMode(mode: Int) {
            try {
                File(FORCE_UPDATE_MODE).writeText(mode.toString())
            } catch (_: IOException) {}
        }
    }

    override fun getPlatform(): String = "freescale"
    override fun getMode(): String = "all"

    override fun getWaveformFull(): Int = EMPEROR_EINK_NO_MERGE + EMPEROR_EINK_GC16_MODE
    override fun getWaveformPartial(): Int = EMPEROR_EINK_GU16_MODE
    override fun getWaveformFullUi(): Int = EMPEROR_EINK_NO_MERGE + NTXEPDController.EINK_WAVEFORM_MODE_GLR16
    override fun getWaveformPartialUi(): Int = EMPEROR_EINK_GU16_MODE
    override fun getWaveformFast(): Int = EMPEROR_EINK_GU16_MODE

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
        lastView = WeakReference(targetView)

        // Reflection paths send GLR16 (0x40) for UI; HWC maps it to GU16 on shipping firmware.
        // Sysfs path is GC16-only (silently ignores 0x40).
        val scMode = when (mode) {
            getWaveformFull() -> EMPEROR_EINK_GC16_MODE
            getWaveformFullUi() -> EMPEROR_EINK_GLR16_MODE
            else -> 0
        }
        val sysfsMode = when (mode) {
            getWaveformFull() -> EMPEROR_EINK_GC16_MODE
            else -> 0
        }

        if (surfaceControlSetMode(targetView, scMode)) {
            Log.i(
                TAG,
                String.format(
                    Locale.US,
                    "Emperor EPD: setRefreshMode=0x%x (waveform=0x%x)",
                    scMode,
                    mode,
                ),
            )
        } else {
            sysfsSetMode(sysfsMode)
            Log.i(
                TAG,
                String.format(
                    Locale.US,
                    "Emperor EPD: force_update_mode=0x%x (waveform=0x%x)",
                    sysfsMode,
                    mode,
                ),
            )
        }
    }

    override fun resume() {
        // Reset waveform to GU16 default so partial refreshes after resume aren't forced to GC16.
        val view = lastView.get()
        if (view == null || !surfaceControlSetMode(view, 0)) {
            sysfsSetMode(0)
        }
    }

    override fun pause() {}
}
