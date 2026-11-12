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
            invalidate()
        }

    /**
     * Maps a point of the page bitmap into this view. Zoomed readers need it; fit-width readers can leave it null.
     */
    var sourceToView: ((Float, Float) -> PointF?)? = null

    private val density = resources.displayMetrics.density

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        strokeJoin = Paint.Join.ROUND
    }

    private val rect = RectF()

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

            drawBlock(canvas, block.translated, rect, margin)
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

    private fun drawBlock(canvas: Canvas, text: String, blockRect: RectF, margin: Float) {
        if (text.isBlank()) return

        // Vertical Japanese columns are tall and narrow: widen a little so the translation doesn't end up one
        // letter per line, but stay close to the original text so neighbouring art isn't covered.
        val boxWidth = max(blockRect.width(), min(blockRect.height() * 0.35f, blockRect.width() * 1.35f))
        val boxHeight = blockRect.height()
        val left = blockRect.centerX() - boxWidth / 2f
        val top = blockRect.top

        val textWidth = (boxWidth - margin * 2f).toInt().coerceAtLeast(1)
        val maxTextHeight = boxHeight * MAX_GROWTH

        var textSize = (boxHeight / 2.4f).coerceIn(MIN_TEXT_SIZE_DP * density, MAX_TEXT_SIZE_DP * density)
        var layout = layout(text, textSize, textWidth)
        while (layout.height > maxTextHeight && textSize > MIN_TEXT_SIZE_DP * density) {
            textSize -= 0.5f * density
            layout = layout(text, textSize, textWidth)
        }

        val textHeight = layout.height.toFloat()
        val boxHeightDrawn = max(boxHeight, min(textHeight + margin * 2f, boxHeight * MAX_GROWTH))
        val boxTop = top + (boxHeight - boxHeightDrawn) / 2f

        canvas.save()
        canvas.translate(
            left + boxWidth / 2f,
            boxTop + margin + (boxHeightDrawn - margin * 2f - textHeight) / 2f,
        )

        // No background: the artwork stays visible, the letters carry a dark outline instead.
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = max(2f, textSize * 0.14f)
        textPaint.color = Color.BLACK
        layout.draw(canvas)

        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        layout.draw(canvas)

        canvas.restore()
    }

    private fun layout(text: String, textSize: Float, width: Int): StaticLayout {
        textPaint.textSize = textSize
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .build()
    }

    companion object {
        private const val MIN_TEXT_SIZE_DP = 7f
        private const val MAX_TEXT_SIZE_DP = 40f
        private const val MAX_GROWTH = 1.7f
    }
}
