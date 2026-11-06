package eu.kanade.tachiyomi.ui.reader.viewer.book

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.BookPageLayout
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageError
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionPage
import eu.kanade.tachiyomi.ui.reader.viewer.mouseWheelPaging
import eu.kanade.tachiyomi.ui.reader.viewer.transitionDownloadState
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.CombinedCircularProgressIndicator
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun BookContent(viewer: BookViewer, modifier: Modifier) {
    val pageLayout by viewer.preferences.bookPageLayout.collectAsState()
    val firstPageAlone by viewer.preferences.bookFirstPageAlone.collectAsState()
    val shadows by viewer.preferences.bookPageShadows.collectAsState()
    val general by viewer.settingsViewModel.general.collectAsState()
    val context = LocalContext.current
    val background = remember(general.theme, context) { Color(context.readerBackgroundColor(general.theme)) }
    val hinges = currentWindowAdaptiveInfoV2().windowPosture.hingeList
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val paints = remember { CurlPaints() }
    val layers = remember { mutableStateMapOf<Any, GraphicsLayer>() }
    val zoom = remember { BookZoom() }

    LaunchedEffect(viewer) {
        for (forward in viewer.turnRequests) viewer.playTurn(forward)
    }
    LaunchedEffect(viewer) {
        snapshotFlow { viewer.spreads.getOrNull(viewer.currentIndex) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { if (viewer.turn == null) viewer.onSpreadShown(it) }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(background)
            .clipToBounds()
            .onGloballyPositioned { windowOffset = it.positionInWindow() },
    ) {
        val area = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        if (area.width <= 0f || area.height <= 0f) return@BoxWithConstraints

        // A fold down the middle of the window, as on an unfolded foldable held like a book.
        val hinge = hinges.firstOrNull { it.isVertical }
            ?.takeIf {
                val center = it.bounds.center.x - windowOffset.x
                center > area.width * 0.3f && center < area.width * 0.7f
            }
        val wideScreen = with(LocalDensity.current) { area.width.toDp() >= 600.dp }
        val double = when (pageLayout) {
            BookPageLayout.SINGLE -> false
            BookPageLayout.DOUBLE -> true
            BookPageLayout.AUTOMATIC ->
                hinge != null || area.width >= area.height || (wideScreen && area.width >= area.height * 0.8f)
        }
        val spine = when {
            hinge == null -> area.width / 2f..area.width / 2f
            // Two screens with the hinge between them: the pages go either side of it.
            hinge.isOccluding -> (hinge.bounds.left - windowOffset.x)..(hinge.bounds.right - windowOffset.x)
            else -> (hinge.bounds.center.x - windowOffset.x).let { it..it }
        }

        SideEffect {
            viewer.isDouble = double
            viewer.firstPageAlone = firstPageAlone
            viewer.images.viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        }

        val placer = SpreadPlacer(area, spine, viewer.isRightToLeft) { viewer.images.sizes[it] }
        val spreads = viewer.spreads
        val index = viewer.currentIndex
        val turn = viewer.turn

        LaunchedEffect(index, spreads) {
            val pages = (index - 2..index + 3)
                .flatMap { spreads.getOrNull(it)?.items.orEmpty() }
                .filterIsInstance<ReaderPage>()
                .toSet()
            viewer.images.keep(pages)
        }

        // The spread shown and its neighbors, so a turn either way has both sides drawn from its first frame.
        val composedSpreads = buildSet {
            add(index - 1)
            add(index)
            add(index + 1)
            turn?.let {
                add(it.from)
                add(it.to)
            }
        }
        val placements: Map<Any, Rect> = buildMap {
            composedSpreads.sorted().forEach { i -> spreads.getOrNull(i)?.let { putAll(placer.place(it)) } }
        }
        val shownItems = spreads.getOrNull(index)?.items.orEmpty()
        LaunchedEffect(shownItems) { zoom.reset() }

        val currentPlacer by rememberUpdatedState(placer)
        val currentArea by rememberUpdatedState(area)
        val currentPlacements by rememberUpdatedState(placements)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .mouseWheelPaging(viewer)
                .pointerInput(viewer) {
                    detectTapGestures(
                        onTap = { position ->
                            if (viewer.turn == null && !viewer.buttonPressed) {
                                viewer.activity.onViewerTap(
                                    Offset(position.x / size.width, position.y / size.height),
                                )
                            }
                        },
                        onDoubleTap = { position ->
                            if (viewer.turn != null || viewer.buttonPressed) return@detectTapGestures
                            if (zoom.isZoomed) zoom.reset() else zoom.zoomAround(position, DOUBLE_TAP_ZOOM, currentArea)
                        },
                        onLongPress = { position ->
                            val contentPosition = zoom.toContent(position, currentArea)
                            val shown = viewer.spreads.getOrNull(viewer.currentIndex)?.items.orEmpty()
                            val page = currentPlacements.entries
                                .firstOrNull { (item, rect) -> item in shown && rect.contains(contentPosition) }
                                ?.key as? ReaderPage
                            if (page != null &&
                                (viewer.activity.viewModel.state.value.menuVisible || viewer.general.longTapEnabled)
                            ) {
                                viewer.activity.onPageLongTap(page)
                            }
                        },
                    )
                }
                .pointerInput(viewer) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (viewer.turn != null || viewer.buttonPressed) return@awaitEachGesture
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        var mode = DragMode.Undecided
                        var total = Offset.Zero
                        var dragBase = 0f
                        var dragWidth = 1f
                        var dragForward = true
                        var movedX = 0f
                        val direction = if (viewer.isRightToLeft) -1f else 1f
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size > 1 && mode != DragMode.Turn) {
                                    mode = DragMode.Zoom
                                    zoom.zoomAround(
                                        focus = event.calculateCentroid(),
                                        newScale = zoom.scale * event.calculateZoom(),
                                        area = currentArea,
                                        panBy = event.calculatePan(),
                                    )
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                val change = pressed.first()
                                velocity.addPointerInputChange(change)
                                if (mode == DragMode.Undecided) {
                                    total += change.positionChange()
                                    if (total.getDistance() > viewConfiguration.touchSlop) {
                                        mode = when {
                                            zoom.isZoomed -> DragMode.Pan
                                            abs(total.x) > abs(total.y) -> {
                                                val forward = total.x * direction < 0f
                                                val next = viewer.turnFrom(forward)
                                                val leaf = next?.let { leafOf(it, viewer, currentPlacer) }
                                                if (next != null && leaf != null) {
                                                    dragBase = if (forward) 0f else 1f
                                                    dragWidth = leaf.width.coerceAtLeast(1f)
                                                    dragForward = forward
                                                    val top = down.position.y < leaf.rect.center.y
                                                    viewer.beginTurn(next, forward, top)
                                                    DragMode.Turn
                                                } else {
                                                    DragMode.Ignore
                                                }
                                            }
                                            else -> DragMode.Ignore
                                        }
                                    }
                                }
                                when (mode) {
                                    DragMode.Turn -> {
                                        val moved = change.position - down.position
                                        movedX = moved.x
                                        val turned = dragBase - direction * moved.x / (2f * dragWidth)
                                        viewer.progress = turned.coerceIn(0f, 1f)
                                        viewer.cornerDy = moved.y * CORNER_FOLLOW
                                        change.consume()
                                    }
                                    DragMode.Pan -> {
                                        zoom.panBy(change.positionChange(), currentArea)
                                        change.consume()
                                    }
                                    DragMode.Ignore -> change.consume()
                                    DragMode.Undecided, DragMode.Zoom -> {}
                                }
                            }
                            if (mode == DragMode.Turn) {
                                val turnsPerSecond = -direction * velocity.calculateVelocity().x / (2f * dragWidth)
                                // How far the finger went the way the turn goes, of the screen's width
                                val towardTurn = if (dragForward) -direction * movedX else direction * movedX
                                val travel = towardTurn / currentArea.width
                                released = true
                                scope.launch { viewer.releaseTurn(turnsPerSecond, travel) }
                            }
                        } finally {
                            // The gesture was cut short: let the leaf fall rather than leave it hanging.
                            if (mode == DragMode.Turn && !released) scope.launch { viewer.releaseTurn(0f) }
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.pan.x
                        translationY = zoom.pan.y
                    },
            ) {
                placements.forEach { (item, rect) ->
                    key(item) {
                        BookSlot(
                            viewer = viewer,
                            item = item,
                            rect = rect,
                            layers = layers,
                            drawDirectly = turn == null && item in shownItems,
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val activeTurn = viewer.turn
                            if (activeTurn == null) {
                                val spread = viewer.spreads.getOrNull(viewer.currentIndex) as? Spread.Facing
                                    ?: return@drawBehind
                                drawGutters(facingGutters(spread, currentPlacer, viewer.isRightToLeft), shadows)
                                return@drawBehind
                            }
                            val scene = turnScene(
                                turn = activeTurn,
                                spreads = viewer.spreads,
                                placer = currentPlacer,
                                isRightToLeft = viewer.isRightToLeft,
                                layers = layers,
                            ) ?: return@drawBehind
                            val corner = cornerPosition(scene.leaf, viewer.progress, viewer.cornerDy, viewer.fromTop)
                            drawTurn(scene, corner, viewer.fromTop, viewer.progress, PaperColor, shadows, paints)
                        },
                )
            }
        }
    }
}

