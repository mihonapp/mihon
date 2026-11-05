package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The area of a page that belongs to one speech bubble (or caption box).
 *
 * @property window region of the page that [mask] covers.
 * @property mask `window.width * window.height` flags, true for pixels that must be repainted with [background].
 * @property background colour of the bubble (ARGB).
 * @property textRect where the translation is drawn, in page coordinates.
 * @property fromFloodFill false when the bubble could not be traced (open text). Then only the letters are erased.
 */
class BubbleRegion(
    val window: PixelRect,
    val mask: BooleanArray,
    val background: Int,
    val textRect: PixelRect,
    val fromFloodFill: Boolean,
    /** When set, the mask pixels are replaced with these colours (window sized) instead of [background]. */
    val fill: IntArray? = null,
    /** Colour of the original lettering when it was detected (open text), else null. */
    val textColor: Int? = null,
    /** Area that counts as belonging to this region for grouping, when the mask only holds the letters. */
    val extent: PixelRect? = null,
    /**
     * Open text only: the page around the lettering is one flat colour. A speech bubble whose outline could not be
     * traced (dashed or broken outline) can then still be lettered in place without damaging artwork.
     */
    val flatSurroundings: Boolean = false,
    /**
     * Open text only: share of the lettering pixels that were really erased. Low when the lettering is stylised
     * artwork (logos, huge titles) that the eraser deliberately leaves alone. Drawing a translation over
     * lettering that is still visible would overlap it, so such boxes are skipped.
     */
    val inkErasedShare: Float = 1f,
) {
    /** True if the page pixel ([x], [y]) belongs to this bubble. */
    fun contains(x: Int, y: Int): Boolean {
        if (extent != null) return extent.contains(x, y)
        if (!window.contains(x, y)) return false
        return mask[(y - window.top) * window.width + (x - window.left)]
    }
}

/**
 * Pure pixel logic (no Android classes) that finds the real inner area of a bubble around a text box.
 *
 * Steps for one box:
 * 1. Sample the bubble colour just inside and just outside the box edge (median per channel).
 * 2. Flood fill outwards from the box edge through pixels of that colour until the bubble outline stops it.
 * 3. Add the whole padded box and any holes (letters that stick out), so no old text is left.
 * 4. If the fill leaked (open bubble, page background) treat it as open text and erase only the letters.
 * 5. Grow a rectangle from the box centre inside the traced area. The translation is drawn there.
 */
object BubbleFinder {

    class Config(
        /** Padding added around the text box so no letter edges stay visible (fraction of box size). */
        val boxPadding: Float = 0.08f,
        /** How far outside the box the fill may travel, as a multiple of the box's longer side. */
        val windowScale: Float = 1.5f,
        /** Upper limit of the window margin in pixels, to bound memory use on huge pages. */
        val maxWindowMargin: Int = 600,
        /** Max per-channel difference from the bubble colour for a pixel to count as bubble. */
        val colorTolerance: Int = 40,
        /** A traced area touching more than this fraction of the window border is treated as a leak. */
        val maxEdgeContact: Float = 0.25f,
        /** Fraction of the inscribed rectangle kept as margin for the text. */
        val textMargin: Float = 0.05f,
        /** Minimum share of a growth strip that must lie inside the bubble. */
        val growthCoverage: Float = 0.97f,
        /** Open text (no bubble outline): how far the erased area may grow vertically over leftover ink. */
        val inkExtendVertical: Float = 0.4f,
        /** Same, horizontally. Models are usually right about the width and wrong about the height. */
        val inkExtendHorizontal: Float = 0.1f,
        /**
         * Open text: largest height of a connected group of lettering pixels. Bigger groups are artwork (faces,
         * outlines, panel borders) and are never erased. The caller sets it from the page size.
         */
        val maxLetterSide: Int = Int.MAX_VALUE,
        /** A traced area whose mid-tone share is above this is gradient or artwork, not a flat bubble. */
        val maxMidToneShare: Float = 0.15f,
        /** Gap bridging for dashed outlines, as a share of the shorter side of the text box. */
        val gapCloseRatio: Float = 0.12f,
        /** Upper limit in pixels of the gap bridging radius. */
        val maxGapClose: Int = 10,
    )

