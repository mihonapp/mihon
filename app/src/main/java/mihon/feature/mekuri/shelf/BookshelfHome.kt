package mihon.feature.mekuri.shelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Lock
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Sparkle
import kotlin.math.roundToInt

/**
 * Mekuri: the library home. A full-screen bookshelf, one spine per category; thicker spines hold more titles.
 *
 * Drag the shelf sideways to move along it (it keeps gliding after a flick); the spine in the middle lifts and its
 * name shows above while you move. Tap a spine to open that shelf. Shelves with chapters that arrived since you last
 * read them carry a small coral marker.
 */
@Composable
fun BookshelfHome(
    shelves: List<Shelf>,
    lastOpenedIndex: Int,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    vaultUnlocked: Boolean = false,
    onVault: () -> Unit = {},
    onShelfMenu: ((Int) -> Unit)? = null,
) {
    if (shelves.isEmpty()) return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val colors = rememberShelfColors(shelves)
    val lastIndex = lastOpenedIndex.coerceIn(0, shelves.lastIndex)

    var offset by remember { mutableFloatStateOf(0f) }

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
        val sidePx = with(density) { sidePadding.toPx() }
        // When every spine fits, the shelf sits centred (a fixed negative offset). Otherwise it scrolls far enough
        // that either end spine (the vault included) can reach the middle and be highlighted.
        val fits = totalPx < viewportPx
        val minOffset = if (fits) -(viewportPx - totalPx) / 2 else widthsPx.first() / 2 - viewportPx / 2
        val maxOffset = if (fits) minOffset else lefts.last() + widthsPx.last() / 2 - viewportPx / 2
        val shelfHeight = (maxHeight * 0.42f).coerceIn(200.dp, 340.dp)
        val liftRoom = 32.dp

        fun indexAt(trackX: Float): Int? {
            lefts.forEachIndexed { i, left -> if (trackX >= left && trackX <= left + widthsPx[i]) return i }
            return null
        }
        fun nearestTo(trackX: Float): Int {
            lefts.forEachIndexed { i, left -> if (trackX < left + widthsPx[i] + gapPx / 2) return i }
            return shelves.lastIndex
        }
        fun centerOn(i: Int): Float =
            if (fits) minOffset else (lefts[i] + widthsPx[i] / 2 - viewportPx / 2).coerceIn(minOffset, maxOffset)

        LaunchedEffect(shelves.size, viewportPx, lastIndex) {
            offset = centerOn(lastIndex)
        }

        val scrollState = rememberScrollableState { delta ->
            val old = offset
            offset = (offset - delta).coerceIn(minOffset, maxOffset)
            old - offset
        }
        val moving = scrollState.isScrollInProgress
        // The highlighted spine is whichever sits in the middle (the last-opened one is centred on arrival)
        val active = if (fits) lastIndex else nearestTo(offset + viewportPx / 2)
        val activeShelf = shelves[active]
        LaunchedEffect(active) {
            if (moving) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // When a drag or fling ends, settle the nearest spine into the middle so nothing stops half-way
        LaunchedEffect(moving, fits) {
            if (moving || fits) return@LaunchedEffect
            val target = centerOn(nearestTo(offset + viewportPx / 2))
            if (kotlin.math.abs(target - offset) < 0.5f) return@LaunchedEffect
            androidx.compose.animation.core.animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 400f),
            ) { value, _ -> offset = value }
        }
        val activeColor by animateColorAsState(
            colors[activeShelf.key] ?: fallbackColor(activeShelf.key),
            label = "glow",
        )

        // Soft glow in the active shelf's colour behind the shelf
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(460.dp)
                .background(
                    Brush.radialGradient(
                        listOf(activeColor.copy(alpha = if (moving) 0.38f else 0.26f), Color.Transparent),
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
                val onVaultSpine = activeShelf.kind == Shelf.Kind.Vault
                Text(
                    text = when {
                        onVaultSpine -> "VAULT"
                        active == lastIndex && !moving -> "LAST OPENED"
                        else -> "TAP TO OPEN"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (moving) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (onVaultSpine && vaultUnlocked) "Lock vault" else activeShelf.name,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val menuShown = onShelfMenu != null && !moving &&
                        (activeShelf.kind == Shelf.Kind.Normal || activeShelf.kind == Shelf.Kind.Hidden)
                    if (menuShown) {
                        IconButton(onClick = { onShelfMenu?.invoke(active) }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.MoreVert,
                                contentDescription = "Shelf options for ${activeShelf.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        onVaultSpine && vaultUnlocked -> "Hidden shelves are showing"
                        onVaultSpine -> "Tap to unlock with fingerprint or PIN"
                        activeShelf.kind == Shelf.Kind.Smart ->
                            if (activeShelf.count ==
                                1
                            ) {
                                "1 title · smart shelf"
                            } else {
                                "${activeShelf.count} titles · smart shelf"
                            }
                        else -> buildString {
                            append(if (activeShelf.count == 1) "1 title" else "${activeShelf.count} titles")
                            if (activeShelf.unread > 0) append(" · ${activeShelf.unread} unread")
                            if (activeShelf.hasUpdates) append(" · new chapters")
                            if (activeShelf.kind == Shelf.Kind.Hidden) append(" · in the vault")
                        }
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
                    // Clip at the screen edge, not the inner padding, so spines slide off naturally
                    .clipToBounds()
                    .semantics { contentDescription = "Bookshelf. Drag to move along it, tap a shelf to open it" }
                    .scrollable(scrollState, Orientation.Horizontal)
                    .pointerInput(shelves, minOffset, maxOffset) {
                        detectTapGestures { position ->
                            val i = indexAt(position.x - sidePx + offset) ?: return@detectTapGestures
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (shelves[i].kind == Shelf.Kind.Vault) onVault() else onOpen(i)
                        }
                    },
            ) {
                shelves.forEachIndexed { i, shelf ->
                    val on = i == active
                    val lift by animateDpAsState(
                        targetValue = when {
                            on && moving -> 16.dp
                            on -> 8.dp
                            else -> 0.dp
                        },
                        label = "lift",
                    )
                    val alpha by animateFloatAsState(if (on) 1f else 0.82f, label = "alpha")
                    Spine(
                        name = if (shelf.kind == Shelf.Kind.Vault && vaultUnlocked) "Lock" else shelf.name,
                        count = shelf.count,
                        color = colors[shelf.key] ?: fallbackColor(shelf.key),
                        kind = shelf.kind,
                        hasUpdates = shelf.hasUpdates,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset((sidePx + lefts[i] - offset).roundToInt(), 0) }
                            .width(with(density) { widthsPx[i].toDp() })
                            .height(shelfHeight)
                            .graphicsLayer {
                                translationY = -lift.toPx()
                                this.alpha = alpha
                            }
                            .clearAndSetSemantics {
                                contentDescription = "${shelf.name}, ${shelf.count} " +
                                    (if (shelf.count == 1) "title" else "titles") +
                                    if (shelf.hasUpdates) ", new chapters" else ""
                                role = Role.Button
                                onClick {
                                    if (shelf.kind == Shelf.Kind.Vault) onVault() else onOpen(i)
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

            if (!fits) {
                val thumbFraction = (viewportPx / totalPx).coerceIn(0.12f, 1f)
                val scrolled = ((offset - minOffset) / (maxOffset - minOffset)).coerceIn(0f, 1f)
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
                            .offset(x = (120.dp * (1f - thumbFraction)) * scrolled)
                            .width(120.dp * thumbFraction)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
                    )
                }
            }

            Text(
                text = "Drag to move along your shelf · tap one to open it",
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
    kind: Shelf.Kind = Shelf.Kind.Normal,
    hasUpdates: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.secondary
    val isVault = kind == Shelf.Kind.Vault
    val isSmart = kind == Shelf.Kind.Smart
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                when {
                    isVault -> MaterialTheme.colorScheme.surfaceContainerHigh
                    isSmart -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> color
                },
            )
            .drawWithContent {
                drawContent()
                // New chapters since last read: a short coral bar across the top of the spine
                if (hasUpdates) {
                    val barW = size.width * 0.5f
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset((size.width - barW) / 2, 5.dp.toPx()),
                        size = Size(barW, 4.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
                // Vault: dashed coral outline. Hidden shelves: solid coral outline.
                if (kind == Shelf.Kind.Vault || kind == Shelf.Kind.Hidden) {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = if (isVault) 0.7f else 0.9f),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(
                            width = stroke,
                            pathEffect = if (isVault) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                        ),
                    )
                }
            }
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
                    color = when {
                        isVault -> accent
                        isSmart -> primary
                        else -> Color.White
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.verticalText(),
                )
            }
        }
        if (showText && isSmart) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Sparkle,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(16.dp),
            )
            Text(text = count.toString(), color = primary, style = MaterialTheme.typography.labelSmall)
        } else if (showText && isVault) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        } else if (showText) {
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
