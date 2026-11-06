package mihon.feature.mekuri.shelf

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Mekuri: the small overview shelf shown at the top of a category, in place of Mihon's category tabs.
 * Tap a spine, or hold and slide across it (a name bubble follows the finger) and let go to switch shelves.
 */
@Composable
fun CompactShelf(
    shelves: List<Shelf>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
    var bubbleWidth by remember { mutableIntStateOf(0) }
    val active = (if (scrubbing) hover else currentIndex).coerceIn(0, shelves.lastIndex)
    val height by animateDpAsState(if (scrubbing) 104.dp else 48.dp, label = "compactShelfHeight")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val naturalDp = baseWidths.sum() + gapDp * (shelves.size - 1)
        val fit = (maxWidth.value / naturalDp).coerceAtMost(1f)
        val spineWidths = baseWidths.map { it * fit }
        val usedDp = spineWidths.sum() + gapDp * fit * (shelves.size - 1)
        val startPx = with(density) { ((maxWidth.value - usedDp) / 2).dp.toPx() }
        val leftsPx = spineWidths.runningFold(0f) { acc, w -> acc + w + gapDp * fit }.dropLast(1)
            .map { with(density) { it.dp.toPx() } + startPx }
        val widthsPx = spineWidths.map { with(density) { it.dp.toPx() } }
        fun indexAt(x: Float): Int {
            val halfGapPx = with(density) { (gapDp * fit / 2).dp.toPx() }
            leftsPx.forEachIndexed { i, left -> if (x < left + widthsPx[i] + halfGapPx) return i }
            return shelves.lastIndex
        }
        val centerFraction = (leftsPx[active] + widthsPx[active] / 2) / widthPx

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (scrubbing) 40.dp else 0.dp)
                .height(height)
                .pointerInput(shelves, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        scrubbing = true
                        hover = indexAt(down.position.x)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                        scrubbing = false
                        if (!cancelled) onSelect(hover)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy((gapDp * fit).dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            shelves.forEachIndexed { i, shelf ->
                val on = i == active
                val lift by animateDpAsState(if (on) (if (scrubbing) 12.dp else 4.dp) else 0.dp, label = "lift")
                val alpha by animateFloatAsState(if (on) 1f else 0.4f, label = "alpha")
                Spine(
                    name = shelf.name,
                    count = shelf.count,
                    color = colors[shelf.key] ?: fallbackColor(shelf.key),
                    showText = false,
                    kind = shelf.kind,
                    modifier = Modifier
                        .width(spineWidths[i].dp)
                        .height(height * SpineMetrics.heightFraction(shelf.key, shelf.name))
                        .graphicsLayer {
                            translationY = -lift.toPx()
                            this.alpha = alpha
                        }
                        .clearAndSetSemantics {
                            contentDescription = "${shelf.name}, ${shelf.count} titles"
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

        if (scrubbing) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .onSizeChanged { bubbleWidth = it.width }
                    .offset {
                        val x = (centerFraction * widthPx - bubbleWidth / 2f)
                            .coerceIn(0f, (widthPx - bubbleWidth).coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), 0)
                    },
            ) {
                Text(
                    text = shelves[active].name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
