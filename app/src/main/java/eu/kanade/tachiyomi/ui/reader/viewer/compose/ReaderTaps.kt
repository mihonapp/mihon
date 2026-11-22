package eu.kanade.tachiyomi.ui.reader.viewer.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Taps and long taps for the viewer's navigation, seen after the pages have had the events, consumed or not, so a
 * page's own zoom and pan don't hide them and a button reporting its press has turned this off through [isEnabled]
 * first. A tap waits out the double tap timeout and a second press makes it the page's double tap instead, and a long
 * tap is a press held still past the long press timeout.
 */
suspend fun PointerInputScope.detectReaderTaps(
    isEnabled: () -> Boolean,
    onTap: (Offset) -> Unit,
    onLongTap: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (!isEnabled()) return@awaitEachGesture

        var up: PointerInputChange? = null
        val isLongTap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            up = awaitStillUp(down)
            false
        } ?: true
        if (isLongTap) {
            onLongTap(down.position)
            return@awaitEachGesture
        }
        val tap = up ?: return@awaitEachGesture

        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        }
        if (secondDown == null) onTap(tap.position)
    }
}

/**
 * The lift of [down] if the pointer stayed within the touch slop and alone, or null.
 */
private suspend fun AwaitPointerEventScope.awaitStillUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        if (event.changes.count { it.pressed } > 1) return null
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
        if (change.changedToUpIgnoreConsumed()) return change
    }
}
