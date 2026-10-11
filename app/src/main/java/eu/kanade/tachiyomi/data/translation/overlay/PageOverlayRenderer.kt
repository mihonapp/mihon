package eu.kanade.tachiyomi.data.translation.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import eu.kanade.tachiyomi.data.translation.TranslationException
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Draws translations over the original page (overlay mode):
 * erase the old text by repainting the bubble in its own colour, then draw the translation centred with
 * [StaticLayout], which handles right-to-left scripts such as Arabic.
 *
 * All geometry decisions are made by the pure classes in this package; this class only touches bitmaps.
 */
object PageOverlayRenderer {

    class Result(val bytes: ByteArray, val mimeType: String)

    private const val MAX_SIDE = 4096

    /** Lettering is never taller than this share of the page width. Anything bigger is artwork. */
    private const val MAX_LETTER_SIDE_RATIO = 0.07f
    private const val OUTPUT_QUALITY = 93
    private const val LUMINANCE_DARK_BACKGROUND = 110
    private const val PERCENT = 100f
    private const val CAPTION_BACKGROUND_ALPHA = 236
    private const val CAPTION_BORDER_ALPHA = 150
    private const val CAPTION_CORNER_RATIO = 0.012f
    private const val CAPTION_PADDING_RATIO = 0.04f

    /** No text on a page may be fitted bigger than this multiple of the page's median fitted size. */
    private const val SIZE_CAP_FACTOR = 1.35f

