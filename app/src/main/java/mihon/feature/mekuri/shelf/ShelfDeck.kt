package mihon.feature.mekuri.shelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.manga.components.MangaCover
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Lock
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Sparkle
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.manga.model.Manga
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mekuri: what the Continue reading card shows. */
data class ResumeInfo(
    val manga: Manga,
    val readCount: Long,
    val totalChapters: Long,
    val lastReadLabel: String,
)

/**
 * Mekuri: the library home. A compact Continue reading card, then the shelves as a 3D deck of cards.
 *
 * Drag to move along the deck; a hard flick carries on through several shelves (or to the end) and settles on one.
 * Tap a card to open that shelf. The last card is the vault.
 */
@Composable
fun ShelfDeck(
    shelves: List<Shelf>,
    lastOpenedIndex: Int,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    vaultUnlocked: Boolean = false,
    onVault: () -> Unit = {},
    onShelfMenu: ((Int) -> Unit)? = null,
    resume: ResumeInfo? = null,
    onResume: () -> Unit = {},
    onHistory: () -> Unit = {},
) {
    if (shelves.isEmpty()) return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = rememberShelfColors(shelves)
    val last = shelves.lastIndex
    val start = lastOpenedIndex.coerceIn(0, last)

    val position = remember { Animatable(start.toFloat()) }
    LaunchedEffect(start, shelves.size) {
        if (!position.isRunning) position.snapTo(start.toFloat())
    }
    val focused = position.value.roundToInt().coerceIn(0, last)
    LaunchedEffect(focused) { if (position.isRunning) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val focusedShelf = shelves[focused]
    val glow by animateColorAsState(
        if (focusedShelf.kind ==
            Shelf.Kind.Vault
        ) {
            Color(0xFFFF6B7D)
        } else {
            colors[focusedShelf.key] ?: fallbackColor(focusedShelf.key)
        },
        label = "glow",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (resume != null) {
            ResumeCard(resume = resume, onResume = onResume, onHistory = onHistory)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your shelves",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Swipe · tap to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val menuShown = onShelfMenu != null &&
                (focusedShelf.kind == Shelf.Kind.Normal || focusedShelf.kind == Shelf.Kind.Hidden)
            IconButton(enabled = menuShown, onClick = { onShelfMenu?.invoke(focused) }) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.MoreVert,
                    contentDescription = "Options for ${focusedShelf.name}",
                    tint = if (menuShown) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val stepPx = with(density) { 138.dp.toPx() }
            val cardHeight = (maxHeight - 40.dp).coerceIn(220.dp, 320.dp)
            val cardWidth = cardHeight * 0.68f
            val centerX = constraints.maxWidth / 2f

            // Glow in the focused shelf's colour
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(440.dp)
                    .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.3f), Color.Transparent))),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(shelves.size, stepPx) {
                        val tracker = VelocityTracker()
                        val decay = exponentialDecay<Float>(frictionMultiplier = 0.55f)
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                scope.launch { position.stop() }
                            },
                            onHorizontalDrag = { change, dx ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                scope.launch {
                                    position.snapTo((position.value - dx / stepPx).coerceIn(-0.35f, last + 0.35f))
                                }
                            },
                            onDragEnd = {
                                // Cards per second; a hard flick travels several cards before settling
                                val velocity = -tracker.calculateVelocity().x / stepPx
                                val target = decay.calculateTargetValue(position.value, velocity)
                                    .roundToInt()
                                    .coerceIn(0, last)
                                scope.launch {
                                    position.animateTo(
                                        targetValue = target.toFloat(),
                                        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow),
                                        initialVelocity = velocity,
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    position.animateTo(position.value.roundToInt().coerceIn(0, last).toFloat())
                                }
                            },
                        )
                    }
                    .pointerInput(shelves.size, stepPx) {
                        detectTapGestures { tap ->
                            val relative = (tap.x - centerX) / stepPx
                            val i = (position.value + relative).roundToInt()
                            if (i !in 0..last) return@detectTapGestures
                            val cardLeft = centerX + (i - position.value) * stepPx - cardWidth.toPx() / 2
                            if (tap.x < cardLeft || tap.x > cardLeft + cardWidth.toPx()) return@detectTapGestures
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (shelves[i].kind == Shelf.Kind.Vault) onVault() else onOpen(i)
                        }
                    },
            ) {
                shelves.forEachIndexed { i, shelf ->
                    val p = i - position.value
                    if (abs(p) > 3.2f) return@forEachIndexed
                    val ap = abs(p)
                    val lift by animateDpAsState(if (i == focused) 0.dp else 6.dp, label = "lift")
                    DeckCard(
                        shelf = shelf,
                        color = colors[shelf.key] ?: fallbackColor(shelf.key),
                        vaultUnlocked = vaultUnlocked,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset((p * stepPx).roundToInt(), lift.roundToPx()) }
                            .size(cardWidth, cardHeight)
                            .zIndex(10f - ap)
                            .graphicsLayer {
                                rotationY = p * -9f
                                cameraDistance = 14f * density.density
                                val scale = (1f - ap * 0.16f).coerceAtLeast(0.62f)
                                scaleX = scale
                                scaleY = scale
                                alpha = (1f - ap * 0.38f).coerceAtLeast(0.12f)
                            }
                            .clearAndSetSemantics {
                                contentDescription = shelf.name + if (shelf.kind == Shelf.Kind.Vault) {
                                    ""
                                } else {
                                    ", ${shelf.count} " + (if (shelf.count == 1) "title" else "titles") +
                                        if (shelf.hasUpdates) ", new this week" else ""
                                }
                                role = Role.Button
                                onClick {
                                    if (shelf.kind == Shelf.Kind.Vault) onVault() else onOpen(i)
                                    true
                                }
                            },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                shelves.forEachIndexed { i, shelf ->
                    val on = i == focused
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (on) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    on -> MaterialTheme.colorScheme.onSurface
                                    shelf.hasUpdates -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(resume: ResumeInfo, onResume: () -> Unit, onHistory: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                data = resume.manga,
                modifier = Modifier
                    .width(42.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CONTINUE READING",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) { detectTapGestures { onHistory() } }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = resume.manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Ch. ${resume.readCount} of ${resume.totalChapters} · ${resume.lastReadLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                LinearProgressIndicator(
                    progress = {
                        if (resume.totalChapters <=
                            0
                        ) {
                            0f
                        } else {
                            (resume.readCount.toFloat() / resume.totalChapters).coerceIn(0f, 1f)
                        }
                    },
                    color = scheme.secondary,
                    trackColor = scheme.onSurface.copy(alpha = 0.1f),
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            FilledTonalIconButton(onClick = onResume) {
                Icon(
                    imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                    contentDescription = "Resume ${resume.manga.title}",
                )
            }
        }
    }
}

@Composable
private fun DeckCard(shelf: Shelf, color: Color, vaultUnlocked: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isVault = shelf.kind == Shelf.Kind.Vault
    val isSmart = shelf.kind == Shelf.Kind.Smart
    val accent = scheme.secondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                when {
                    isVault -> scheme.surfaceContainerHigh
                    isSmart -> scheme.surfaceContainerHighest
                    else -> color
                },
            )
            .drawWithContent {
                drawContent()
                if (isVault || shelf.kind == Shelf.Kind.Hidden) {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = if (isVault) 0.7f else 0.9f),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(
                            width = stroke,
                            pathEffect = if (isVault) PathEffect.dashPathEffect(floatArrayOf(16f, 12f)) else null,
                        ),
                    )
                }
            },
    ) {
        if (isVault) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    MaterialSymbols.Rounded.Lock,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    "Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    text = if (vaultUnlocked) "Open · tap to lock" else "Tap to unlock",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            return@Box
        }

        // A little fan of the shelf's covers
        val covers = shelf.covers.ifEmpty { listOfNotNull(shelf.coverManga) }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
        ) {
            covers.getOrNull(2)?.let {
                MangaCover.Book(
                    data = it,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 18.dp)
                        .width(62.dp)
                        .graphicsLayer {
                            rotationZ = -7f
                            alpha = 0.9f
                        }
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            covers.getOrNull(1)?.let {
                MangaCover.Book(
                    data = it,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 14.dp, top = 18.dp)
                        .width(62.dp)
                        .graphicsLayer {
                            rotationZ = 7f
                            alpha = 0.9f
                        }
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            covers.getOrNull(0)?.let {
                MangaCover.Book(
                    data = it,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(78.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            if (covers.isEmpty() && isSmart) {
                Icon(
                    MaterialSymbols.Rounded.Sparkle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 30.dp)
                        .size(40.dp),
                )
            }
        }

        if (shelf.hasUpdates) {
            Text(
                text = "New",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.secondary)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = shelf.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSmart) scheme.primary else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (shelf.count == 1) "1 title" else "${shelf.count} titles")
                    if (shelf.unread > 0) append(" · ${shelf.unread} unread")
                    if (shelf.kind == Shelf.Kind.Hidden) append(" · vault")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isSmart) scheme.onSurfaceVariant else Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
