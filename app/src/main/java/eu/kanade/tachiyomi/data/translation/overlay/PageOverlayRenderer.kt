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
import eu.kanade.tachiyomi.data.translation.TranslationLog
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

    /**
     * @property drawn translations lettered in place over an erased bubble
     * @property captioned translations put in a caption box because the original could not be erased safely
     * @property duplicates boxes dropped because they repeated another box
     */
    class Result(
        val bytes: ByteArray,
        val mimeType: String,
        val drawn: Int = 0,
        val captioned: Int = 0,
        val duplicates: Int = 0,
    )

    private class Stats(val drawn: Int, val captioned: Int, val duplicates: Int)

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
     * @param log receives one line per decision (what was lettered, what became a caption box, what was dropped).
     * @throws TranslationException.CorruptPage when the page cannot be decoded or is too large for memory.
     */
    fun render(
        original: ByteArray,
        boxes: List<TextBox>,
        style: RenderStyle = RenderStyle.DEFAULT,
        log: (String) -> Unit = {},
    ): Result {
        var bitmap: Bitmap? = null
        try {
            bitmap = decodeMutable(original)
            val stats = drawTranslations(bitmap, boxes, style, log)
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, out)) {
                throw TranslationException.CorruptPage("could not encode the result")
            }
            return Result(out.toByteArray(), "image/jpeg", stats.drawn, stats.captioned, stats.duplicates)
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

    private fun drawTranslations(
        bitmap: Bitmap,
        allBoxes: List<TextBox>,
        style: RenderStyle,
        log: (String) -> Unit,
    ): Stats {
        if (style.placement == RenderStyle.Placement.BELOW) {
            drawCaptions(bitmap, allBoxes, style)
            return Stats(drawn = 0, captioned = allBoxes.size, duplicates = 0)
        }

        val imageRect = PixelRect(0, 0, bitmap.width, bitmap.height)
        val allPixelBoxes = allBoxes.map { it.toPixelRect(bitmap.width, bitmap.height) }
        val kept = BubbleGrouping.withoutDuplicates(allBoxes, allPixelBoxes)
        val boxes = kept.map { allBoxes[it] }
        val pixelBoxes = kept.map { allPixelBoxes[it] }
        allBoxes.indices.filter { it !in kept }.forEach {
            log("duplicate box dropped: ${TranslationLog.describe(allBoxes[it])}")
        }

        // First pass: trace every bubble on the untouched page, so one erased bubble cannot confuse the next.
        val regions = pixelBoxes.mapIndexed { index, box ->
            regionFor(bitmap, box, imageRect, traceBubble = boxes[index].kind == BoxKind.BUBBLE)
        }
        val groups = BubbleGrouping.group(pixelBoxes, regions)

        // Merge groups into single items (union box, joined text) and trace them again if they changed.
        class Item(
            val region: BubbleRegion,
            val text: String,
            val box: PixelRect,
            val safe: Boolean,
            val kind: BoxKind,
            val count: Int,
        )

        val all = groups.map { group ->
            val kind = boxes[group.first()].kind
            if (group.size == 1) {
                val region = regions[group.first()]
                Item(
                    region,
                    boxes[group.first()].text,
                    pixelBoxes[group.first()],
                    BubbleGrouping.canRenderSafely(kind, region),
                    kind,
                    1,
                )
            } else {
                val union = group.map { pixelBoxes[it] }.reduce(PixelRect::union)
                val text = BubbleGrouping.mergedText(group.map { boxes[it] })
                val region = regionFor(bitmap, union, imageRect, traceBubble = kind == BoxKind.BUBBLE)
                Item(region, text, union, BubbleGrouping.canRenderSafely(kind, region), kind, group.size)
            }
        }
        for (item in all) {
            val share = (item.region.inkErasedShare * PERCENT).toInt()
            val how = if (item.region.fromFloodFill) "bubble traced" else "open text"
            val decision = if (item.safe) {
                "lettered in place ($how, flat surroundings ${item.region.flatSurroundings}, erased ink $share%)"
            } else {
                "NOT safe to erase ($how, flat surroundings ${item.region.flatSurroundings}, " +
                    "erased ink $share%), goes into a caption box"
            }
            log(
                "${item.kind.name.lowercase()} of ${item.count} box(es) at " +
                    "[${item.box.left},${item.box.top},${item.box.right},${item.box.bottom}]: $decision; " +
                    "text \"${TranslationLog.clip(item.text)}\"",
            )
        }
        val items = all.filter { it.safe }
        // The original cannot be erased without damaging the page (artwork behind it, stylised lettering). The
        // translation then goes into a caption box next to it instead of being left out.
        val fallback = all.filter { !it.safe && it.text.isNotBlank() }

        val canvas = Canvas(bitmap)
        val erase = Paint().apply { this.style = Paint.Style.FILL }
        for (item in items) paintMask(bitmap, canvas, erase, item.region)

        // Keep the lettering of one page even: a bubble with little text must not get far bigger letters than the
        // typical bubble on the page.
        val fitted = items.map { fittedSize(it.text.trim(), it.region.textRect, bitmap.width, style) }
        val sorted = fitted.filter { it > 0f }.sorted()
        val cap = if (sorted.isEmpty()) 0f else sorted[sorted.size / 2] * SIZE_CAP_FACTOR
        if (sorted.isNotEmpty()) {
            log("font sizes fitted ${sorted.first().toInt()}-${sorted.last().toInt()} px, capped at ${cap.toInt()} px")
        }
        items.forEachIndexed { index, item ->
            val size = if (cap > 0f) min(fitted[index], cap) else fitted[index]
            drawText(canvas, item.text, item.region, bitmap.width, style, size)
        }

        if (fallback.isNotEmpty()) {
            val caption = captionStyleFor(bitmap)
            val placed = items.map { it.region.textRect }.toMutableList()
            for (item in fallback.sortedBy { it.box.top }) {
                drawCaptionBox(canvas, bitmap, item.box, item.text.trim(), placed, caption, style)
            }
        }
        return Stats(drawn = items.size, captioned = fallback.size, duplicates = allBoxes.size - boxes.size)
    }

    /**
     * Leaves the page as it is and puts every translation in a caption box next to its original text. For layouts
     * where erasing the original would damage the artwork.
     */
    private fun drawCaptions(bitmap: Bitmap, boxes: List<TextBox>, style: RenderStyle) {
        val canvas = Canvas(bitmap)
        val placed = mutableListOf<PixelRect>()
        val caption = captionStyleFor(bitmap)
        for (box in boxes.sortedBy { it.yMin }) {
            val text = box.text.trim()
            if (text.isEmpty()) continue
            drawCaptionBox(canvas, bitmap, box.toPixelRect(bitmap.width, bitmap.height), text, placed, caption, style)
        }
    }

    private class CaptionStyle(val background: Paint, val border: Paint, val radius: Float)

    private fun captionStyleFor(bitmap: Bitmap) = CaptionStyle(
        background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(CAPTION_BACKGROUND_ALPHA, 255, 255, 255)
            this.style = Paint.Style.FILL
        },
        border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(CAPTION_BORDER_ALPHA, 0, 0, 0)
            this.style = Paint.Style.STROKE
            strokeWidth = max(1f, bitmap.width * 0.002f)
        },
        radius = bitmap.width * CAPTION_CORNER_RATIO,
    )

    /** Draws [text] in a caption box placed next to [original] and remembers the box in [placed]. */
    private fun drawCaptionBox(
        canvas: Canvas,
        bitmap: Bitmap,
        original: PixelRect,
        text: String,
        placed: MutableList<PixelRect>,
        caption: CaptionStyle,
        style: RenderStyle,
    ) {
        val page = PixelRect(0, 0, bitmap.width, bitmap.height)
        val area = CaptionPlacer.place(original, page, placed)
        placed += area
        val rect = RectF(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), area.bottom.toFloat())
        canvas.drawRoundRect(rect, caption.radius, caption.radius, caption.background)
        canvas.drawRoundRect(rect, caption.radius, caption.radius, caption.border)

        val padding = max(2, (area.width * CAPTION_PADDING_RATIO).toInt())
        val inner = PixelRect(area.left + padding, area.top + padding, area.right - padding, area.bottom - padding)
        drawFitted(canvas, text, inner, Color.BLACK, outlined = false, bitmap.width, style)
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
