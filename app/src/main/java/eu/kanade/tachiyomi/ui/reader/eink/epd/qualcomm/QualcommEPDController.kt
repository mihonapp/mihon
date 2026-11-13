package eu.kanade.tachiyomi.ui.reader.eink.epd.qualcomm

import android.util.Log
import android.view.View
import java.util.*

// More information including epd mode values
// https://github.com/koreader/android-luajit-launcher/pull/250#issuecomment-711443457
abstract class QualcommEPDController {
    companion object {
        private const val TAG = "EPD"
        const val EINK_WAVEFORM_UPDATE_FULL = 32
        const val EINK_WAVEFORM_UPDATE_PARTIAL = 0
        const val EINK_WAVEFORM_MODE_WAIT = 64
        const val EINK_WAVEFORM_MODE_DU = 1
        const val EINK_WAVEFORM_MODE_GC16 = 2
        const val EINK_WAVEFORM_MODE_REAGL = 6
        const val EINK_WAVEFORM_DELAY = 250
        const val EINK_WAVEFORM_DELAY_UI = 100
        const val EINK_WAVEFORM_DELAY_FAST = 0

        private fun preventSystemRefresh(): Boolean {
            // Sets UpdateMode and UpdateScheme to None
            // this function is called EpdController.setSystemUpdateModeAndScheme in onyxsdk
            return try {
                Class.forName("android.view.View").getMethod(
                    "setWaveformAndScheme",
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                ).invoke(null, 5, 1, 0)
                true
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                false
            }
        }

        fun requestEpdMode(
            targetView: View,
            mode: Int,
            delay: Long,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ): Boolean {
            return try {
                // We need to always call this, not sure why, if it's not called before
                // system will refresh after us, it'll refresh anyway if user set
                // Normal mode, or Regal mode works flawlessly otherwise
                preventSystemRefresh()
                // EpdController.refreshScreenRegion in onyxsdk
                val refreshScreen = Class.forName("android.view.View").getMethod(
                    "refreshScreen",
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                )
                object : Thread() {
                    override fun run() {
                        sleep(delay)
                        try {
                            refreshScreen.invoke(targetView, x, y, width, height, mode)
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
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                        }
                    }
                }.start()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
