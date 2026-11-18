package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
     * Moves forward through the pages.
     */
    fun moveForward()

    /**
     * Moves backward through the pages.
     */
    fun moveBackward()
}

/**
 * The reader's grayscale and inverted colours, for page images only: transition, loading and error pages keep
 * theirs. Null when both are off.
 */
val LocalPageColorFilter = compositionLocalOf<ColorFilter?> { null }

/**
 * Turns pages with the mouse wheel instead of letting the content scroll.
 */
fun Modifier.mouseWheelPaging(viewer: Viewer): Modifier = pointerInput(viewer) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val delta = event.changes.first().scrollDelta.y
            when {
                delta > 0 -> viewer.moveForward()
                delta < 0 -> viewer.moveBackward()
                else -> continue
            }
            event.changes.forEach { it.consume() }
        }
    }
}
