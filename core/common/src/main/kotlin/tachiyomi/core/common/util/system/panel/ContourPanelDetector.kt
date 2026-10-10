package tachiyomi.core.common.util.system.panel

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Contour/blob panel detector. Stages: background luminance from the median of the image frame
 * (falling back to white, then black, for full-bleed pages), ink mask by luminance distance, 8-connected components (iterative flood fill), small-component
 * noise removal, merging of overlapping or nearly touching boxes to a fixed point, rejection
 * of a single near-full-page box, growing panels over lettering that spills out of them, and grouping of
 * the art left outside the panels into borderless regions. Output rects are normalised to 0..1 and not ordered.
 */
class ContourPanelDetector : PanelDetector {

    override fun detect(image: GrayImage): List<PanelRect> {
        // Thin strips (some releases cut a page into slivers) are never a panel layout.
        if (minOf(image.width, image.height) < maxOf(image.width, image.height) * MIN_ASPECT_RATIO) {
            return emptyList()
        }
        // Full-bleed art has no page margin, so the frame isn't paper; fall back to white, then
        // black gutters when the frame colour finds nothing.
        return listOf(frameMedian(image), WHITE, BLACK)
            .distinct()
            .firstNotNullOfOrNull { background -> detect(image, background).ifEmpty { null } }
            .orEmpty()
    }

    private fun detect(image: GrayImage, background: Int): List<PanelRect> {
        val width = image.width
        val height = image.height
        val imageArea = width.toDouble() * height

        val ink = inkMask(image, background)
        val labels = IntArray(ink.size)
        val boxes = components(ink, labels, width, height)

        val minArea = imageArea * MIN_COMPONENT_AREA_FRACTION
        val (large, small) = boxes.partition { it.area >= minArea }
        val gutters = GutterFinder(labels, width, height, nextLabel = boxes.size + 1, minArea = minArea)
        val kept = ArrayList<Box>()
        large.forEach { gutters.split(it, kept) }
        kept.forEach {
            it.outline = Outline.of(it, labels, width)
            it.corners = frameCorners(it, labels, width)
        }
        mergeToFixedPoint(kept, minOf(width, height) * MERGE_GAP_FRACTION)
        // After merging, so that growing a panel over a bubble doesn't join it to its neighbour again.
        gutters.bridges.forEach { (panel, bubble) -> if (panel in kept) panel.absorb(bubble) }

        if (kept.isEmpty()) return emptyList()
        if (kept.size == 1 && kept[0].area >= imageArea * DEGENERATE_AREA_FRACTION) return emptyList()

        // Art and lettering that is not inside a single panel: a bubble or sound effect poking out of
        // its panel (often with no outline, so nothing connects it to the frame), or a borderless
        // panel drawn straight on the paper. Both come out as several small components. Group them so
        // that stepping through the page neither cuts them off nor skips them.
        val pieces = small.filter { maxOf(it.right - it.left, it.bottom - it.top) >= MIN_LOOSE_SIDE }
        val outside = pieces.filter { piece -> kept.none { it.contains(piece) } }
        if (outside.size <= MAX_LOOSE_COMPONENTS) {
            val groups = groupWithNeighbours(outside, pieces, minOf(width, height) * SPILL_GAP_FRACTION)
            val maxSpillArea = imageArea * MAX_SPILL_AREA_FRACTION
            val loose = ArrayList<Box>()
            val spills = ArrayList<Pair<Box, Box>>()
            for (group in groups) {
                val panel = kept.maxBy { it.intersectionArea(group) }
                if (group.area <= maxSpillArea && panel.intersectionArea(group) >= group.area * MIN_SPILL_OVERLAP) {
                    spills += panel to group
                } else {
                    loose += group
                }
            }
            // A panel grows over what spills out of it, even where that overlaps the next panel.
            spills.forEach { (panel, group) -> panel.absorb(group) }
            mergeLoose(loose, kept, minOf(width, height) * LOOSE_GAP_FRACTION)
            val minLooseArea = imageArea * MIN_LOOSE_AREA_FRACTION
            loose.filterTo(kept) { it.area >= minLooseArea }
        }

        return kept.map { box ->
            PanelRect(
                left = (box.left.toFloat() / width).coerceIn(0f, 1f),
                top = (box.top.toFloat() / height).coerceIn(0f, 1f),
                right = ((box.right + 1).toFloat() / width).coerceIn(0f, 1f),
                bottom = ((box.bottom + 1).toFloat() / height).coerceIn(0f, 1f),
                corners = box.corners?.let { c ->
                    // Same convention as the rect: right and bottom edges lie past the last pixel.
                    listOf(c[0], c[1], c[2] + 1, c[3], c[4] + 1, c[5] + 1, c[6], c[7] + 1)
                        .mapIndexed { i, v -> (v.toFloat() / if (i % 2 == 0) width else height).coerceIn(0f, 1f) }
                }.orEmpty(),
            )
        }
    }