    /** The part of the page that [find] needs pixels for. */
    fun windowFor(box: PixelRect, image: PixelRect, config: Config = Config()): PixelRect {
        val margin = (max(box.width, box.height) * config.windowScale).toInt().coerceIn(2, config.maxWindowMargin)
        return box.expandedWithin(margin, margin, image)
    }

    /**
     * @param windowPixels ARGB pixels of [window], row by row (stride = window.width).
     * @param box the text box in page coordinates, already clipped to the page.
     * @param traceBubble whether a filled bubble may be flood-filled and repainted. False keeps erasure glyph-only.
     * @param image the whole page, so a bubble cut off by the page edge is not mistaken for a leaking fill.
     */
    fun find(
        windowPixels: IntArray,
        window: PixelRect,
        box: PixelRect,
        config: Config = Config(),
        traceBubble: Boolean = true,
        image: PixelRect? = null,
    ): BubbleRegion {
        val w = window.width
        val h = window.height
        require(windowPixels.size >= w * h) { "windowPixels is smaller than the window" }

        val windowBounds = PixelRect(0, 0, w, h)
        val boxInWindow = PixelRect(
            box.left - window.left,
            box.top - window.top,
            box.right - window.left,
            box.bottom - window.top,
        ).coerceIn(windowBounds)

        val padX = max(1, (box.width * config.boxPadding).toInt())
        val padY = max(1, (box.height * config.boxPadding).toInt())
        val paddedBox = boxInWindow.expandedWithin(padX, padY, windowBounds)

        val background = sampleBackground(windowPixels, w, h, boxInWindow)
        if (!traceBubble) {
            return openText(windowPixels, window, boxInWindow, paddedBox, background, config)
        }

        // Sides of the window that lie on the page edge. A bubble cut off by the page edge touches such a side over
        // part of its length, which is no leak; a fill that escaped into the page background covers all of it.
        val sides = OpenSides(
            top = image != null && window.top <= image.top,
            bottom = image != null && window.bottom >= image.bottom,
            left = image != null && window.left <= image.left,
            right = image != null && window.right >= image.right,
        )
        // A bubble with a dashed or broken outline lets a plain flood fill escape, so a second try closes the gaps
        // of the outline first. The result is still the real bubble shape, not a rectangle.
        val firstRadius = (min(box.width, box.height) * config.gapCloseRatio).toInt().coerceIn(2, config.maxGapClose)
        // Radius 0 is the plain fill. Wider gaps need a bigger radius, so it grows until a try does not leak.
        val radii = listOf(0, firstRadius, firstRadius * 2, config.maxGapClose)
            .map { it.coerceAtMost(config.maxGapClose) }
            .distinct()
        for (radius in radii) {
            val traced = traceBubble(windowPixels, window, boxInWindow, paddedBox, background, config, sides, radius)
            if (traced != null) return traced
        }
        return openText(windowPixels, window, boxInWindow, paddedBox, background, config)
    }

    /** True when the filled area has a real amount of pixels outside [box], that is, it found the bubble around it. */
    private fun reachesBeyond(filled: BooleanArray, w: Int, box: PixelRect): Boolean {
        var outside = 0
        for (i in filled.indices) {
            if (!filled[i]) continue
            val x = i % w
            val y = i / w
            if (x < box.left || x >= box.right || y < box.top || y >= box.bottom) outside++
        }
        return outside > 2 * (box.width + box.height)
    }

    /** Sides of the window that lie on the page edge: a bubble cut by the page edge touches them legitimately. */
    private class OpenSides(val top: Boolean, val bottom: Boolean, val left: Boolean, val right: Boolean)

