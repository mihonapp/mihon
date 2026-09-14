package eu.kanade.tachiyomi.ui.reader.cast

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a [CastState] on an external display.
 *
 * The reader owns the position; this view only applies the cast presentation settings. Everything
 * is laid out in a logical viewport (the screen rotated by [CastState.orientation]) that [onDraw]
 * maps back onto the screen. Decoding happens in [CastImageRepository], which pokes
 * [postInvalidate] through [CastImageRepository.onImageDecoded]; everything else runs on the main
 * thread.
 */
class CastPageView(
    context: Context,
    private val controller: CastController,
) : View(context) {

    private var state: CastState = controller.state.value

    private var shownChapterId = -1L
    private var shownPageIndex = -1

    private var hudText = ""
    private var hudShownAt = 0L
    private val hudRunnable = Runnable { invalidate() }

    /**
     * Last image drawn for each page key. Drawn while a decode at another width (zoom or strip
     * width change) is pending so pages don't flash back to a placeholder.
     */
    private val drawnImages = HashMap<String, CastImage>()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hudBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(HUD_BACKGROUND_ALPHA, 0, 0, 0)
    }
    private val hudTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val fontMetrics = Paint.FontMetrics()

    init {
        applyColors(state.background)
    }

    fun setState(state: CastState) {
        val previous = this.state
        this.state = state
        if (previous.background != state.background) applyColors(state.background)

        if (!state.active) {
            shownChapterId = -1L
            shownPageIndex = -1
            hudShownAt = 0L
            drawnImages.clear()
            invalidate()
            return
        }
        val position = state.position as? CastPosition.Page
        if (position != null && (state.chapterId != shownChapterId || position.index != shownPageIndex)) {
            shownChapterId = state.chapterId
            shownPageIndex = position.index
            if (position.index in state.pages.indices) showHud(position.index, state.pages.size)
            trimDecoded(state, position.index)
        }
        prefetch()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        prefetch()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hudRunnable)
        drawnImages.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val state = state
        canvas.drawColor(state.background.color)
        val width = width
        val height = height
        if (width == 0 || height == 0) return
        val vw = logicalWidth(state)
        val vh = logicalHeight(state)

        canvas.save()
        canvas.rotate(state.orientation.degrees.toFloat(), width / 2f, height / 2f)
        canvas.translate((width - vw) / 2f, (height - vh) / 2f)
        canvas.clipRect(0f, 0f, vw, vh)
        val position = state.position
        when {
            !state.active -> drawIdle(canvas, vw, vh)
            position is CastPosition.Transition -> drawTransition(canvas, position, vw, vh)
            position is CastPosition.Page && state.layoutMode == CastLayoutMode.CONTINUOUS ->
                drawContinuous(canvas, state, position, vw, vh)
            position is CastPosition.Page -> drawPaged(canvas, state, position, vw, vh)
        }
        drawHud(canvas, state, vw, vh)
        canvas.restore()
    }

    // region Layout helpers

    private fun logicalWidth(state: CastState): Float {
        return (if (state.orientation.swapsAxes) height else width).toFloat()
    }

    private fun logicalHeight(state: CastState): Float {
        return (if (state.orientation.swapsAxes) width else height).toFloat()
    }

    /** Height / width of the page; [PLACEHOLDER_ASPECT] until its dimensions are known. */
    private fun aspectOf(page: CastPageInfo): Float {
        if (page.hasDimensions) return page.height.toFloat() / page.width
        val dimensions = controller.images.dimensions(page) ?: return PLACEHOLDER_ASPECT
        return dimensions.height.toFloat() / dimensions.width
    }

    /** Width of the source image in pixels, 0 until known. */
    private fun sourceWidthOf(page: CastPageInfo): Int {
        if (page.hasDimensions) return page.width
        return controller.images.dimensions(page)?.width ?: 0
    }

    /**
     * Screen pixels per pixel of an image of [imageWidth] x [imageHeight] in paged layouts.
     * [sourceWidth] is the width of the original file, which [CastScaleMode.ORIGINAL] maps 1:1
     * to the screen regardless of the width the image was decoded at.
     */
    private fun pagedScale(
        state: CastState,
        imageWidth: Float,
        imageHeight: Float,
        sourceWidth: Float,
        vw: Float,
        vh: Float,
    ): Float {
        val base = when (state.scaleMode) {
            CastScaleMode.FIT_SCREEN -> min(vw / imageWidth, vh / imageHeight)
            CastScaleMode.FIT_WIDTH -> vw / imageWidth
            CastScaleMode.FIT_HEIGHT -> vh / imageHeight
            CastScaleMode.ORIGINAL -> sourceWidth / imageWidth
        }
        return base * state.zoomPercent / 100f
    }

    private fun pagedDecodeWidth(state: CastState, page: CastPageInfo, vw: Float, vh: Float): Int {
        val sourceWidth = sourceWidthOf(page)
        val imageWidth = if (sourceWidth > 0) sourceWidth.toFloat() else PLACEHOLDER_WIDTH
        val scale = pagedScale(state, imageWidth, imageWidth * aspectOf(page), imageWidth, vw, vh)
        return (imageWidth * scale).roundToInt().coerceIn(1, CastImageRepository.MAX_DECODE_WIDTH)
    }

    private fun stripWidth(state: CastState, vw: Float): Float = vw * state.stripWidthPercent / 100f

    private fun continuousDecodeWidth(stripWidth: Float): Int {
        return stripWidth.roundToInt().coerceIn(1, CastImageRepository.MAX_DECODE_WIDTH)
    }

    // endregion

    // region Cache management

    private fun prefetch() {
        val state = state
        if (!state.active || width == 0 || height == 0) return
        val position = state.position as? CastPosition.Page ?: return
        val vw = logicalWidth(state)
        val vh = logicalHeight(state)
        val continuous = state.layoutMode == CastLayoutMode.CONTINUOUS
        // Continuous strips only move forward; paged layouts keep the previous page warm.
        val window = if (continuous) {
            position.index..position.index + PREFETCH_AHEAD_CONTINUOUS
        } else {
            position.index - 1..position.index + PREFETCH_AHEAD_PAGED
        }
        val pinned = HashSet<String>()
        for (index in window) {
            state.pageInfo(index)?.let { pinned += it.key }
        }
        controller.images.setPinned(pinned)
        // Current page first so its decode is queued before the neighbours.
        prefetchPage(state, position.index, continuous, vw, vh)
        for (index in window) {
            if (index != position.index) prefetchPage(state, index, continuous, vw, vh)
        }
    }

    private fun prefetchPage(state: CastState, index: Int, continuous: Boolean, vw: Float, vh: Float) {
        val page = state.pageInfo(index) ?: return
        val targetWidth = if (continuous) {
            continuousDecodeWidth(stripWidth(state, vw))
        } else {
            pagedDecodeWidth(state, page, vw, vh)
        }
        controller.images.getDecodedOrLoad(page, targetWidth)
    }

    private fun trimDecoded(state: CastState, index: Int) {
        val keep = HashSet<String>()
        for (i in (index - TRIM_BEHIND)..(index + TRIM_AHEAD)) {
            val page = state.pageInfo(i) ?: continue
            keep += page.key
        }
        controller.images.trimDecoded(keep)
        drawnImages.keys.retainAll(keep)
    }

    /** The last image drawn for [page], or any decode of it at another width. */
    private fun fallbackImage(page: CastPageInfo): CastImage? {
        return drawnImages[page.key] ?: controller.images.decodedAny(page)
    }

    // endregion

    // region Drawing

    private fun drawPaged(canvas: Canvas, state: CastState, position: CastPosition.Page, vw: Float, vh: Float) {
        val page = state.pageInfo(position.index) ?: return
        val image = controller.images.getDecodedOrLoad(page, pagedDecodeWidth(state, page, vw, vh))
            ?: fallbackImage(page)
        val sourceWidth = image?.sourceWidth?.takeIf { it > 0 } ?: sourceWidthOf(page)
        val imageWidth = image?.width?.toFloat()
            ?: if (sourceWidth > 0) sourceWidth.toFloat() else PLACEHOLDER_WIDTH
        val imageHeight = image?.height?.toFloat() ?: (imageWidth * aspectOf(page))
        val scale = pagedScale(
            state = state,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            sourceWidth = if (sourceWidth > 0) sourceWidth.toFloat() else imageWidth,
            vw = vw,
            vh = vh,
        )
        val w = imageWidth * scale
        val h = imageHeight * scale
        val panX = if (w > vw) state.panX * (w - vw) / 2f else 0f
        val panY = if (h > vh) state.panY * (h - vh) / 2f else 0f
        val x = (vw - w) / 2f - panX
        val y = (vh - h) / 2f - panY
        if (image != null) {
            drawImage(canvas, image, x, y, scale, vh)
            drawnImages[page.key] = image
        } else {
            drawPlaceholder(canvas, page, x, y, w, h, vh)
        }
    }

    private fun drawContinuous(
        canvas: Canvas,
        state: CastState,
        position: CastPosition.Page,
        vw: Float,
        vh: Float,
    ) {
        val pages = state.pages
        if (position.index !in pages.indices) return
        val stripWidth = stripWidth(state, vw)
        val x = (vw - stripWidth) / 2f
        val decodeWidth = continuousDecodeWidth(stripWidth)
        val anchorHeight = stripWidth * aspectOf(pages[position.index])
        val anchorTop = -position.offset * anchorHeight

        var y = anchorTop
        var index = position.index
        while (index < pages.size && y < vh) {
            val page = pages[index]
            val h = if (index == position.index) anchorHeight else stripWidth * aspectOf(page)
            drawStripPage(canvas, page, x, y, stripWidth, h, decodeWidth, vh)
            y += h
            index++
        }
        y = anchorTop
        index = position.index - 1
        while (index >= 0 && y > 0f) {
            val page = pages[index]
            val h = stripWidth * aspectOf(page)
            y -= h
            drawStripPage(canvas, page, x, y, stripWidth, h, decodeWidth, vh)
            index--
        }
    }

    private fun drawStripPage(
        canvas: Canvas,
        page: CastPageInfo,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        decodeWidth: Int,
        vh: Float,
    ) {
        val image = controller.images.getDecodedOrLoad(page, decodeWidth) ?: fallbackImage(page)
        if (image == null) {
            drawPlaceholder(canvas, page, x, y, w, h, vh)
            return
        }
        drawImage(canvas, image, x, y, w / image.width, vh)
        drawnImages[page.key] = image
    }

    private fun drawImage(canvas: Canvas, image: CastImage, x: Float, y: Float, scale: Float, vh: Float) {
        val right = x + image.width * scale
        val tiles = image.tiles
        for (i in tiles.indices) {
            val tile = tiles[i]
            val bitmap = tile.bitmap
            val top = y + tile.top * scale
            val bottom = y + (tile.top + bitmap.height) * scale
            if (bottom <= 0f || top >= vh) continue
            rect.set(x, top, right, bottom)
            canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        }
    }

    private fun drawPlaceholder(
        canvas: Canvas,
        page: CastPageInfo,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        vh: Float,
    ) {
        if (y + h <= 0f || y >= vh) return
        val radius = vh * PLACEHOLDER_RADIUS
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, radius, radius, placeholderPaint)
        placeholderTextPaint.textSize = min(vh * PLACEHOLDER_TEXT_SIZE, h * 0.4f)
        placeholderTextPaint.getFontMetrics(fontMetrics)
        val baseline = y + h / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText((page.index + 1).toString(), x + w / 2f, baseline, placeholderTextPaint)
    }

    private fun drawTransition(canvas: Canvas, position: CastPosition.Transition, vw: Float, vh: Float) {
        val maxWidth = vw * TRANSITION_MAX_WIDTH
        titlePaint.textSize = vh * TITLE_TEXT_SIZE
        subtitlePaint.textSize = vh * SUBTITLE_TEXT_SIZE
        val title = TextUtils.ellipsize(position.title, titlePaint, maxWidth, TextUtils.TruncateAt.END)
        val subtitle = position.subtitle?.let {
            TextUtils.ellipsize(it, subtitlePaint, maxWidth, TextUtils.TruncateAt.END)
        }

        titlePaint.getFontMetrics(fontMetrics)
        val titleAscent = fontMetrics.ascent
        val titleHeight = fontMetrics.descent - fontMetrics.ascent
        var subtitleAscent = 0f
        var subtitleHeight = 0f
        if (subtitle != null) {
            subtitlePaint.getFontMetrics(fontMetrics)
            subtitleAscent = fontMetrics.ascent
            subtitleHeight = fontMetrics.descent - fontMetrics.ascent
        }
        val gap = if (subtitle != null) vh * TRANSITION_GAP else 0f

        var top = (vh - titleHeight - gap - subtitleHeight) / 2f
        canvas.drawText(title, 0, title.length, vw / 2f, top - titleAscent, titlePaint)
        if (subtitle != null) {
            top += titleHeight + gap
            canvas.drawText(subtitle, 0, subtitle.length, vw / 2f, top - subtitleAscent, subtitlePaint)
        }
    }

    private fun drawIdle(canvas: Canvas, vw: Float, vh: Float) {
        titlePaint.textSize = vh * TITLE_TEXT_SIZE
        titlePaint.getFontMetrics(fontMetrics)
        val baseline = vh / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(IDLE_TEXT, vw / 2f, baseline, titlePaint)
    }

    private fun drawHud(canvas: Canvas, state: CastState, vw: Float, vh: Float) {
        if (hudShownAt == 0L || state.pages.isEmpty() || state.position !is CastPosition.Page) return
        if (SystemClock.uptimeMillis() - hudShownAt >= HUD_DURATION_MS) return
        hudTextPaint.textSize = vh * HUD_TEXT_SIZE
        hudTextPaint.getFontMetrics(fontMetrics)
        val textWidth = hudTextPaint.measureText(hudText)
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val paddingX = textHeight * 0.6f
        val paddingY = textHeight * 0.3f
        val margin = vh * HUD_MARGIN
        rect.set(
            vw - margin - textWidth - 2 * paddingX,
            vh - margin - textHeight - 2 * paddingY,
            vw - margin,
            vh - margin,
        )
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, hudBackgroundPaint)
        canvas.drawText(hudText, rect.centerX(), rect.top + paddingY - fontMetrics.ascent, hudTextPaint)
    }

    private fun showHud(index: Int, count: Int) {
        hudText = "${index + 1} / $count"
        hudShownAt = SystemClock.uptimeMillis()
        removeCallbacks(hudRunnable)
        postDelayed(hudRunnable, HUD_DURATION_MS)
    }

    private fun applyColors(background: CastBackground) {
        val foreground = if (background == CastBackground.WHITE) Color.rgb(0x1C, 0x1B, 0x1F) else Color.WHITE
        titlePaint.color = foreground
        subtitlePaint.color = ColorUtils.setAlphaComponent(foreground, SUBTITLE_ALPHA)
        placeholderTextPaint.color = ColorUtils.setAlphaComponent(foreground, PLACEHOLDER_TEXT_ALPHA)
        placeholderPaint.color = ColorUtils.blendARGB(background.color, foreground, PLACEHOLDER_BLEND)
    }

    // endregion

    companion object {
        private const val IDLE_TEXT = "Mihon"

        private const val HUD_DURATION_MS = 2_500L
        private const val HUD_BACKGROUND_ALPHA = 0x99
        private const val HUD_TEXT_SIZE = 0.025f
        private const val HUD_MARGIN = 0.02f

        private const val SUBTITLE_ALPHA = 0xB3
        private const val PLACEHOLDER_TEXT_ALPHA = 0x99
        private const val PLACEHOLDER_BLEND = 0.08f

        private const val TITLE_TEXT_SIZE = 0.06f
        private const val SUBTITLE_TEXT_SIZE = 0.035f
        private const val TRANSITION_GAP = 0.02f
        private const val TRANSITION_MAX_WIDTH = 0.85f

        private const val PLACEHOLDER_TEXT_SIZE = 0.05f
        private const val PLACEHOLDER_RADIUS = 0.01f

        /** Synthetic page used for layout until the real dimensions are known. */
        private const val PLACEHOLDER_WIDTH = 1000f
        private const val PLACEHOLDER_ASPECT = 1.4f

        private const val PREFETCH_AHEAD_PAGED = CastImageRepository.PIN_WINDOW_SIZE - 2
        private const val PREFETCH_AHEAD_CONTINUOUS = CastImageRepository.PIN_WINDOW_SIZE - 1
        private const val TRIM_BEHIND = 2
        private const val TRIM_AHEAD = 4
    }
}
