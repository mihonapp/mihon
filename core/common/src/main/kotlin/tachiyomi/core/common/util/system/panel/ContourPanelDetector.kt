package tachiyomi.core.common.util.system.panel

import kotlin.math.abs

/**
 * Contour/blob panel detector. Stages: background luminance from the median of the image frame
 * (falling back to white, then black, for full-bleed pages), ink mask by luminance distance, 8-connected components (iterative flood fill), small-component
 * noise removal, merging of overlapping or nearly touching boxes to a fixed point, rejection
 * of a single near-full-page box, and grouping of the art left outside the panels into borderless regions. Output rects are normalised to 0..1 and not ordered.
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
        val boxes = components(ink, width, height)

        val minArea = imageArea * MIN_COMPONENT_AREA_FRACTION
        val (large, small) = boxes.partition { it.area >= minArea }
        val kept = ArrayList(large)
        mergeToFixedPoint(kept, minOf(width, height) * MERGE_GAP_FRACTION)

        if (kept.isEmpty()) return emptyList()
        if (kept.size == 1 && kept[0].area >= imageArea * DEGENERATE_AREA_FRACTION) return emptyList()

        // Borderless panels are drawn straight on the paper, so they come out as several small
        // components (a bubble, a figure) instead of one frame. Group what lies outside every
        // panel so that stepping through the page doesn't skip that art.
        val loose = small.filterTo(ArrayList()) { box ->
            maxOf(box.right - box.left, box.bottom - box.top) >= MIN_LOOSE_SIDE && kept.none { it.intersects(box) }
        }
        if (loose.size <= MAX_LOOSE_COMPONENTS) {
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
            )
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

    /** Bounding boxes of 8-connected ink components. Consumes [ink]. Explicit stack, no recursion. */
    private fun components(ink: BooleanArray, width: Int, height: Int): List<Box> {
        val result = ArrayList<Box>()
        // Each pixel is pushed at most once (cleared on push), so width * height slots always suffice.
        var s = IntArray(0)
        for (start in ink.indices) {
            if (!ink[start]) continue
            if (s.isEmpty()) s = IntArray(ink.size)
            ink[start] = false
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
                            s[sp++] = q
                        }
                    }
                }
            }
            result.add(Box(minX, minY, maxX, maxY))
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
                    if (boxes[i].isNear(boxes[j], maxGap)) {
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
    private class Box(var left: Int, var top: Int, var right: Int, var bottom: Int) {
        val area: Double get() = (right - left + 1).toDouble() * (bottom - top + 1)

        /** True if the boxes overlap, nest, touch, or are separated by at most [maxGap] pixels on both axes. */
        fun isNear(other: Box, maxGap: Double): Boolean {
            val gapX = maxOf(0, maxOf(left, other.left) - minOf(right, other.right) - 1)
            val gapY = maxOf(0, maxOf(top, other.top) - minOf(bottom, other.bottom) - 1)
            return gapX <= maxGap && gapY <= maxGap
        }

        fun intersects(other: Box): Boolean {
            return left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom
        }

        fun copy() = Box(left, top, right, bottom)

        fun absorb(other: Box) {
            left = minOf(left, other.left)
            top = minOf(top, other.top)
            right = maxOf(right, other.right)
            bottom = maxOf(bottom, other.bottom)
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

        // A single remaining box covering at least this fraction of the image is the whole page, not a panel.
        private const val DEGENERATE_AREA_FRACTION = 0.90

        // Images whose short side is under this fraction of the long side are strips, not pages.
        private const val MIN_ASPECT_RATIO = 0.25

        private const val WHITE = 255
        private const val BLACK = 0
    }
}
