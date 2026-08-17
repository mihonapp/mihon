package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelBoundaryDetectorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun buffer(width: Int, height: Int, fill: (x: Int, y: Int) -> Int): PixelBuffer {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = fill(x, y)
            }
        }
        return PixelBuffer(width, height, pixels)
    }

    /**
     * A dense, non-periodic dither pattern has an edge on most pixel-to-pixel steps in both
     * directions, standing in for real panel content (line art, screentone, text): unlike a
     * solid fill, which has zero internal edges and would itself look like a gutter under
     * edge-density detection, this always reads as "content" no matter what color it's drawn
     * in. Deliberately NOT a simple checkerboard: a clean 2x2 checkerboard's row-to-row
     * transition is itself periodic (alternating fully-zero and fully-flipped row pairs),
     * which can fool the border-LINE detector into treating the texture's own repeating
     * structure as a series of drawn lines. This pattern has no such alignment.
     */
    private fun isTextured(x: Int, y: Int): Boolean = ((x * 31 + y * 17) % 7) < 3

    @Test
    fun `two textured panels separated by a white gutter split into two panels`() {
        // 40x30 page: two checkerboard "panels" with a clean 6px white gutter between them,
        // white margins elsewhere.
        val page = buffer(40, 30) { x, y ->
            val inLeft = x in 3..15 && y in 3..26
            val inRight = x in 22..36 && y in 3..26
            when {
                inLeft -> if (isTextured(x, y)) black else white
                inRight -> if (isTextured(x, y)) black else white
                else -> white
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `two textured panels separated by a black gutter split into two panels`() {
        // Same layout, but the gutter and margins are solid black instead of white — the
        // manga convention for a flashback sequence. Detection must not care which color is
        // "background" vs "gutter", only whether a band is locally flat.
        val page = buffer(40, 30) { x, y ->
            val inLeft = x in 3..15 && y in 3..26
            val inRight = x in 22..36 && y in 3..26
            when {
                inLeft -> if (isTextured(x, y)) white else black
                inRight -> if (isTextured(x, y)) white else black
                else -> black
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `floating panels in a large solid-black void are still found, no matter how big the void is`() {
        // 60x60 page, almost entirely solid black (the "floating panels in full-page bleed"
        // flashback style), with two small textured panels placed off-center with large,
        // asymmetric black voids around and between them — much wider than an ordinary gutter,
        // deliberately exercising that there is no cap on gutter thickness.
        val page = buffer(60, 60) { x, y ->
            val inA = x in 5..20 && y in 5..20
            val inB = x in 35..52 && y in 32..50
            when {
                inA -> if (isTextured(x, y)) white else black
                inB -> if (isTextured(x, y)) white else black
                else -> black
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `a gutter row with a few stray bleed pixels still counts as gutter`() {
        // 300x40 page, same two-panel-with-gutter shape as the basic case, but with a handful
        // of isolated stray dark pixels inside the gutter band (simulating hair-strand bleed,
        // speed lines, or scan compression noise). The gutter is wide enough that a few stray
        // marks stay under the density tolerance for the rows they touch.
        val page = buffer(300, 40) { x, y ->
            val inLeft = x in 10..130 && y in 3..36
            val inRight = x in 170..290 && y in 3..36
            val isStray = (x == 145 && y == 8) || (x == 150 && y == 20) || (x == 148 && y == 30)
            when {
                inLeft -> if (isTextured(x, y)) black else white
                inRight -> if (isTextured(x, y)) black else white
                isStray -> black
                else -> white
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size, "a few stray bleed pixels in the gutter should not prevent the split")
    }

    @Test
    fun `disconnected content with no drawn border and no full-span gap stays as one panel`() {
        // Three separate textured blobs (standing in for a speech bubble, SFX text, and a
        // piece of character art) with no drawn border around them and gaps of white between
        // them individually — but staggered so that no row or column is ever fully blank
        // across the whole page within their combined extent. This is the real failure mode
        // found on an actual manga page: without a full-span gap anywhere, the whole staggered
        // group must stay merged as a single panel rather than fragmenting into three.
        val page = buffer(40, 40) { x, y ->
            val inA = x in 3..21 && y in 3..15
            val inB = x in 8..26 && y in 10..22
            val inC = x in 5..23 && y in 17..29
            when {
                inA || inB || inC -> if (isTextured(x, y)) black else white
                else -> white
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size, "staggered disconnected content should not be split into separate panels")
    }

    @Test
    fun `a blank page detects no panels`() {
        val page = buffer(30, 20) { _, _ -> white }

        val panels = PanelBoundaryDetector.detect(page)

        assertTrue(panels.isEmpty())
    }

    @Test
    fun `a single full-bleed textured panel with no internal gutters detects as one panel`() {
        val page = buffer(30, 20) { x, y -> if (isTextured(x, y)) black else white }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `panels touching directly with only a thin border line still split via line detection`() {
        // 40x60 page: two textured panels stacked with ZERO gutter between them - the bottom
        // of the top panel is immediately followed by a 2px solid border line, immediately
        // followed by the top of the bottom panel. No row anywhere is low-density (both
        // panels' content runs right up to the border), so this can only be found by
        // detecting the border LINE itself, not a whitespace gutter.
        val page = buffer(40, 60) { x, y ->
            val inTop = x in 3..36 && y in 3..27
            val inBottom = x in 3..36 && y in 30..56
            val inBorder = x in 3..36 && y in 28..29
            when {
                inTop -> if (isTextured(x, y)) black else white
                inBottom -> if (isTextured(x, y)) white else black
                inBorder -> black
                else -> white
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size, "a thin border line between directly-touching panels should still split them")
    }

    @Test
    fun `a bordered panel with two internal content blobs and a gap between them is not split further`() {
        // 40x60 page: a second, unrelated textured panel on top (forcing a real first-level
        // split, matching how this happens on an actual multi-panel page - the
        // border-enclosure check only applies to sub-regions discovered after at least one
        // split, not to the whole page as a single top-level region), then below it, a panel
        // enclosed by a thin drawn frame containing two textured blobs (e.g. a character and
        // a speech bubble) with a real whitespace gap between them. A real manga panel's
        // border, once detected, means everything inside belongs together - the internal gap
        // must not be treated as a second-level gutter and used to split the panel into pieces.
        val page = buffer(40, 60) { x, y ->
            val inTopPanel = x in 3..36 && y in 3..20
            val inFrame = x in 3..36 && y in 30..53
            val inInnerWhite = x in 5..34 && y in 32..51
            val inBlobA = x in 7..15 && y in 35..48
            val inBlobB = x in 24..32 && y in 35..48
            when {
                inTopPanel -> if (isTextured(x, y)) black else white
                inBlobA || inBlobB -> if (isTextured(x, y)) black else white
                inFrame && !inInnerWhite -> black
                else -> white
            }
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size, "content inside a drawn border must stay one panel, not split at the internal gap")
    }
}
