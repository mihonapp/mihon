package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

/**
 * Interface for implementing a viewer.
 */
interface Viewer {

    /**
     * Returns the view this viewer uses.
     */
    fun getView(): View

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    fun destroy() {}

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    fun setChapters(chapters: ViewerChapters)

    /**
     * Tells this viewer to move to the given [page].
     */
    fun moveToPage(page: ReaderPage)

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean

    /**
     * Called when an external scroll event is received (e.g. from a secondary screen touchpad).
     */
    fun handleExternalScroll(dy: Float) {}

    /**
     * Called when an external fling event is received (e.g. from a secondary screen touchpad).
     */
    fun handleExternalFling(velocityY: Float) {}

    /**
     * Called when an external scale event is received (e.g. pinch-to-zoom from a touchpad).
     */
    fun handleExternalScale(scaleFactor: Float) {}

    /**
     * Called when an external pan event is received (e.g. 2-finger drag from a touchpad).
     */
    fun handleExternalPan(dx: Float, dy: Float) {}

    /**
     * Called when zoom should be reset to default (e.g. double-tap on touchpad).
     */
    fun handleExternalZoomReset() {}
}
