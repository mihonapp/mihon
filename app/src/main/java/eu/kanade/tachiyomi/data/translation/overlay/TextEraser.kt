package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Removes lettering that is not inside a traceable bubble (plain pages, glowing game windows, text over art).
 *
 * Instead of painting a flat rectangle, only the letters are replaced. The colour of a letter pixel is taken from
 * the nearest non-letter pixels on the same row (linear blend), so gradients and artwork around the text stay intact.
 * Pure Kotlin on pixel arrays, so it can be unit tested on the JVM.
 */
object TextEraser {

    class Result(
        /** Window sized flags, true where a pixel must be replaced. */
        val mask: BooleanArray,
        /** Window sized replacement colours (valid where [mask] is true). */
        val fill: IntArray,
        /** The detected lettering colour, ARGB. */
        val textColor: Int,
    )

    /** Fraction of the area that must look like lettering before it is trusted. */
    private const val MIN_TEXT_SHARE = 0.004f

    /** A pixel this far (max channel difference) from the background can be lettering. */
    private const val FAR_FROM_BACKGROUND = 70

    /** A pixel this close to the lettering colour counts as lettering. */
    private const val NEAR_TEXT_COLOR = 70

    /**
     * Finds the lettering colour inside [area]: the median colour of pixels clearly different from [background].
     * Returns null when there is (almost) nothing that stands out.
     */
    fun detectTextColor(pixels: IntArray, w: Int, area: PixelRect, background: Int): Int? {
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) {
                val p = pixels[y * w + x]
                if (distance(p, background) >= FAR_FROM_BACKGROUND) {
                    reds += (p shr 16) and 0xFF
                    greens += (p shr 8) and 0xFF
                    blues += p and 0xFF
                }
            }
        }
        if (reds.size < max(3f, area.area * MIN_TEXT_SHARE)) return null
        reds.sort()
        greens.sort()
        blues.sort()
        val mid = reds.size / 2
        return (0xFF shl 24) or (reds[mid] shl 16) or (greens[mid] shl 8) or blues[mid]
    }

    fun isLetter(pixel: Int, textColor: Int, background: Int): Boolean {
        return distance(pixel, textColor) <= NEAR_TEXT_COLOR && distance(pixel, background) >= 30
    }

    /**
     * @param area where lettering may be erased, in window coordinates.
     * @param textColor from [detectTextColor].
     */
    fun erase(
        pixels: IntArray,
        w: Int,
        h: Int,
        area: PixelRect,
        background: Int,
        textColor: Int,
        dilation: Int,
        maxLetterSide: Int = Int.MAX_VALUE,
        keepArea: PixelRect? = null,
    ): Result {
        val candidates = BooleanArray(w * h)
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) {
                val pixel = pixels[y * w + x]
                // Lettering can have a fill, stroke, and shadow in different colours. Keep the original median-
                // colour test, but also include other strong foreground colours; connected-component size and
                // keepArea still reject most unrelated artwork.
                if (isLetter(pixel, textColor, background) || distance(pixel, background) >= FAR_FROM_BACKGROUND) {
                    candidates[y * w + x] = true
                }
            }
        }
        val letters = keepLetterSized(candidates, w, h, area, maxLetterSide, keepArea)

        // Grow by a few pixels to take the soft anti-aliased edges with it.
        val mask = BooleanArray(w * h)
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) {
                if (!letters[y * w + x]) continue
                for (dy in -dilation..dilation) {
                    for (dx in -dilation..dilation) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h) mask[ny * w + nx] = true
                    }
                }
            }
        }

        val fill = IntArray(w * h)
        for (y in 0 until h) {
            var x = 0
            while (x < w) {
                if (!mask[y * w + x]) {
                    x++
                    continue
                }
                val start = x
                while (x < w && mask[y * w + x]) x++
                // Pixels just outside the run, or the background colour when the run touches the window edge.
                val left = if (start > 0) pixels[y * w + start - 1] else null
                val right = if (x < w) pixels[y * w + x] else null
                val span = x - start
                for (i in 0 until span) {
                    fill[y * w + start + i] = when {
                        left != null && right != null -> blend(left, right, (i + 1f) / (span + 1f))
                        left != null -> left
                        right != null -> right
                        else -> background
                    }
                }
            }
        }
        return Result(mask, fill, textColor)
    }

    /**
     * Keeps only connected groups of pixels that are small enough to be lettering. Outlines, panel borders, hair,
     * faces and other artwork of the same colour form groups that are much larger than a letter and are left alone.
     * A group may be up to [maxLetterSide] tall and three times as wide (touching letters of one word), and its
     * centre must lie inside [keepArea] when given.
     */
    private fun keepLetterSized(
        candidates: BooleanArray,
        w: Int,
        h: Int,
        area: PixelRect,
        maxLetterSide: Int,
        keepArea: PixelRect?,
    ): BooleanArray {
        val kept = BooleanArray(w * h)
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val maxWidth = if (maxLetterSide == Int.MAX_VALUE) Int.MAX_VALUE else maxLetterSide * 3
        for (startY in area.top until area.bottom) {
            for (startX in area.left until area.right) {
                val start = startY * w + startX
                if (!candidates[start] || seen[start]) continue

                var head = 0
                var tail = 0
                queue[tail++] = start
                seen[start] = true
                var minX = startX
                var maxX = startX
                var minY = startY
                var maxY = startY
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
                            if (nx < area.left || nx >= area.right || ny < area.top || ny >= area.bottom) continue
                            val next = ny * w + nx
                            if (candidates[next] && !seen[next]) {
                                seen[next] = true
                                queue[tail++] = next
                            }
                        }
                    }
                }
                // Dashes of a bubble outline and similar marks lie outside the text box. Only groups whose centre is
                // inside the box (plus a small margin) count as lettering.
                val centered = keepArea == null || keepArea.contains((minX + maxX) / 2, (minY + maxY) / 2)
                if (centered && maxY - minY + 1 <= maxLetterSide && maxX - minX + 1 <= maxWidth) {
                    for (i in 0 until tail) kept[queue[i]] = true
                }
            }
        }
        return kept
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * Share of the masked pixels that are neither close to [background] nor clearly different from it. A flat
     * bubble has almost none (only soft letter edges); a gradient, halftone or artwork has many.
     */
    fun midToneShare(pixels: IntArray, mask: BooleanArray, background: Int): Float {
        var total = 0
        var mid = 0
        for (i in mask.indices) {
            if (!mask[i]) continue
            total++
            val d = distance(pixels[i], background)
            if (d > 24 && d < FAR_FROM_BACKGROUND) mid++
        }
        return if (total == 0) 0f else mid.toFloat() / total
    }

    internal fun distance(a: Int, b: Int): Int = max(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        max(abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
    )

    /** Dilation radius for a text box: about 1/12 of its smaller side, 1 to 4 px. */
    fun dilationFor(box: PixelRect) = min(4, max(1, min(box.width, box.height) / 12))
}