    /**
     * @param boxes translated text boxes. Must not be empty.
     * @param style font, text size and where the translation goes.
     * @throws TranslationException.CorruptPage when the page cannot be decoded or is too large for memory.
     */
    fun render(original: ByteArray, boxes: List<TextBox>, style: RenderStyle = RenderStyle.DEFAULT): Result {
        var bitmap: Bitmap? = null
        try {
            bitmap = decodeMutable(original)
            drawTranslations(bitmap, boxes, style)
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, out)) {
                throw TranslationException.CorruptPage("could not encode the result")
            }
            return Result(out.toByteArray(), "image/jpeg")
        } catch (e: OutOfMemoryError) {
            throw TranslationException.CorruptPage("page too large to process", e)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun decodeMutable(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw TranslationException.CorruptPage("not a decodable image")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw TranslationException.CorruptPage("not a decodable image")
    }

    private fun drawTranslations(bitmap: Bitmap, boxes: List<TextBox>, style: RenderStyle) {
        if (style.placement == RenderStyle.Placement.BELOW) {
            drawCaptions(bitmap, boxes, style)
            return
        }

        val imageRect = PixelRect(0, 0, bitmap.width, bitmap.height)
        val pixelBoxes = boxes.map { it.toPixelRect(bitmap.width, bitmap.height) }

        // First pass: trace every bubble on the untouched page, so one erased bubble cannot confuse the next.
        val regions = pixelBoxes.mapIndexed { index, box ->
            regionFor(bitmap, box, imageRect, traceBubble = boxes[index].kind == BoxKind.BUBBLE)
        }
        val groups = BubbleGrouping.group(pixelBoxes, regions)

        // Merge groups into single items (union box, joined text) and trace them again if they changed.
        class Item(val region: BubbleRegion, val text: String)

        val items = groups.mapNotNull { group ->
            val kind = boxes[group.first()].kind
            val item = if (group.size == 1) {
                Item(regions[group.first()], boxes[group.first()].text)
            } else {
                val union = group.map { pixelBoxes[it] }.reduce(PixelRect::union)
                val text = BubbleGrouping.mergedText(group.map { boxes[it] })
                Item(regionFor(bitmap, union, imageRect, traceBubble = kind == BoxKind.BUBBLE), text)
            }
            if (BubbleGrouping.canRenderSafely(kind, item.region)) item else null
        }

        val canvas = Canvas(bitmap)
        val erase = Paint().apply { this.style = Paint.Style.FILL }
        for (item in items) paintMask(bitmap, canvas, erase, item.region)

        // Keep the lettering of one page even: a bubble with little text must not get far bigger letters than the
        // typical bubble on the page.
        val fitted = items.map { fittedSize(it.text.trim(), it.region.textRect, bitmap.width, style) }
        val sorted = fitted.filter { it > 0f }.sorted()
        val cap = if (sorted.isEmpty()) 0f else sorted[sorted.size / 2] * SIZE_CAP_FACTOR
        items.forEachIndexed { index, item ->
            val size = if (cap > 0f) min(fitted[index], cap) else fitted[index]
            drawText(canvas, item.text, item.region, bitmap.width, style, size)
        }
    }

    /**
     * Leaves the page as it is and puts every translation in a caption box next to its original text. For layouts
     * where erasing the original would damage the artwork.
     */
    private fun drawCaptions(bitmap: Bitmap, boxes: List<TextBox>, style: RenderStyle) {
        val canvas = Canvas(bitmap)
        val page = PixelRect(0, 0, bitmap.width, bitmap.height)
        val placed = mutableListOf<PixelRect>()
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(CAPTION_BACKGROUND_ALPHA, 255, 255, 255)
            this.style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(CAPTION_BORDER_ALPHA, 0, 0, 0)
            this.style = Paint.Style.STROKE
            strokeWidth = max(1f, bitmap.width * 0.002f)
        }
        val radius = bitmap.width * CAPTION_CORNER_RATIO

        for (box in boxes.sortedBy { it.yMin }) {
            val text = box.text.trim()
            if (text.isEmpty()) continue
            val area = CaptionPlacer.place(box.toPixelRect(bitmap.width, bitmap.height), page, placed)
            placed += area
            val rect = RectF(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), area.bottom.toFloat())
            canvas.drawRoundRect(rect, radius, radius, background)
            canvas.drawRoundRect(rect, radius, radius, border)

            val padding = max(2, (area.width * CAPTION_PADDING_RATIO).toInt())
            val inner = PixelRect(area.left + padding, area.top + padding, area.right - padding, area.bottom - padding)
            drawFitted(canvas, text, inner, Color.BLACK, outlined = false, bitmap.width, style)
        }
    }

    private fun regionFor(bitmap: Bitmap, box: PixelRect, image: PixelRect, traceBubble: Boolean): BubbleRegion {
        val window = BubbleFinder.windowFor(box, image)
        val pixels = IntArray(window.width * window.height)
        bitmap.getPixels(pixels, 0, window.width, window.left, window.top, window.width, window.height)
        val config = BubbleFinder.Config(maxLetterSide = (bitmap.width * MAX_LETTER_SIDE_RATIO).toInt())
        return BubbleFinder.find(pixels, window, box, config, traceBubble)
    }

    /** Fills the mask with the bubble colour, one horizontal run at a time. */
    private fun paintMask(bitmap: Bitmap, canvas: Canvas, paint: Paint, region: BubbleRegion) {
        val w = region.window.width
        val h = region.window.height
        val fill = region.fill
        if (fill != null) {
            // Open text: replace the letters only, each pixel with its own colour.
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (region.mask[y * w + x]) {
                        bitmap.setPixel(region.window.left + x, region.window.top + y, fill[y * w + x])
                    }
                }
            }
            return
        }
        paint.color = region.background
        for (y in 0 until h) {
            var x = 0
            while (x < w) {
                if (!region.mask[y * w + x]) {
                    x++
                    continue
                }
                val start = x
                while (x < w && region.mask[y * w + x]) x++
                canvas.drawRect(
                    (region.window.left + start).toFloat(),
                    (region.window.top + y).toFloat(),
                    (region.window.left + x).toFloat(),
                    (region.window.top + y + 1).toFloat(),
                    paint,
                )
            }
        }
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        region: BubbleRegion,
        imageWidth: Int,
        style: RenderStyle,
        fittedSize: Float? = null,
    ) {
        val clean = text.trim()
        val area = region.textRect
        if (clean.isEmpty() || area.isEmpty) return

        // Open text keeps the colour of the original lettering and gets a thin contrasting outline, so it stays
        // readable on gradients and artwork. Bubbles use black or white depending on their colour.
        val letterColor = region.textColor
        val textColor = letterColor
            ?: if (luminance(region.background) < LUMINANCE_DARK_BACKGROUND) Color.WHITE else Color.BLACK
        drawFitted(canvas, clean, area, textColor, outlined = letterColor != null, imageWidth, style, fittedSize)
    }

    /** Largest size (before the style's size setting) at which [clean] fits [area]; 0 when there is nothing to draw. */
    private fun fittedSize(clean: String, area: PixelRect, imageWidth: Int, style: RenderStyle): Float {
        if (clean.isEmpty() || area.isEmpty) return 0f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typefaceFor(style.font) }
        val minSize = minFontSize(imageWidth)
        val maxSize = max(minSize, min(area.height * 0.9f, imageWidth * 0.05f))
        return FontFit.largestFitting(minSize, maxSize) { candidate ->
            val layout = layoutFor(clean, paint, candidate, area.width)
            layout.height <= area.height && widestLine(layout) <= area.width
        }
    }

    private fun minFontSize(imageWidth: Int) = max(6f, imageWidth * 0.007f)

    /** Draws [clean] centred in [area] at the largest size that fits, scaled by the style's size setting. */
    private fun drawFitted(
        canvas: Canvas,
        clean: String,
        area: PixelRect,
        textColor: Int,
        outlined: Boolean,
        imageWidth: Int,
        style: RenderStyle,
        fittedSize: Float? = null,
    ) {
        if (area.isEmpty) return
        // Not read inside apply {}: there "style" would mean the paint's own style.
        val face = typefaceFor(style.font)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = face
        }

        val minSize = minFontSize(imageWidth)
        val maxSize = max(minSize, min(area.height * 0.9f, imageWidth * 0.05f))
        val fitted = fittedSize ?: FontFit.largestFitting(minSize, maxSize) { candidate ->
            val layout = layoutFor(clean, paint, candidate, area.width)
            layout.height <= area.height && widestLine(layout) <= area.width
        }
        val size = max(minSize, fitted * style.sizePercent / PERCENT)
        val layout = layoutFor(clean, paint, size, area.width)

        canvas.save()
        canvas.clipRect(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), area.bottom.toFloat())
        val top = area.top + (area.height - layout.height) / 2f
        canvas.translate(area.left.toFloat(), max(area.top.toFloat(), top))
        if (outlined) {
            val outlinePaint = TextPaint(paint).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = max(1.5f, size * 0.12f)
                strokeJoin = Paint.Join.ROUND
                color = if (luminance(textColor) < LUMINANCE_DARK_BACKGROUND) Color.WHITE else Color.BLACK
            }
            layoutFor(clean, outlinePaint, size, area.width).draw(canvas)
            // layoutFor() set the size on the paint it was given, keep the fill paint in sync.
            paint.textSize = size
        }
        layout.draw(canvas)
        canvas.restore()
    }

    private fun typefaceFor(font: RenderStyle.Font): Typeface = when (font) {
        RenderStyle.Font.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        RenderStyle.Font.REGULAR -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        RenderStyle.Font.SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        // "casual" is Android's handwriting-style family. Scripts it lacks (such as Arabic) use the system font.
        RenderStyle.Font.COMIC -> Typeface.create("casual", Typeface.BOLD)
    }

    private fun layoutFor(text: String, paint: TextPaint, size: Float, width: Int): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    /** A single word wider than the area does not wrap, so the height check alone would let it overflow. */
    private fun widestLine(layout: StaticLayout): Float {
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = max(widest, layout.getLineWidth(i))
        return widest
    }

    private fun luminance(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }
}
