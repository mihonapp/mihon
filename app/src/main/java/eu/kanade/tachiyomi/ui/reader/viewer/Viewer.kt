package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

/**
 * Interface for implementing a viewer.
 */
interface Viewer {

    /**
     * Draws this viewer.
     */
    @Composable
    fun Content(modifier: Modifier)

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
}

/**
 * The reader's grayscale and inverted colours, for page images only: transition, loading and error pages keep
 * theirs. Null when both are off.
 */
val LocalPageColorFilter = compositionLocalOf<ColorFilter?> { null }
