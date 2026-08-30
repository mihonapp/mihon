package mihon.desktop.window

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowPlacementTest {

    private val screen = ScreenBounds(x = 0, y = 0, width = 1920, height = 1080)

    @Test
    fun `valid placement is preserved`() {
        val placement = WindowPlacement(120, 80, 1280, 800, maximized = false)

        placement.sanitize(screen) shouldBe placement
    }

    @Test
    fun `undersized and offscreen placement is centered and enlarged`() {
        WindowPlacement(5000, 5000, 200, 100, maximized = false).sanitize(screen) shouldBe
            WindowPlacement(510, 240, 900, 600, maximized = false)
    }

    @Test
    fun `placement is limited to available screen size`() {
        WindowPlacement(0, 0, 4000, 3000, maximized = true).sanitize(screen) shouldBe
            WindowPlacement(0, 0, 1920, 1080, maximized = true)
    }
}