/**
 * Composes [item] at [rect] into a layer, which it draws itself when [drawDirectly] and a turn draws otherwise.
 */
@Composable
private fun BookSlot(
    viewer: BookViewer,
    item: Any,
    rect: Rect,
    layers: MutableMap<Any, GraphicsLayer>,
    drawDirectly: Boolean,
) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(item, layer) {
        layers[item] = layer
        onDispose { if (layers[item] === layer) layers.remove(item) }
    }
    Box(
        modifier = Modifier
            // Out of reach of touches unless shown, so a page kept ready for a turn can't be pressed.
            .placeAt(rect, offscreen = !drawDirectly)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                if (drawDirectly) drawLayer(layer)
            },
    ) {
        when (item) {
            is ReaderPage -> BookPage(viewer, item)
            is ChapterTransition -> BookTransition(viewer, item)
        }
    }
}

@Composable
private fun BookPage(viewer: BookViewer, page: ReaderPage) {
    val bitmap = viewer.images.bitmaps[page]
    val decodeError = viewer.images.errors[page]
    val status by page.statusFlow.collectAsState()
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = LocalPageColorFilter.current,
            filterQuality = FilterQuality.High,
        )
        decodeError != null || status is Page.State.Error -> ReaderPageError(
            page = page,
            error = decodeError ?: (status as? Page.State.Error)?.error,
            sourceId = viewer.activity.viewModel.manga?.source,
            modifier = Modifier.fillMaxSize(),
            onButtonPressChange = viewer::onButtonPressChange,
        )
        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val progress by page.progressFlow.collectAsState()
            CombinedCircularProgressIndicator(progress = { progress / 100f })
        }
    }
}

