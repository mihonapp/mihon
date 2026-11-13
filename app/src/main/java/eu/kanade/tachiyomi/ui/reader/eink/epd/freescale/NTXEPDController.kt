/* generic EPD Controller for Android,
 *
 * interface for newer ntx devices, like Nooks and Tolinos.
 * based on https://github.com/koreader/koreader/issues/3517
 *
 * Thanks to @char11
 *
 *	val EINK_DITHER_COLOR_Y1 = 268435456;
 *	val EINK_DITHER_COLOR_Y4 = 0;
 *	val EINK_DITHER_MODE_DITHER = 256;
 *	val EINK_DITHER_MODE_NODITHER = 0;
 *	val EINK_MONOCHROME_MODE_MONOCHROME = 2048;
 *	val EINK_MONOCHROME_MODE_NOMONOCHROME = 0;
 *	val EINK_UPDATE_MODE_FULL = 32;
 *	val EINK_UPDATE_MODE_PARTIAL = 0;
 *	val EINK_WAIT_MODE_NOWAIT = 0;
 *	val EINK_WAIT_MODE_WAIT = 64;
 *	val EINK_WAVEFORM_MODE_A2 = 5;
 *	val EINK_WAVEFORM_MODE_AUTO = 4;
 *	val EINK_WAVEFORM_MODE_DU = 1;
 *	val EINK_WAVEFORM_MODE_GC16 = 2;
 *	val EINK_WAVEFORM_MODE_GC4 = 3;
 *	val EINK_WAVEFORM_MODE_GL16 = 6;
 *	val EINK_WAVEFORM_MODE_GLD16 = 8;
 *	val EINK_WAVEFORM_MODE_GLR16 = 7;
 *	val EINK_WAVEFORM_MODE_INIT = 0;
 */

package eu.kanade.tachiyomi.ui.reader.eink.epd.freescale

import android.util.Log
import java.util.*

abstract class NTXEPDController {

    companion object {
        private const val TAG = "EPD"
        const val EINK_WAVEFORM_UPDATE_FULL = 32
        const val EINK_WAVEFORM_UPDATE_PARTIAL = 0
        const val EINK_WAVEFORM_MODE_DU = 1
        const val EINK_WAVEFORM_MODE_GC16 = 2
        const val EINK_WAVEFORM_MODE_GL16 = 6
        const val EINK_WAVEFORM_MODE_GLR16 = 7
        const val EINK_WAVEFORM_DELAY = 0

        fun requestEpdMode(
            view: android.view.View,
            mode: Int,
            delay: Long,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ): Boolean {
            return try {
                Class.forName("android.view.View").getMethod(
                    "postInvalidateDelayed",
                    java.lang.Long.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                ).invoke(view, delay, x, y, width, height, mode)

                Log.i(
                    TAG,
                    String.format(
                        Locale.US,
                        "requested eink refresh, type: %d x:%d y:%d w:%d h:%d",
                        mode,
                        x,
                        y,
                        width,
                        height,
                    ),
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                false
            }
        }
    }
}
