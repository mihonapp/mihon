package eu.kanade.tachiyomi.ui.reader.cast

import android.content.Context
import android.view.Display
import android.view.WindowManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Cast target that shows a [CastPresentation] on an external display. Main thread only.
 */
class CastDisplayTarget(
    private val context: Context,
    private val controller: CastController,
) {

    private var presentation: CastPresentation? = null

    /** Id of the display in use, -1 when none. */
    var displayId: Int = -1
        private set

    /** Shows the presentation on [display]; returns false (and logs) when the display refuses it. */
    fun start(display: Display): Boolean {
        stop()
        var presentation: CastPresentation? = null
        return try {
            presentation = CastPresentation(context, display, controller)
            presentation.show()
            this.presentation = presentation
            displayId = display.displayId
            true
        } catch (e: WindowManager.InvalidDisplayException) {
            logcat(LogPriority.ERROR, e) { "Display ${display.displayId} can't host a presentation" }
            presentation?.release()
            false
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Unable to show the cast presentation on display ${display.displayId}" }
            presentation?.release()
            false
        }
    }

    /** Dismisses the presentation and releases everything it holds. Safe to call repeatedly. */
    fun stop() {
        val current = presentation ?: return
        presentation = null
        displayId = -1
        try {
            current.release()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Unable to dismiss the cast presentation" }
        }
    }
}
