/* EPD controller for the iFlytek AiNote X1 (rk3288, HTFYUN EinkHTFY panel).
 *
 * Based on https://github.com/unwmun/refreshU
 *
 * WHY THIS DEVICE NEEDS ITS OWN CONTROLLER
 * ----------------------------------------
 * This device does not expose an EPD driver through sysfs or a vendor service.
 * It only updates the panel when an ordinary View reports a dirty rect through
 * the vendor extension on android.view.View, which the custom HWC turns into a
 * waveform update. There is therefore no way to request a refresh without a
 * View, hence needsView() returning true and the invalidate() call below.
 *
 * Everything here was recovered from the device's own firmware (OTA package
 * XFCYJ10E_R_3.0.6) rather than guessed:
 *
 *  1. The stock reader gates its e-ink work on DevTypeUtils.isAINoteX1(),
 *     implemented as `Build.MODEL == "AInote"` -- which matches this tablet.
 *
 *  2. The waveform is selected with
 *         View.setEinkUpdateMode(short dataMode, short dispMode)
 *     The stock reader's RefreshModeUtils.setGuowenShowMode() pins dataMode to 3
 *     (a `const/4 v0, 3` precedes every call site) and varies only dispMode:
 *         1  = DU    (fast, light ghosting)
 *         2  = GC16  (full quality, no ghosting)   <-- used for pages
 *         3  = AUTO
 *         10 = balanced reading    11 = speed reading   12 = quality reading
 *         13 = handwriting         14 = note
 *
 *  3. Dirty rects are reported with
 *         View.invalidate(int l, int t, int r, int b, short dispMode)
 *     The panel is rotated with respect to the framebuffer, so the rect covers
 *     the full physical extent (1872x1404) rather than the logical one
 *     (1404x1872).
 *
 *  4. IMPORTANT: the mode must be passed as a real 16-bit value. KOReader's
 *     android.lua declares the JNI method entries as C varargs, so a jshort
 *     handed over from Lua is promoted to int and arrives as 0, silently
 *     leaving the panel on its default waveform. Living in Java is precisely
 *     what allows us to call setEinkUpdateMode(short, short) correctly.
 *
 * The vendor methods are public on this device's framework, so ordinary
 * reflection is enough -- no hidden-API access is involved.
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd

import android.util.Log
import android.view.View

class IflytekAiNoteX1EPDController : EPDInterface {

    companion object {
        private const val TAG = "EPD"

        // dataMode is pinned to 3 by the stock reader for every mode it requests.
        private const val DATA_MODE: Short = 3

        // dispMode values, per EINK_DISPLAY_MODE_* and RefreshModeUtils.
        private const val DISP_AUTO: Short = 3
        private const val DISP_DU: Short = 1
        private const val DISP_GC16: Short = 2
        private const val DISP_BALANCED: Short = 10
        private const val DISP_SPEED: Short = 11
        private const val DISP_QUALITY: Short = 12

        private const val WAVEFORM_DELAY = 0

        /** View.setEinkUpdateMode(short dataMode, short dispMode) */
        private fun setMode(view: View, dataMode: Short, dispMode: Short): Boolean {
            return try {
                val method = View::class.java.getMethod(
                    "setEinkUpdateMode",
                    Short::class.javaPrimitiveType,
                    Short::class.javaPrimitiveType,
                )
                method.invoke(view, dataMode, dispMode)
                true
            } catch (e: Exception) {
                Log.e(TAG, "setEinkUpdateMode($dataMode, $dispMode) failed: $e")
                false
            }
        }

        /* Report a full-screen dirty rect so the vendor HWC pushes the frame.
         * Without this the panel is never updated at all, because KOReader
         * draws through ANativeWindow and never enters the View draw traversal.
         */

        /** Run the two stages the stock reader uses for a page turn. */
        private fun commit(view: View, dispMode: Short) {
            setMode(view, DATA_MODE, DISP_AUTO) // stage 1: clear / gate pass
            reportDirty(view, DISP_AUTO)
            setMode(view, DATA_MODE, dispMode) // stage 2: image pass
            reportDirty(view, dispMode)
        }

        private fun reportDirty(view: View, dispMode: Short): Boolean {
            val w = view.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
            val h = view.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels
            // The framebuffer reports the logical (portrait) size while the panel
            // is landscape, so cover the full physical extent.
            val wide = maxOf(w, h)
            val tall = minOf(w, h)
            return try {
                val method = View::class.java.getMethod(
                    "invalidate",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Short::class.javaPrimitiveType,
                )
                method.invoke(view, 0, 0, wide, tall, dispMode)
                true
            } catch (e: Exception) {
                Log.e(TAG, "invalidate failed: $e")
                false
            }
        }
    }

    override fun getPlatform(): String = "iflytek"
    override fun getMode(): String = "full-only"

    override fun getWaveformFull(): Int = DISP_GC16.toInt()
    override fun getWaveformPartial(): Int = DISP_GC16.toInt()
    override fun getWaveformFullUi(): Int = DISP_AUTO.toInt()
    override fun getWaveformPartialUi(): Int = DISP_DU.toInt()
    override fun getWaveformFast(): Int = DISP_DU.toInt()

    override fun getWaveformDelay(): Int = WAVEFORM_DELAY
    override fun getWaveformDelayUi(): Int = WAVEFORM_DELAY
    override fun getWaveformDelayFast(): Int = WAVEFORM_DELAY

    override fun needsView(): Boolean = true

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
        val disp = when (epdMode) {
            "full" -> DISP_GC16 // pages: full quality, no ghosting
            "partial" -> DISP_GC16
            "fullui" -> DISP_AUTO
            "partialui" -> DISP_DU
            "fast" -> DISP_DU
            "a2" -> DISP_DU
            "balanced" -> DISP_BALANCED
            "speed" -> DISP_SPEED
            "quality" -> DISP_QUALITY
            // Fall back to the numeric mode when no name was supplied.
            else -> mode.takeIf { it in 1..14 }?.toShort() ?: DISP_AUTO
        }
        // Mirror the stock reader's page turn: a clear/gate pass, then the
        // requested image pass.
        commit(targetView, disp)
    }

    override fun resume() {}
    override fun pause() {}
}
