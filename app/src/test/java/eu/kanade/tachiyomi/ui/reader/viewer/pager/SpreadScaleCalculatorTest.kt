package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.SpreadVerticalFit
import eu.kanade.tachiyomi.ui.reader.viewer.pager.SpreadScaleCalculator.ScaleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class SpreadScaleCalculatorTest {

    private val eps = 0.001f

    // --- overallScale: the per-scale-type formulas against the combined canvas ---
    // Combined 1800x1350 (landscape) in a 1080x2100 portrait viewport, so the branches differ.

    @Test
    fun `overallScale fit width uses viewport width over combined width`() {
        assertEquals(
            1080f / 1800f,
            SpreadScaleCalculator.overallScale(ScaleType.FIT_WIDTH, 1800f, 1350f, 1080f, 2100f),
            eps,
        )
    }

    @Test
    fun `overallScale fit height uses viewport height over combined height`() {
        assertEquals(
            2100f / 1350f,
            SpreadScaleCalculator.overallScale(ScaleType.FIT_HEIGHT, 1800f, 1350f, 1080f, 2100f),
            eps,
        )
    }

    @Test
    fun `overallScale fit screen is the smaller of the two fits`() {
        assertEquals(
            1080f / 1800f,
            SpreadScaleCalculator.overallScale(ScaleType.FIT_SCREEN, 1800f, 1350f, 1080f, 2100f),
            eps,
        )
    }

    @Test
    fun `overallScale stretch is the larger of the two fits`() {
        assertEquals(
            2100f / 1350f,
            SpreadScaleCalculator.overallScale(ScaleType.STRETCH, 1800f, 1350f, 1080f, 2100f),
            eps,
        )
    }

    @Test
    fun `overallScale original size is always one`() {
        assertEquals(1f, SpreadScaleCalculator.overallScale(ScaleType.ORIGINAL, 1800f, 1350f, 1080f, 2100f), eps)
    }

    @Test
    fun `overallScale smart fit fits height for a landscape combined shape`() {
        // combinedHeight (1350) is not greater than combinedWidth (1800), so it fits height.
        assertEquals(
            2100f / 1350f,
            SpreadScaleCalculator.overallScale(ScaleType.SMART_FIT, 1800f, 1350f, 1080f, 2100f),
            eps,
        )
    }

    @Test
    fun `overallScale smart fit fits width for a portrait combined shape`() {
        // combinedHeight (1800) greater than combinedWidth (1350), so it fits width.
        assertEquals(
            1080f / 1350f,
            SpreadScaleCalculator.overallScale(ScaleType.SMART_FIT, 1350f, 1800f, 1080f, 2100f),
            eps,
        )
    }

    // --- computePlacement: the resolution-mismatch case ---
    // Left 450x675 (LoRes), right 900x1350 (HiRes), 540px slots, 2100px viewport, fit-width.

    @Test
    fun `mismatch without scale-to-match renders both halves at the same scale (2 to 1 on screen)`() {
        val p = mismatch()
        // overallScale = 1080/1350 = 0.8; both halves get it since logical == native.
        assertEquals(0.8f, p.left.scale, eps)
        assertEquals(0.8f, p.right.scale, eps)
        assertEquals(1f, p.scaleRatioRightOverLeft, eps)
        // LoRes renders 450*0.8 = 360 in a 540 slot -> 180 slack pushed to its outer (left) edge.
        assertEquals(180, p.left.padding.left)
        assertEquals(0, p.left.padding.right)
        // HiRes renders 900*0.8 = 720, overflowing its 540 slot -> no slack.
        assertEquals(0, p.right.padding.left)
        assertEquals(0, p.right.padding.right)
    }

    @Test
    fun `mismatch with scale-to-match upsamples the smaller half so both render equal`() {
        val p = mismatch(SpreadVerticalFit.MATCH)
        // LoRes logical becomes 900x1350; overallScale = 1080/1800 = 0.6.
        // LoRes scale = 900/450*0.6 = 1.2 (upsampled), HiRes scale = 0.6; both render 540 wide.
        assertEquals(1.2f, p.left.scale, eps)
        assertEquals(0.6f, p.right.scale, eps)
        assertEquals(0.5f, p.scaleRatioRightOverLeft, eps)
        // Each fills its slot exactly -> no horizontal slack either side.
        assertEquals(0, p.left.padding.left)
        assertEquals(0, p.right.padding.right)
        // scale-to-match suppresses vertical alignment padding.
        assertEquals(0, p.left.padding.top)
        assertEquals(0, p.left.padding.bottom)
    }

    @Test
    fun `original size scales against the logical canvas, not raw pixels`() {
        // With scale-to-match on, ORIGINAL is 1:1 on the normalized 900x1350 canvas, so the LoRes
        // half is upsampled 2x rather than shown at its true (smaller) pixel size.
        val p = mismatch(SpreadVerticalFit.MATCH, scaleType = ScaleType.ORIGINAL)
        assertEquals(2f, p.left.scale, eps)
        assertEquals(1f, p.right.scale, eps)
    }

    @Test
    fun `matched pair fills both slots with no padding`() {
        val p = SpreadScaleCalculator.computePlacement(
            nativeWidthLeft = 900, nativeHeightLeft = 1350,
            nativeWidthRight = 900, nativeHeightRight = 1350,
            slotWidthLeft = 900, slotWidthRight = 900, viewportHeight = 2100,
            scaleType = ScaleType.FIT_WIDTH, verticalFit = SpreadVerticalFit.CENTER,
        )
        // viewport width 1800 / combined 1800 = 1.0.
        assertEquals(1f, p.left.scale, eps)
        assertEquals(1f, p.right.scale, eps)
        assertEquals(SpreadScaleCalculator.Padding(0, 0, 0, 0), p.left.padding)
        assertEquals(SpreadScaleCalculator.Padding(0, 0, 0, 0), p.right.padding)
    }

    // --- unequal-height alignment (only applies without match-heights, when heights differ) ---

    @Test
    fun `top alignment pads the shorter half to its taller sibling's top edge`() {
        val p = mismatch(SpreadVerticalFit.TOP)
        // Rendered heights: LoRes 675*0.8=540, HiRes 1350*0.8=1080. Box top = (2100-1080)/2 = 510.
        assertEquals(510, p.left.padding.top)
        assertEquals(2100 - 540 - 510, p.left.padding.bottom) // 1050
        // The taller half is the full box height -> no padding.
        assertEquals(0, p.right.padding.top)
        assertEquals(0, p.right.padding.bottom)
    }

    @Test
    fun `center alignment adds no vertical padding`() {
        val p = mismatch(SpreadVerticalFit.CENTER)
        assertEquals(0, p.left.padding.top)
        assertEquals(0, p.left.padding.bottom)
    }

    // --- live pan/zoom sync ---

    @Test
    fun `sync mirrors scale and pan for a matched pair`() {
        // Matched 900x1350 halves: initial centres (450, 675) both; ratio 1.0. Zoom left to 2x and pan.
        val s = SpreadScaleCalculator.syncTarget(
            sourceScale = 2f, sourceCenterX = 500f, sourceCenterY = 400f, sourceIsLeft = true,
            scaleRatioRightOverLeft = 1f,
            initialCenterXLeft = 450f, initialCenterYLeft = 675f,
            initialCenterXRight = 450f, initialCenterYRight = 675f,
        )!!
        assertEquals(2f, s.scale, eps)
        assertEquals(500f, s.centerX, eps)
        assertEquals(400f, s.centerY, eps)
    }

    @Test
    fun `sync honours the fixed scale ratio and equal screen delta for a mismatched pair`() {
        // Left 450x675 -> initial (225, 337.5); right 900x1350 -> initial (450, 675); ratio 0.5.
        val s = SpreadScaleCalculator.syncTarget(
            sourceScale = 1.2f, sourceCenterX = 250f, sourceCenterY = 400f, sourceIsLeft = true,
            scaleRatioRightOverLeft = 0.5f,
            initialCenterXLeft = 225f, initialCenterYLeft = 337.5f,
            initialCenterXRight = 450f, initialCenterYRight = 675f,
        )!!
        assertEquals(0.6f, s.scale, eps) // 1.2 * 0.5
        assertEquals(500f, s.centerX, eps) // 450 + (250-225)*1.2 / 0.6
        assertEquals(800f, s.centerY, eps) // 675 + (400-337.5)*1.2 / 0.6
    }

    @Test
    fun `sync returns null for a degenerate source scale`() {
        assertNull(
            SpreadScaleCalculator.syncTarget(
                sourceScale = 0f, sourceCenterX = 0f, sourceCenterY = 0f, sourceIsLeft = true,
                scaleRatioRightOverLeft = 1f,
                initialCenterXLeft = 0f, initialCenterYLeft = 0f, initialCenterXRight = 0f, initialCenterYRight = 0f,
            ),
        )
    }

    private fun mismatch(
        verticalFit: SpreadVerticalFit = SpreadVerticalFit.CENTER,
        scaleType: ScaleType = ScaleType.FIT_WIDTH,
    ) = SpreadScaleCalculator.computePlacement(
        nativeWidthLeft = 450, nativeHeightLeft = 675,
        nativeWidthRight = 900, nativeHeightRight = 1350,
        slotWidthLeft = 540, slotWidthRight = 540, viewportHeight = 2100,
        scaleType = scaleType, verticalFit = verticalFit,
    )

    // --- generative invariants (random inputs, seeded for reproducibility) ---

    @Test
    fun `overallScale relationships hold across random shapes`() {
        val rnd = Random(3)
        repeat(2000) {
            val cw = rnd.nextInt(1, 6000).toFloat()
            val ch = rnd.nextInt(1, 6000).toFloat()
            val vw = rnd.nextInt(1, 4000).toFloat()
            val vh = rnd.nextInt(1, 4000).toFloat()
            val fitW = SpreadScaleCalculator.overallScale(ScaleType.FIT_WIDTH, cw, ch, vw, vh)
            val fitH = SpreadScaleCalculator.overallScale(ScaleType.FIT_HEIGHT, cw, ch, vw, vh)
            val screen = SpreadScaleCalculator.overallScale(ScaleType.FIT_SCREEN, cw, ch, vw, vh)
            val stretch = SpreadScaleCalculator.overallScale(ScaleType.STRETCH, cw, ch, vw, vh)
            val smart = SpreadScaleCalculator.overallScale(ScaleType.SMART_FIT, cw, ch, vw, vh)

            assertTrue(fitW > 0f && fitH > 0f && screen > 0f && stretch > 0f)
            assertEquals(1f, SpreadScaleCalculator.overallScale(ScaleType.ORIGINAL, cw, ch, vw, vh))
            // fit-width fills the width exactly; fit-height fills the height exactly
            assertEquals(vw, cw * fitW, vw * 1e-4f)
            assertEquals(vh, ch * fitH, vh * 1e-4f)
            // fit-screen is the smaller of the two axis fits; stretch is the larger
            assertEquals(minOf(fitW, fitH), screen, 1e-6f)
            assertEquals(maxOf(fitW, fitH), stretch, 1e-6f)
            // fit-screen never overflows the viewport; stretch always covers it
            assertTrue(cw * screen <= vw + vw * 1e-4f && ch * screen <= vh + vh * 1e-4f)
            assertTrue(cw * stretch >= vw - vw * 1e-4f && ch * stretch >= vh - vh * 1e-4f)
            // smart-fit resolves to one of the two axis fits
            assertTrue(smart == fitW || smart == fitH)
        }
    }

    @Test
    fun `computePlacement abuts the seam, keeps scales positive, and equalises heights under scale-to-match`() {
        val rnd = Random(4)
        val types = ScaleType.entries
        val fits = SpreadVerticalFit.entries
        repeat(2000) {
            val nwl = rnd.nextInt(50, 4000)
            val nhl = rnd.nextInt(50, 4000)
            val nwr = rnd.nextInt(50, 4000)
            val nhr = rnd.nextInt(50, 4000)
            val fit = fits[rnd.nextInt(fits.size)]
            val p = SpreadScaleCalculator.computePlacement(
                nwl, nhl, nwr, nhr,
                slotWidthLeft = rnd.nextInt(50, 2000), slotWidthRight = rnd.nextInt(50, 2000),
                viewportHeight = rnd.nextInt(100, 4000),
                scaleType = types[rnd.nextInt(types.size)],
                verticalFit = fit,
            )
            // the two halves always abut at the seam: no padding on the inner (seam) edges
            assertEquals(0, p.left.padding.right)
            assertEquals(0, p.right.padding.left)
            assertTrue(p.left.scale > 0f && p.right.scale > 0f)
            if (fit == SpreadVerticalFit.MATCH) {
                val hl = nhl * p.left.scale
                val hr = nhr * p.right.scale
                assertEquals(hl, hr, maxOf(hl, hr) * 1e-3f)
            }
        }
    }

    @Test
    fun `syncTarget mirrors an equal on-screen delta and round-trips`() {
        val rnd = Random(5)
        repeat(2000) {
            val nwl = rnd.nextInt(50, 4000)
            val nhl = rnd.nextInt(50, 4000)
            val p = SpreadScaleCalculator.computePlacement(
                nwl, nhl, rnd.nextInt(50, 4000), rnd.nextInt(50, 4000),
                slotWidthLeft = rnd.nextInt(50, 2000), slotWidthRight = rnd.nextInt(50, 2000),
                viewportHeight = rnd.nextInt(100, 4000),
                scaleType = ScaleType.FIT_SCREEN, verticalFit = SpreadVerticalFit.DEFAULT,
            )
            // the user zooms/pans the LEFT half to an arbitrary scale/centre
            val srcScale = p.left.scale * (0.5f + rnd.nextFloat() * 3f)
            val srcCx = rnd.nextFloat() * nwl
            val srcCy = rnd.nextFloat() * nhl
            val right = SpreadScaleCalculator.syncTarget(
                srcScale, srcCx, srcCy, sourceIsLeft = true,
                p.scaleRatioRightOverLeft,
                p.initialCenterXLeft, p.initialCenterYLeft, p.initialCenterXRight, p.initialCenterYRight,
            )!!
            // both halves move by the same on-screen pixel delta
            val srcDeltaX = (srcCx - p.initialCenterXLeft) * srcScale
            val tgtDeltaX = (right.centerX - p.initialCenterXRight) * right.scale
            assertEquals(srcDeltaX, tgtDeltaX, maxOf(1f, abs(srcDeltaX)) * 1e-3f)
            // syncing back from the right half returns the left's original scale/centre
            val back = SpreadScaleCalculator.syncTarget(
                right.scale, right.centerX, right.centerY, sourceIsLeft = false,
                p.scaleRatioRightOverLeft,
                p.initialCenterXLeft, p.initialCenterYLeft, p.initialCenterXRight, p.initialCenterYRight,
            )!!
            assertEquals(srcScale, back.scale, srcScale * 1e-3f)
            assertEquals(srcCx, back.centerX, maxOf(1f, srcCx) * 1e-3f)
            assertEquals(srcCy, back.centerY, maxOf(1f, srcCy) * 1e-3f)
        }
    }

    // --- soloPlacement: a justified lone page fit into one half-slot, abutting the seam ---

    @Test
    fun `soloPlacement fits a portrait page to exactly one half-width and abuts the seam`() {
        // 900x1350 in 1080x2100, fit-screen: slot = 540; scale = min(540/900, 2100/1350) = 0.6;
        // rendered width = 540 = the whole slot, so no slack.
        val left = SpreadScaleCalculator.soloPlacement(900, 1350, 1080, 2100, ScaleType.FIT_SCREEN, alignLeft = true)
        assertEquals(0.6f, left.scale, eps)
        // right half (540..1080) blocked off; page fills the left slot flush to the seam.
        assertEquals(540, left.padding.right)
        assertEquals(0, left.padding.left)

        val right = SpreadScaleCalculator.soloPlacement(900, 1350, 1080, 2100, ScaleType.FIT_SCREEN, alignLeft = false)
        assertEquals(540, right.padding.left)
        assertEquals(0, right.padding.right)
    }

    @Test
    fun `soloPlacement pushes a narrow page against the seam with the slack outward`() {
        // 300x2000 in 1080x2100, fit-screen: slot=540; scale=min(540/300, 2100/2000)=1.05;
        // rendered width = 315, slack = 540-315 = 225 goes to the OUTER edge.
        val left = SpreadScaleCalculator.soloPlacement(300, 2000, 1080, 2100, ScaleType.FIT_SCREEN, alignLeft = true)
        assertEquals(1.05f, left.scale, eps)
        assertEquals(225, left.padding.left) // slack outward (left of a left-justified page)
        assertEquals(540, left.padding.right) // far half blocked
    }

    @Test
    fun `soloPlacement always keeps the page within its half, inner edge at the seam`() {
        val rnd = Random(6)
        val types = ScaleType.entries
        repeat(2000) {
            val nw = rnd.nextInt(50, 4000)
            val nh = rnd.nextInt(50, 4000)
            val vw = rnd.nextInt(50, 2000) * 2 // even, so half-width is exact and the seam is integral
            val vh = rnd.nextInt(100, 4000)
            val alignLeft = rnd.nextBoolean()
            val p = SpreadScaleCalculator.soloPlacement(nw, nh, vw, vh, types[rnd.nextInt(types.size)], alignLeft)
            val slot = vw / 2
            val renderedWidth = nw * p.scale
            assertTrue(p.scale > 0f)
            // The far half is always blocked off entirely.
            assertEquals(slot, if (alignLeft) p.padding.right else p.padding.left)
            // When the page fits its half, its inner edge lands on the seam (±1px rounding).
            if (renderedWidth <= slot) {
                val nearPad = if (alignLeft) p.padding.left else p.padding.right
                assertTrue(abs((nearPad + renderedWidth) - slot) <= 1f, "inner edge at seam (align=$alignLeft)")
            }
        }
    }

    // --- slotWeights: proportional half-slots so an unequal-aspect pair isn't clipped by a 50/50 split ---

    @Test
    fun `slotWeights are 1 to 1 for an equal-width pair`() {
        val (l, r) = SpreadScaleCalculator.slotWeights(800, 1200, 800, 1200, SpreadVerticalFit.DEFAULT)
        assertEquals(1f, l, eps)
        assertEquals(1f, r, eps)
    }

    @Test
    fun `slotWeights are proportional to width and sum to 2`() {
        // widths 800 and 1200 -> shares 0.8 and 1.2 (normalised to sum 2).
        val (l, r) = SpreadScaleCalculator.slotWeights(800, 1000, 1200, 1000, SpreadVerticalFit.DEFAULT)
        assertEquals(0.8f, l, eps)
        assertEquals(1.2f, r, eps)
        assertEquals(2f, l + r, eps)
    }

    @Test
    fun `slotWeights use the height-matched logical width under MATCH`() {
        // Right half is half the height, so MATCH upsamples it 2x -> its logical width doubles (600->1200),
        // making the pair equal-logical-width (1:1), unlike the raw-native 1200:600 it would be otherwise.
        val (l, r) = SpreadScaleCalculator.slotWeights(1200, 2000, 600, 1000, SpreadVerticalFit.MATCH)
        assertEquals(1f, l, eps)
        assertEquals(1f, r, eps)
    }

    @Test
    fun `slotWeights fall back to 1 to 1 on a degenerate size`() {
        val (l, r) = SpreadScaleCalculator.slotWeights(0, 1000, 800, 1000, SpreadVerticalFit.DEFAULT)
        assertEquals(1f, l, eps)
        assertEquals(1f, r, eps)
    }

    @Test
    fun `proportional slots keep the wider half inside its slot, where a 50-50 split clips it`() {
        val nwL = 900
        val nwR = 1500
        val nh = 1000
        val viewport = 2400
        val (wl, wr) = SpreadScaleCalculator.slotWeights(nwL, nh, nwR, nh, SpreadVerticalFit.DEFAULT)
        val slotL = (viewport * wl / (wl + wr)).toInt()
        val slotR = viewport - slotL
        val proportional = SpreadScaleCalculator.computePlacement(
            nwL, nh, nwR, nh, slotL, slotR, viewportHeight = 3000,
            scaleType = ScaleType.FIT_SCREEN, verticalFit = SpreadVerticalFit.DEFAULT,
        )
        assertTrue(nwL * proportional.left.scale <= slotL + 1f, "left half within its slot")
        assertTrue(nwR * proportional.right.scale <= slotR + 1f, "right (wider) half within its slot")

        // Regression guard: the old 50/50 split renders the wider half past its half-slot (clipped).
        val half = viewport / 2
        val equal = SpreadScaleCalculator.computePlacement(
            nwL, nh, nwR, nh, half, half, viewportHeight = 3000,
            scaleType = ScaleType.FIT_SCREEN, verticalFit = SpreadVerticalFit.DEFAULT,
        )
        assertTrue(nwR * equal.right.scale > half, "50/50 split overflows the wider half")
    }
}
