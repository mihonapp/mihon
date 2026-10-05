package tachiyomi.core.common.util.system.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelOrderTest {

    private val a = PanelRect(0.05f, 0.05f, 0.45f, 0.45f)
    private val b = PanelRect(0.55f, 0.05f, 0.95f, 0.45f)
    private val c = PanelRect(0.05f, 0.55f, 0.45f, 0.95f)
    private val d = PanelRect(0.55f, 0.55f, 0.95f, 0.95f)

    @Test
    fun `2x2 grid left to right`() {
        assertEquals(listOf(a, b, c, d), PanelOrder.sort(listOf(d, b, c, a), rightToLeft = false))
    }

    @Test
    fun `2x2 grid right to left`() {
        assertEquals(listOf(b, a, d, c), PanelOrder.sort(listOf(d, a, c, b), rightToLeft = true))
    }

    @Test
    fun `stacked strips are the same in both directions`() {
        val s1 = PanelRect(0f, 0f, 1f, 0.3f)
        val s2 = PanelRect(0f, 0.33f, 1f, 0.63f)
        val s3 = PanelRect(0f, 0.66f, 1f, 1f)
        val expected = listOf(s1, s2, s3)
        assertEquals(expected, PanelOrder.sort(listOf(s3, s1, s2), rightToLeft = false))
        assertEquals(expected, PanelOrder.sort(listOf(s2, s3, s1), rightToLeft = true))
    }

    private val tall = PanelRect(0.05f, 0.05f, 0.45f, 0.95f)
    private val topRight = PanelRect(0.5f, 0.05f, 0.95f, 0.48f)
    private val bottomRight = PanelRect(0.5f, 0.52f, 0.95f, 0.95f)

    @Test
    fun `tall left panel next to two stacked panels left to right`() {
        assertEquals(
            listOf(tall, topRight, bottomRight),
            PanelOrder.sort(listOf(bottomRight, tall, topRight), rightToLeft = false),
        )
    }

    @Test
    fun `tall left panel next to two stacked panels right to left`() {
        assertEquals(
            listOf(topRight, bottomRight, tall),
            PanelOrder.sort(listOf(bottomRight, tall, topRight), rightToLeft = true),
        )
    }

    @Test
    fun `empty list`() {
        assertTrue(PanelOrder.sort(emptyList(), rightToLeft = false).isEmpty())
        assertTrue(PanelOrder.sort(emptyList(), rightToLeft = true, isSpread = true).isEmpty())
    }

    @Test
    fun `single panel`() {
        assertEquals(listOf(a), PanelOrder.sort(listOf(a), rightToLeft = false))
        assertEquals(listOf(a), PanelOrder.sort(listOf(a), rightToLeft = true))
    }

    @Test
    fun `slightly misaligned tops stay in the same row`() {
        val p1 = PanelRect(0.02f, 0.05f, 0.30f, 0.40f)
        val p2 = PanelRect(0.35f, 0.08f, 0.65f, 0.42f)
        val p3 = PanelRect(0.70f, 0.02f, 0.98f, 0.38f)
        val below = PanelRect(0.02f, 0.55f, 0.98f, 0.95f)
        assertEquals(
            listOf(p1, p2, p3, below),
            PanelOrder.sort(listOf(below, p3, p1, p2), rightToLeft = false),
        )
        assertEquals(
            listOf(p3, p2, p1, below),
            PanelOrder.sort(listOf(below, p1, p3, p2), rightToLeft = true),
        )
    }

    private val la = PanelRect(0.02f, 0.05f, 0.23f, 0.45f)
    private val lb = PanelRect(0.27f, 0.05f, 0.48f, 0.45f)
    private val lc = PanelRect(0.02f, 0.55f, 0.23f, 0.95f)
    private val ld = PanelRect(0.27f, 0.55f, 0.48f, 0.95f)
    private val ra = PanelRect(0.52f, 0.05f, 0.73f, 0.45f)
    private val rb = PanelRect(0.77f, 0.05f, 0.98f, 0.45f)
    private val rc = PanelRect(0.52f, 0.55f, 0.73f, 0.95f)
    private val rd = PanelRect(0.77f, 0.55f, 0.98f, 0.95f)
    private val spread = listOf(rd, lc, ra, lb, rc, la, rb, ld)

    @Test
    fun `spread left to right does not interleave halves`() {
        assertEquals(
            listOf(la, lb, lc, ld, ra, rb, rc, rd),
            PanelOrder.sort(spread, rightToLeft = false, isSpread = true),
        )
    }

    @Test
    fun `spread right to left does not interleave halves`() {
        assertEquals(
            listOf(rb, ra, rd, rc, lb, la, ld, lc),
            PanelOrder.sort(spread, rightToLeft = true, isSpread = true),
        )
    }
}
