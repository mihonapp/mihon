package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class YoloPanelDecoderTest {

    private val decoder = YoloPanelDecoder(inputSize = 640)

    private fun near(expected: Float, actual: Float, tol: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected ± $tol, got $actual")

    /** Builds an end-to-end `[1, n, 6]` output (rows of x1,y1,x2,y2,score,cls) in pixel space. */
    private fun endToEnd(rows: List<FloatArray>, capacity: Int = 300): Pair<FloatArray, IntArray> {
        val raw = FloatArray(capacity * 6)
        rows.forEachIndexed { i, r -> r.copyInto(raw, i * 6) }
        return raw to intArrayOf(1, capacity, 6)
    }

    @Test
    fun decodesEndToEndBoxesIntoNormalizedPageCoords() {
        // Square page == square input, so no letterbox: pixel/640 maps straight to [0,1].
        val lb = Letterbox.fit(640, 640, 640)
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(64f, 128f, 320f, 384f, 0.9f, 0f), // panel
                floatArrayOf(400f, 100f, 480f, 200f, 0.8f, 1f), // text/bubble
            ),
        )
        val result = decoder.decode(raw, shape, lb, 640, 640)

        assertEquals(1, result.panels.size)
        assertEquals(1, result.bubbles.size)
        val p = result.panels.single()
        near(0.1f, p.left); near(0.2f, p.top); near(0.5f, p.right); near(0.6f, p.bottom)
    }

    @Test
    fun belowThresholdDetectionsAreDropped() {
        val lb = Letterbox.fit(640, 640, 640)
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(64f, 64f, 320f, 320f, 0.10f, 0f), // < 0.25 default → dropped
            ),
        )
        assertTrue(decoder.decode(raw, shape, lb, 640, 640).panels.isEmpty())
    }

    @Test
    fun tinyPanelsAreFilteredByMinArea() {
        val lb = Letterbox.fit(640, 640, 640)
        // 10x10 px box → area 100 < minAreaFraction(0.008)*640*640 ≈ 3277.
        val (raw, shape) = endToEnd(listOf(floatArrayOf(0f, 0f, 10f, 10f, 0.9f, 0f)))
        assertTrue(decoder.decode(raw, shape, lb, 640, 640).panels.isEmpty())
    }

    @Test
    fun nestedDuplicatePanelsAreSuppressed() {
        val lb = Letterbox.fit(640, 640, 640)
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(50f, 50f, 400f, 400f, 0.95f, 0f), // outer, higher score
                floatArrayOf(60f, 60f, 390f, 390f, 0.90f, 0f), // mostly contained → dropped
            ),
        )
        assertEquals(1, decoder.decode(raw, shape, lb, 640, 640).panels.size)
    }

    @Test
    fun largePanelEvictsHigherScoringNestedPanel() {
        // A small panel scores higher than the huge panel that engulfs it. Suppression must still
        // drop the nested one (the larger box arrives second) — never keep panel-in-panel.
        val lb = Letterbox.fit(640, 640, 640)
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(20f, 8f, 160f, 150f, 0.95f, 0f), // small, higher score, fully nested
                floatArrayOf(0f, 6f, 640f, 536f, 0.90f, 0f), // huge, lower score, engulfs the small one
            ),
        )
        assertEquals(1, decoder.decode(raw, shape, lb, 640, 640).panels.size)
    }

    @Test
    fun partiallyOverlappingPanelsAreBothKept() {
        // A panel only ~⅓ inside a larger one (below the containment threshold) is a real neighbour
        // and must survive — guards the eviction above from being too aggressive.
        val lb = Letterbox.fit(640, 640, 640)
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(0f, 6f, 640f, 536f, 0.90f, 0f), // large upper region
                floatArrayOf(309f, 493f, 566f, 624f, 0.85f, 0f), // ~33% inside it → kept
            ),
        )
        assertEquals(2, decoder.decode(raw, shape, lb, 640, 640).panels.size)
    }

    @Test
    fun normalizedCoordsAreScaledToInput() {
        val lb = Letterbox.fit(640, 640, 640)
        // All coords ≤ 1 → decoder multiplies by inputSize.
        val (raw, shape) = endToEnd(listOf(floatArrayOf(0.1f, 0.2f, 0.5f, 0.6f, 0.9f, 0f)))
        val p = decoder.decode(raw, shape, lb, 640, 640).panels.single()
        near(0.1f, p.left); near(0.2f, p.top); near(0.5f, p.right); near(0.6f, p.bottom)
    }

    @Test
    fun letterboxIsUndoneForNonSquarePages() {
        // 1000x1500 page → scale 640/1500 ≈ 0.4267, newW ≈ 426, padX = (640-426)/2 = 107, padY = 0.
        val lb = Letterbox.fit(1000, 1500, 640)
        near(0.4267f, lb.scale, 0.001f)
        assertEquals(107, lb.padX)
        assertEquals(0, lb.padY)
        // A box spanning the full padded content width should map back to ~full page width.
        val (raw, shape) = endToEnd(
            listOf(
                floatArrayOf(lb.padX.toFloat(), 0f, (lb.padX + lb.newW).toFloat(), 640f, 0.9f, 0f),
            ),
        )
        val p = decoder.decode(raw, shape, lb, 1000, 1500).panels.single()
        near(0f, p.left); near(1f, p.right)
    }

    @Test
    fun decodesRawTransposedLayoutWithCxcywh() {
        // Raw layout [1, 6, anchors] (transposed): attrs=6 rows, anchors columns; cxcywh + 2 classes.
        val lb = Letterbox.fit(640, 640, 640)
        val anchors = 8400
        val raw = FloatArray(6 * anchors)
        // Anchor 0: a panel centered at (192,256), size 256x256 → xyxy (64,128,320,384).
        fun set(attr: Int, anchor: Int, v: Float) { raw[attr * anchors + anchor] = v }
        set(0, 0, 192f); set(1, 0, 256f); set(2, 0, 256f); set(3, 0, 256f)
        set(4, 0, 0.9f); set(5, 0, 0.1f) // cls0 (panel) wins
        val result = decoder.decode(raw, intArrayOf(1, 6, anchors), lb, 640, 640)
        assertEquals(1, result.panels.size)
        val p = result.panels.single()
        near(0.1f, p.left); near(0.2f, p.top); near(0.5f, p.right); near(0.6f, p.bottom)
    }

    @Test
    fun malformedShapesReturnEmpty() {
        val lb = Letterbox.fit(640, 640, 640)
        assertTrue(decoder.decode(FloatArray(10), intArrayOf(10), lb, 640, 640).panels.isEmpty())
        assertTrue(decoder.decode(FloatArray(10), intArrayOf(1, 5, 2), lb, 640, 640).panels.isEmpty())
    }
}