    /**
     * Traces the bubble around [boxInWindow] by flood fill. With [closeRadius] above 0 the outline is thickened by
     * that many pixels while filling, which bridges gaps in a dashed outline.
     *
     * @return the traced bubble, or null when the fill escaped, is not flat, or found nothing to start from.
     */
    private fun traceBubble(
        pixels: IntArray,
        window: PixelRect,
        boxInWindow: PixelRect,
        paddedBox: PixelRect,
        background: Int,
        config: Config,
        sides: OpenSides,
        closeRadius: Int,
    ): BubbleRegion? {
        val w = window.width
        val h = window.height
        val mask = BooleanArray(w * h)
        val seeds = collectSeeds(pixels, w, boxInWindow, background, config.colorTolerance)
        if (seeds.isEmpty()) return null
        if (closeRadius > 0) {
            floodClosed(pixels, w, h, seeds, background, config.colorTolerance, closeRadius, boxInWindow, mask)
        } else {
            floodFill(pixels, w, h, seeds, background, config.colorTolerance, mask)
        }
        val flooded = mask.copyOf()
        if (closeRadius > 0 && !reachesBeyond(flooded, w, paddedBox)) {
            // Thickened letters can wall off every seed. A fill that never left the text box says nothing about the
            // bubble, and painting the box as a rectangle is exactly what this try is meant to avoid.
            return null
        }
        fillRect(mask, w, paddedBox)
        if (config.maxLetterSide != Int.MAX_VALUE) {
            // The model's box often reaches over the bubble outline. Repainting the padded box as a whole would
            // wipe out a piece of the outline (and the page beside it), so take those pixels out again.
            protectOutline(pixels, w, h, mask, flooded, background, config, paddedBox)
        }
        fillHoles(mask, w, h)

        // A real bubble is closed by its outline, so it stays away from the window border. A fill that reaches
        // the border for a large part of its length has escaped into the page background. Sides on the page edge
        // do not count: a bubble cut off by the page edge reaches them.
        val leaked = edgeContact(mask, w, h, sides) > config.maxEdgeContact
        val notFlat = TextEraser.midToneShare(pixels, mask, background) > config.maxMidToneShare
        if (leaked || notFlat) return null

        val inscribed = growInside(mask, w, h, boxInWindow, config.growthCoverage)
        return BubbleRegion(
            window = window,
            mask = mask,
            background = background,
            textRect = shrink(inscribed, config.textMargin).offset(window.left, window.top),
            fromFloodFill = true,
        )
    }

    /**
     * Text on a plain page, a glowing window or artwork: no outline to trace. Only the letters are erased (see
     * [TextEraser]); if no lettering can be told apart from the background the padded box is painted flat.
     */
    private fun openText(
        pixels: IntArray,
        window: PixelRect,
        box: PixelRect,
        paddedBox: PixelRect,
        background: Int,
        config: Config,
    ): BubbleRegion {
        val w = window.width
        val h = window.height
        val textColor = TextEraser.detectTextColor(pixels, w, paddedBox, background)

        if (textColor == null) {
            // Nothing stands out from the background, so there is nothing to erase. Better to leave the page
            // alone than to paint a flat block over artwork.
            return BubbleRegion(
                window = window,
                mask = BooleanArray(w * h),
                background = background,
                textRect = shrink(paddedBox, config.textMargin).offset(window.left, window.top),
                fromFloodFill = false,
                extent = paddedBox.offset(window.left, window.top),
            )
        }

        val cleared = extendOverLetters(pixels, w, h, paddedBox, background, textColor, config)
        val erased = TextEraser.erase(
            pixels,
            w,
            h,
            cleared,
            background,
            textColor,
            TextEraser.dilationFor(box),
            config.maxLetterSide,
            paddedBox.expandedWithin(
                (paddedBox.width * 0.03f).toInt() + 2,
                (paddedBox.height * 0.15f).toInt() + 2,
                PixelRect(0, 0, w, h),
            ),
        )
        return BubbleRegion(
            window = window,
            mask = erased.mask,
            background = background,
            textRect = shrink(cleared, config.textMargin).offset(window.left, window.top),
            fromFloodFill = false,
            fill = erased.fill,
            textColor = textColor,
            extent = cleared.offset(window.left, window.top),
            flatSurroundings = isFlatAround(pixels, w, h, cleared, background, config.colorTolerance),
            inkErasedShare = erasedInkShare(pixels, erased.mask, w, cleared, background),
        )
    }

