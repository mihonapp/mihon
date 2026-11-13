package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.Paint
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
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
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
import eu.kanade.tachiyomi.data.coil.ImageDecoder
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.webtoonStrips
import eu.kanade.tachiyomi.ui.reader.translation.TranslationGeometry
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonStripImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import okio.BufferedSource

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonStripImageView] will be used instead of [SubsamplingScaleImageView]
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

    private var lastPublishedRect: RectF? = null
    private var lastPublishedWidth: Int = 0
    private var lastPublishedHeight: Int = 0

    /**
     * Publishes where the page image currently sits on screen, together with the size of the image
     * the view is showing (which can be cropped or split relative to the raw file).
     */
    var onImageRectChanged: ((rect: RectF, sourceWidth: Int, sourceHeight: Int, cropBorders: Boolean) -> Unit)? = null

    /**
     * The reader's grayscale and inverted colours for the page image alone, so the transition and error views
     * keep theirs - see [LocalPageColorFilter].
     */
    var pageColorFilter: ColorFilter? = null
        set(value) {
            if (field == value) return
            field = value
            applyPageColorFilter()
        }

    private fun applyPageColorFilter() {
        val filter = pageColorFilter
        when (val view = pageView) {
            is ImageView -> view.colorFilter = filter
            is WebtoonStripImageView -> view.colorFilter = filter
            // Drawn with its own paints: a layer takes the filter instead, as wide as the screen it fills.
            is SubsamplingScaleImageView -> view.setLayerType(
                if (filter == null) LAYER_TYPE_NONE else LAYER_TYPE_HARDWARE,
                filter?.let { Paint().apply { colorFilter = it } },
            )
        }
    }

    private var stripRequest: Disposable? = null

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
        // The image only becomes measurable once it is ready, so publish again here: layout ran
        // earlier, when sWidth/sHeight were still zero.
        onImageLoaded?.invoke()
        background = pageBackground
        publishImageRect()
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

    open fun onPageSelected(forward: Boolean) {
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
        } else if (isWebtoon) {
            prepareStripImageView()
            setStripImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() {
        stripRequest?.dispose()
        stripRequest = null
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is AppCompatImageView -> it.dispose()
                is WebtoonStripImageView -> it.recycle()
            }
            it.isVisible = false
        }
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

        pageView = SubsamplingScaleImageView(context).apply {
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                        publishImageRect()
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Panning moves the image under the overlay without changing its scale.
                        publishImageRect()
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
        applyPageColorFilter()
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
                setImage(ImageSource.inputStream(data.inputStream()))
                isVisible = true
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareStripImageView() {
        if (pageView is WebtoonStripImageView) return
        removeView(pageView)

        pageView = WebtoonStripImageView(context)
        addView(pageView, MATCH_PARENT, WRAP_CONTENT)
        applyPageColorFilter()
    }

    private fun setStripImage(data: BufferedSource, config: Config) {
        val view = pageView as? WebtoonStripImageView ?: return
        view.onReload = { requestStrips(view, data, config, isReload = true) }
        requestStrips(view, data, config, isReload = false)
    }

    private fun requestStrips(view: WebtoonStripImageView, data: BufferedSource, config: Config, isReload: Boolean) {
        // A recycled view is reused for another page, where a request still running for the old one must not land
        stripRequest?.dispose()
        stripRequest = ImageRequest.Builder(context)
            // Peeking leaves the data whole for decoding again after the strips were freed off screen
            .data(data.peek())
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    view.setImage(result as ImageDecoder.StripsImage)
                    if (!isReload) {
                        view.isVisible = true
                        onImageLoaded()
                    }
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .size(ViewSizeResolver(this))
            .precision(Precision.INEXACT)
            .cropBorders(config.cropBorders)
            .webtoonStrips(true)
            .crossfade(false)
            .build()
            .let(context.imageLoader::enqueue)
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
        applyPageColorFilter()
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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        publishImageRect()
        // SubsamplingScaleImageView derives its pan/scale offsets while drawing, so the rect read
        // here is still the previous frame's. Publishing again on the next frame catches up.
        postOnAnimation { publishImageRect() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            postOnAnimation { publishImageRect() }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        publishImageRect()
        return handled
    }

    /** Rect of the image on screen right now, without publishing it. */
    fun currentImageFrame(): TranslationGeometry.Frame? {
        // A detached or recycled holder has no meaningful window position, and its page view may
        // already belong to another page; reporting it put boxes in random places.
        if (!isAttachedToWindow) return null
        val view = pageView ?: return null
        val cropBorders = config?.cropBorders == true
        val rect: RectF
        val sourceWidth: Int
        val sourceHeight: Int
        when (view) {
            is SubsamplingScaleImageView -> {
                if (view.sWidth <= 0 || view.sHeight <= 0) return null
                val topLeft = view.sourceToViewCoord(0f, 0f) ?: return null
                val bottomRight = view.sourceToViewCoord(view.sWidth.toFloat(), view.sHeight.toFloat()) ?: return null
                rect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                // sWidth/sHeight already exclude the borders SSIV cropped while decoding.
                sourceWidth = view.sWidth
                sourceHeight = view.sHeight
            }
            is WebtoonStripImageView -> {
                if (view.imageWidth <= 0 || view.imageHeight <= 0) return null
                rect = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                sourceWidth = view.imageWidth
                sourceHeight = view.imageHeight
            }
            is android.widget.ImageView -> {
                val drawable = view.drawable ?: return null
                if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return null
                rect = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                sourceWidth = drawable.intrinsicWidth
                sourceHeight = drawable.intrinsicHeight
            }
            else -> return null
        }
        if (rect.width() <= 0f || rect.height() <= 0f) return null
        // Must match Compose's positionInWindow(): using screen coordinates here shifted every
        // box by the status bar height and made them drift after a rotation. The rect is in the
        // page view's own space, so take that view's origin rather than this container's.
        val location = IntArray(2)
        view.getLocationInWindow(location)
        rect.offset(location[0].toFloat(), location[1].toFloat())
        return TranslationGeometry.Frame(rect, sourceWidth, sourceHeight, cropBorders)
    }

    private fun publishImageRect() {
        val frame = currentImageFrame() ?: return
        val rect = frame.rect

        // Pans and zoom animations call this every frame; only report real movement.
        val previous = lastPublishedRect
        if (previous != null &&
            frame.sourceWidth == lastPublishedWidth &&
            frame.sourceHeight == lastPublishedHeight &&
            kotlin.math.abs(previous.left - rect.left) < 0.5f &&
            kotlin.math.abs(previous.top - rect.top) < 0.5f &&
            kotlin.math.abs(previous.right - rect.right) < 0.5f &&
            kotlin.math.abs(previous.bottom - rect.bottom) < 0.5f
        ) {
            return
        }
        lastPublishedRect = RectF(rect)
        lastPublishedWidth = frame.sourceWidth
        lastPublishedHeight = frame.sourceHeight
        onImageRectChanged?.invoke(rect, frame.sourceWidth, frame.sourceHeight, frame.cropBorders)
    }

}

private const val MAX_ZOOM_SCALE = 5F