    /**
     * Corners of [box]'s component when it is a four-sided frame with slanted edges, as
     * `x0, y0 .. x3, y3` clockwise from the top left, or null when the box itself describes it (a
     * plain rectangle) or four corners don't (the frame would cut into the art).
     */
    private fun frameCorners(box: Box, labels: IntArray, width: Int): IntArray? {
        val outline = box.outline ?: return null
        // The pixel furthest out along each diagonal is the corner on that side.
        val c = IntArray(8)
        var topLeft = Int.MAX_VALUE
        var topRight = Int.MIN_VALUE
        var bottomRight = Int.MIN_VALUE
        var bottomLeft = Int.MIN_VALUE
        for (y in box.top..box.bottom) {
            for (x in box.left..box.right) {
                if (labels[y * width + x] != box.label) continue
                if (x + y < topLeft) {
                    topLeft = x + y
                    c[0] = x
                    c[1] = y
                }
                if (x - y > topRight) {
                    topRight = x - y
                    c[2] = x
                    c[3] = y
                }
                if (x + y > bottomRight) {
                    bottomRight = x + y
                    c[4] = x
                    c[5] = y
                }
                if (y - x > bottomLeft) {
                    bottomLeft = y - x
                    c[6] = x
                    c[7] = y
                }
            }
        }
        var frameArea = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            frameArea += c[2 * i].toDouble() * c[2 * j + 1] - c[2 * j].toDouble() * c[2 * i + 1]
        }
        frameArea /= 2
        if (frameArea <= 0 || frameArea > box.area * MAX_FRAME_FILL) return null

