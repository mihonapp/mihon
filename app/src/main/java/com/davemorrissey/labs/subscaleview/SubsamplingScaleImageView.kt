@file:Suppress("detekt:all")

package com.davemorrissey.labs.subscaleview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.IntDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After zooming in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pan and
 * zoom, tiles off screen or higher/lower resolution than required are discarded from memory.
 *
 *
 * Tiles are no larger than the max supported bitmap size, so with large images tiling may be used even when zoomed out.
 *
 *
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * <br></br>
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image pixels (scaled)
 * <br></br>
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped source file pixels
 *
 *
 * [View project on GitHub](https://github.com/davemorrissey/subsampling-scale-image-view)
 *
 */
open class SubsamplingScaleImageView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null,
) : View(context, attr) {
    // Coroutines scope
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Current quickscale state
    private val quickScaleThreshold =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, context.resources.displayMetrics)

    // Long click handler
    private var longClickJob: Job? = null

    // The logical density of the display
    private val density: Float = resources.displayMetrics.density

    // Bitmap (preview or full image)
    private var bitmap: Bitmap? = null

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    private var bitmapIsCached = false

    /**
     * Maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish to set this according
     * to screen density - on a retina screen, 1:1 may still be too small. Consider using [setMinimumDpi],
     * which is density aware.
     */
    var maxScale = 2F

    // Pan limiting style
    @PanLimit
    private var panLimit = PAN_LIMIT_INSIDE

    // Minimum scale type
    @ScaleType
    private var minimumScaleType = SCALE_TYPE_CENTER_INSIDE

    // Gesture detection settings
    private var panEnabled = true

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the the current scale.
     */
    var isZoomEnabled = true

    /**
     * Enable or disable double tap &amp; swipe to zoom.
     */
    var isQuickScaleEnabled = true

    // Double tap zoom behaviour
    private var doubleTapZoomScale = 1F

    @ZoomStyle
    private var doubleTapZoomStyle = ZOOM_FOCUS_FIXED
    private var doubleTapZoomDuration = 500

    /**
     * Returns the current scale value.
     *
     * @return the current scale as a source/view pixels ratio.
     */
    var scale = 0F
        private set
    private var scaleStart = 0F

    // Screen coordinate of top-left corner of source image
    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null
    private var vTranslateBefore: PointF? = null

    // Source coordinate to center on, used when new position is set externally before view is ready
    private var pendingScale = -1F
    private var sPendingCenter: PointF? = null
    private var sRequestedCenter: PointF? = null

    /**
     * Get source width.
     *
     * @return the source image width in pixels.
     */
    var sWidth = 0
        private set

    /**
     * Get source height.
     *
     * @return the source image height in pixels.
     */
    var sHeight = 0
        private set

    // Min scale allowed (prevent infinite zoom)
    private var minScale = minScale()

    // Is two-finger zooming in progress
    private var isZooming = false

    // Is one-finger panning in progress
    private var isPanning = false

    // Is quick-scale gesture in progress
    private var isQuickScaling = false

    // Max touches used in current gesture
    private var maxTouchCount = 0

    // Fling detector
    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    // Debug values
    private var vCenterStart: PointF? = null
    private var vDistStart = 0F
    private var quickScaleLastDistance = 0F
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    // Scale and center animation tracking
    private var anim: Anim? = null

    /**
     * Call to find whether the view is initialised, has dimensions, and will display an image on
     * the next draw. If a preview has been provided, it may be the preview that will be displayed
     * and the full size image may still be loading. If no preview was provided, this is called once
     * the base layer tiles of the full size image are loaded.
     *
     * @return true if the view is ready to display an image and accept touch gestures.
     */
    var isReady = false
        private set

    // Event listener
    private var onImageEventListener: OnImageEventListener? = null

    // Scale and center listener
    private var onStateChangedListener: OnStateChangedListener? = null

    // Long click listener
    private var onLongClickListener: OnLongClickListener? = null

    // Paint objects created once and reused for efficiency
    private val bitmapPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
    }

    // Volatile fields used to reduce object creation
    private var satTemp: ScaleAndTranslate? = null
    private val mtrx = Matrix()

    init {
        setMinimumDpi(160)
        setGestureDetector(context)
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI.
     *
     * @param bitmap Image source.
     */
    fun setImage(bitmap: Bitmap) {
        reset(true)
        onImageLoaded(bitmap)
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private fun reset(newImage: Boolean) {
        scale = 0F
        scaleStart = 0F
        vTranslate = null
        vTranslateStart = null
        vTranslateBefore = null
        pendingScale = -1F
        sPendingCenter = null
        sRequestedCenter = null
        isZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        vCenterStart = null
        vDistStart = 0F
        quickScaleLastDistance = 0F
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null
        satTemp = null
        mtrx.reset()
        if (newImage) {
            if (!bitmapIsCached) {
                bitmap?.recycle()
            }
            sWidth = 0
            sHeight = 0
            isReady = false
            bitmap = null
            bitmapIsCached = false
        }
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (panEnabled && isReady && vTranslate != null && e1 != null &&
                        (abs(e1.x - e2.x) > 50 || abs(e1.y - e2.y) > 50) &&
                        (abs(velocityX) > 500 || abs(velocityY) > 500) && !isZooming
                    ) {
                        val vTranslateEnd =
                            PointF(vTranslate!!.x + velocityX * 0.25F, vTranslate!!.y + velocityY * 0.25F)
                        val sCenterXEnd = (width / 2 - vTranslateEnd.x) / scale
                        val sCenterYEnd = (height / 2 - vTranslateEnd.y) / scale
                        AnimationBuilder(PointF(sCenterXEnd, sCenterYEnd))
                            .withEasing(EASE_OUT_QUAD)
                            .withPanLimited(false)
                            .withOrigin(ORIGIN_FLING)
                            .start()
                        return true
                    }
                    return super.onFling(e1, e2, velocityX, velocityY)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (isZoomEnabled && isReady && vTranslate != null) {
                        // Hacky solution for #15 - after a double tap the GestureDetector gets in a state
                        // where the next fling is ignored, so here we replace it with a new one.
                        setGestureDetector(context)
                        return if (isQuickScaleEnabled) {
                            // Store quick scale params. This will become either a double tap zoom or a
                            // quick scale depending on whether the user swipes.
                            vCenterStart = PointF(e.x, e.y)
                            vTranslateStart = PointF(vTranslate!!.x, vTranslate!!.y)
                            scaleStart = scale
                            isQuickScaling = true
                            isZooming = true
                            quickScaleLastDistance = -1F
                            quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                            quickScaleVStart = PointF(e.x, e.y)
                            quickScaleVLastPoint = PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                            quickScaleMoved = false
                            // We need to get events in onTouchEvent after this.
                            false
                        } else {
                            // Start double tap zoom animation.
                            viewToSourceCoord(PointF(e.x, e.y))?.let { center ->
                                doubleTapZoom(center, PointF(e.x, e.y))
                            }
                            true
                        }
                    }
                    return super.onDoubleTapEvent(e)
                }
            },
        )
        singleDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }
            },
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    /**
     * On resize, preserve center and scale. Various behaviours are possible, override this method to use another.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (isReady && center != null) {
            anim = null
            pendingScale = scale
            sPendingCenter = center
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth
                height = sHeight
            } else if (resizeHeight) {
                height = (sHeight.toDouble() / sWidth.toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth.toDouble() / sHeight.toDouble() * height).toInt()
            }
        }
        width = width.coerceAtLeast(suggestedMinimumWidth)
        height = height.coerceAtLeast(suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // During non-interruptible anims, ignore all touch events
        if (anim?.interruptible == false) {
            requestDisallowInterceptTouchEvent(true)
            return true
        }

        anim = null

        // Abort if not ready
        if (vTranslate == null) {
            singleDetector?.onTouchEvent(event)
            return true
        }
        // Detect flings, taps and double taps
        // May throw NPE
        runCatching {
            if (!isQuickScaling && detector?.onTouchEvent(event) != false) {
                isZooming = false
                isPanning = false
                maxTouchCount = 0
                return true
            }
        }

        if (vTranslateStart == null) {
            vTranslateStart = PointF(0F, 0F)
        }
        if (vTranslateBefore == null) {
            vTranslateBefore = PointF(0F, 0F)
        }
        if (vCenterStart == null) {
            vCenterStart = PointF(0F, 0F)
        }

        // Store current values so we can send an event if they change
        vTranslateBefore!!.set(vTranslate!!)
        val handled = onTouchEventInternal(event)
        sendStateChanged(scale, vTranslateBefore!!, ORIGIN_TOUCH)
        return handled || super.onTouchEvent(event)
    }

    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                anim = null
                requestDisallowInterceptTouchEvent(true)
                maxTouchCount = maxTouchCount.coerceAtLeast(touchCount)
                if (touchCount >= 2) {
                    if (isZoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        scaleStart = scale
                        vDistStart = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                        vCenterStart!![(event.getX(0) + event.getX(1)) / 2] = (event.getY(0) + event.getY(1)) / 2
                    } else {
                        // Abort all gestures on second touch
                        maxTouchCount = 0
                    }
                    // Cancel long click timer
                    longClickJob?.cancel()
                } else if (!isQuickScaling) {
                    // Start one-finger pan
                    vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                    vCenterStart!![event.x] = event.y

                    // Start long click timer
                    longClickJob = coroutineScope.launch {
                        if (onLongClickListener != null) {
                            delay(600)
                            super@SubsamplingScaleImageView.setOnLongClickListener(onLongClickListener)
                            performLongClick()
                            super@SubsamplingScaleImageView.setOnLongClickListener(null)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        val vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2
                        val distance = maxOf(
                            distance(vCenterStart!!.x, vCenterEndX, vCenterStart!!.y, vCenterEndY),
                            abs(vDistEnd - vDistStart),
                        )
                        if (isZoomEnabled && (distance > 5 || isPanning)) {
                            isZooming = true
                            isPanning = true
                            consumed = true
                            val previousScale = scale.toDouble()
                            scale = (vDistEnd / vDistStart * scaleStart).coerceAtMost(maxScale)
                            if (scale <= minScale()) {
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                vDistStart = vDistEnd
                                scaleStart = minScale()
                                vCenterStart!![vCenterEndX] = vCenterEndY
                                vTranslateStart!!.set(vTranslate!!)
                            } else if (panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                                val vLeftNow = vLeftStart * (scale / scaleStart)
                                val vTopNow = vTopStart * (scale / scaleStart)
                                vTranslate!!.x = vCenterEndX - vLeftNow
                                vTranslate!!.y = vCenterEndY - vTopNow
                                if (previousScale * sHeight < height && scale * sHeight >= height ||
                                    previousScale * sWidth < width && scale * sWidth >= width
                                ) {
                                    fitToBounds(true)
                                    vCenterStart!![vCenterEndX] = vCenterEndY
                                    vTranslateStart!!.set(vTranslate!!)
                                    scaleStart = scale
                                    vDistStart = vDistEnd
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate!!.x = width / 2 - scale * sRequestedCenter!!.x
                                vTranslate!!.y = height / 2 - scale * sRequestedCenter!!.y
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate!!.x = width / 2 - scale * (sWidth / 2)
                                vTranslate!!.y = height / 2 - scale * (sHeight / 2)
                            }
                            fitToBounds(true)
                        }
                    } else if (isQuickScaling) {
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        var dist = abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold
                        if (quickScaleLastDistance == -1F) {
                            quickScaleLastDistance = dist
                        }
                        val isUpwards = event.y > quickScaleVLastPoint!!.y
                        quickScaleVLastPoint!![0F] = event.y
                        val spanDiff = abs(1 - dist / quickScaleLastDistance) * 0.5F
                        if (spanDiff > 0.03F || quickScaleMoved) {
                            quickScaleMoved = true
                            var multiplier = 1F
                            if (quickScaleLastDistance > 0) {
                                multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
                            }
                            val previousScale = scale.toDouble()
                            scale = (scale * multiplier).coerceIn(minScale(), maxScale)
                            if (panEnabled) {
                                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                                val vLeftNow = vLeftStart * (scale / scaleStart)
                                val vTopNow = vTopStart * (scale / scaleStart)
                                vTranslate!!.x = vCenterStart!!.x - vLeftNow
                                vTranslate!!.y = vCenterStart!!.y - vTopNow
                                if (previousScale * sHeight < height && scale * sHeight >= height ||
                                    previousScale * sWidth < width && scale * sWidth >= width
                                ) {
                                    fitToBounds(true)
                                    vCenterStart!!.set(sourceToViewCoord(quickScaleSCenter)!!)
                                    vTranslateStart!!.set(vTranslate!!)
                                    scaleStart = scale
                                    dist = 0F
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate!!.x = width / 2 - scale * sRequestedCenter!!.x
                                vTranslate!!.y = height / 2 - scale * sRequestedCenter!!.y
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate!!.x = width / 2 - scale * (sWidth / 2)
                                vTranslate!!.y = height / 2 - scale * (sHeight / 2)
                            }
                        }
                        quickScaleLastDistance = dist
                        fitToBounds(true)
                        consumed = true
                    } else if (!isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.
                        val dx = abs(event.x - vCenterStart!!.x)
                        val dy = abs(event.y - vCenterStart!!.y)

                        // On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        val offset = density * 5
                        if (dx > offset || dy > offset || isPanning) {
                            consumed = true
                            vTranslate!!.x = vTranslateStart!!.x + (event.x - vCenterStart!!.x)
                            vTranslate!!.y = vTranslateStart!!.y + (event.y - vCenterStart!!.y)
                            val lastX = vTranslate!!.x
                            val lastY = vTranslate!!.y
                            fitToBounds(true)
                            val atXEdge = lastX != vTranslate!!.x
                            val atYEdge = lastY != vTranslate!!.y
                            val edgeXSwipe = atXEdge && dx > dy && !isPanning
                            val edgeYSwipe = atYEdge && dy > dx && !isPanning
                            val yPan = lastY == vTranslate!!.y && dy > offset * 3
                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                                isPanning = true
                            } else if (dx > offset || dy > offset) {
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount = 0
                                longClickJob?.cancel()
                                requestDisallowInterceptTouchEvent(false)
                            }
                            if (!panEnabled) {
                                vTranslate!!.x = vTranslateStart!!.x
                                vTranslate!!.y = vTranslateStart!!.y
                                requestDisallowInterceptTouchEvent(false)
                            }
                        }
                    }
                }
                if (consumed) {
                    longClickJob?.cancel()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                longClickJob?.cancel()
                if (isQuickScaling) {
                    isQuickScaling = false
                    if (!quickScaleMoved && quickScaleSCenter != null && vCenterStart != null) {
                        doubleTapZoom(quickScaleSCenter!!, vCenterStart!!)
                    }
                }
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true
                        vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                        if (event.actionIndex == 1) {
                            vCenterStart!![event.getX(0)] = event.getY(0)
                        } else {
                            vCenterStart!![event.getX(1)] = event.getY(1)
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false
                        maxTouchCount = 0
                    }
                    // Trigger load of tiles now required
                    return true
                }
                if (touchCount == 1) {
                    isZooming = false
                    isPanning = false
                    maxTouchCount = 0
                }
                return true
            }
        }
        return false
    }

    private fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * Double tap zoom handler triggered from gesture detector or on touch, depending on whether
     * quick scale is enabled.
     */
    private fun doubleTapZoom(sCenter: PointF, vFocus: PointF) {
        if (!panEnabled) {
            if (sRequestedCenter != null) {
                // With a center specified from code, zoom around that point.
                sCenter.x = sRequestedCenter!!.x
                sCenter.y = sRequestedCenter!!.y
            } else {
                // With no requested center, scale around the image center.
                sCenter.x = (sWidth / 2).toFloat()
                sCenter.y = (sHeight / 2).toFloat()
            }
        }
        val doubleTapZoomScale = doubleTapZoomScale.coerceAtMost(maxScale)
        val zoomIn = scale <= doubleTapZoomScale * 0.9 || scale == minScale
        val targetScale = if (zoomIn) doubleTapZoomScale else minScale()
        if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter)
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn || !panEnabled) {
            AnimationBuilder(targetScale, sCenter)
                .withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong())
                .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                .start()
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_FIXED) {
            AnimationBuilder(targetScale, sCenter, vFocus)
                .withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong())
                .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                .start()
        }
        invalidate()
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is setup, tiles are displayed as they are loaded.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || width == 0 || height == 0) {
            return
        }

        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
        // dimensions and therefore the first opportunity to set scale and translate. If this call returns
        // false there is nothing to be drawn so return immediately.
        if (!checkReady()) {
            return
        }

        // Set scale and translate before draw.
        preDraw()

        // If animating scale, calculate current scale and center with easing equations
        if (anim != null && anim!!.vFocusStart != null) {
            // Store current values so we can send an event if they change
            val scaleBefore = scale
            if (vTranslateBefore == null) {
                vTranslateBefore = PointF(0F, 0F)
            }
            vTranslateBefore!!.set(vTranslate!!)
            var scaleElapsed = System.currentTimeMillis() - anim!!.time
            val finished = scaleElapsed > anim!!.duration
            scaleElapsed = scaleElapsed.coerceAtMost(anim!!.duration)
            scale = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.scaleStart,
                anim!!.scaleEnd - anim!!.scaleStart,
                anim!!.duration,
            )

            // Apply required animation to the focal point
            val vFocusNowX = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.vFocusStart!!.x,
                anim!!.vFocusEnd.x - anim!!.vFocusStart!!.x,
                anim!!.duration,
            )
            val vFocusNowY = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.vFocusStart!!.y,
                anim!!.vFocusEnd.y - anim!!.vFocusStart!!.y,
                anim!!.duration,
            )
            // Find out where the focal point is at this scale and adjust its position to follow the animation path
            vTranslate!!.x -= sourceToViewX(anim!!.sCenterEnd.x) - vFocusNowX
            vTranslate!!.y -= sourceToViewY(anim!!.sCenterEnd.y) - vFocusNowY

            // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
            fitToBounds(finished || anim!!.scaleStart == anim!!.scaleEnd)
            sendStateChanged(scaleBefore, vTranslateBefore!!, anim!!.origin)
            if (finished) {
                anim = null
            }
            invalidate()
        }
        if (bitmap != null && !bitmap!!.isRecycled) {
            val xScale = scale
            val yScale = scale
            mtrx.reset()
            mtrx.postScale(xScale, yScale)
            mtrx.postTranslate(vTranslate!!.x, vTranslate!!.y)
            canvas.drawBitmap(bitmap!!, mtrx, bitmapPaint)
        }
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    private fun checkReady(): Boolean {
        val ready = width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && bitmap != null
        if (!isReady && ready) {
            preDraw()
            isReady = true
            onImageEventListener?.onReady()
        }
        return ready
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale >= 0) {
            scale = pendingScale
            if (vTranslate == null) {
                vTranslate = PointF()
            }
            vTranslate!!.x = width / 2 - scale * sPendingCenter!!.x
            vTranslate!!.y = height / 2 - scale * sPendingCenter!!.y
            sPendingCenter = null
            pendingScale = -1F
            fitToBounds(true)
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false)
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     *
     * @param maybeCenter Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat    The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    private fun fitToBounds(maybeCenter: Boolean, sat: ScaleAndTranslate) {
        val center = if (panLimit == PAN_LIMIT_OUTSIDE && isReady) {
            false
        } else {
            maybeCenter
        }
        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val scaleWidth = scale * sWidth
        val scaleHeight = scale * sHeight
        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            vTranslate.x = vTranslate.x.coerceAtLeast(width / 2 - scaleWidth)
            vTranslate.y = vTranslate.y.coerceAtLeast(height / 2 - scaleHeight)
        } else if (center) {
            vTranslate.x = vTranslate.x.coerceAtLeast(width - scaleWidth)
            vTranslate.y = vTranslate.y.coerceAtLeast(height - scaleHeight)
        } else {
            vTranslate.x = vTranslate.x.coerceAtLeast(-scaleWidth)
            vTranslate.y = vTranslate.y.coerceAtLeast(-scaleHeight)
        }

        // Asymmetric padding adjustments
        val xPaddingRatio =
            if (paddingLeft > 0 || paddingRight > 0) paddingLeft / (paddingLeft + paddingRight).toFloat() else 0.5F
        val yPaddingRatio =
            if (paddingTop > 0 || paddingBottom > 0) paddingTop / (paddingTop + paddingBottom).toFloat() else 0.5F

        val maxTx: Float
        val maxTy: Float
        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            maxTx = (width / 2F).coerceAtLeast(0F)
            maxTy = (height / 2F).coerceAtLeast(0F)
        } else if (center) {
            maxTx = ((width - scaleWidth) * xPaddingRatio).coerceAtLeast(0F)
            maxTy = ((height - scaleHeight) * yPaddingRatio).coerceAtLeast(0F)
        } else {
            maxTx = width.toFloat().coerceAtLeast(0F)
            maxTy = height.toFloat().coerceAtLeast(0F)
        }

        vTranslate.x = vTranslate.x.coerceAtMost(maxTx)
        vTranslate.y = vTranslate.y.coerceAtMost(maxTy)
        sat.scale = scale
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    private fun fitToBounds(center: Boolean) {
        var init = false
        if (vTranslate == null) {
            init = true
            vTranslate = PointF(0F, 0F)
        }
        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0F, PointF(0F, 0F))
        }
        satTemp!!.scale = scale
        satTemp!!.vTranslate.set(vTranslate!!)
        fitToBounds(center, satTemp!!)
        scale = satTemp!!.scale
        vTranslate!!.set(satTemp!!.vTranslate)
        if (init) {
            vTranslate!!.set(vTranslateForSCenter((sWidth / 2).toFloat(), (sHeight / 2).toFloat(), scale))
        }
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap) {
        // If actual dimensions don't match the declared size, reset everything.
        if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap.width || sHeight != bitmap.height)) {
            reset(false)
        }
        if (!this.bitmapIsCached) {
            this.bitmap?.recycle()
        }
        this.bitmapIsCached = true
        this.bitmap = bitmap
        sWidth = bitmap.width
        sHeight = bitmap.height
        checkReady()
        invalidate()
        requestLayout()
    }

    /**
     * Pythagoras distance between two points.
     */
    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
     * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
     * but state (scale and center) is forgotten. You can restore these yourself if required.
     */
    fun recycle() {
        reset(true)
    }

    /**
     * Convert screen to source x coordinate.
     */
    private fun viewToSourceX(vx: Float): Float {
        return if (vTranslate == null) Float.NaN else (vx - vTranslate!!.x) / scale
    }

    /**
     * Convert screen to source y coordinate.
     */
    private fun viewToSourceY(vy: Float): Float {
        return if (vTranslate == null) Float.NaN else (vy - vTranslate!!.y) / scale
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vxy: PointF): PointF? {
        return viewToSourceCoord(vxy.x, vxy.y, PointF())
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return a coordinate representing the corresponding source coordinate. This is the same instance passed to the sTarget param.
     */
    private fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }
        sTarget[viewToSourceX(vx)] = viewToSourceY(vy)
        return sTarget
    }

    /**
     * Convert source to view x coordinate.
     */
    private fun sourceToViewX(sx: Float): Float {
        return if (vTranslate == null) Float.NaN else sx * scale + vTranslate!!.x
    }

    /**
     * Convert source to view y coordinate.
     */
    private fun sourceToViewY(sy: Float): Float {
        return if (vTranslate == null) Float.NaN else sy * scale + vTranslate!!.y
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sxy: PointF?): PointF? {
        return sourceToViewCoord(sxy!!.x, sxy.y, PointF())
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    private fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }
        vTarget[sourceToViewX(sx)] = sourceToViewY(sy)
        return vTarget
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
    private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0F, PointF(0F, 0F))
        }
        satTemp!!.scale = scale
        satTemp!!.vTranslate[vxCenter - sCenterX * scale] = vyCenter - sCenterY * scale
        fitToBounds(true, satTemp!!)
        return satTemp!!.vTranslate
    }

    /**
     * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
     * pan limits, keeping the requested center as near to the middle of the screen as allowed.
     */
    private fun limitedSCenter(sCenterX: Float, sCenterY: Float, scale: Float, sTarget: PointF): PointF {
        val vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale)
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        val sx = (vxCenter - vTranslate.x) / scale
        val sy = (vyCenter - vTranslate.y) / scale
        sTarget[sx] = sy
        return sTarget
    }

    /**
     * Returns the minimum allowed scale.
     */
    private fun minScale(): Float {
        val vPadding = paddingBottom + paddingTop
        val hPadding = paddingLeft + paddingRight
        return when (minimumScaleType) {
            SCALE_TYPE_CENTER_INSIDE -> min(
                (width - hPadding) / sWidth.toFloat(),
                (height - vPadding) / sHeight.toFloat(),
            )
            SCALE_TYPE_CENTER_CROP -> max(
                (width - hPadding) / sWidth.toFloat(),
                (height - vPadding) / sHeight.toFloat(),
            )
            SCALE_TYPE_FIT_WIDTH -> (width - hPadding) / sWidth.toFloat()
            SCALE_TYPE_FIT_HEIGHT -> (height - vPadding) / sHeight.toFloat()
            SCALE_TYPE_ORIGINAL_SIZE -> 1F
            SCALE_TYPE_SMART_FIT -> if (sHeight > sWidth) {
                // Fit to width
                (width - hPadding) / sWidth.toFloat()
            } else {
                // Fit to height
                (height - vPadding) / sHeight.toFloat()
            }
            SCALE_TYPE_CUSTOM -> minScale
            else -> min((width - hPadding) / sWidth.toFloat(), (height - vPadding) / sHeight.toFloat())
        }
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    private fun limitedScale(targetScale: Float): Float {
        return targetScale.coerceIn(minScale(), maxScale)
    }

    /**
     * Apply a selected type of easing.
     *
     * @param type     Easing type, from static fields
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun ease(type: Int, time: Long, from: Float, change: Float, duration: Long): Float {
        return when (type) {
            EASE_IN_OUT_QUAD -> easeInOutQuad(time, from, change, duration)
            EASE_OUT_QUAD -> easeOutQuad(time, from, change, duration)
            else -> throw IllegalStateException("Unexpected easing type: $type")
        }
    }

    /**
     * Quadratic easing for fling. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        val progress = time.toFloat() / duration.toFloat()
        return -change * progress * (progress - 2) + from
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeInOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        var timeF = time / (duration / 2F)
        return if (timeF < 1) {
            change / 2f * timeF * timeF + from
        } else {
            timeF--
            -change / 2f * (timeF * (timeF - 2) - 1) + from
        }
    }

    /**
     * Calculate how much further the image can be panned in each direction. The results are set on
     * the supplied [RectF] and expressed as screen pixels. For example, if the image cannot be
     * panned any further towards the left, the value of [RectF.left] will be set to 0.
     *
     * @param vTarget target object for results. Re-use for efficiency.
     */
    fun getPanRemaining(vTarget: RectF) {
        if (!isReady) {
            return
        }
        val scaleWidth = scale * sWidth
        val scaleHeight = scale * sHeight
        when (panLimit) {
            PAN_LIMIT_CENTER -> {
                vTarget.top = (-(vTranslate!!.y - height / 2)).coerceAtLeast(0F)
                vTarget.left = (-(vTranslate!!.x - width / 2)).coerceAtLeast(0F)
                vTarget.bottom = (vTranslate!!.y - (height / 2 - scaleHeight)).coerceAtLeast(0F)
                vTarget.right = (vTranslate!!.x - (width / 2 - scaleWidth)).coerceAtLeast(0F)
            }
            PAN_LIMIT_OUTSIDE -> {
                vTarget.top = (-(vTranslate!!.y - height)).coerceAtLeast(0F)
                vTarget.left = (-(vTranslate!!.x - width)).coerceAtLeast(0F)
                vTarget.bottom = (vTranslate!!.y + scaleHeight).coerceAtLeast(0F)
                vTarget.right = (vTranslate!!.x + scaleWidth).coerceAtLeast(0F)
            }
            else -> {
                vTarget.top = (-vTranslate!!.y).coerceAtLeast(0F)
                vTarget.left = (-vTranslate!!.x).coerceAtLeast(0F)
                vTarget.bottom = (scaleHeight + vTranslate!!.y - height).coerceAtLeast(0F)
                vTarget.right = (scaleWidth + vTranslate!!.x - width).coerceAtLeast(0F)
            }
        }
    }

    /**
     * Set the pan limiting style. See static fields. Normally [.PAN_LIMIT_INSIDE] is best, for image galleries.
     *
     * @param panLimit a pan limit constant. See static fields.
     */
    fun setPanLimit(@PanLimit panLimit: Int) {
        this.panLimit = panLimit
        if (isReady) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * Set the minimum scale type. See static fields. Normally [.SCALE_TYPE_CENTER_INSIDE] is best, for image galleries.
     *
     * @param scaleType a scale type constant. See static fields.
     */
    fun setMinimumScaleType(@ScaleType scaleType: Int) {
        minimumScaleType = scaleType
        if (isReady) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * This is a screen density aware alternative to [.setMaxScale]; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     *
     * @param dpi Source image pixel density at maximum zoom.
     */
    fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        maxScale = averageDpi / dpi
    }

    /**
     * Returns the minimum allowed scale.
     *
     * @return the minimum scale as a source/view pixels ratio.
     */
    fun getMinScale(): Float {
        return minScale()
    }

    /**
     * Returns the source point at the center of the view.
     *
     * @return the source coordinates current at the center of the view.
     */
    val center: PointF?
        get() {
            val mX = width / 2F
            val mY = height / 2F
            return viewToSourceCoord(mX, mY)
        }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     *
     * @param scale   New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    fun setScaleAndCenter(scale: Float, sCenter: PointF?) {
        anim = null
        pendingScale = scale
        sPendingCenter = sCenter
        sRequestedCenter = sCenter
        invalidate()
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     *
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomScale(doubleTapZoomScale: Float) {
        this.doubleTapZoomScale = doubleTapZoomScale
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     *
     * @param doubleTapZoomStyle New value for zoom style.
     */
    fun setDoubleTapZoomStyle(@ZoomStyle doubleTapZoomStyle: Int) {
        this.doubleTapZoomStyle = doubleTapZoomStyle
    }

    /**
     * Set the duration of the double tap zoom animation.
     *
     * @param durationMs Duration in milliseconds.
     */
    fun setDoubleTapZoomDuration(durationMs: Int) {
        doubleTapZoomDuration = durationMs.coerceAtLeast(0)
    }

    /**
     * {@inheritDoc}
     */
    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        this.onLongClickListener = onLongClickListener
    }

    /**
     * Add a listener allowing notification of load and error events.
     *
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun setOnImageEventListener(onImageEventListener: OnImageEventListener?) {
        this.onImageEventListener = onImageEventListener
    }

    /**
     * Add a listener for pan and zoom events.
     *
     * @param onStateChangedListener an [OnStateChangedListener] instance.
     */
    fun setOnStateChangedListener(onStateChangedListener: OnStateChangedListener?) {
        this.onStateChangedListener = onStateChangedListener
    }

    private fun sendStateChanged(oldScale: Float, oldVTranslate: PointF, origin: Int) {
        if (scale != oldScale) {
            onStateChangedListener?.onScaleChanged(scale, origin)
        }
        if (vTranslate != oldVTranslate) {
            onStateChangedListener?.onCenterChanged(center, origin)
        }
    }

    /**
     * Creates a panning animation builder, that when started will animate the image to place the given coordinates of
     * the image in the center of the screen. If doing this would move the image beyond the edges of the screen, the
     * image is instead animated to move the center point as near to the center of the screen as is allowed - it's
     * guaranteed to be on screen.
     *
     * @param sCenter Target center point
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.AnimationBuilder.start] to start the anim.
     */
    fun animateCenter(sCenter: PointF): AnimationBuilder? {
        return if (isReady) AnimationBuilder(sCenter) else null
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this would move the image
     * beyond the panning limits, the image is automatically panned during the animation.
     *
     * @param scale   Target scale.
     * @param sCenter Target source center.
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.AnimationBuilder.start] to start the anim.
     */
    fun animateScaleAndCenter(scale: Float, sCenter: PointF?): AnimationBuilder? {
        return if (isReady) AnimationBuilder(scale, sCenter) else null
    }

    /**
     * An event listener, allowing subclasses and activities to be notified of significant events.
     */
    fun interface OnImageEventListener {
        /**
         * Called when the dimensions of the image and view are known, and either a preview image,
         * the full size image, or base layer tiles are loaded. This indicates the scale and translate
         * are known and the next draw will display an image. This event can be used to hide a loading
         * graphic, or inform a subclass that it is safe to draw overlays.
         */
        fun onReady()
    }

    /**
     * An event listener, allowing activities to be notified of pan and zoom events. Initialisation
     * and calls made by your code do not trigger events; touch events and animations do. Methods in
     * this listener will be called on the UI thread and may be called very frequently - your
     * implementation should return quickly.
     */
    interface OnStateChangedListener {
        /**
         * The scale has changed. Use with [.getMaxScale] and [.getMinScale] to determine
         * whether the image is fully zoomed in or out.
         *
         * @param newScale The new scale.
         * @param origin   Where the event originated from - one of [.ORIGIN_ANIM], [.ORIGIN_TOUCH].
         */
        fun onScaleChanged(newScale: Float, origin: Int)

        /**
         * The source center has been changed. This can be a result of panning or zooming.
         *
         * @param newCenter The new source center point.
         * @param origin    Where the event originated from - one of [.ORIGIN_ANIM], [.ORIGIN_TOUCH].
         */
        fun onCenterChanged(newCenter: PointF?, origin: Int)
    }

    /**
     * @param scaleStart Scale at start of anim
     * @param scaleEnd Scale at end of anim (target)
     * @param sCenterStart Source center point at start
     * @param sCenterEnd Source center point at end, adjusted for pan limits
     * @param sCenterEndRequested Source center point that was requested, without adjustments
     * @param vFocusStart View point that was double tapped
     * @param vFocusEnd Where the view focal point should be moved to during the anim
     * @param duration How long the anim takes
     * @param interruptible Whether the anim can be interrupted by a touch
     * @param easing Easing style
     * @param origin Animation origin (API, double tap or fling)
     * @param time Start time
     */
    private data class Anim(
        val scaleStart: Float,
        val scaleEnd: Float,
        val sCenterStart: PointF?,
        val sCenterEnd: PointF,
        val sCenterEndRequested: PointF,
        val vFocusStart: PointF?,
        val vFocusEnd: PointF,
        val duration: Long,
        val interruptible: Boolean,
        val easing: Int,
        val origin: Int,
        val time: Long = System.currentTimeMillis(),
    )

    private data class ScaleAndTranslate(var scale: Float, val vTranslate: PointF)

    /**
     * Builder class used to set additional options for a scale animation. Create an instance using [.animateScale],
     * then set your options and call [.start].
     */
    inner class AnimationBuilder {
        private val targetScale: Float
        private val targetSCenter: PointF?
        private val vFocus: PointF?
        private var duration: Long = 500
        private var easing = EASE_IN_OUT_QUAD
        private var origin = ORIGIN_ANIM
        private var interruptible = true
        private var panLimited = true

        constructor(sCenter: PointF) {
            targetScale = scale
            targetSCenter = sCenter
            vFocus = null
        }

        constructor(scale: Float, sCenter: PointF?) {
            targetScale = scale
            targetSCenter = sCenter
            vFocus = null
        }

        constructor(scale: Float, sCenter: PointF?, vFocus: PointF?) {
            targetScale = scale
            targetSCenter = sCenter
            this.vFocus = vFocus
        }

        /**
         * Desired duration of the anim in milliseconds. Default is 500.
         *
         * @param duration duration in milliseconds.
         * @return this builder for method chaining.
         */
        fun withDuration(duration: Long): AnimationBuilder {
            this.duration = duration
            return this
        }

        /**
         * Whether the animation can be interrupted with a touch. Default is true.
         *
         * @param interruptible interruptible flag.
         * @return this builder for method chaining.
         */
        fun withInterruptible(interruptible: Boolean): AnimationBuilder {
            this.interruptible = interruptible
            return this
        }

        /**
         * Set the easing style. See static fields. [.EASE_IN_OUT_QUAD] is recommended, and the default.
         *
         * @param easing easing style.
         * @return this builder for method chaining.
         */
        fun withEasing(@EasingStyle easing: Int): AnimationBuilder {
            this.easing = easing
            return this
        }

        /**
         * Only for internal use. When set to true, the animation proceeds towards the actual end point - the nearest
         * point to the center allowed by pan limits. When false, animation is in the direction of the requested end
         * point and is stopped when the limit for each axis is reached. The latter behaviour is used for flings but
         * nothing else.
         */
        fun withPanLimited(panLimited: Boolean): AnimationBuilder {
            this.panLimited = panLimited
            return this
        }

        /**
         * Only for internal use. Indicates what caused the animation.
         */
        fun withOrigin(origin: Int): AnimationBuilder {
            this.origin = origin
            return this
        }

        /**
         * Starts the animation.
         */
        fun start() {
            val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
            val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
            val targetScale = limitedScale(targetScale)
            val targetSCenter = if (panLimited) {
                limitedSCenter(
                    targetSCenter!!.x,
                    targetSCenter.y,
                    targetScale,
                    PointF(),
                )
            } else {
                targetSCenter!!
            }
            anim = Anim(
                scaleStart = scale,
                scaleEnd = targetScale,
                sCenterEndRequested = targetSCenter,
                sCenterStart = center,
                sCenterEnd = targetSCenter,
                vFocusStart = sourceToViewCoord(targetSCenter),
                vFocusEnd = PointF(
                    vxCenter.toFloat(),
                    vyCenter.toFloat(),
                ),
                duration = duration,
                interruptible = interruptible,
                easing = easing,
                origin = origin,
            )
            if (vFocus != null) {
                // Calculate where translation will be at the end of the anim
                val vTranslateXEnd = vFocus.x - targetScale * anim!!.sCenterStart!!.x
                val vTranslateYEnd = vFocus.y - targetScale * anim!!.sCenterStart!!.y
                val satEnd = ScaleAndTranslate(targetScale, PointF(vTranslateXEnd, vTranslateYEnd))
                // Fit the end translation into bounds
                fitToBounds(true, satEnd)
                // Adjust the position of the focus point at end so image will be in bounds
                anim!!.vFocusEnd.set(
                    vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                    vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd),
                )
            }
            invalidate()
        }
    }

    companion object {
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            ZOOM_FOCUS_FIXED,
            ZOOM_FOCUS_CENTER,
            ZOOM_FOCUS_CENTER_IMMEDIATE,
        )
        annotation class ZoomStyle

        /**
         * During zoom animation, keep the point of the image that was tapped in the same place, and scale the image around it.
         */
        const val ZOOM_FOCUS_FIXED = 1

        /**
         * During zoom animation, move the point of the image that was tapped to the center of the screen.
         */
        const val ZOOM_FOCUS_CENTER = 2

        /**
         * Zoom in to and center the tapped point immediately without animating.
         */
        const val ZOOM_FOCUS_CENTER_IMMEDIATE = 3

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            EASE_IN_OUT_QUAD,
            EASE_OUT_QUAD,
        )
        annotation class EasingStyle

        /**
         * Quadratic ease out. Not recommended for scale animation, but good for panning.
         */
        const val EASE_OUT_QUAD = 1

        /**
         * Quadratic ease in and out.
         */
        const val EASE_IN_OUT_QUAD = 2

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            PAN_LIMIT_INSIDE,
            PAN_LIMIT_OUTSIDE,
            PAN_LIMIT_CENTER,
        )
        annotation class PanLimit

        /**
         * Don't allow the image to be panned off screen. As much of the image as possible is always displayed, centered in the view when it is smaller. This is the best option for galleries.
         */
        const val PAN_LIMIT_INSIDE = 1

        /**
         * Allows the image to be panned until it is just off screen, but no further. The edge of the image will stop when it is flush with the screen edge.
         */
        const val PAN_LIMIT_OUTSIDE = 2

        /**
         * Allows the image to be panned until a corner reaches the center of the screen but no further. Useful when you want to pan any spot on the image to the exact center of the screen.
         */
        const val PAN_LIMIT_CENTER = 3

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            SCALE_TYPE_CENTER_INSIDE,
            SCALE_TYPE_CENTER_CROP,
            SCALE_TYPE_FIT_WIDTH,
            SCALE_TYPE_FIT_HEIGHT,
            SCALE_TYPE_ORIGINAL_SIZE,
            SCALE_TYPE_SMART_FIT,
            SCALE_TYPE_CUSTOM,
        )
        annotation class ScaleType

        /**
         * Scale the image so that both dimensions of the image will be equal to or less than the corresponding dimension of the view. The image is then centered in the view. This is the default behaviour and best for galleries.
         */
        const val SCALE_TYPE_CENTER_INSIDE = 1

        /**
         * Scale the image uniformly so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The image is then centered in the view.
         */
        const val SCALE_TYPE_CENTER_CROP = 2
        const val SCALE_TYPE_FIT_WIDTH = 3
        const val SCALE_TYPE_FIT_HEIGHT = 4
        const val SCALE_TYPE_ORIGINAL_SIZE = 5
        const val SCALE_TYPE_SMART_FIT = 6

        /**
         * Scale the image so that both dimensions of the image will be equal to or less than the maxScale and equal to or larger than minScale. The image is then centered in the view.
         */
        const val SCALE_TYPE_CUSTOM = 7

        /**
         * State change originated from animation.
         */
        const val ORIGIN_ANIM = 1

        /**
         * State change originated from touch gesture.
         */
        const val ORIGIN_TOUCH = 2

        /**
         * State change originated from a fling momentum anim.
         */
        const val ORIGIN_FLING = 3

        /**
         * State change originated from a double tap zoom anim.
         */
        const val ORIGIN_DOUBLE_TAP_ZOOM = 4
    }
}
