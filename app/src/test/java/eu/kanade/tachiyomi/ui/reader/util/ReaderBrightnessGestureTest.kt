package eu.kanade.tachiyomi.ui.reader.util

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.VerticalBrightnessGesture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderBrightnessGestureTest {

    @Test
    fun `isInEdgeZone returns true for LEFT mode when x is within left edge threshold`() {
        val width = 1000f
        ReaderBrightnessGesture.isInEdgeZone(0f, width, VerticalBrightnessGesture.LEFT) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(25f, width, VerticalBrightnessGesture.LEFT) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(50f, width, VerticalBrightnessGesture.LEFT) shouldBe true
    }

    @Test
    fun `isInEdgeZone returns false for LEFT mode when x is outside left edge threshold`() {
        val width = 1000f
        ReaderBrightnessGesture.isInEdgeZone(51f, width, VerticalBrightnessGesture.LEFT) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(500f, width, VerticalBrightnessGesture.LEFT) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(980f, width, VerticalBrightnessGesture.LEFT) shouldBe false
    }

    @Test
    fun `isInEdgeZone returns true for RIGHT mode when x is within right edge threshold`() {
        val width = 1000f
        ReaderBrightnessGesture.isInEdgeZone(950f, width, VerticalBrightnessGesture.RIGHT) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(975f, width, VerticalBrightnessGesture.RIGHT) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(1000f, width, VerticalBrightnessGesture.RIGHT) shouldBe true
    }

    @Test
    fun `isInEdgeZone returns false for RIGHT mode when x is outside right edge threshold`() {
        val width = 1000f
        ReaderBrightnessGesture.isInEdgeZone(0f, width, VerticalBrightnessGesture.RIGHT) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(500f, width, VerticalBrightnessGesture.RIGHT) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(949f, width, VerticalBrightnessGesture.RIGHT) shouldBe false
    }

    @Test
    fun `isInEdgeZone returns false when mode is OFF`() {
        val width = 1000f
        ReaderBrightnessGesture.isInEdgeZone(10f, width, VerticalBrightnessGesture.OFF) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(990f, width, VerticalBrightnessGesture.OFF) shouldBe false
    }

    @Test
    fun `isInEdgeZone returns false for zero or negative width`() {
        ReaderBrightnessGesture.isInEdgeZone(0f, 0f, VerticalBrightnessGesture.LEFT) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(0f, -100f, VerticalBrightnessGesture.LEFT) shouldBe false
    }

    @Test
    fun `isInEdgeZone respects custom threshold fraction 0_15`() {
        val width = 1000f
        val fraction = 0.15f
        // LEFT mode -> 0..150
        ReaderBrightnessGesture.isInEdgeZone(0f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(150f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(151f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe false

        // RIGHT mode -> 850..1000
        ReaderBrightnessGesture.isInEdgeZone(849f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(850f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(1000f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe true
    }

    @Test
    fun `isInEdgeZone respects custom threshold fraction 0_25`() {
        val width = 1000f
        val fraction = 0.25f
        ReaderBrightnessGesture.isInEdgeZone(250f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(251f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(750f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(749f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe false
    }

    @Test
    fun `isInEdgeZone with fraction 1_0 returns true across full width`() {
        val width = 1000f
        val fraction = 1.0f
        ReaderBrightnessGesture.isInEdgeZone(500f, width, VerticalBrightnessGesture.LEFT, fraction) shouldBe true
        ReaderBrightnessGesture.isInEdgeZone(500f, width, VerticalBrightnessGesture.RIGHT, fraction) shouldBe true
    }

    @Test
    fun `isInEdgeZone with custom fraction returns false for zero or negative width`() {
        ReaderBrightnessGesture.isInEdgeZone(0f, 0f, VerticalBrightnessGesture.LEFT, 0.15f) shouldBe false
        ReaderBrightnessGesture.isInEdgeZone(0f, -500f, VerticalBrightnessGesture.LEFT, 0.25f) shouldBe false
    }

    @Test
    fun `isVerticalGesture returns false when deltaY is below touch slop`() {
        val touchSlop = 10f
        ReaderBrightnessGesture.isVerticalGesture(dx = 0f, dy = 5f, touchSlop = touchSlop) shouldBe false
        ReaderBrightnessGesture.isVerticalGesture(dx = 0f, dy = -10f, touchSlop = touchSlop) shouldBe false
    }

    @Test
    fun `isVerticalGesture returns true when deltaY exceeds slop and dominates deltaX`() {
        val touchSlop = 10f
        ReaderBrightnessGesture.isVerticalGesture(dx = 2f, dy = 25f, touchSlop = touchSlop) shouldBe true
        ReaderBrightnessGesture.isVerticalGesture(dx = -3f, dy = -30f, touchSlop = touchSlop) shouldBe true
    }

    @Test
    fun `isVerticalGesture returns false for horizontal or diagonal gestures`() {
        val touchSlop = 10f
        // Pure horizontal
        ReaderBrightnessGesture.isVerticalGesture(dx = 30f, dy = 2f, touchSlop = touchSlop) shouldBe false
        // Diagonal (|dy| <= |dx| * 2)
        ReaderBrightnessGesture.isVerticalGesture(dx = 15f, dy = 20f, touchSlop = touchSlop) shouldBe false
    }

    @Test
    fun `calculateBrightnessValue increases brightness when swiping UP`() {
        val screenHeight = 1000f
        // Swiping UP (-200px dy) on 1000px screen height = +20 units
        ReaderBrightnessGesture.calculateBrightnessValue(
            initialBrightness = 50,
            deltaYPixels = -200f,
            screenHeightPixels = screenHeight,
        ) shouldBe 70
    }

    @Test
    fun `calculateBrightnessValue decreases brightness when swiping DOWN`() {
        val screenHeight = 1000f
        // Swiping DOWN (+200px dy) on 1000px screen height = -20 units
        ReaderBrightnessGesture.calculateBrightnessValue(
            initialBrightness = 50,
            deltaYPixels = 200f,
            screenHeightPixels = screenHeight,
        ) shouldBe 30
    }

    @Test
    fun `calculateBrightnessValue clamps at max brightness 100`() {
        val screenHeight = 1000f
        ReaderBrightnessGesture.calculateBrightnessValue(
            initialBrightness = 80,
            deltaYPixels = -500f,
            screenHeightPixels = screenHeight,
        ) shouldBe 100
    }

    @Test
    fun `calculateBrightnessValue clamps at min brightness -75`() {
        val screenHeight = 1000f
        ReaderBrightnessGesture.calculateBrightnessValue(
            initialBrightness = -50,
            deltaYPixels = 500f,
            screenHeightPixels = screenHeight,
        ) shouldBe -75
    }

    @Test
    fun `calculateBrightnessValue returns initial brightness when screen height is zero or negative`() {
        ReaderBrightnessGesture.calculateBrightnessValue(
            initialBrightness = 40,
            deltaYPixels = -200f,
            screenHeightPixels = 0f,
        ) shouldBe 40
    }
}
