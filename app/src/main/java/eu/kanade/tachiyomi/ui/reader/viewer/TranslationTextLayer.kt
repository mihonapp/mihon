package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import eu.kanade.tachiyomi.data.translation.TranslatedPage
import eu.kanade.tachiyomi.data.translation.TranslatedTextBlock
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a page's translation on top of the page itself, in the page's own coordinate space.
 *
 * Because it is a child of the page view, the text moves and scales with the art: panning, zooming and scrolling
 * can't leave the translation behind.
 */
class TranslationTextLayer(context: Context) : View(context) {

    var page: TranslatedPage? = null
        set(value) {
            field = value
            lightCache.clear()
            invalidate()
        }

    /**
     * Maps a point of the page bitmap into this view. Zoomed readers need it; fit-width readers can leave it null.
     */
    var sourceToView: ((Float, Float) -> PointF?)? = null

    private val density = resources.displayMetrics.density

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val rect = RectF()

    /** Sampling the page on every redraw makes zooming in the paged reader stutter. */
    private val lightCache = HashMap<TranslatedTextBlock, Boolean>()

    init {
        // Taps belong to the page underneath, so the reader still reacts normally.
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val page = page ?: return
        val bitmap = page.bitmap
        if (bitmap.width <= 0 || bitmap.height <= 0 || width <= 0 || height <= 0) return

        val scaleX = width.toFloat() / bitmap.width
        val scaleY = height.toFloat() / bitmap.height
        val mapper = sourceToView
        val margin = 3f * density

        page.blocks.forEach { block ->
            mapBlock(block, mapper, scaleX, scaleY)
            if (rect.width() < 2f || rect.height() < 2f) return@forEach

            drawBlock(canvas, block, bitmap, rect, margin)
        }
    }

    private fun mapBlock(
        block: TranslatedTextBlock,
        mapper: ((Float, Float) -> PointF?)?,
        scaleX: Float,
        scaleY: Float,
    ) {
        val box = block.box
        if (mapper != null) {
            val topLeft = mapper(box.left.toFloat(), box.top.toFloat())
            val bottomRight = mapper(box.right.toFloat(), box.bottom.toFloat())
            if (topLeft != null && bottomRight != null) {
                rect.set(
                    min(topLeft.x, bottomRight.x),
                    min(topLeft.y, bottomRight.y),
                    max(topLeft.x, bottomRight.x),
                    max(topLeft.y, bottomRight.y),
                )
                return
            }
        }
        rect.set(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)
    }

    private fun drawBlock(
        canvas: Canvas,
        block: TranslatedTextBlock,
        bitmap: Bitmap,
        blockRect: RectF,
        margin: Float,
    ) {
        val text = block.translated
        if (text.isBlank()) return

        val originalWidth = blockRect.width()
        val originalHeight = blockRect.height()

        // Vertical Japanese columns are tall and narrow. Arabic and other horizontal scripts need room, so those
        // blocks get a wider box; everything else stays close to the original lettering.
        val aspect = originalHeight / max(originalWidth, 1f)
        val widthMultiplier = when {
            aspect > 2.2f -> 2.0f
            aspect > 1.4f -> 1.4f
            else -> 1.2f
        }
        val maxBoxWidth = originalWidth * min(widthMultiplier + 0.4f, MAX_WIDTH_GROWTH)
        val textWidth = (maxBoxWidth - margin * 2f).toInt().coerceAtLeast(1)

        var textSize = (originalHeight / 2.4f).coerceIn(MIN_TEXT_SIZE_DP * density, MAX_TEXT_SIZE_DP * density)
        var layout = layout(text, textSize, textWidth)
        while (layout.height > originalHeight * MAX_GROWTH && textSize > MIN_TEXT_SIZE_DP * density) {
            textSize -= 0.5f * density
            layout = layout(text, textSize, textWidth)
        }

        val textHeight = layout.height.toFloat()
        var widestLine = 0f
        for (line in 0 until layout.lineCount) {
            widestLine = max(widestLine, layout.getLineWidth(line))
        }

        // The box has to hide the original lettering completely, otherwise its edges stay readable underneath, and
        // it has to contain the translation, otherwise lines spill over the bubble. Both are hard lower bounds.
        val coverWidth = min(max(originalWidth, widestLine + margin * 2f), maxBoxWidth)
        val coverHeight = max(originalHeight, textHeight + margin * 2f)
        val centerX = blockRect.centerX()
        val centerY = blockRect.centerY()

        val left = centerX - coverWidth / 2f
        val top = centerY - coverHeight / 2f

        val light = lightCache.getOrPut(block) { isLightAround(bitmap, block.box) }
        erasePaint.color = if (light) Color.WHITE else Color.rgb(14, 14, 14)
        val corner = 2f * density
        canvas.save()
        canvas.clipRect(left, top, left + coverWidth, top + coverHeight)
        canvas.drawRoundRect(
            left,
            top,
            left + coverWidth,
            top + coverHeight,
            corner,
            corner,
            erasePaint,
        )

        textPaint.color = if (light) Color.BLACK else Color.WHITE
        canvas.translate(centerX, top + (coverHeight - textHeight) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * Average brightness of the page under the original text, so the box blends into bubbles and dark panels alike.
     */
    private fun isLightAround(bitmap: Bitmap, box: Rect): Boolean {
        if (bitmap.config == Bitmap.Config.HARDWARE) return true

        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)

        val stepX = max(1, (right - left) / 8)
        val stepY = max(1, (bottom - top) / 8)
        var sum = 0.0
        var count = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                sum += Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
                count++
                x += stepX
            }
            y += stepY
        }
        return count == 0 || sum / count >= LUMA_THRESHOLD
    }

    private fun layout(text: String, textSize: Float, width: Int): StaticLayout {
        textPaint.textSize = textSize
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    companion object {
        private const val MIN_TEXT_SIZE_DP = 6f
        private const val MAX_TEXT_SIZE_DP = 40f
        private const val MAX_GROWTH = 1.8f
        private const val MAX_WIDTH_GROWTH = 2.2f
        private const val LUMA_THRESHOLD = 128.0
    }
}
