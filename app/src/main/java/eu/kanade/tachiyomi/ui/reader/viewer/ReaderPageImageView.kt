package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.data.translation.TranslationLogLevel
import eu.kanade.tachiyomi.data.translation.TranslationOverlayBoxStyle
import eu.kanade.tachiyomi.data.translation.TranslationOverlayMappedRect
import eu.kanade.tachiyomi.data.translation.TranslationOverlayRectMapper
import eu.kanade.tachiyomi.data.translation.TranslationOverlayRenderSkipPolicy
import eu.kanade.tachiyomi.data.translation.TranslationOverlayTextFitPolicy
import eu.kanade.tachiyomi.data.translation.TranslationOverlayTextOrientationPolicy
import eu.kanade.tachiyomi.data.translation.TranslationRepository
import tachiyomi.domain.translation.service.TranslationPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okio.BufferedSource
import tachiyomi.data.Translation_boxes
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.roundToInt

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

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV.get()
    }

    private var pageView: View? = null
    private var translationOverlayView: TranslationOverlayView? = null

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
        translationOverlayView?.invalidate()
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
        clearTranslationOverlay()
        it.isVisible = false
    }

    fun setTranslationOverlay(boxes: List<Translation_boxes>) {
        if (boxes.isEmpty()) {
            clearTranslationOverlay()
            return
        }
        val overlay = translationOverlayView ?: TranslationOverlayView(context) { pageView }.also {
            translationOverlayView = it
            addView(it, MATCH_PARENT, MATCH_PARENT)
        }
        overlay.boxes = boxes
        overlay.isVisible = true
        overlay.bringToFront()
        overlay.invalidate()
    }

    fun clearTranslationOverlay() {
        translationOverlayView?.let {
            it.boxes = emptyList()
            it.isVisible = false
            it.invalidate()
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

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        translationOverlayView?.invalidate()
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
        translationOverlayView?.bringToFront()
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
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
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
        translationOverlayView?.bringToFront()
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

    private class TranslationOverlayView(
        context: Context,
        private val pageViewProvider: () -> View?,
    ) : View(context) {

        var boxes: List<Translation_boxes> = emptyList()
            set(value) {
                if (field != value) {
                    textLayoutCache.clear()
                }
                field = value
            }

        private val density = resources.displayMetrics.density
        private val translationPreferences: TranslationPreferences = Injekt.get()
        private val translationRepository: TranslationRepository = Injekt.get()
        private var logScope: CoroutineScope? = null
        private val loggedRenderSkips = LinkedHashSet<String>()
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 32, 32, 32)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        private val baseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }
        private val textLayoutCache = object : LinkedHashMap<TextLayoutCacheKey, FittedTextLayout>(
            TEXT_LAYOUT_CACHE_LIMIT,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextLayoutCacheKey, FittedTextLayout>): Boolean {
                return size > TEXT_LAYOUT_CACHE_LIMIT
            }
        }

        private data class FittedTextLayout(
            val layout: StaticLayout,
            val textSizePx: Float,
            val textScaleX: Float,
            val contentWidth: Int,
            val contentHeight: Float,
            val ellipsisCount: Int,
            val lineCount: Int,
            val rotated: Boolean,
        )

        private data class TextLayoutCacheKey(
            val boxId: Long,
            val pageId: Long,
            val text: String,
            val width: Int,
            val contentHeight: Int,
            val boxHeight: Int,
            val padding: Int,
            val textSizeMode: String,
            val customSizeSp: Int,
            val scaledDensityBits: Int,
            val fontFamily: String?,
            val textColor: String?,
            val textAlign: String?,
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (boxes.isEmpty()) return

            val baseStyle = TranslationOverlayBoxStyle.fromPreferences(translationPreferences)
            boxes.forEach { box ->
                val rect = box.toViewRect() ?: return@forEach
                val sizeSkipReason = TranslationOverlayRenderSkipPolicy.reason(
                    hasPageView = true,
                    pageViewReady = true,
                    sourceWidth = 1,
                    sourceHeight = 1,
                    rectWidth = rect.width(),
                    rectHeight = rect.height(),
                )
                if (sizeSkipReason != null) {
                    box.logRenderSkip(sizeSkipReason)
                    return@forEach
                }

                val style = TranslationOverlayBoxStyle
                    .fromJson(box.style_json)
                    .mergedWith(baseStyle)
                val radius = 4f * density
                fillPaint.color = parseColor(style.fillColor, Color.argb(210, 255, 255, 255))
                strokePaint.color = parseColor(style.strokeColor, Color.argb(230, 32, 32, 32))
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
                canvas.drawRoundRect(rect, radius, radius, strokePaint)
                drawText(canvas, rect, box, style)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (logScope == null) {
                logScope = MainScope()
            }
        }

        override fun onDetachedFromWindow() {
            logScope?.cancel()
            logScope = null
            textLayoutCache.clear()
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (w != oldw || h != oldh) {
                textLayoutCache.clear()
            }
            super.onSizeChanged(w, h, oldw, oldh)
        }

        private fun drawText(
            canvas: Canvas,
            rect: RectF,
            box: Translation_boxes,
            style: TranslationOverlayBoxStyle,
        ) {
            val text = box.translated_text
            if (text.isBlank()) return
            val padding = ((style.paddingDp ?: 0f).coerceIn(0f, 24f) * density)
                .coerceAtMost(rect.width() / 5f)
                .coerceAtMost(rect.height() / 5f)
            val fitted = fittedTextLayout(box, text, rect, style, padding)

            if (TranslationOverlayTextFitPolicy.shouldLogTruncation(fitted.ellipsisCount)) {
                box.logTextFitTruncated(
                    rect = rect,
                    text = text,
                    textSizePx = fitted.textSizePx,
                    textScaleX = fitted.textScaleX,
                    contentWidth = fitted.contentWidth,
                    contentHeight = fitted.contentHeight,
                    lineCount = fitted.lineCount,
                    ellipsisCount = fitted.ellipsisCount,
                    paddingPx = padding,
                    rotated = fitted.rotated,
                )
            }

            canvas.save()
            canvas.clipRect(rect)
            val verticalOffset = ((fitted.contentHeight - fitted.layout.height) / 2f).coerceAtLeast(0f)
            canvas.translate(rect.left + padding, rect.top + padding + verticalOffset)
            fitted.layout.draw(canvas)
            canvas.restore()
        }

        private fun fittedTextLayout(
            box: Translation_boxes,
            text: String,
            rect: RectF,
            style: TranslationOverlayBoxStyle,
            padding: Float,
        ): FittedTextLayout {
            val width = (rect.width() - padding * 2).toInt().coerceAtLeast(1)
            val contentHeight = (rect.height() - padding * 2).coerceAtLeast(1f)
            val textSizeMode = translationPreferences.overlayTextSizeMode.get()
            val customSizeSp = translationPreferences.overlayTextSizeSp.get()
            val scaledDensity = resources.configuration.fontScale * density
            val cacheKey = TextLayoutCacheKey(
                boxId = box._id,
                pageId = box.page_id,
                text = text,
                width = width,
                contentHeight = contentHeight.roundToInt(),
                boxHeight = rect.height().roundToInt(),
                padding = padding.roundToInt(),
                textSizeMode = textSizeMode,
                customSizeSp = customSizeSp,
                scaledDensityBits = scaledDensity.toBits(),
                fontFamily = style.fontFamily,
                textColor = style.textColor,
                textAlign = style.textAlign,
            )
            textLayoutCache[cacheKey]?.let { return it }
            val range = TranslationOverlayTextFitPolicy.sizeRangePx(
                mode = textSizeMode,
                customSp = customSizeSp,
                boxHeightPx = rect.height(),
                density = density,
                scaledDensity = scaledDensity,
                paddingPx = padding,
            )
            val textPaint = TextPaint(baseTextPaint).apply {
                color = parseColor(style.textColor, Color.BLACK)
                typeface = typefaceFor(style.fontFamily)
            }
            val normal = bestTextLayout(
                text = text,
                paint = textPaint,
                width = width,
                heightLimit = contentHeight,
                alignment = alignmentFor(style.textAlign),
                range = range,
            )
            return normal.also { textLayoutCache[cacheKey] = it }
        }

        private fun bestTextLayout(
            text: String,
            paint: TextPaint,
            width: Int,
            heightLimit: Float,
            alignment: Layout.Alignment,
            range: eu.kanade.tachiyomi.data.translation.TranslationOverlayTextSizeRange,
        ): FittedTextLayout {
            var low = range.minPx
            var high = range.preferredMaxPx.coerceAtLeast(low)
            var bestSize = low
            var bestLayout = buildTextLayout(
                text = text,
                paint = paint,
                textSizePx = low,
                textScaleX = 1f,
                width = width,
                alignment = alignment,
                maxLines = Int.MAX_VALUE,
                ellipsize = false,
            )
            if (layoutFits(bestLayout, width, heightLimit)) {
                repeat(14) {
                    val mid = (low + high) / 2f
                    val candidate = buildTextLayout(
                        text = text,
                        paint = paint,
                        textSizePx = mid,
                        textScaleX = 1f,
                        width = width,
                        alignment = alignment,
                        maxLines = Int.MAX_VALUE,
                        ellipsize = false,
                    )
                    if (layoutFits(candidate, width, heightLimit)) {
                        bestSize = mid
                        bestLayout = candidate
                        low = mid
                    } else {
                        high = mid
                    }
                }
            } else {
                bestCompressedLayout(
                    text = text,
                    paint = paint,
                    textSizePx = range.minPx,
                    width = width,
                    heightLimit = heightLimit,
                    alignment = alignment,
                )?.let { return it }
                paint.textSize = range.minPx
                paint.textScaleX = MIN_TEXT_SCALE_X
                val maxLines = (heightLimit / paint.fontSpacing).toInt().coerceAtLeast(1)
                bestSize = range.minPx
                bestLayout = buildTextLayout(
                    text = text,
                    paint = paint,
                    textSizePx = range.minPx,
                    textScaleX = MIN_TEXT_SCALE_X,
                    width = width,
                    alignment = alignment,
                    maxLines = maxLines,
                    ellipsize = true,
                )
            }
            return FittedTextLayout(
                layout = bestLayout,
                textSizePx = bestSize,
                textScaleX = bestLayout.paint.textScaleX,
                contentWidth = width,
                contentHeight = heightLimit,
                ellipsisCount = bestLayout.totalEllipsisCount(),
                lineCount = bestLayout.lineCount,
                rotated = TranslationOverlayTextOrientationPolicy.shouldRotateTranslatedText(
                    boxWidthPx = width.toFloat(),
                    boxHeightPx = heightLimit,
                    textLength = text.length,
                ),
            )
        }

        private fun bestCompressedLayout(
            text: String,
            paint: TextPaint,
            textSizePx: Float,
            width: Int,
            heightLimit: Float,
            alignment: Layout.Alignment,
        ): FittedTextLayout? {
            var scale = 0.95f
            while (scale >= MIN_TEXT_SCALE_X) {
                val layout = buildTextLayout(
                    text = text,
                    paint = paint,
                    textSizePx = textSizePx,
                    textScaleX = scale,
                    width = width,
                    alignment = alignment,
                    maxLines = Int.MAX_VALUE,
                    ellipsize = false,
                )
                if (layoutFits(layout, width, heightLimit)) {
                    return FittedTextLayout(
                        layout = layout,
                        textSizePx = textSizePx,
                        textScaleX = scale,
                        contentWidth = width,
                        contentHeight = heightLimit,
                        ellipsisCount = layout.totalEllipsisCount(),
                        lineCount = layout.lineCount,
                        rotated = TranslationOverlayTextOrientationPolicy.shouldRotateTranslatedText(
                            boxWidthPx = width.toFloat(),
                            boxHeightPx = heightLimit,
                            textLength = text.length,
                        ),
                    )
                }
                scale -= 0.05f
            }
            return null
        }

        private fun buildTextLayout(
            text: String,
            paint: TextPaint,
            textSizePx: Float,
            textScaleX: Float,
            width: Int,
            alignment: Layout.Alignment,
            maxLines: Int,
            ellipsize: Boolean,
        ): StaticLayout {
            paint.textSize = textSizePx
            paint.textScaleX = textScaleX
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(maxLines)
                .also { builder ->
                    if (ellipsize) {
                        builder.setEllipsize(TextUtils.TruncateAt.END)
                    }
                }
                .build()
        }

        private fun layoutFits(layout: StaticLayout, width: Int, height: Float): Boolean {
            if (layout.height > height + 0.5f) return false
            return (0 until layout.lineCount).all { line ->
                layout.getLineWidth(line) <= width + 0.5f
            }
        }

        private fun StaticLayout.totalEllipsisCount(): Int {
            return (0 until lineCount).sumOf { line -> getEllipsisCount(line) }
        }

        private fun parseColor(value: String?, fallback: Int): Int {
            return value
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                ?: fallback
        }

        private fun typefaceFor(value: String?): Typeface {
            return when (value?.lowercase(Locale.ROOT)) {
                "serif" -> Typeface.SERIF
                "monospace" -> Typeface.MONOSPACE
                "sans", "sans-serif" -> Typeface.DEFAULT
                else -> Typeface.DEFAULT
            }
        }

        private fun alignmentFor(value: String?): Layout.Alignment {
            return when (value?.lowercase(Locale.ROOT)) {
                "start", "left" -> Layout.Alignment.ALIGN_NORMAL
                "end", "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }
        }

        private fun Translation_boxes.toViewRect(): RectF? {
            val view = pageViewProvider()
            if (view == null) {
                logRenderSkip("missing_page_view")
                return null
            }
            return when (view) {
                is SubsamplingScaleImageView -> toSubsamplingRect(view)
                is AppCompatImageView -> toImageViewRect(view)
                else -> {
                    logRenderSkip("unsupported_page_view")
                    null
                }
            }
        }

        private fun Translation_boxes.toSubsamplingRect(view: SubsamplingScaleImageView): RectF? {
            val readinessReason = TranslationOverlayRenderSkipPolicy.reason(
                hasPageView = true,
                pageViewReady = view.isReady,
                sourceWidth = view.sWidth,
                sourceHeight = view.sHeight,
                rectWidth = 2f,
                rectHeight = 2f,
            )
            if (readinessReason != null) {
                logRenderSkip(readinessReason)
                return null
            }
            val imageStart = view.sourceToViewCoord(0f, 0f)
                ?: run {
                    logRenderSkip("coordinate_mapping_failed")
                    return null
                }
            val imageEnd = view.sourceToViewCoord(view.sWidth.toFloat(), view.sHeight.toFloat()) ?: run {
                logRenderSkip("coordinate_mapping_failed")
                return null
            }
            val imageLeft = view.left + minOf(imageStart.x, imageEnd.x)
            val imageTop = view.top + minOf(imageStart.y, imageEnd.y)
            val imageWidth = kotlin.math.abs(imageEnd.x - imageStart.x)
            val imageHeight = kotlin.math.abs(imageEnd.y - imageStart.y)
            return toMappedRect(
                sourceWidth = view.sWidth,
                sourceHeight = view.sHeight,
                imageLeft = imageLeft,
                imageTop = imageTop,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = view.width,
                viewHeight = view.height,
            )
        }

        private fun Translation_boxes.toImageViewRect(view: AppCompatImageView): RectF? {
            val drawable = view.drawable ?: run {
                logRenderSkip("missing_drawable")
                return null
            }
            val imageRect = RectF(
                0f,
                0f,
                drawable.intrinsicWidth.toFloat(),
                drawable.intrinsicHeight.toFloat(),
            )
            view.imageMatrix.mapRect(imageRect)
            imageRect.offset(view.left.toFloat(), view.top.toFloat())
            return toMappedRect(
                sourceWidth = drawable.intrinsicWidth,
                sourceHeight = drawable.intrinsicHeight,
                imageLeft = imageRect.left,
                imageTop = imageRect.top,
                imageWidth = imageRect.width(),
                imageHeight = imageRect.height(),
                viewWidth = view.width,
                viewHeight = view.height,
            )
        }

        private fun Translation_boxes.toMappedRect(
            sourceWidth: Int,
            sourceHeight: Int,
            imageLeft: Float,
            imageTop: Float,
            imageWidth: Float,
            imageHeight: Float,
            viewWidth: Int,
            viewHeight: Int,
        ): RectF? {
            val mapped = TranslationOverlayRectMapper.map(
                x = x.toFloat(),
                y = y.toFloat(),
                width = width.toFloat(),
                height = height.toFloat(),
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                imageLeft = imageLeft,
                imageTop = imageTop,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
            )
            mapped.skipReason?.let { reason ->
                logRenderSkip(reason, mapped)
                return null
            }
            return RectF(mapped.left, mapped.top, mapped.right, mapped.bottom)
        }

        private fun Translation_boxes.logRenderSkip(
            reason: String,
            mapping: TranslationOverlayMappedRect? = null,
        ) {
            val key = "${_id}:$reason"
            if (!loggedRenderSkips.add(key)) return
            if (loggedRenderSkips.size > MAX_RENDER_SKIP_LOG_KEYS) {
                val iterator = loggedRenderSkips.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            val scope = logScope ?: MainScope().also { logScope = it }
            scope.launch {
                withIOContext {
                    translationRepository.insertLog(
                        jobId = null,
                        pageId = page_id,
                        level = TranslationLogLevel.Debug,
                        tag = "overlay",
                        message = "Skipped rendering translation overlay box",
                        details = buildString {
                            appendLine("action=overlay_render_skip")
                            appendLine("reason=$reason")
                            appendLine("page_id=$page_id")
                            appendLine("box_id=$_id")
                            appendLine("x=$x")
                            appendLine("y=$y")
                            appendLine("width=$width")
                            appendLine("height=$height")
                            appendLine("text_type=$text_type")
                            appendLine("orientation=${resources.configuration.orientation}")
                            mapping?.let {
                                appendLine("source_width=${it.sourceWidth}")
                                appendLine("source_height=${it.sourceHeight}")
                                appendLine("view_width=${it.viewWidth}")
                                appendLine("view_height=${it.viewHeight}")
                                appendLine("image_left=${it.imageLeft}")
                                appendLine("image_top=${it.imageTop}")
                                appendLine("image_width=${it.imageWidth}")
                                appendLine("image_height=${it.imageHeight}")
                                appendLine("mapped_left=${it.left}")
                                appendLine("mapped_top=${it.top}")
                                appendLine("mapped_right=${it.right}")
                                appendLine("mapped_bottom=${it.bottom}")
                                appendLine("scale_x=${it.scaleX}")
                                appendLine("scale_y=${it.scaleY}")
                            }
                        },
                    )
                }
            }
        }

        private fun Translation_boxes.logTextFitTruncated(
            rect: RectF,
            text: String,
            textSizePx: Float,
            textScaleX: Float,
            contentWidth: Int,
            contentHeight: Float,
            lineCount: Int,
            ellipsisCount: Int,
            paddingPx: Float,
            rotated: Boolean,
        ) {
            val key = "text_fit:$_id:${text.length}:$textSizePx:$textScaleX:$lineCount:$ellipsisCount:$rotated"
            if (!loggedRenderSkips.add(key)) return
            if (loggedRenderSkips.size > MAX_RENDER_SKIP_LOG_KEYS) {
                val iterator = loggedRenderSkips.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            val scope = logScope ?: MainScope().also { logScope = it }
            scope.launch {
                withIOContext {
                    translationRepository.insertLog(
                        jobId = null,
                        pageId = null,
                        level = TranslationLogLevel.Debug,
                        tag = "overlay",
                        message = "Translation overlay text fit truncated",
                        details = buildString {
                            appendLine("action=overlay_text_fit_truncated")
                            appendLine("page_id=$page_id")
                            appendLine("box_id=$_id")
                            appendLine("rect_left=${rect.left}")
                            appendLine("rect_top=${rect.top}")
                            appendLine("rect_width=${rect.width()}")
                            appendLine("rect_height=${rect.height()}")
                            appendLine("content_width=$contentWidth")
                            appendLine("content_height=$contentHeight")
                            appendLine("selected_text_size_px=$textSizePx")
                            appendLine("selected_text_scale_x=$textScaleX")
                            appendLine("line_count=$lineCount")
                            appendLine("ellipsis_count=$ellipsisCount")
                            appendLine("text_length=${text.length}")
                            appendLine("padding_px=$paddingPx")
                            appendLine("rotated=$rotated")
                            appendLine("orientation=${resources.configuration.orientation}")
                        },
                    )
                }
            }
        }
    }
}

private const val MAX_RENDER_SKIP_LOG_KEYS = 256
private const val TEXT_LAYOUT_CACHE_LIMIT = 256
private const val MIN_TEXT_SCALE_X = 0.55f
private const val MAX_ZOOM_SCALE = 5F
