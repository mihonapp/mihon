package mihon.feature.mekuri.shelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Mekuri: the library home. A full-screen bookshelf, one spine per category.
 *
 * Hold anywhere on the shelf and slide: the spine under the finger lifts (with a haptic tick) and its name shows
 * above. Near either edge the shelf glides sideways. Let go to open that shelf; drag up off the shelf first to
 * cancel. A quick tap opens the spine under the finger.
 */
@Composable
fun BookshelfHome(
    shelves: List<Shelf>,
    lastOpenedIndex: Int,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (shelves.isEmpty()) return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val colors = rememberShelfColors(shelves)
    val lastIndex = lastOpenedIndex.coerceIn(0, shelves.lastIndex)

    var scrubbing by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(lastIndex) }
    var edge by remember { mutableIntStateOf(0) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }

    val active = if (scrubbing) hover else lastIndex
    val activeShelf = shelves[active.coerceIn(0, shelves.lastIndex)]
    val activeColor by animateColorAsState(colors[activeShelf.key] ?: fallbackColor(activeShelf.key), label = "glow")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val sidePadding = 20.dp
        val viewportPx = with(density) { (maxWidth - sidePadding * 2).toPx() }
        val gapPx = with(density) { SpineMetrics.Gap.toPx() }
        val widthsPx = shelves.map { with(density) { SpineMetrics.width(it.count).toPx() } }
        val lefts = widthsPx.runningFold(0f) { acc, w -> acc + w + gapPx }.dropLast(1)
        val totalPx = (lefts.lastOrNull() ?: 0f) + (widthsPx.lastOrNull() ?: 0f)
        val maxOffset = (totalPx - viewportPx).coerceAtLeast(0f)
        val shelfHeight = (maxHeight * 0.42f).coerceIn(200.dp, 340.dp)
        val liftRoom = 44.dp
        val edgeZonePx = with(density) { 44.dp.toPx() }
        val cancelPx = 0f // above the shelf area (which already includes the lift room) cancels
        val glidePxPerFrame = with(density) { 7.dp.toPx() }

        fun indexAt(trackX: Float): Int {
            lefts.forEachIndexed { i, left -> if (trackX < left + widthsPx[i] + gapPx / 2) return i }
            return shelves.lastIndex
        }
        // When every spine fits, the shelf sits centred (a negative offset); otherwise it scrolls within bounds
        val minOffset = if (totalPx < viewportPx) -(viewportPx - totalPx) / 2 else 0f
        fun centerOn(i: Int): Float =
            if (minOffset < 0f) minOffset else (lefts[i] + widthsPx[i] / 2 - viewportPx / 2).coerceIn(0f, maxOffset)
        fun edgeFor(x: Float): Int = when {
            x < edgeZonePx -> -1
            x > viewportPx - edgeZonePx -> 1
            else -> 0
        }

        LaunchedEffect(shelves.size, viewportPx, lastIndex) {
            offset = centerOn(lastIndex)
        }
        // Edge glide while the finger rests near either end of the shelf
        LaunchedEffect(scrubbing) {
            while (scrubbing) {
                withFrameNanos { }
                if (edge != 0) {
                    if (minOffset < 0f) continue
                    offset = (offset + edge * glidePxPerFrame).coerceIn(0f, maxOffset)
                    val i = indexAt(lastX + offset)
                    if (i != hover) {
                        hover = i
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }

        // Soft glow in the active shelf's colour behind the shelf
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(460.dp)
                .background(
                    Brush.radialGradient(
                        listOf(activeColor.copy(alpha = if (scrubbing) 0.42f else 0.26f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = when {
                        !scrubbing -> "LAST OPENED"
                        cancelling -> "LET GO TO CANCEL"
                        else -> "LET GO TO OPEN"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (scrubbing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = activeShelf.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = buildString {
                        append(if (activeShelf.count == 1) "1 title" else "${activeShelf.count} titles")
                        if (activeShelf.unread > 0) append(" · ${activeShelf.unread} unread")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(shelfHeight + liftRoom)
                    .padding(horizontal = sidePadding)
                    .clipToBounds()
                    .semantics { contentDescription = "Bookshelf. Hold and slide to pick a shelf, let go to open it" }
                    .pointerInput(shelves, maxOffset, viewportPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            scrubbing = true
                            cancelling = false
                            lastX = down.position.x
                            edge = edgeFor(lastX)
                            hover = indexAt(lastX + offset)
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
                                lastX = change.position.x
                                cancelling = change.position.y < -cancelPx
                                edge = if (cancelling) 0 else edgeFor(lastX)
                                val i = indexAt(lastX + offset)
                                if (i != hover) {
                                    hover = i
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                change.consume()
                            }
                            val open = !cancelled && !cancelling
                            val target = hover
                            scrubbing = false
                            cancelling = false
                            edge = 0
                            if (open) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpen(target)
                            }
                        }
                    },
            ) {
                shelves.forEachIndexed { i, shelf ->
                    val on = i == active
                    val near = kotlin.math.abs(i - active) == 1
                    val lift by animateDpAsState(
                        targetValue = when {
                            scrubbing && on -> 30.dp
                            scrubbing && near -> 10.dp
                            on -> 8.dp
                            else -> 0.dp
                        },
                        label = "lift",
                    )
                    val alpha by animateFloatAsState(
                        targetValue = when {
                            on -> 1f
                            scrubbing -> 0.5f
                            else -> 0.82f
                        },
                        label = "alpha",
                    )
                    val scale by animateFloatAsState(if (on && scrubbing) 1.06f else 1f, label = "scale")
                    Spine(
                        name = shelf.name,
                        count = shelf.count,
                        color = colors[shelf.key] ?: fallbackColor(shelf.key),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset((lefts[i] - offset).roundToInt(), 0) }
                            .width(with(density) { widthsPx[i].toDp() })
                            .height(shelfHeight * SpineMetrics.heightFraction(shelf.key, shelf.name))
                            .graphicsLayer {
                                translationY = -lift.toPx()
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            }
                            .clearAndSetSemantics {
                                contentDescription = "${shelf.name}, ${shelf.count} titles"
                                role = Role.Button
                                onClick {
                                    onOpen(i)
                                    true
                                }
                            },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
            )

            if (maxOffset > 0f) {
                val thumbFraction = (viewportPx / totalPx).coerceIn(0.12f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 14.dp)
                        .width(120.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (120.dp * (1f - thumbFraction)) * (offset / maxOffset))
                            .width(120.dp * thumbFraction)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
                    )
                }
            }

            Text(
                text = if (scrubbing) " " else "Hold and slide across your shelf · let go to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
internal fun Spine(
    name: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(color)
            .padding(top = 14.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (showText) {
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.verticalText(),
                )
            }
        }
        if (showText) {
            Text(
                text = count.toString(),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Lays text out top-to-bottom along a spine (rotated 90° clockwise, like an English book spine). */
private fun Modifier.verticalText(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) {
            rotationZ = 90f
        }
    }
}
