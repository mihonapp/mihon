package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelRect
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import kotlin.math.min

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private var pageView: View? = null

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    /** Set by [PagerPageHolder] before load starts, when this page belongs to a panel-by-panel viewer. */
    var panelModeActive: Boolean = false

    private var panelStops: List<PanelRect> = emptyList()
    private var panelStopIndex: Int = -1
    private var panelStopsEnterForward: Boolean = true

    /**
     * Set by [PagerPageHolder] before load starts, to resume on a specific stop (e.g. after the
     * viewer was recreated by a device rotation) instead of the page's first/last stop. Consumed
     * (cleared) the first time [setPanelStops] runs.
     */
    var panelStopIndexOverride: Int? = null

    /** Notified whenever the current panel stop changes, so it can be persisted for restoration. */
    var onPanelStopChanged: ((index: Int) -> Unit)? = null

    /**
     * True once the user pinch-zooms/pans/flings away from the current stop, so
     * [syncPanelStopIndexToCurrentView] knows there's actually a drift to correct for. Without this
     * guard, that resync would also run right after a tap-driven [animateToPanelStop] — reading the
     * view's still-mid-flight center back as "nearest stop" snaps the index back near its start and
     * a rapid next tap re-plays the same short hop instead of advancing, so the reader never visibly
     * progresses through closely-spaced stops. Set on genuine touch/fling center changes; cleared
     * once a stop is set programmatically (its own [onCenterChanged] firing with `ORIGIN_ANIM` never
     * sets it in the first place).
     */
    private var userMovedAwayFromStop: Boolean = false

    /** Dims panels other than the current stop; created lazily the first time it's needed. */
    private var panelSpotlight: PanelSpotlightOverlay? = null

    /** Set by [PagerPageHolder] from the user's preference before load starts. */
    var panelOverlayOpacityPercent: Int = PanelSpotlightOverlay.DEFAULT_OPACITY_PERCENT
        set(value) {
            field = value
            panelSpotlight?.opacityPercent = value
        }

    private fun spotlightFor(view: SubsamplingScaleImageView): PanelSpotlightOverlay {
        panelSpotlight?.let { return it }
        val overlay = PanelSpotlightOverlay(context).also {
            it.sourceView = view
            it.opacityPercent = panelOverlayOpacityPercent
        }
        addView(overlay, MATCH_PARENT, MATCH_PARENT)
        panelSpotlight = overlay
        return overlay
    }

    private fun setSpotlightVisible(visible: Boolean) {
        val overlay = panelSpotlight ?: return
        val target = if (visible) 1f else 0f
        if (overlay.alpha == target) return
        overlay.animate().alpha(target).setDuration(SPOTLIGHT_FADE_MS).start()
    }

    open fun onPageSelected(forward: Boolean) {
        panelStopsEnterForward = forward
        if (panelModeActive) return
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    /**
     * Sets the ordered list of panel stops (normalized 0f..1f image-fraction coordinates) for
     * panel-by-panel guided navigation, and jumps to the first (or last, if this page was
     * entered backward) stop. Pass an empty list to clear (falls back to a single full-page stop).
     */
    fun setPanelStops(stops: List<PanelRect>, anchorRect: PanelRect? = null) {
        panelStops = stops.ifEmpty { listOf(PanelRect.FULL_PAGE) }
        panelStopIndex = when {
            // A settings change (reading direction, intro/outro toggle) re-supplied the stop list
            // for the page currently being read — land on whichever new stop covers roughly the
            // same content the reader was already looking at, instead of jumping to the entry stop.
            anchorRect != null -> nearestPanelStopIndex(anchorRect)
            else -> panelStopIndexOverride?.coerceIn(0, panelStops.lastIndex)
                ?: if (panelStopsEnterForward) 0 else panelStops.lastIndex
        }
        panelStopIndexOverride = null
        userMovedAwayFromStop = false
        if (panelModeActive) {
            (pageView as? SubsamplingScaleImageView)?.let { spotlightFor(it).alpha = 1f }
        }
        jumpToPanelStop(panelStopIndex)
        onPanelStopChanged?.invoke(panelStopIndex)
    }

    /** The stop currently being read, so it can be passed back into [setPanelStops] as an anchor. */
    fun currentPanelStopRect(): PanelRect? = panelStops.getOrNull(panelStopIndex)

    private fun nearestPanelStopIndex(anchor: PanelRect): Int = panelStops.indices.minByOrNull { i ->
        val s = panelStops[i]
        val dx = s.centerX - anchor.centerX
        val dy = s.centerY - anchor.centerY
        dx * dx + dy * dy
    } ?: 0

    fun hasPanelStops(): Boolean = panelStops.isNotEmpty()

    fun canAdvancePanelStop(): Boolean = panelStops.isNotEmpty() && panelStopIndex < panelStops.lastIndex

    fun canRetreatPanelStop(): Boolean = panelStops.isNotEmpty() && panelStopIndex > 0

    fun advancePanelStop() {
        syncPanelStopIndexToCurrentView()
        if (!canAdvancePanelStop()) return
        panelStopIndex++
        animateToPanelStop(panelStopIndex)
        onPanelStopChanged?.invoke(panelStopIndex)
    }

    fun retreatPanelStop() {
        syncPanelStopIndexToCurrentView()
        if (!canRetreatPanelStop()) return
        panelStopIndex--
        animateToPanelStop(panelStopIndex)
        onPanelStopChanged?.invoke(panelStopIndex)
    }

    /**
     * If the user pinch-zoomed away from the current panel stop, find the nearest stop to
     * where they actually are before advancing/retreating, so guided navigation resumes
     * from the right place instead of jumping relative to a stale index.
     */
    private fun syncPanelStopIndexToCurrentView() {
        if (!userMovedAwayFromStop) return
        val view = pageView as? SubsamplingScaleImageView ?: return
        val center = view.center ?: return
        if (panelStops.isEmpty()) return
        val nearestIndex = panelStops.indices.minByOrNull { index ->
            val (_, target) = view.panelStopTarget(panelStops[index])
            val dx = target.x - center.x
            val dy = target.y - center.y
            dx * dx + dy * dy
        } ?: return
        panelStopIndex = nearestIndex
        userMovedAwayFromStop = false
        setSpotlightVisible(true)
    }

    private fun jumpToPanelStop(index: Int) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        val target = panelStops.getOrNull(index) ?: return
        if (panelModeActive) spotlightFor(view).targetRect = target
        if (view.isReady) {
            val (scale, center) = view.panelStopTarget(target)
            view.setScaleAndCenter(scale, center)
        } else {
            view.setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        view.setupZoom(config)
                        val (scale, center) = view.panelStopTarget(target)
                        view.setScaleAndCenter(scale, center)
                        // targetRect was set above while the view wasn't ready yet, so that
                        // assignment's own invalidate() drew nothing (sourceToViewCoord needs a
                        // ready view). setScaleAndCenter usually re-triggers the state-changed
                        // listener too, but don't rely on that alone — explicitly redraw now that
                        // the view can actually resolve view coordinates, so the spotlight can't
                        // end up stuck undrawn until some later, unrelated stop change.
                        panelSpotlight?.invalidate()
                        this@ReaderPageImageView.onImageLoaded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageLoadError(e)
                    }
                },
            )
        }
    }

    private fun animateToPanelStop(index: Int) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        val target = panelStops.getOrNull(index) ?: return
        if (panelModeActive) spotlightFor(view).targetRect = target
        val (scale, center) = view.panelStopTarget(target)
        view.animateScaleAndCenter(scale, center)!!
            .withEasing(EASE_OUT_QUAD)
            .withDuration(250)
            .withInterruptible(true)
            .start()
    }

    private fun SubsamplingScaleImageView.panelStopTarget(rect: PanelRect): Pair<Float, PointF> {
        val targetScale = min(
            width / (rect.width * sWidth),
            height / (rect.height * sHeight),
        ).coerceIn(minScale, maxScale)
        val center = PointF(
            (rect.left + rect.width / 2f) * sWidth,
            (rect.top + rect.height / 2f) * sHeight,
        )
        return targetScale to center
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        if (
            config != null &&
            config!!.landscapeZoom &&
            config!!.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config!!.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                animateScaleAndCenter(targetScale, point)!!
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() = pageView?.let {
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            // PAN_LIMIT_INSIDE (the default elsewhere) clamps the requested center so the image
            // never shows blank space past its edges — but that also stops a panel stop near a
            // page edge from ever actually reaching screen-center; the clamp pulls it back toward
            // the edge instead. Panel-by-panel trades that guarantee for PAN_LIMIT_CENTER, which
            // honors the requested center exactly (letterboxing with blank margin if needed) so
            // every panel, edge or not, lands centered.
            val panLimit = if (panelModeActive) {
                SubsamplingScaleImageView.PAN_LIMIT_CENTER
            } else {
                SubsamplingScaleImageView.PAN_LIMIT_INSIDE
            }
            setPanLimit(panLimit)
            setMinimumTileDpi(180)
            // Panel-by-panel is a fully guided flow — pinch/pan/double-tap would let the reader
            // zoom out from under it. Disabled here (not just left alone) so it can't happen at
            // all until the page is rebuilt for a different reading mode.
            if (panelModeActive) {
                setZoomEnabled(false)
                setPanEnabled(false)
            }
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                        panelSpotlight?.invalidate()
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        if (origin != SubsamplingScaleImageView.ORIGIN_ANIM) {
                            userMovedAwayFromStop = true
                            setSpotlightVisible(false)
                        }
                        panelSpotlight?.invalidate()
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon) {
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F
private const val SPOTLIGHT_FADE_MS = 150L