    /**
     * True when a thin ring just outside [rect] is (almost) all in the [background] colour: a plain page or the
     * inside of a bubble whose outline has gaps, as opposed to lettering over artwork or a gradient.
     */
    internal fun isFlatAround(
        pixels: IntArray,
        w: Int,
        h: Int,
        rect: PixelRect,
        background: Int,
        tolerance: Int,
    ): Boolean {
        val ring = max(3, min(rect.width, rect.height) / 6)
        val outer = rect.expandedWithin(ring, ring, PixelRect(0, 0, w, h))
        var total = 0
        var flat = 0
        for (y in outer.top until outer.bottom) {
            for (x in outer.left until outer.right) {
                if (rect.contains(x, y)) continue
                total++
                if (isBackground(pixels[y * w + x], background, tolerance)) flat++
            }
        }
        return total > 0 && flat >= total * MIN_FLAT_SHARE
    }

    /** Share of the clearly visible lettering pixels inside [rect] that [mask] covers. 1 when there are none. */
    internal fun erasedInkShare(
        pixels: IntArray,
        mask: BooleanArray,
        w: Int,
        rect: PixelRect,
        background: Int,
    ): Float {
        var ink = 0
        var erased = 0
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val index = y * w + x
                if (TextEraser.distance(pixels[index], background) < INK_DISTANCE) continue
                ink++
                if (mask[index]) erased++
            }
        }
        return if (ink == 0) 1f else erased.toFloat() / ink
    }

    // region Background colour

    /** Median colour of a thin ring just inside and just outside [box]. */
    internal fun sampleBackground(pixels: IntArray, w: Int, h: Int, box: PixelRect): Int {
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()

        fun add(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val p = pixels[y * w + x]
            reds += (p shr 16) and 0xFF
            greens += (p shr 8) and 0xFF
            blues += p and 0xFF
        }

        val ring = max(1, min(3, min(box.width, box.height) / 10))
        for (r in 0 until ring) {
            // Inside edge
            for (x in box.left until box.right) {
                add(x, box.top + r)
                add(x, box.bottom - 1 - r)
            }
            for (y in box.top until box.bottom) {
                add(box.left + r, y)
                add(box.right - 1 - r, y)
            }
            // Outside edge
            for (x in box.left - 1 - r until box.right + 1 + r) {
                add(x, box.top - 1 - r)
                add(x, box.bottom + r)
            }
            for (y in box.top - 1 - r until box.bottom + 1 + r) {
                add(box.left - 1 - r, y)
                add(box.right + r, y)
            }
        }
        if (reds.isEmpty()) return WHITE

        fun median(values: MutableList<Int>): Int {
            values.sort()
            return values[values.size / 2]
        }
        return (0xFF shl 24) or (median(reds) shl 16) or (median(greens) shl 8) or median(blues)
    }

    // endregion

    // region Flood fill

    private fun isBackground(pixel: Int, background: Int, tolerance: Int): Boolean {
        return abs(((pixel shr 16) and 0xFF) - ((background shr 16) and 0xFF)) <= tolerance &&
            abs(((pixel shr 8) and 0xFF) - ((background shr 8) and 0xFF)) <= tolerance &&
            abs((pixel and 0xFF) - (background and 0xFF)) <= tolerance
    }

    /** Pixels along the inner edge of the box that already have the bubble colour. */
    private fun collectSeeds(pixels: IntArray, w: Int, box: PixelRect, background: Int, tolerance: Int): IntArray {
        val seeds = ArrayList<Int>()
        fun check(x: Int, y: Int) {
            if (isBackground(pixels[y * w + x], background, tolerance)) seeds += y * w + x
        }
        if (box.isEmpty) return IntArray(0)
        for (x in box.left until box.right) {
            check(x, box.top)
            check(x, box.bottom - 1)
        }
        for (y in box.top until box.bottom) {
            check(box.left, y)
            check(box.right - 1, y)
        }
        return seeds.toIntArray()
    }

    private fun floodFill(
        pixels: IntArray,
        w: Int,
        h: Int,
        seeds: IntArray,
        background: Int,
        tolerance: Int,
        mask: BooleanArray,
    ) {
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (seed in seeds) {
            if (!mask[seed]) {
                mask[seed] = true
                queue[tail++] = seed
            }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0) tail = visit(index - 1, pixels, background, tolerance, mask, queue, tail)
            if (x < w - 1) tail = visit(index + 1, pixels, background, tolerance, mask, queue, tail)
            if (y > 0) tail = visit(index - w, pixels, background, tolerance, mask, queue, tail)
            if (y < h - 1) tail = visit(index + w, pixels, background, tolerance, mask, queue, tail)
        }
    }

    private fun visit(
        index: Int,
        pixels: IntArray,
        background: Int,
        tolerance: Int,
        mask: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (mask[index] || !isBackground(pixels[index], background, tolerance)) return tail
        mask[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun fillRect(mask: BooleanArray, w: Int, rect: PixelRect) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) mask[y * w + x] = true
        }
    }

    /** Marks unreachable non-mask pixels (enclosed by the mask) as part of the mask. */
    private fun fillHoles(mask: BooleanArray, w: Int, h: Int) {
        val outside = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        fun push(index: Int) {
            if (!mask[index] && !outside[index]) {
                outside[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until w) {
            push(x)
            push((h - 1) * w + x)
        }
        for (y in 0 until h) {
            push(y * w)
            push(y * w + w - 1)
        }
        while (head < queue.size && head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0) push(index - 1)
            if (x < w - 1) push(index + 1)
            if (y > 0) push(index - w)
            if (y < h - 1) push(index + w)
        }
        for (i in mask.indices) if (!mask[i] && !outside[i]) mask[i] = true
    }

    private fun edgeContact(mask: BooleanArray, w: Int, h: Int, sides: OpenSides): Float {
        var touching = 0
        var perimeter = 0

        fun add(count: Int, length: Int, onPageEdge: Boolean) {
            if (length <= 0) return
            // Only part of a page edge side is touched by a bubble that the page edge cuts off.
            if (onPageEdge && count.toFloat() / length <= PAGE_EDGE_PARTIAL_CONTACT) return
            touching += count
            perimeter += length
        }

        var top = 0
        var bottom = 0
        for (x in 0 until w) {
            if (mask[x]) top++
            if (mask[(h - 1) * w + x]) bottom++
        }
        var left = 0
        var right = 0
        for (y in 1 until h - 1) {
            if (mask[y * w]) left++
            if (mask[y * w + w - 1]) right++
        }
        add(top, w, sides.top)
        add(bottom, w, sides.bottom)
        add(left, max(0, h - 2), sides.left)
        add(right, max(0, h - 2), sides.right)
        return if (perimeter <= 0) 0f else touching.toFloat() / perimeter
    }

    /**
     * Like [floodFill], but ink is thickened by [radius] first so a gap in the outline narrower than twice the
     * radius cannot be crossed. Afterwards the fill grows back by the same radius over bubble coloured pixels only,
     * so it reaches the outline again without ever painting over it. Ink inside [box] (the lettering) is ignored.
     */
    private fun floodClosed(
        pixels: IntArray,
        w: Int,
        h: Int,
        seeds: IntArray,
        background: Int,
        tolerance: Int,
        radius: Int,
        box: PixelRect,
        mask: BooleanArray,
    ) {
        // The lettering inside the text box is not outline. Counting it would wall off the seeds on the box edge.
        val ink = BooleanArray(w * h) {
            val x = it % w
            val y = it / w
            val inBox = x >= box.left && x < box.right && y >= box.top && y < box.bottom
            !inBox && !isBackground(pixels[it], background, tolerance)
        }
        val barrier = dilate(ink, w, h, radius)
        val interior = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (seed in seeds) {
            if (!barrier[seed] && !interior[seed]) {
                interior[seed] = true
                queue[tail++] = seed
            }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0 && !interior[index - 1] && !barrier[index - 1]) {
                interior[index - 1] = true
                queue[tail++] = index - 1
            }
            if (x < w - 1 && !interior[index + 1] && !barrier[index + 1]) {
                interior[index + 1] = true
                queue[tail++] = index + 1
            }
            if (y > 0 && !interior[index - w] && !barrier[index - w]) {
                interior[index - w] = true
                queue[tail++] = index - w
            }
            if (y < h - 1 && !interior[index + w] && !barrier[index + w]) {
                interior[index + w] = true
                queue[tail++] = index + w
            }
        }
        val grown = dilate(interior, w, h, radius)
        for (i in 0 until w * h) mask[i] = interior[i] || (grown[i] && !ink[i])
    }

    /** Square dilation of [src] by [r] pixels, in two linear passes. */
    private fun dilate(src: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        if (r <= 0) return src.copyOf()
        val horizontal = BooleanArray(w * h)
        for (y in 0 until h) {
            var count = 0
            for (x in 0 until min(r, w)) if (src[y * w + x]) count++
            for (x in 0 until w) {
                val add = x + r
                if (add < w && src[y * w + add]) count++
                val remove = x - r - 1
                if (remove >= 0 && src[y * w + remove]) count--
                horizontal[y * w + x] = count > 0
            }
        }
        val out = BooleanArray(w * h)
        for (x in 0 until w) {
            var count = 0
            for (y in 0 until min(r, h)) if (horizontal[y * w + x]) count++
            for (y in 0 until h) {
                val add = y + r
                if (add < h && horizontal[add * w + x]) count++
                val remove = y - r - 1
                if (remove >= 0 && horizontal[remove * w + x]) count--
                out[y * w + x] = count > 0
            }
        }
        return out
    }

    // endregion

    // region Outline protection

    /**
     * Takes bubble outline and artwork pixels out of [mask] again after the padded box was added to it.
     *
     * Ink (anything that is not the bubble colour) forms connected groups. A group that is taller than a letter and
     * reaches outside the padded box is the bubble outline, a panel border or artwork: it is kept. Lettering is
     * small, or lies inside the box. Afterwards only mask pixels still connected to the traced bubble interior stay,
     * so the part of the padded box that lies beyond the outline is not repainted either.
     */
    private fun protectOutline(
        pixels: IntArray,
        w: Int,
        h: Int,
        mask: BooleanArray,
        flooded: BooleanArray,
        background: Int,
        config: Config,
        paddedBox: PixelRect,
    ) {
        val maxWidth = config.maxLetterSide.toLong() * 3
        val slack = max(3, min(paddedBox.width, paddedBox.height) / 8)
        val limit = paddedBox.expandedWithin(slack, slack, PixelRect(0, 0, w, h))

        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (seen[start] || isBackground(pixels[start], background, config.colorTolerance)) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = start % w
            var maxX = minX
            var minY = start / w
            var maxY = minY
            while (head < tail) {
                val index = queue[head++]
                val x = index % w
                val y = index / w
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val next = ny * w + nx
                        if (!seen[next] && !isBackground(pixels[next], background, config.colorTolerance)) {
                            seen[next] = true
                            queue[tail++] = next
                        }
                    }
                }
            }
            val large = maxY - minY + 1 > config.maxLetterSide || maxX - minX + 1 > maxWidth
            val insideBox = minX >= limit.left && maxX < limit.right && minY >= limit.top && maxY < limit.bottom
            if (large && !insideBox) {
                for (i in 0 until tail) mask[queue[i]] = false
            }
        }

        // Keep only what is still connected to the traced interior.
        val keep = BooleanArray(w * h)
        var head = 0
        var tail = 0

        fun push(index: Int) {
            if (mask[index] && !keep[index]) {
                keep[index] = true
                queue[tail++] = index
            }
        }
        for (i in 0 until w * h) if (flooded[i]) push(i)
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0) push(index - 1)
            if (x < w - 1) push(index + 1)
            if (y > 0) push(index - w)
            if (y < h - 1) push(index + w)
        }
        for (i in 0 until w * h) mask[i] = keep[i]
    }

    // endregion

    // region Leftover ink

    /**
     * Grows [start] one row or column at a time while the strip next to it still holds lettering (pixels close to
     * [textColor]), up to a limit. Stops at the first clean strip, so neighbouring lines separated by a gap, and
     * artwork farther away, are left alone. Two rounds, because growing sideways can reveal more rows.
     */
    internal fun extendOverLetters(
        pixels: IntArray,
        w: Int,
        h: Int,
        start: PixelRect,
        background: Int,
        textColor: Int,
        config: Config,
    ): PixelRect {
        val maxV = (start.height * config.inkExtendVertical).toInt() + 4
        val maxH = (start.width * config.inkExtendHorizontal).toInt() + 4
        var rect = start

        fun lettersIn(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
            if (x0 < 0 || y0 < 0 || x1 > w || y1 > h || x0 >= x1 || y0 >= y1) return false
            var count = 0
            val needed = max(2, ((x1 - x0) * (y1 - y0)) / 400)
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    if (TextEraser.isLetter(pixels[y * w + x], textColor, background) && ++count >= needed) {
                        return true
                    }
                }
            }
            return false
        }

        repeat(2) {
            var up = 0
            while (up < maxV && lettersIn(rect.left, rect.top - 1, rect.right, rect.top)) {
                rect = rect.copy(top = rect.top - 1)
                up++
            }
            var down = 0
            while (down < maxV && lettersIn(rect.left, rect.bottom, rect.right, rect.bottom + 1)) {
                rect = rect.copy(bottom = rect.bottom + 1)
                down++
            }
            var left = 0
            while (left < maxH && lettersIn(rect.left - 1, rect.top, rect.left, rect.bottom)) {
                rect = rect.copy(left = rect.left - 1)
                left++
            }
            var right = 0
            while (right < maxH && lettersIn(rect.right, rect.top, rect.right + 1, rect.bottom)) {
                rect = rect.copy(right = rect.right + 1)
                right++
            }
        }
        return rect
    }

    // endregion

    // region Text rectangle

    /**
     * Grows [start] side by side (round robin) while the new strip is mostly inside the mask.
     * The result is roughly the largest rectangle around the box centre that fits the bubble.
     */
    private fun growInside(mask: BooleanArray, w: Int, h: Int, start: PixelRect, coverage: Float): PixelRect {
        var rect = start
        val step = max(1, min(start.width, start.height) / 20)

        fun stripInside(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
            if (x0 < 0 || y0 < 0 || x1 > w || y1 > h || x0 >= x1 || y0 >= y1) return false
            var inside = 0
            var total = 0
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    total++
                    if (mask[y * w + x]) inside++
                }
            }
            return inside >= total * coverage
        }

        var grew = true
        var guard = 0
        while (grew && guard++ < 400) {
            grew = false
            if (stripInside(rect.left - step, rect.top, rect.left, rect.bottom)) {
                rect = rect.copy(left = rect.left - step)
                grew = true
            }
            if (stripInside(rect.right, rect.top, rect.right + step, rect.bottom)) {
                rect = rect.copy(right = rect.right + step)
                grew = true
            }
            if (stripInside(rect.left, rect.top - step, rect.right, rect.top)) {
                rect = rect.copy(top = rect.top - step)
                grew = true
            }
            if (stripInside(rect.left, rect.bottom, rect.right, rect.bottom + step)) {
                rect = rect.copy(bottom = rect.bottom + step)
                grew = true
            }
        }
        return rect
    }

    private fun shrink(rect: PixelRect, fraction: Float): PixelRect {
        val dx = (rect.width * fraction).toInt()
        val dy = (rect.height * fraction).toInt()
        val shrunk = PixelRect(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
        return if (shrunk.isEmpty) rect else shrunk
    }

    private fun PixelRect.offset(dx: Int, dy: Int) = PixelRect(left + dx, top + dy, right + dx, bottom + dy)

    // endregion

    private const val WHITE = -1 // 0xFFFFFFFF

    /** Share of the ring around open text that must have the page colour for the surroundings to count as flat. */
    private const val MIN_FLAT_SHARE = 0.75f

    /** A fill touching more than this share of a page edge side is treated as a leak, not as a cut off bubble. */
    private const val PAGE_EDGE_PARTIAL_CONTACT = 0.8f

    /** A pixel this far from the page colour is clearly visible lettering (matches the eraser's threshold). */
    private const val INK_DISTANCE = 70
}
