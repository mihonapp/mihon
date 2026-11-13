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
import mihon.app.di.appGraph
import tachiyomi.domain.translation.model.TranslationTextColor
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

    private val preferences = context.appGraph.translationPreferences

    private val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val normalTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
    }

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** Sampling the page on every redraw makes zooming in the paged reader stutter. */
    private val lightCache = HashMap<TranslatedTextBlock, Boolean>()

    init {
        // Taps belong to the page underneath, so the reader still reacts normally.
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val page = page ?: return
        val bitmap = page.preview
        if (page.width <= 0 || page.height <= 0 || width <= 0 || height <= 0) return

        val scaleX = width.toFloat() / page.width
        val scaleY = height.toFloat() / page.height
        val mapper = sourceToView
        val margin = 3f * density

        val placed = page.blocks.mapNotNull { block ->
            val box = RectF()
            mapBlock(block, mapper, scaleX, scaleY, box)
            if (box.width() < 2f || box.height() < 2f) null else block to box
        }
        if (placed.isEmpty()) return

        // One letter size for the whole page: sizing every bubble on its own made neighbouring bubbles look like
        // different readers had pasted them in.
        val heights = placed.map { it.second.height() }.sorted()
        val reference = heights[(heights.size - 1) * 3 / 5]
        val baseTextSize = (reference / TEXT_HEIGHT_DIVISOR)
            .coerceIn(MIN_TEXT_SIZE_DP * density, MAX_TEXT_SIZE_DP * density)

        placed.forEach { (block, box) -> drawBlock(canvas, block, bitmap, page.previewScale, box, margin, baseTextSize) }
    }

    private fun mapBlock(
        block: TranslatedTextBlock,
        mapper: ((Float, Float) -> PointF?)?,
        scaleX: Float,
        scaleY: Float,
        into: RectF,
    ) {
        val box = block.box
        if (mapper != null) {
            val topLeft = mapper(box.left.toFloat(), box.top.toFloat())
            val bottomRight = mapper(box.right.toFloat(), box.bottom.toFloat())
            if (topLeft != null && bottomRight != null) {
                into.set(
                    min(topLeft.x, bottomRight.x),
                    min(topLeft.y, bottomRight.y),
                    max(topLeft.x, bottomRight.x),
                    max(topLeft.y, bottomRight.y),
                )
                return
            }
        }
        into.set(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)
    }

    private fun drawBlock(
        canvas: Canvas,
        block: TranslatedTextBlock,
        preview: Bitmap?,
        previewScale: Float,
        blockRect: RectF,
        margin: Float,
        baseTextSize: Float,
    ) {
        val text = preferences.letterCase.get().apply(block.translated)
        if (text.isBlank()) return

        // Set before measuring: the layout has to be built with the typeface it will be drawn with.
        textPaint.typeface = if (preferences.boldText.get()) boldTypeface else normalTypeface

        val originalWidth = blockRect.width()
        val originalHeight = blockRect.height()

        // Stay inside the bubble: a translation that spills over the art looks broken, so the text shrinks to fit
        // the original lettering area instead of the box growing around it.
        val boxWidth = originalWidth * MAX_WIDTH_GROWTH
        val textWidth = (boxWidth - margin * 2f).toInt().coerceAtLeast(1)

        var textSize = baseTextSize
        var layout = layout(text, textSize, textWidth)
        while (layout.height > originalHeight * MAX_GROWTH && textSize > MIN_TEXT_SIZE_DP * density) {
            textSize -= 0.5f * density
            layout = layout(text, textSize, textWidth)
        }

        val textHeight = layout.height.toFloat()

        // The box never shrinks below the original lettering and never lets the text escape sideways.
        val coverWidth = boxWidth
        val coverHeight = max(originalHeight, textHeight + margin * 2f)

        val centerX = blockRect.centerX()
        val centerY = blockRect.centerY()

        val left = centerX - coverWidth / 2f
        val top = centerY - coverHeight / 2f

        val chosenColor = TranslationTextColor.of(preferences.textColor.get())
        // A fixed colour still needs a readable plate, so the plate follows the chosen lettering brightness.
        val light = chosenColor?.let { isLightColor(it) } ?: lightCache.getOrPut(block) {
            isLightAround(preview, previewScale, block.box)
        }
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

        textPaint.color = chosenColor ?: if (light) Color.BLACK else Color.WHITE
        canvas.translate(centerX, top + (coverHeight - textHeight) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    /** True when the given colour is bright enough for a dark plate. */
    private fun isLightColor(color: Int): Boolean =
        Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 >= LUMA_THRESHOLD

    /**
     * Average brightness of the page under the original text, so the box blends into bubbles and dark panels alike.
     * It reads the thumbnail, whose coordinates are the page's multiplied by [previewScale].
     */
    private fun isLightAround(preview: Bitmap?, previewScale: Float, box: Rect): Boolean {
        if (preview == null || preview.config == Bitmap.Config.HARDWARE) return true

        val left = (box.left * previewScale).toInt().coerceIn(0, preview.width - 1)
        val top = (box.top * previewScale).toInt().coerceIn(0, preview.height - 1)
        val right = (box.right * previewScale).toInt().coerceIn(left + 1, preview.width)
        val bottom = (box.bottom * previewScale).toInt().coerceIn(top + 1, preview.height)

        val stepX = max(1, (right - left) / 6)
        val stepY = max(1, (bottom - top) / 6)
        var sum = 0.0
        var count = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = preview.getPixel(x, y)
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
        private const val MAX_TEXT_SIZE_DP = 28f
        private const val TEXT_HEIGHT_DIVISOR = 3.2f
        private const val MAX_GROWTH = 1.25f
        private const val MAX_WIDTH_GROWTH = 1.06f
        private const val LUMA_THRESHOLD = 128.0
    }
}