        var outside = 0
        for (y in box.top..box.bottom) {
            for (x in box.left..box.right) {
                if (outline.covers(x, y) && !insideFrame(c, x, y)) outside++
            }
        }
        return c.takeIf { outside <= outline.area * MAX_FRAME_CUT }
    }

    /**
     * Cuts components that hold several panels joined by ink crossing a gutter (a sound effect, a
     * bubble, a figure breaking out of its frame). A gutter is a straight line across the whole
     * component, upright or lying down and up to [MAX_GUTTER_SLANT] off that axis, that is a
     * band of mostly paper with a frame line running along it on both sides.
     */
    private class GutterFinder(
        private val labels: IntArray,
        private val width: Int,
        height: Int,
        private var nextLabel: Int,
        private val minArea: Double,
    ) {
        private val minSide = (minOf(width, height) * MIN_PANEL_SIDE_FRACTION).toInt()

        private val bandHalfWidth = maxOf(1, (minOf(width, height) * GUTTER_HALF_WIDTH_FRACTION).toInt())

        /** Pixels the search may still read. Keeps pages of loose, unframed art from taking seconds. */
        private var budget = GUTTER_SEARCH_BUDGET

        // On larger images a gutter is several pixels wide, so every other position finds it.
        private val positionStep = if (minOf(width, height) >= COARSE_GUTTER_SEARCH_SIDE) 2 else 1
        private val maxFrameLineDistance =
            maxOf(MIN_FRAME_LINE_REACH, (minOf(width, height) * FRAME_LINE_REACH_FRACTION).toInt())

        /** Adds [box], or the panels it was cut into, to [out]. */
        fun split(box: Box, out: MutableList<Box>, depth: Int = 0) {
            val halves = if (depth < MAX_SPLIT_DEPTH && box.area >= 2 * minArea) {
                // The gutter with the clearest frame lines wins, whichever way it runs.
                listOfNotNull(findGutter(box, upright = true), findGutter(box, upright = false))
                    .maxByOrNull { it.score }
                    ?.let { cut(box, it) }
            } else {
                null
            }
            if (halves == null) {
                out += box
            } else {
                split(halves.first, out, depth + 1)
                split(halves.second, out, depth + 1)
            }
        }

        // An upright gutter runs along y and is searched across x; a lying one is the transpose.
        private fun inked(across: Int, along: Int, upright: Boolean): Boolean {
            return (if (upright) labels[along * width + across] else labels[across * width + along]) != 0
        }

        /** A gutter line from [first] to [last] (positions across the box at its two ends). */
        private class Gutter(val first: Int, val last: Int, val upright: Boolean, val score: Double)

        private fun findGutter(box: Box, upright: Boolean): Gutter? {
            val alongStart = if (upright) box.top else box.left
            val alongEnd = if (upright) box.bottom else box.right
            val acrossStart = (if (upright) box.left else box.top) + minSide
            val acrossEnd = (if (upright) box.right else box.bottom) - minSide
            val length = alongEnd - alongStart + 1
            if (length < minSide || acrossStart > acrossEnd) return null
            // Long lines are sampled, so that the search costs the same on large images.
            val stride = maxOf(1, length / MAX_GUTTER_SAMPLES)
            val samples = (length + stride - 1) / stride
            val maxBlocked = (samples * MAX_GUTTER_BLOCKED).toInt()
            val minFrameInk = (samples * MIN_FRAME_LINE_INK).toInt()
            val maxSlant = (length * MAX_GUTTER_SLANT).toInt()
            val slantStep = maxOf(2, 2 * maxSlant / MAX_GUTTER_SLANTS)

            var best: Gutter? = null

            fun consider(first: Int, last: Int) {
                // The costly part is reading whole lines, so first glance for a frame on each side.
                if (!glimpsesFrameLine(first, last, -1, alongStart, length, upright)) return
                if (!glimpsesFrameLine(first, last, 1, alongStart, length, upright)) return
                val blocked = inkAlong(first, last, 0, alongStart, length, stride, upright, maxBlocked)
                if (blocked > maxBlocked) return
                // Paper on both sides too: a gutter is a band, wider than the gaps in hatching or
                // inside a double line.
                for (shift in 1..bandHalfWidth) {
                    if (inkAlong(first, last, -shift, alongStart, length, stride, upright, maxBlocked) >
                        maxBlocked
                    ) {
                        return
                    }
                    if (inkAlong(first, last, shift, alongStart, length, stride, upright, maxBlocked) >
                        maxBlocked
                    ) {
                        return
                    }
                }
                val before = frameLineInk(first, last, -1, alongStart, length, stride, upright, minFrameInk, maxBlocked)
                if (before < minFrameInk) return
                val after = frameLineInk(first, last, 1, alongStart, length, stride, upright, minFrameInk, maxBlocked)
                if (after < minFrameInk) return
                // Solid frame lines on both sides and little crossing the paper between them.
                val score = (before + after - blocked).toDouble() / samples
                if (score > (best?.score ?: 0.0)) best = Gutter(first, last, upright, score)
            }

            for (first in acrossStart..acrossEnd step positionStep) {
                if (budget <= 0) break
                val opensAtStart = !inked(first, alongStart, upright) && !inked(first, alongStart + 1, upright)
                for (slant in -maxSlant..maxSlant step slantStep) {
                    val last = first + slant
                    if (last < acrossStart || last > acrossEnd) continue
                    // A gutter runs out into the paper around the panels at both ends. A line that
                    // stops at a frame is a light band inside one panel. Checked first: it is cheap
                    // and rules out most lines.
                    val opensAtEnd = !inked(last, alongEnd, upright) && !inked(last, alongEnd - 1, upright)
                    if (!opensAtStart || !opensAtEnd) continue
                    if (slantStep <= 2) {
                        consider(first, last)
                        continue
                    }
                    // Leans are tried in coarse steps on long lines, but a frame line only shows up
                    // when the lean is exact. Where a coarse line is paper with ink close by on both
                    // sides, lean it to run parallel to that ink and look again.
                    if (inkAlong(first, last, 0, alongStart, length, stride, upright, maxBlocked) > maxBlocked) continue
                    val reach = maxFrameLineDistance + slantStep / 2
                    if (!hasInkBeside(first, last, -1, reach, alongStart, length, upright)) continue
                    if (!hasInkBeside(first, last, 1, reach, alongStart, length, upright)) continue
                    var tried = Int.MIN_VALUE
                    for (side in SIDES) {
                        val atStart = inkDistance(first, last, side, reach, alongStart, length, upright, atEnd = false)
                        val atEnd = inkDistance(first, last, side, reach, alongStart, length, upright, atEnd = true)
                        if (atStart < 0 || atEnd < 0) continue
                        val exact = last + side * (atEnd - atStart)
                        if (exact == tried || abs(exact - last) > slantStep ||
                            exact !in acrossStart..acrossEnd
                        ) {
                            continue
                        }
                        tried = exact
                        consider(first, exact)
                    }
                }
            }
            return best
        }

        /** True if some line within reach on one [side] is inked at half or more of a few points. */
        private fun glimpsesFrameLine(
            first: Int,
            last: Int,
            side: Int,
            alongStart: Int,
            length: Int,
            upright: Boolean,
        ): Boolean {
            val glanceStride = maxOf(1, length / FRAME_LINE_GLANCE_SAMPLES)
            val needed = (length + glanceStride - 1) / glanceStride / 2
            for (distance in 2..maxFrameLineDistance) {
                val ink = inkAlong(first, last, side * distance, alongStart, length, glanceStride, upright, needed)
                if (ink >= needed) return true
            }
            return false
        }

        /**
         * Distance from the line to the nearest ink on one [side], near the line's start or its end:
         * the middle value of three probes, or -1 when fewer than two of them find ink within [reach].
         */
        private fun inkDistance(
            first: Int,
            last: Int,
            side: Int,
            reach: Int,
            alongStart: Int,
            length: Int,
            upright: Boolean,
            atEnd: Boolean,
        ): Int {
            val found = IntArray(3)
            var count = 0
            for (probe in 1..3) {
                val offset = length * probe / LEAN_PROBE_DIVISOR
                val along = if (atEnd) length - 1 - offset else offset
                val across = acrossAt(first, last, along, length)
                for (distance in 2..reach) {
                    if (inked(across + side * distance, alongStart + along, upright)) {
                        found[count++] = distance
                        break
                    }
                }
            }
            if (count < 2) return -1
            found.sort(0, count)
            return found[count / 2]
        }

        /** True if most of a few points along the line have ink within [reach] pixels on one [side]. */
        private fun hasInkBeside(
            first: Int,
            last: Int,
            side: Int,
            reach: Int,
            alongStart: Int,
            length: Int,
            upright: Boolean,
        ): Boolean {
            var found = 0
            var checked = 0
            for (along in 0 until length step maxOf(1, length / FRAME_LINE_GLANCE_SAMPLES)) {
                checked++
                val across = acrossAt(first, last, along, length)
                for (distance in 2..reach) {
                    if (inked(across + side * distance, alongStart + along, upright)) {
                        found++
                        break
                    }
                }
            }
            return found >= checked * MIN_FRAME_LINE_INK
        }

        private fun cut(box: Box, gutter: Gutter): Pair<Box, Box>? {
            val upright = gutter.upright
            val alongStart = if (upright) box.top else box.left
            val length = (if (upright) box.bottom else box.right) - alongStart + 1
            val line = intArrayOf(gutter.first, gutter.last)

            // Everything of the component past the line becomes a component of its own.
            val near = Box(Int.MAX_VALUE, Int.MAX_VALUE, -1, -1, box.label)
            val far = Box(Int.MAX_VALUE, Int.MAX_VALUE, -1, -1, nextLabel)
            for (y in box.top..box.bottom) {
                for (x in box.left..box.right) {
                    if (labels[y * width + x] != box.label) continue
                    val along = (if (upright) y else x) - alongStart
                    val across = if (upright) x else y
                    val half = if (across > acrossAt(line[0], line[1], along, length)) far else near
                    half.left = minOf(half.left, x)
                    half.top = minOf(half.top, y)
                    half.right = maxOf(half.right, x)
                    half.bottom = maxOf(half.bottom, y)
                }
            }
            if (near.right < 0 || far.right < 0 || near.area < minArea || far.area < minArea) return null
            for (y in far.top..far.bottom) {
                for (x in far.left..far.right) {
                    if (labels[y * width + x] != box.label) continue
                    val along = (if (upright) y else x) - alongStart
                    val across = if (upright) x else y
                    if (across > acrossAt(line[0], line[1], along, length)) labels[y * width + x] = far.label
                }
            }
            nextLabel++
            noteBridges(near, far, line, alongStart, length, upright)
            return near to far
        }

        /**
         * A bubble drawn over the gutter belongs to one panel but reaches into the other, and the cut
         * goes through it. Its inside is paper closed off from the paper of the page, so any such
         * pocket on the gutter line is one, and the panel holding more of it is to grow over it.
         */
        private fun noteBridges(near: Box, far: Box, line: IntArray, alongStart: Int, length: Int, upright: Boolean) {
            val pockets = paperPockets
            val seen = HashSet<Int>()
            for (along in 0 until length) {
                val across = acrossAt(line[0], line[1], along, length)
                val x = if (upright) across else alongStart + along
                val y = if (upright) alongStart + along else across
                val pocket = pockets.ids[y * width + x]
                if (pocket <= 0 || pocket == pockets.page || !seen.add(pocket)) continue
                val bounds = pockets.bounds[pocket - 1]
                // A bubble is a rounded blob with room for lettering. Slivers of paper caught between
                // the strokes of a sound effect are neither.
                if (bounds.area < minBridgeArea || bounds.area > maxBridgeArea) continue
                if (pockets.sizes[pocket - 1] < bounds.area * MIN_BUBBLE_FILL) continue
                // How far the pocket reaches on each side of the gutter where it crosses it.
                val before = across - (if (upright) bounds.left else bounds.top)
                val after = (if (upright) bounds.right else bounds.bottom) - across
                bridges += (if (before >= after) near else far) to bounds
            }
        }

        /** Panels paired with what they are to grow over once merging is done. See [noteBridges]. */
        val bridges = ArrayList<Pair<Box, Box>>()

        private val maxBridgeArea = width.toDouble() * height * MAX_SPILL_AREA_FRACTION
        private val minBridgeArea = width.toDouble() * height * MIN_BUBBLE_AREA_FRACTION

        private class PaperPockets(val ids: IntArray, val bounds: List<Box>, val sizes: List<Int>, val page: Int)

        /** 4-connected regions of paper: the page itself (the largest) and pockets closed in by ink. */
        private val paperPockets: PaperPockets by lazy {
            val ids = IntArray(labels.size)
            val bounds = ArrayList<Box>()
            val sizes = ArrayList<Int>()
            val stack = IntArray(labels.size)
            val height = labels.size / width
            for (start in labels.indices) {
                if (labels[start] != 0 || ids[start] != 0) continue
                val id = bounds.size + 1
                val box = Box(width, height, -1, -1)
                var size = 0
                var sp = 0
                ids[start] = id
                stack[sp++] = start
                while (sp > 0) {
                    val p = stack[--sp]
                    val x = p % width
                    val y = p / width
                    size++
                    if (x < box.left) box.left = x
                    if (x > box.right) box.right = x
                    if (y < box.top) box.top = y
                    if (y > box.bottom) box.bottom = y
                    if (x > 0 && labels[p - 1] == 0 && ids[p - 1] == 0) {
                        ids[p - 1] = id
                        stack[sp++] = p - 1
                    }
                    if (x < width - 1 && labels[p + 1] == 0 && ids[p + 1] == 0) {
                        ids[p + 1] = id
                        stack[sp++] = p + 1
                    }
                    if (y > 0 && labels[p - width] == 0 && ids[p - width] == 0) {
                        ids[p - width] = id
                        stack[sp++] = p - width
                    }
                    if (y < height - 1 && labels[p + width] == 0 && ids[p + width] == 0) {
                        ids[p + width] = id
                        stack[sp++] = p + width
                    }
                }
                bounds += box
                sizes += size
            }
            PaperPockets(ids, bounds, sizes, page = sizes.indices.maxByOrNull { sizes[it] }?.plus(1) ?: 0)
        }

        private fun acrossAt(first: Int, last: Int, along: Int, length: Int): Int {
            return if (length <= 1) first else first + (last - first) * along / (length - 1)
        }

        /** Inked samples on the line shifted by [shift], one every [stride] pixels; stops counting once past [limit]. */
        private fun inkAlong(
            first: Int,
            last: Int,
            shift: Int,
            alongStart: Int,
            length: Int,
            stride: Int,
            upright: Boolean,
            limit: Int,
        ): Int {
            var count = 0
            var read = 0
            for (along in 0 until length step stride) {
                read++
                if (inked(acrossAt(first, last, along, length) + shift, alongStart + along, upright)) {
                    if (++count > limit) break
                }
            }
            budget -= read
            return count
        }

        /**
         * Walks away from the gutter line on one [side] (-1 or 1), through paper, to where the paper
         * ends. Returns the inked samples of the line there when it has at least [needed] of them (a
         * panel's frame), or 0 when it is something else or nothing is found in reach.
         */
        private fun frameLineInk(
            first: Int,
            last: Int,
            side: Int,
            alongStart: Int,
            length: Int,
            stride: Int,
            upright: Boolean,
            needed: Int,
            maxBlocked: Int,
        ): Int {
            // A glance at a few points first: most lines next to a band of paper are paper too.
            val glanceStride = maxOf(stride, length / FRAME_LINE_GLANCE_SAMPLES)
            // Where the paper ends, the frame is that line or one just behind it (a frame's edge is
            // often ragged by a pixel or two).
            var end = maxFrameLineDistance
            var distance = 2
            while (distance <= end) {
                val shift = side * distance
                if (inkAlong(first, last, shift, alongStart, length, glanceStride, upright, length) > 0) {
                    val ink = inkAlong(first, last, shift, alongStart, length, stride, upright, length)
                    if (ink >= needed) return ink
                    if (ink > maxBlocked) end = minOf(end, distance + FRAME_EDGE_SLACK)
                }
                distance++
            }
            return 0
        }
    }

    /** Lower median luminance of the 1-pixel frame around the image. */
    private fun frameMedian(image: GrayImage): Int {
        val w = image.width
        val h = image.height
        val histogram = IntArray(256)
        var count = 0
        for (x in 0 until w) {
            histogram[image.lum(x, 0)]++
            count++
            if (h > 1) {
                histogram[image.lum(x, h - 1)]++
                count++
            }
        }
        for (y in 1 until h - 1) {
            histogram[image.lum(0, y)]++
            count++
            if (w > 1) {
                histogram[image.lum(w - 1, y)]++
                count++
            }
        }
        val medianIndex = (count - 1) / 2
        var cumulative = 0
        for (value in 0..255) {
            cumulative += histogram[value]
            if (cumulative > medianIndex) return value
        }
        return 255
    }

    private fun inkMask(image: GrayImage, background: Int): BooleanArray {
        val pixels = image.pixels
        return BooleanArray(pixels.size) { i ->
            abs((pixels[i].toInt() and 0xFF) - background) > INK_THRESHOLD
        }
    }

    /**
     * Bounding boxes of 8-connected ink components. Consumes [ink] and writes each pixel's component
     * into [labels] (the box's [Box.label], 0 for background). Explicit stack, no recursion.
     */
    private fun components(ink: BooleanArray, labels: IntArray, width: Int, height: Int): List<Box> {
        val result = ArrayList<Box>()
        // Each pixel is pushed at most once (cleared on push), so width * height slots always suffice.
        var s = IntArray(0)
        for (start in ink.indices) {
            if (!ink[start]) continue
            if (s.isEmpty()) s = IntArray(ink.size)
            ink[start] = false
            val label = result.size + 1
            labels[start] = label
            var sp = 0
            s[sp++] = start
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            while (sp > 0) {
                val p = s[--sp]
                val x = p % width
                val y = p / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val q = ny * width + nx
                        if (ink[q]) {
                            ink[q] = false
                            labels[q] = label
                            s[sp++] = q
                        }
                    }
                }
            }
            result.add(Box(minX, minY, maxX, maxY, label))
        }
        return result
    }

    private fun mergeToFixedPoint(boxes: MutableList<Box>, maxGap: Double) {
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < boxes.size) {
                var j = i + 1
                while (j < boxes.size) {
                    if (boxes[i].isNear(boxes[j], maxGap) && !areSeparatePanels(boxes[i], boxes[j])) {
                        boxes[i].absorb(boxes.removeAt(j))
                        changed = true
                    } else {
                        j++
                    }
                }
                i++
            }
        }
    }

    /**
     * True if [a] and [b] are two panels with a slanted gutter between them: their bounding boxes
     * partly overlap, but each one fills most of its own box and the shapes themselves stay apart. Loose art
     * (lettering, strokes, clouds) doesn't fill its box, so it still merges.
     */
    private fun areSeparatePanels(a: Box, b: Box): Boolean {
        val outlineA = a.outline ?: return false
        val outlineB = b.outline ?: return false
        if (outlineA.area < a.area * MIN_PANEL_FILL || outlineB.area < b.area * MIN_PANEL_FILL) return false
        // A box lying mostly inside another is part of it, not a neighbour across a slanted gutter.
        if (a.intersectionArea(b) > minOf(a.area, b.area) * MAX_PANEL_BOX_OVERLAP) return false
        var shared = 0
        for (y in maxOf(a.top, b.top)..minOf(a.bottom, b.bottom)) {
            for (x in maxOf(a.left, b.left)..minOf(a.right, b.right)) {
                if (outlineA.covers(x, y) && outlineB.covers(x, y)) shared++
            }
        }
        return shared <= minOf(outlineA.area, outlineB.area) * MAX_PANEL_SHARED
    }

    /**
     * Grows a group from each of [seeds] by repeatedly adding the [pieces] within [maxGap] of a member,
     * so a block of lettering is collected whole from the few letters that start it. Returns the
     * bounding box of each group.
     */
    private fun groupWithNeighbours(seeds: List<Box>, pieces: List<Box>, maxGap: Double): List<Box> {
        val remaining = ArrayList(pieces)
        val groups = ArrayList<Box>()
        for (seed in seeds) {
            if (!remaining.remove(seed)) continue
            val members = arrayListOf(seed)
            var next = 0
            while (next < members.size) {
                val member = members[next++]
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val piece = iterator.next()
                    if (member.isNear(piece, maxGap)) {
                        iterator.remove()
                        members += piece
                    }
                }
            }
            groups += seed.copy().apply { members.forEach(::absorb) }
        }
        return groups
    }

    /** Merges [loose] boxes within [maxGap] of each other, unless the merged box would run into a [panels] box. */
    private fun mergeLoose(loose: MutableList<Box>, panels: List<Box>, maxGap: Double) {
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < loose.size) {
                var j = i + 1
                while (j < loose.size) {
                    val union = loose[i].copy().apply { absorb(loose[j]) }
                    if (loose[i].isNear(loose[j], maxGap) && panels.none { it.intersects(union) }) {
                        loose[i] = union
                        loose.removeAt(j)
                        changed = true
                    } else {
                        j++
                    }
                }
                i++
            }
        }
    }

    /** Pixel bounding box, all edges inclusive. */
    private class Box(var left: Int, var top: Int, var right: Int, var bottom: Int, val label: Int = 0) {
        val area: Double get() = (right - left + 1).toDouble() * (bottom - top + 1)

        /** Shape of the component inside the box. Only set on panel candidates. */
        var outline: Outline? = null

        /** See [frameCorners]. Dropped when the box grows over something else. */
        var corners: IntArray? = null

        /** True if the boxes overlap, nest, touch, or are separated by at most [maxGap] pixels on both axes. */
        fun isNear(other: Box, maxGap: Double): Boolean {
            val gapX = maxOf(0, maxOf(left, other.left) - minOf(right, other.right) - 1)
            val gapY = maxOf(0, maxOf(top, other.top) - minOf(bottom, other.bottom) - 1)
            return gapX <= maxGap && gapY <= maxGap
        }

        fun intersects(other: Box): Boolean {
            return left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom
        }

        fun contains(other: Box): Boolean {
            return left <= other.left && other.right <= right && top <= other.top && other.bottom <= bottom
        }

        fun intersectionArea(other: Box): Double {
            val w = minOf(right, other.right) - maxOf(left, other.left) + 1
            val h = minOf(bottom, other.bottom) - maxOf(top, other.top) + 1
            return if (w > 0 && h > 0) w.toDouble() * h else 0.0
        }

        fun copy() = Box(left, top, right, bottom)

        fun absorb(other: Box) {
            left = minOf(left, other.left)
            top = minOf(top, other.top)
            right = maxOf(right, other.right)
            bottom = maxOf(bottom, other.bottom)
            outline = other.outline?.let { outline?.plus(it) }
            // The frame still holds if what joins the box already lies inside it (art within the panel).
            val frame = corners
            val framed = frame != null &&
                insideFrame(frame, other.left, other.top) && insideFrame(frame, other.right, other.top) &&
                insideFrame(frame, other.right, other.bottom) && insideFrame(frame, other.left, other.bottom)
            if (!framed) corners = null
        }
    }

    /**
     * A component with its holes and dents filled in: the pixels lying between the component's first
     * and last pixel of their row and of their column. Arrays are indexed by image row or column and
     * hold an empty range (`first > last`) where the component is absent.
     */
    private class Outline(
        private val rowFirst: IntArray,
        private val rowLast: IntArray,
        private val columnFirst: IntArray,
        private val columnLast: IntArray,
    ) {
        val area: Double = run {
            var count = 0
            for (y in rowFirst.indices) for (x in rowFirst[y]..rowLast[y]) if (covers(x, y)) count++
            count.toDouble()
        }

        fun covers(x: Int, y: Int): Boolean {
            return x >= rowFirst[y] && x <= rowLast[y] && y >= columnFirst[x] && y <= columnLast[x]
        }

        operator fun plus(other: Outline) = Outline(
            IntArray(rowFirst.size) { minOf(rowFirst[it], other.rowFirst[it]) },
            IntArray(rowLast.size) { maxOf(rowLast[it], other.rowLast[it]) },
            IntArray(columnFirst.size) { minOf(columnFirst[it], other.columnFirst[it]) },
            IntArray(columnLast.size) { maxOf(columnLast[it], other.columnLast[it]) },
        )

        companion object {
            fun of(box: Box, labels: IntArray, width: Int): Outline {
                val height = labels.size / width
                val rowFirst = IntArray(height) { Int.MAX_VALUE }
                val rowLast = IntArray(height) { -1 }
                val columnFirst = IntArray(width) { Int.MAX_VALUE }
                val columnLast = IntArray(width) { -1 }
                for (y in box.top..box.bottom) {
                    for (x in box.left..box.right) {
                        if (labels[y * width + x] != box.label) continue
                        if (x < rowFirst[y]) rowFirst[y] = x
                        if (x > rowLast[y]) rowLast[y] = x
                        if (y < columnFirst[x]) columnFirst[x] = y
                        if (y > columnLast[x]) columnLast[x] = y
                    }
                }
                return Outline(rowFirst, rowLast, columnFirst, columnLast)
            }
        }
    }

    private companion object {
        // Max luminance distance from the background that still counts as background.
        private const val INK_THRESHOLD = 40

        // Components whose bbox covers less than this fraction of the image are noise (text, specks).
        private const val MIN_COMPONENT_AREA_FRACTION = 0.015

        // Boxes closer than this fraction of the image's shorter side are merged. Zero merges only
        // touching/overlapping boxes: real gutters are often under 1% of the page once downsampled,
        // and art bleeding across a gutter is already joined by pixel connectivity.
        private const val MERGE_GAP_FRACTION = 0.0

        // Components this close (as a fraction of the image's shorter side) belong to the same block
        // of lettering or the same drawing.
        private const val SPILL_GAP_FRACTION = 0.015

        // A group joins the panel it overlaps most when at least this much of it lies over that panel.
        private const val MIN_SPILL_OVERLAP = 0.25

        // A group larger than this fraction of the image is a region of its own, not a bubble or a caption.
        private const val MAX_SPILL_AREA_FRACTION = 0.08

        // Components outside every panel that lie within this fraction of the image's shorter side of
        // each other are grouped into one borderless region.
        private const val LOOSE_GAP_FRACTION = 0.05

        // Borderless regions covering less than this fraction of the image are dropped (page numbers,
        // signatures).
        private const val MIN_LOOSE_AREA_FRACTION = 0.005

        // Components under this many pixels on their longer side are scan specks, not art.
        private const val MIN_LOOSE_SIDE = 3

        // Above this many components outside the panels the page is too noisy to group them.
        private const val MAX_LOOSE_COMPONENTS = 400

        // A component whose filled-in shape covers at least this fraction of its bounding box is a
        // panel, even with slanted edges.
        private const val MIN_PANEL_FILL = 0.6

        // Two panels stay separate only when their bounding boxes share at most this fraction of the smaller one.
        private const val MAX_PANEL_BOX_OVERLAP = 0.5

        // Two panels stay separate when their filled-in shapes share at most this fraction of the smaller one.
        private const val MAX_PANEL_SHARED = 0.05

        // A gutter may have ink on up to this fraction of its length (what crosses it).
        private const val MAX_GUTTER_BLOCKED = 0.3

        // A gutter line is checked at up to about this many points along its length.
        private const val MAX_GUTTER_SAMPLES = 100

        // Images with a shorter side of at least this many pixels are searched at every other position.
        private const val COARSE_GUTTER_SEARCH_SIDE = 500

        // How far a gutter may lean, as sideways drift per unit of length.
        private const val MAX_GUTTER_SLANT = 0.2

        // At most about this many leans are tried per gutter position.
        private const val MAX_GUTTER_SLANTS = 24

        // A frame line next to a gutter is inked on at least this fraction of its length...
        private const val MIN_FRAME_LINE_INK = 0.6

        // ...and lies within this fraction of the image's shorter side of the gutter line, or this
        // many pixels on small images.
        private const val FRAME_LINE_REACH_FRACTION = 0.04
        private const val MIN_FRAME_LINE_REACH = 6

        // Pixels the gutter search may read per page. A page of framed panels needs a small part of it.
        private const val GUTTER_SEARCH_BUDGET = 40_000_000L

        // The lean of a frame is measured at 1, 2 and 3 parts in this many from each end of the line.
        private const val LEAN_PROBE_DIVISOR = 20

        private val SIDES = intArrayOf(-1, 1)

        // A gutter is paper for at least this fraction of the image's shorter side on each side of
        // its line (one pixel at least).
        private const val GUTTER_HALF_WIDTH_FRACTION = 0.002

        // A bubble's inside covers at least this fraction of the image...
        private const val MIN_BUBBLE_AREA_FRACTION = 0.003

        // ...and fills at least this fraction of its bounding box, lettering aside.
        private const val MIN_BUBBLE_FILL = 0.45

        // A frame line may lie this many pixels behind the first line that isn't paper.
        private const val FRAME_EDGE_SLACK = 2

        // A line beside a gutter is first looked at in about this many places, and read in full only
        // if one of them is inked.
        private const val FRAME_LINE_GLANCE_SAMPLES = 10

        // No panel is narrower than this fraction of the image's shorter side, so no gutter is closer
        // than that to the edge of the component it cuts.
        private const val MIN_PANEL_SIDE_FRACTION = 0.12

        // Panels found by cutting are cut again at most this many times.
        private const val MAX_SPLIT_DEPTH = 4

        // A four-cornered frame covering more than this fraction of the box is as good as the box.
        private const val MAX_FRAME_FILL = 0.95

        // A four-cornered frame may leave at most this fraction of the panel's shape outside it.
        private const val MAX_FRAME_CUT = 0.03

        // Pixels of tolerance when testing whether a point is inside a frame.
        private const val FRAME_SLACK = 1.5

        // A single remaining box covering at least this fraction of the image is the whole page, not a panel.
        private const val DEGENERATE_AREA_FRACTION = 0.90

        // Images whose short side is under this fraction of the long side are strips, not pages.
        private const val MIN_ASPECT_RATIO = 0.25

        private const val WHITE = 255
        private const val BLACK = 0

        /** True if ([x], [y]) is inside the clockwise four-cornered frame [c], or within [FRAME_SLACK] pixels of it. */
        fun insideFrame(c: IntArray, x: Int, y: Int): Boolean {
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                val edgeX = (c[2 * j] - c[2 * i]).toDouble()
                val edgeY = (c[2 * j + 1] - c[2 * i + 1]).toDouble()
                val cross = edgeX * (y - c[2 * i + 1]) - edgeY * (x - c[2 * i])
                if (cross < -FRAME_SLACK * hypot(edgeX, edgeY)) return false
            }
            return true
        }
    }
}