@Composable
private fun BookTransition(viewer: BookViewer, transition: ChapterTransition) {
    val manga = viewer.activity.viewModel.manga
    val source = viewer.activity.viewModel.source
    val downloadState = remember(transition, manga, source) {
        viewer.downloadManager.transitionDownloadState(transition, manga, source)
    }
    LaunchedEffect(downloadState) { downloadState?.checkDisk() }
    ReaderTransitionPage(
        transition = transition,
        downloadState = downloadState,
        onRetry = viewer.activity::requestPreloadChapter,
        onButtonPressChange = viewer::onButtonPressChange,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
    )
}

/**
 * What a turn between the spreads of [turn] draws, with the items' layers where they're composed.
 */
private fun turnScene(
    turn: Turn,
    spreads: List<Spread>,
    placer: SpreadPlacer,
    isRightToLeft: Boolean,
    layers: Map<Any, GraphicsLayer>,
): TurnScene? {
    val a = spreads.getOrNull(turn.from) ?: return null
    val b = spreads.getOrNull(turn.to) ?: return null
    val rectsA = placer.place(a)
    val rectsB = placer.place(b)
    fun layerAt(item: Any?, rects: Map<Any, Rect>): LayerAt? {
        item ?: return null
        val layer = layers[item] ?: return null
        val rect = rects[item] ?: return null
        return LayerAt(layer, rect)
    }
    // Pages of a book bound on the left turn toward the left.
    val towardLeft = !isRightToLeft

    if (a is Spread.Facing && b is Spread.Facing) {
        val leafPage = if (towardLeft) a.right(isRightToLeft) else a.left(isRightToLeft)
        val backPage = if (towardLeft) b.left(isRightToLeft) else b.right(isRightToLeft)
        if (leafPage != null && backPage != null) {
            val facingPage = if (towardLeft) a.left(isRightToLeft) else a.right(isRightToLeft)
            val underPage = if (towardLeft) b.right(isRightToLeft) else b.left(isRightToLeft)
            val leafRect = rectsA.getValue(leafPage)
            return TurnScene(
                leaf = Leaf(leafRect, towardLeft),
                bookStyle = true,
                stationary = listOfNotNull(layerAt(facingPage, rectsA)),
                front = listOfNotNull(layerAt(leafPage, rectsA)),
                under = listOfNotNull(layerAt(underPage, rectsB)),
                back = listOfNotNull(layerAt(backPage, rectsB)),
                gutters = buildList {
                    facingPage?.let { rectsA[it] }?.let { add(it to towardLeft) }
                    add(leafRect to !towardLeft)
                },
            )
        }
    }

    // Anything else turns over whole, like a single sheet.
    val rects = rectsA.values
    if (rects.isEmpty()) return null
    val bounds = Rect(
        left = rects.minOf { it.left },
        top = rects.minOf { it.top },
        right = rects.maxOf { it.right },
        bottom = rects.maxOf { it.bottom },
    )
    return TurnScene(
        leaf = Leaf(bounds, towardLeft),
        bookStyle = false,
        stationary = emptyList(),
        front = a.items.mapNotNull { layerAt(it, rectsA) },
        under = b.items.mapNotNull { layerAt(it, rectsB) },
        back = emptyList(),
        gutters = emptyList(),
    )
}

