package eu.kanade.tachiyomi.ui.reader.translation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.translation.model.NormalizedRect
import tachiyomi.translation.model.TranslationEntry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transparent layer over the page image.
 *
 * Gesture priority while translating: pinch zooms (never consumed here), a tap on a detected
 * region turns it into an entry, a tap on empty space does nothing, a drag past the touch slop
 * draws a new box, a long press inside an existing box moves it, and the small cross at a box's
 * top-right corner deletes it.
 *
 * Taps are deliberately not forwarded to the viewer any more: turning pages by tapping fought with
 * the scanner and made the overlay swallow or double-handle presses. While translating, page turns
 * belong to the volume keys / d-pad.
 */
@Composable
fun TranslationOverlay(
    controller: TranslationController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val pageIndex = controller.currentPageIndex()
    val frame = TranslationGeometry.frame(pageIndex)
    val geometry = frame?.rect
    val pageSize = frame?.let { it.sourceWidth to it.sourceHeight } ?: controller.pageImageSize()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var draft by remember { mutableStateOf<Rect?>(null) }
    var interacting by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Box positions come from live view reads, which are invisible to Compose: a plain webtoon
    // scroll publishes nothing, so without this per-frame tick the boxes froze mid-scroll and
    // drifted away from the art. Reading the tick inside the draw scopes redraws every frame
    // while the panel is open.
    val geometryTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(state.panelVisible) {
        if (!state.panelVisible) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            geometryTick.intValue++
        }
    }
    val densityScale = LocalDensity.current.density
    val deleteRadius = DELETE_RADIUS_DP * densityScale
    val crossHalf = CROSS_HALF_DP * densityScale

    // Cheap enough to call per gesture and per frame: it just asks the page view for its rect.
    fun liveArea(content: Size): Rect {
        val live = TranslationGeometry.read(pageIndex)
        // Falling back to the raw file size on a long strip produced a sliver of an area, which is
        // how manual boxes ended up as a thin line somewhere else on the page.
        val fallback = pageSize?.takeIf { it.first > 0 && it.second <= it.first * PAGE_LIKE_RATIO }
        return imageArea(
            content,
            live?.rect ?: geometry,
            origin,
            live?.let { it.sourceWidth to it.sourceHeight } ?: fallback,
        )
    }

    /** Area for an entry's own page; entries are page-normalized, not viewport-normalized. */
    fun areaForPage(index: Int, content: Size): Rect {
        if (index == pageIndex) return liveArea(content)
        val frame = TranslationGeometry.read(index) ?: return Rect.Zero
        return imageArea(content, frame.rect, origin, frame.sourceWidth to frame.sourceHeight)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        interacting = event.changes.size > 1
                    }
                }
            }
            .pointerInput(state.panelVisible, pageSize, geometry, origin) {
                if (!state.panelVisible) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (currentEvent.changes.size > 1) return@awaitEachGesture

                    val content = Size(size.width.toFloat(), size.height.toFloat())

                    // Every hit test resolves the entry against ITS OWN page area: in the webtoon
                    // reader the boxes at the viewport's top and bottom belong to the neighbouring
                    // pages, and testing them against the current page's area made them dead.
                    val deleteId = hitDeleteCross(down.position, state.entries, content, ::areaForPage, deleteRadius)
                    if (deleteId != null) {
                        down.consume()
                        waitForUpOrCancellation()
                        controller.delete(deleteId)
                        return@awaitEachGesture
                    }

                    val boxId = hitBox(down.position, state.entries, content, ::areaForPage)
                    if (boxId != null) {
                        // Taking the press here keeps the reader's long-press menu out of the way.
                        down.consume()
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            val movedEntry = state.entries.firstOrNull { it.id == boxId }
                            val moveArea = movedEntry?.let { areaForPage(it.pageIndex, content) }
                                ?.takeIf { it.width > 0f && it.height > 0f }
                            if (moveArea != null) {
                                var last = longPress.position
                                drag(longPress.id) { change ->
                                    val delta = change.position - last
                                    last = change.position
                                    controller.moveBox(boxId, delta.x / moveArea.width, delta.y / moveArea.height)
                                    change.consume()
                                }
                            }
                        }
                        return@awaitEachGesture
                    }

                    // Empty space: stay passive until the finger clearly drags, so a tap on a
                    // detected region can be handled and everything else is left alone.
                    val pressedAt = android.os.SystemClock.uptimeMillis()
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        val held = android.os.SystemClock.uptimeMillis() - pressedAt
                        if (held < viewConfiguration.longPressTimeoutMillis) {
                            // Regions are normalized per page, so the tap is resolved against the
                            // page under the finger, not whatever page is "current".
                            val tapPage = TranslationGeometry.pageAtWindow(
                                origin.x + down.position.x,
                                origin.y + down.position.y,
                                pageIndex,
                            )
                            val tapArea = areaForPage(
                                tapPage,
                                Size(size.width.toFloat(), size.height.toFloat()),
                            )
                            if (tapArea.width > 0f && tapArea.height > 0f) {
                                controller.tapRegion(
                                    ((down.position.x - tapArea.left) / tapArea.width).coerceIn(0f, 1f),
                                    ((down.position.y - tapArea.top) / tapArea.height).coerceIn(0f, 1f),
                                    tapPage,
                                )
                            }
                        }
                        return@awaitEachGesture
                    }

                    var current = slop.position
                    draft = Rect(current, current)
                    var cancelled = false

                    drag(down.id) { change ->
                        if (currentEvent.changes.size > 1) {
                            cancelled = true
                            return@drag
                        }
                        current = change.position
                        draft = Rect(
                            min(slop.position.x, current.x),
                            min(slop.position.y, current.y),
                            max(slop.position.x, current.x),
                            max(slop.position.y, current.y),
                        )
                        change.consume()
                    }

                    val rect = draft
                    draft = null
                    if (cancelled || rect == null) return@awaitEachGesture

                    // The box may straddle two webtoon pages; it belongs to the page under its
                    // centre, which is also the page whose image has to be cropped for OCR.
                    // Only the current window exists for the controller; anything further away
                    // would create an entry that never shows up in the panel.
                    val targetPage = TranslationGeometry.pageAtWindow(
                        origin.x + rect.center.x,
                        origin.y + rect.center.y,
                        pageIndex,
                    ).takeIf { kotlin.math.abs(it - pageIndex) <= 1 } ?: pageIndex
                    val targetArea = areaForPage(targetPage, Size(size.width.toFloat(), size.height.toFloat()))
                    val boxArea = if (targetArea.width > 0f && targetArea.height > 0f) {
                        targetArea
                    } else {
                        liveArea(Size(size.width.toFloat(), size.height.toFloat()))
                    }
                    if (boxArea.width <= 0f || boxArea.height <= 0f) return@awaitEachGesture

                    val normalized = NormalizedRect(
                        left = ((rect.left - boxArea.left) / boxArea.width).coerceIn(0f, 1f),
                        top = ((rect.top - boxArea.top) / boxArea.height).coerceIn(0f, 1f),
                        right = ((rect.right - boxArea.left) / boxArea.width).coerceIn(0f, 1f),
                        bottom = ((rect.bottom - boxArea.top) / boxArea.height).coerceIn(0f, 1f),
                    ).clampToUnit()

                    // Split the drawn box by the page areas it covers. A bubble straddling a
                    // strip page boundary must be OCR-read from BOTH pages and joined, or half
                    // of the dialogue is lost for good.
                    val parts = ArrayList<Pair<Int, NormalizedRect>>()
                    for (candidate in (targetPage - 1)..(targetPage + 1)) {
                        if (candidate < 0) continue
                        val pageArea = areaForPage(candidate, content)
                            .takeIf { it.width > 0f && it.height > 0f } ?: continue
                        val l = maxOf(rect.left, pageArea.left)
                        val t = maxOf(rect.top, pageArea.top)
                        val r = minOf(rect.right, pageArea.right)
                        val b = minOf(rect.bottom, pageArea.bottom)
                        if (r <= l || b <= t) continue
                        // Slivers on the neighbouring page are noise; a real straddle covers it.
                        val draftArea = (rect.right - rect.left) * (rect.bottom - rect.top)
                        if (candidate != targetPage && (r - l) * (b - t) < draftArea * 0.08f) continue
                        parts.add(
                            candidate to NormalizedRect(
                                left = ((l - pageArea.left) / pageArea.width).coerceIn(0f, 1f),
                                top = ((t - pageArea.top) / pageArea.height).coerceIn(0f, 1f),
                                right = ((r - pageArea.left) / pageArea.width).coerceIn(0f, 1f),
                                bottom = ((b - pageArea.top) / pageArea.height).coerceIn(0f, 1f),
                            ).clampToUnit(),
                        )
                    }

                    // No minimum size: the user's box is the only signal we have, and a hard
                    // threshold rejected legitimate small bubbles.
                    if (parts.size >= 2) {
                        controller.addStraddlingBox(parts)
                    } else if (normalized.area >= FULL_PAGE_RATIO) {
                        controller.showNotice("请分别框选气泡")
                    } else {
                        controller.addBox(normalized, targetPage)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw-phase read: invalidates this canvas every frame while the panel is open.
            val tick = geometryTick.intValue
            if (interacting) return@Canvas
            // The anchor follows the reader's own page callbacks (ReaderActivity feeds
            // onPageChanged). The overlay used to sync it per frame to the page owning the
            // viewport's top edge, but the reader already reports a center page whose ±1 scan
            // window covers that edge — two sources of truth only made the anchor ping-pong
            // between windows, which flashed earlier pages' boxes over the page on screen.

            // Ask the page view for its rect instead of trusting the last published one: the
            // image moves the moment the panel takes space, before any layout callback lands.
            val area = liveArea(size)
            if (area.width <= 0f) return@Canvas

            // Full-page detection results: dashed hint boxes the user can tap. Each region is
            // drawn on its own page, so the previous and next strip page stay aligned too.
            state.regions.filterNot { it.consumed }.forEach { region ->
                val regionArea = areaForPage(region.pageIndex, size)
                if (regionArea.width <= 0f) return@forEach
                val rect = rectOf(region.rect, regionArea)
                drawRect(
                    color = Color(0xFF4DB6AC).copy(alpha = 0.10f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color(0xFF4DB6AC).copy(alpha = 0.75f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 1.5f),
                )
            }

            state.entries.forEach { entry ->
                val entryArea = areaForPage(entry.pageIndex, size)
                if (entryArea.width <= 0f) return@forEach
                val color = when {
                    entry.isBusy -> Color(0xFFFFB74D)
                    entry.error != null -> Color(0xFFE57373)
                    else -> Color(0xFF4DB6AC)
                }
                val rect = entryRect(entry, entryArea)
                drawRect(color = color.copy(alpha = 0.12f), topLeft = rect.topLeft, size = rect.size)
                drawRect(color = color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2f))

                // A bubble straddling a strip page boundary keeps ONE entry but its outline
                // continues on the neighbouring page; without this half the box read as broken.
                val extraArea: Rect? = run {
                    val extra = entry.extraRect ?: return@run null
                    val page = entry.extraPageIndex ?: return@run null
                    val area = areaForPage(page, size)
                    if (area.width <= 0f || area.height <= 0f) return@run null
                    rectOf(extra, area)
                }
                extraArea?.let { extra ->
                    drawRect(color = color.copy(alpha = 0.12f), topLeft = extra.topLeft, size = extra.size)
                    drawRect(color = color, topLeft = extra.topLeft, size = extra.size, style = Stroke(width = 2f))
                }

                val center = Offset(rect.right, rect.top)
                drawCircle(color = Color(0xCC1E1E1E), radius = deleteRadius, center = center)
                drawLine(
                    color = Color.White,
                    start = center + Offset(-crossHalf, -crossHalf),
                    end = center + Offset(crossHalf, crossHalf),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = Color.White,
                    start = center + Offset(crossHalf, -crossHalf),
                    end = center + Offset(-crossHalf, crossHalf),
                    strokeWidth = 1.5f,
                )
            }

            draft?.let { rect ->
                drawRect(
                    color = Color(0xFF80CBC4).copy(alpha = 0.15f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color(0xFF80CBC4),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2f),
                )
            }
        }

        if (state.scanning) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .background(Color(0xCC1E1E1E), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text = "识别整页中…", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

private val FULL_PAGE_RATIO = 0.9f

/** Height/width above which the page is treated as a long strip rather than a page. */
private const val PAGE_LIKE_RATIO = 2f

/** Visual size of the delete cross: 40% of the previous 22dp marker. */
private const val DELETE_RADIUS_DP = 8.8f
private const val CROSS_HALF_DP = 2.4f

internal fun fitRect(content: Size, image: Pair<Int, Int>): Rect {
    if (image.first <= 0 || image.second <= 0) return Rect.Zero
    val scale = min(content.width / image.first, content.height / image.second)
    val width = image.first * scale
    val height = image.second * scale
    val left = (content.width - width) / 2f
    val top = (content.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * Prefers the rectangle published by the page view; falls back to a centred contain fit until the
 * view has reported one.
 */
private fun imageArea(
    content: Size,
    geometry: android.graphics.RectF?,
    origin: Offset,
    pageSize: Pair<Int, Int>?,
): Rect {
    if (geometry != null && geometry.width() > 0f && geometry.height() > 0f) {
        return Rect(
            geometry.left - origin.x,
            geometry.top - origin.y,
            geometry.right - origin.x,
            geometry.bottom - origin.y,
        )
    }
    return pageSize?.let { fitRect(content, it) } ?: Rect.Zero
}

private fun rectOf(normalized: NormalizedRect, area: Rect): Rect = Rect(
    area.left + normalized.left * area.width,
    area.top + normalized.top * area.height,
    area.left + normalized.right * area.width,
    area.top + normalized.bottom * area.height,
)

private fun entryRect(entry: TranslationEntry, area: Rect): Rect = rectOf(entry.rect, area)

private fun hitDeleteCross(
    point: Offset,
    entries: List<TranslationEntry>,
    content: Size,
    areaForPage: (Int, Size) -> Rect,
    radius: Float,
): Long? {
    // Merged boxes cluster, so several crosses can overlap: the closest one wins, not the
    // first in list order, and the touch target is twice the drawn size.
    var bestId: Long? = null
    var bestDistance = Float.MAX_VALUE
    entries.forEach { entry ->
        val rect = entryRect(entry, areaForPage(entry.pageIndex, content))
        val distance = (point - Offset(rect.right, rect.top)).getDistance()
        if (distance <= radius * 2f && distance < bestDistance) {
            bestId = entry.id
            bestDistance = distance
        }
    }
    return bestId
}

private fun hitBox(
    point: Offset,
    entries: List<TranslationEntry>,
    content: Size,
    areaForPage: (Int, Size) -> Rect,
): Long? = entries.lastOrNull { entry ->
    val mainArea = areaForPage(entry.pageIndex, content)
    val hitMain = mainArea.width > 0f && mainArea.height > 0f &&
        entryRect(entry, mainArea).contains(point)
    val hitExtra = entry.extraRect?.let { extra ->
        val extraArea = entry.extraPageIndex?.let { areaForPage(it, content) }
        extraArea != null && extraArea.width > 0f && extraArea.height > 0f &&
            rectOf(extra, extraArea).contains(point)
    } ?: false
    hitMain || hitExtra
}?.id
