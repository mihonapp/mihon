package mihon.desktop.ui.reader

import mihon.reader.model.PageDescriptor
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Post-layout pan limits for a single transformed page. The renderer clamps with the same
 * geometry, so gesture code can dispatch an already-clamped [ReaderPan] instead of relying on
 * a later layout pass to correct it.
 */
data class ReaderPanBounds(
    val maxX: Float,
    val maxY: Float,
) {
    init {
        require(maxX.isFinite() && maxX >= 0f) { "maxX must be finite and non-negative" }
        require(maxY.isFinite() && maxY >= 0f) { "maxY must be finite and non-negative" }
    }

    val canPanX: Boolean get() = maxX > 0f
    val canPanY: Boolean get() = maxY > 0f
    val canPan: Boolean get() = canPanX || canPanY

    fun clamp(pan: ReaderPan): ReaderPan = ReaderPan(
        x = pan.x.coerceIn(-maxX, maxX),
        y = pan.y.coerceIn(-maxY, maxY),
    )

    fun panBy(current: ReaderPan, delta: ReaderPan): ReaderPan = clamp(
        ReaderPan(current.x + delta.x, current.y + delta.y),
    )

    companion object {
        val ZERO = ReaderPanBounds(0f, 0f)
    }
}

/**
 * Pure gesture decisions shared by the Compose pointer layer and unit tests.
 */
object ReaderGesturePolicy {
    const val DEFAULT_DOUBLE_TAP_ZOOM = 2f
    const val MIN_DOUBLE_TAP_ZOOM = 2f
    const val FIT_ZOOM = 1f

    private const val ZOOM_EPSILON = 0.001f

    /** Toggles between fit ([FIT_ZOOM]) and a caller-configurable zoom of at least 2x. */
    fun doubleTapZoom(
        currentZoom: Float,
        configuredZoom: Float = DEFAULT_DOUBLE_TAP_ZOOM,
    ): Float {
        require(currentZoom.isFinite()) { "currentZoom must be finite" }
        val target = if (configuredZoom.isFinite()) {
            configuredZoom.coerceAtLeast(MIN_DOUBLE_TAP_ZOOM)
        } else {
            DEFAULT_DOUBLE_TAP_ZOOM
        }
        return if (abs(currentZoom - FIT_ZOOM) <= ZOOM_EPSILON) {
            ReaderLayout.clampZoom(target)
        } else {
            ReaderLayout.clampZoom(FIT_ZOOM)
        }
    }

    fun effectiveScaleMode(mode: ReadingMode, scaleMode: ScaleMode): ScaleMode =
        if (mode == ReadingMode.WEBTOON) ScaleMode.FIT_WIDTH else scaleMode

    /**
     * The viewport a single page is laid out inside. Dual-page modes split the reader in half;
     * continuous modes honor the same webtoon width/padding policy as [ContinuousReader].
     */
    fun pageViewport(
        mode: ReadingMode,
        viewport: ReaderViewport,
        webtoonMaxWidthPixels: Int = 0,
        webtoonSidePaddingPercent: Int = 0,
    ): ReaderViewport {
        if (mode == ReadingMode.VERTICAL || mode == ReadingMode.WEBTOON) {
            val maxWidth = if (webtoonMaxWidthPixels > 0) {
                minOf(viewport.width, webtoonMaxWidthPixels)
            } else {
                viewport.width
            }
            val sidePaddingFraction = webtoonSidePaddingPercent.coerceIn(0, 30) / 100f
            val contentWidth = (maxWidth * (1f - 2f * sidePaddingFraction))
                .roundToInt()
                .coerceAtLeast(1)
            return ReaderViewport(contentWidth, viewport.height)
        }
        val width = if (mode.isDualPage) (viewport.width / 2).coerceAtLeast(1) else viewport.width
        return ReaderViewport(width, viewport.height)
    }

    /** Computes the pan limits for one page rendered inside [viewport]. */
    fun panBounds(
        page: PageDescriptor,
        viewport: ReaderViewport,
        scaleMode: ScaleMode,
        zoom: Float,
        allowVerticalPan: Boolean = true,
    ): ReaderPanBounds {
        val transform = calculatePageTransform(
            page = page,
            viewport = viewport,
            scaleMode = scaleMode,
            zoom = zoom,
            requestedPan = ReaderPan(0f, 0f),
        )
        val maxX = (transform.widthPixels - viewport.width).coerceAtLeast(0) / 2f
        val maxY = if (allowVerticalPan) {
            (transform.heightPixels - viewport.height).coerceAtLeast(0) / 2f
        } else {
            0f
        }
        return ReaderPanBounds(maxX, maxY)
    }

    /**
     * Computes pan limits for the currently selected page in [state]. Continuous modes keep
     * vertical movement for the LazyColumn and only expose horizontal panning.
     */
    fun panBounds(
        state: ReaderState,
        viewport: ReaderViewport,
        zoom: Float,
        webtoonMaxWidthPixels: Int = 0,
        webtoonSidePaddingPercent: Int = 0,
        pageSize: PageSize? = null,
    ): ReaderPanBounds {
        val page = state.pages.getOrNull(state.selectedIndex)?.withIntrinsicSize(pageSize)
            ?: return ReaderPanBounds.ZERO
        val pageViewport = pageViewport(
            mode = state.mode,
            viewport = viewport,
            webtoonMaxWidthPixels = webtoonMaxWidthPixels,
            webtoonSidePaddingPercent = webtoonSidePaddingPercent,
        )
        val continuous = state.mode == ReadingMode.VERTICAL || state.mode == ReadingMode.WEBTOON
        return panBounds(
            page = page,
            viewport = pageViewport,
            scaleMode = effectiveScaleMode(state.mode, state.scaleMode),
            zoom = zoom,
            allowVerticalPan = !continuous,
        )
    }

    fun clampPan(
        state: ReaderState,
        viewport: ReaderViewport,
        zoom: Float,
        requested: ReaderPan,
        webtoonMaxWidthPixels: Int = 0,
        webtoonSidePaddingPercent: Int = 0,
        pageSize: PageSize? = null,
    ): ReaderPan = panBounds(
        state = state,
        viewport = viewport,
        zoom = zoom,
        webtoonMaxWidthPixels = webtoonMaxWidthPixels,
        webtoonSidePaddingPercent = webtoonSidePaddingPercent,
        pageSize = pageSize,
    ).clamp(requested)

    /** Keeps the current relative pan when zoom changes, then callers clamp it to the new bounds. */
    fun scalePan(pan: ReaderPan, fromZoom: Float, toZoom: Float): ReaderPan {
        if (!fromZoom.isFinite() || fromZoom <= 0f || !toZoom.isFinite()) {
            return ReaderPan(0f, 0f)
        }
        val factor = toZoom / fromZoom
        return ReaderPan(pan.x * factor, pan.y * factor)
    }
}
