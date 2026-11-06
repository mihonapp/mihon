package mihon.feature.mekuri.shelf

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/**
 * Mekuri: the small overview shelf shown at the top of a category, in place of Mihon's category tabs.
 * Run a finger across it (the toolbar title follows) and let go to switch to that shelf; a tap works too, and so does
 * swiping the covers.
 */
@Composable
fun CompactShelf(
    shelves: List<Shelf>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The shelf under the finger while sliding, or null when not sliding (shown in the toolbar title). */
    onPreview: (Int?) -> Unit = {},
) {
    if (shelves.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val colors = rememberShelfColors(shelves)
    // Slim spines: about half the bookshelf width, centred, shrunk to fit when there are many shelves
    val baseWidths = shelves.map { SpineMetrics.width(it.count).value * 0.5f }
    val gapDp = 4f
    var scrubbing by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(currentIndex) }
    val active = (if (scrubbing) hover else currentIndex).coerceIn(0, shelves.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val naturalDp = baseWidths.sum() + gapDp * (shelves.size - 1)
        val fit = (maxWidth.value / naturalDp).coerceAtMost(1f)
        val spineWidths = baseWidths.map { it * fit }
        val usedDp = spineWidths.sum() + gapDp * fit * (shelves.size - 1)
        val startPx = with(density) { ((maxWidth.value - usedDp) / 2).dp.toPx() }
        val leftsPx = spineWidths.runningFold(0f) { acc, w -> acc + w + gapDp * fit }.dropLast(1)
            .map { with(density) { it.dp.toPx() } + startPx }
        val widthsPx = spineWidths.map { with(density) { it.dp.toPx() } }
        val halfGapPx = with(density) { (gapDp * fit / 2).dp.toPx() }
        fun indexAt(x: Float): Int {
            leftsPx.forEachIndexed { i, left -> if (x <= left + widthsPx[i] + halfGapPx) return i }
            return shelves.lastIndex
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(shelves, maxWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        scrubbing = true
                        hover = indexAt(down.position.x)
                        onPreview(hover)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        var cancelled = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val i = indexAt(change.position.x)
                            if (i != hover) {
                                hover = i
                                onPreview(i)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                        scrubbing = false
                        onPreview(null)
                        if (!cancelled) onSelect(hover)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy((gapDp * fit).dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            shelves.forEachIndexed { i, shelf ->
                val on = i == active
                val lift by animateDpAsState(if (on) (if (scrubbing) 10.dp else 4.dp) else 0.dp, label = "lift")
                val alpha by animateFloatAsState(if (on) 1f else 0.4f, label = "alpha")
                Spine(
                    name = shelf.name,
                    count = shelf.count,
                    color = colors[shelf.key] ?: fallbackColor(shelf.key),
                    showText = false,
                    kind = shelf.kind,
                    hasUpdates = shelf.hasUpdates,
                    modifier = Modifier
                        .width(spineWidths[i].dp)
                        .height(44.dp)
                        .graphicsLayer {
                            translationY = -lift.toPx()
                            this.alpha = if (shelf.hasUpdates) maxOf(alpha, 0.75f) else alpha
                        }
                        .clearAndSetSemantics {
                            contentDescription = "${shelf.name}, ${shelf.count} " +
                                (if (shelf.count == 1) "title" else "titles") +
                                if (shelf.hasUpdates) ", new chapters" else ""
                            role = Role.Tab
                            selected = i == currentIndex
                            onClick {
                                onSelect(i)
                                true
                            }
                        },
                )
            }
        }
    }
}