/**
 * The leaf a drag would turn over for [turn].
 */
private fun leafOf(turn: Turn, viewer: BookViewer, placer: SpreadPlacer): Leaf? =
    turnScene(turn, viewer.spreads, placer, viewer.isRightToLeft, emptyMap())?.leaf

private fun facingGutters(
    spread: Spread.Facing,
    placer: SpreadPlacer,
    isRightToLeft: Boolean,
): List<Pair<Rect, Boolean>> {
    val rects = placer.place(spread)
    return buildList {
        spread.left(isRightToLeft)?.let { rects[it] }?.let { add(it to true) }
        spread.right(isRightToLeft)?.let { rects[it] }?.let { add(it to false) }
    }
}

/**
 * Lays the content out at [rect] of its parent, whatever the parent's constraints, or as large far off to the side
 * when [offscreen], where it still draws into its layer.
 */
private fun Modifier.placeAt(rect: Rect, offscreen: Boolean = false): Modifier = layout { measurable, constraints ->
    val width = rect.width.roundToInt().coerceAtLeast(0)
    val height = rect.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = rect.left.roundToInt() + if (offscreen) OFFSCREEN_X else 0
        placeable.place(x, rect.top.roundToInt())
    }
}

/**
 * Pinch and double tap zoom of the whole spread, which turns no pages while zoomed.
 */
@Stable
private class BookZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > 1.01f

    fun reset() {
        scale = 1f
        pan = Offset.Zero
    }

    /**
     * Zooms to [newScale] keeping what's under [focus] there, then moves by [panBy].
     */
    fun zoomAround(focus: Offset, newScale: Float, area: Size, panBy: Offset = Offset.Zero) {
        val target = newScale.coerceIn(1f, MAX_ZOOM)
        val center = area.center
        val content = (focus - center - pan) / scale
        pan = clamp(focus - center - content * target + panBy, target, area)
        scale = target
    }

    fun panBy(delta: Offset, area: Size) {
        pan = clamp(pan + delta, scale, area)
    }

    /** Where [position] on screen is in the unzoomed spread. */
    fun toContent(position: Offset, area: Size): Offset = (position - area.center - pan) / scale + area.center

    private fun clamp(offset: Offset, scale: Float, area: Size): Offset {
        val maxX = (scale - 1f) * area.width / 2f
        val maxY = (scale - 1f) * area.height / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

private enum class DragMode { Undecided, Turn, Pan, Zoom, Ignore }

/** The back of a leaf with nothing printed on it. */
private val PaperColor = Color(0xFFF4F2ED)

/** Where pages kept ready for a turn are laid out, well clear of the screen. */
private const val OFFSCREEN_X = 100_000

private const val DOUBLE_TAP_ZOOM = 2f
private const val MAX_ZOOM = 5f

/** How much of the finger's vertical movement the turned corner follows. */
private const val CORNER_FOLLOW = 0.5f
