package mihon.feature.mekuri.shelf

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Tap a spine to switch shelves (swiping the covers works too).
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
    val active = currentIndex.coerceIn(0, shelves.lastIndex)

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
        fun indexAt(x: Float): Int? {
            leftsPx.forEachIndexed { i, left ->
                if (x >= left - halfGapPx && x <= left + widthsPx[i] + halfGapPx) return i
            }
            return null
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(shelves, maxWidth) {
                    detectTapGestures { position ->
                        val i = indexAt(position.x) ?: return@detectTapGestures
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy((gapDp * fit).dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            shelves.forEachIndexed { i, shelf ->
                val on = i == active
                val lift by animateDpAsState(if (on) 4.dp else 0.dp, label = "lift")
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
