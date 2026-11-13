package tachiyomi.translation.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class NormalizedRectTest {
    private fun assertClose(actual: Float, expected: Float) {
        (abs(actual - expected) < 1e-6f).shouldBe(true)
    }

    @Test
    fun `fromPixels maps into unit space`() {
        val rect = NormalizedRect.fromPixels(left = 100, top = 50, right = 300, bottom = 250, imageWidth = 1000, imageHeight = 500)

        rect.left shouldBe 0.1f
        rect.top shouldBe 0.1f
        rect.right shouldBe 0.3f
        rect.bottom shouldBe 0.5f
        assertClose(rect.width, 0.2f)
        assertClose(rect.height, 0.4f)
        (rect.width > 0f).shouldBe(true)
    }

    @Test
    fun `fromPixels rejects empty images`() {
        NormalizedRect.fromPixels(0, 0, 10, 10, imageWidth = 0, imageHeight = 100) shouldBe NormalizedRect.EMPTY
    }

    @Test
    fun `clampToUnit fixes inverted and out of range coordinates`() {
        val rect = NormalizedRect(left = 1.4f, top = 0.8f, right = -0.2f, bottom = 0.1f).clampToUnit()

        rect.left shouldBe 0f
        rect.right shouldBe 1f
        rect.top shouldBe 0.1f
        rect.bottom shouldBe 0.8f
    }

    @Test
    fun `toPixels round trips the box`() {
        val source = NormalizedRect(0.25f, 0.5f, 0.75f, 0.9f)

        val pixels = source.toPixels(imageWidth = 800, imageHeight = 1000)

        pixels.left shouldBe 200
        pixels.top shouldBe 500
        pixels.right shouldBe 600
        pixels.bottom shouldBe 900
        pixels.width shouldBe 400
        pixels.shortSide shouldBe 400
    }

    @Test
    fun `zero sized rect is not valid`() {
        NormalizedRect(0.4f, 0.4f, 0.4f, 0.9f).isValid shouldBe false
    }
}
