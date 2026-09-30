package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Draws a very tall webtoon page as a stack of small immutable bitmaps ("strips").
 *
 * A single page bitmap can be tens of MB, and the GPU texture cache only holds a couple of them,
 * which makes the render thread re-upload whole pages every frame while two pages are on screen.
 * With strips only the ones intersecting the clip get uploaded, and immutable bitmaps are uploaded
 * lazily at draw time instead of eagerly on every frame.
 *
 * Ignores all touch events, because the webtoon viewer handles all the gestures.
 */
class WebtoonStripImageView(context: Context) : View(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private var strips: List<Bitmap> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dstRect = Rect()

    /**
     * Slices [bitmap] into strips off the main thread, then calls [onReady].
     * [bitmap] is recycled once it has been sliced.
     */
    fun setBitmap(bitmap: Bitmap, onReady: () -> Unit) {
        job?.cancel()
        val width = bitmap.width
        val height = bitmap.height
        job = scope.launch {
            val result = withContext(Dispatchers.Default) { slice(bitmap) }
            imageWidth = width
            imageHeight = height
            strips = result
            requestLayout()
            invalidate()
            onReady()
        }
    }

    fun recycle() {
        job?.cancel()
        job = null
        strips = emptyList()
        imageWidth = 0
        imageHeight = 0
        requestLayout()
    }

    private fun slice(source: Bitmap): List<Bitmap> {
        val config = source.config ?: Bitmap.Config.ARGB_8888
        val result = ArrayList<Bitmap>((source.height + STRIP_HEIGHT - 1) / STRIP_HEIGHT)
        var y = 0
        while (y < source.height) {
            val h = minOf(STRIP_HEIGHT, source.height - y)
            val part = Bitmap.createBitmap(source, 0, y, source.width, h)
            // Immutable copy: HWUI uploads it lazily and only when it is actually drawn.
            val strip = part.copy(config, false)
            if (part !== source) part.recycle()
            result.add(strip)
            y += h
        }
        source.recycle()
        return result
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (imageWidth > 0) {
            (width.toLong() * imageHeight / imageWidth).toInt()
        } else {
            0
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (strips.isEmpty() || imageWidth <= 0) return

        val scale = width.toFloat() / imageWidth
        val clipTop = canvas.clipBounds.top
        val clipBottom = canvas.clipBounds.bottom

        var srcY = 0
        for (strip in strips) {
            val top = (srcY * scale).roundToInt()
            val bottom = ((srcY + strip.height) * scale).roundToInt()
            srcY += strip.height
            if (bottom <= clipTop) continue
            if (top >= clipBottom) break
            dstRect.set(0, top, width, bottom)
            canvas.drawBitmap(strip, null, dstRect, paint)
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}

private const val STRIP_HEIGHT = 1024
