package mihon.desktop.reader.window

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.window.WindowPlacement
import org.junit.jupiter.api.Test

class ReaderWindowControllerTest {
    @Test fun `all transitions save normal bounds and restore them`() {
        val normal = WindowPlacement(10, 20, 1000, 700, false)
        val controller = ReaderWindowController(normal)
        ReaderWindowMode.entries.forEach { from ->
            ReaderWindowMode.entries.forEach { to ->
                controller.restore(from, normal)
                controller.transition(to, normal) shouldBe to
            }
        }
        controller.restore(ReaderWindowMode.NORMAL, normal)
        controller.transition(ReaderWindowMode.FULLSCREEN, normal)
        controller.transition(ReaderWindowMode.BORDERLESS, WindowPlacement(0, 0, 1920, 1080, false))
        controller.transition(ReaderWindowMode.NORMAL, WindowPlacement(0, 0, 1920, 1080, false)) shouldBe
            ReaderWindowMode.NORMAL
        controller.normalBounds shouldBe normal
    }

    @Test fun `escape exits transient mode before asking reader to close and rapid toggles serialize`() {
        val normal = WindowPlacement(10, 20, 1000, 700, false)
        val applied = mutableListOf<ReaderWindowMode>()
        val controller = ReaderWindowController(normal) { applied += it }
        controller.transition(ReaderWindowMode.FULLSCREEN, normal)
        controller.onEscape(WindowPlacement(0, 0, 1920, 1080, false)) shouldBe ReaderWindowEscape.ReturnedToNormal
        controller.onEscape(normal) shouldBe ReaderWindowEscape.CloseReader
        repeat(40) { controller.toggleFullscreen(normal) }
        controller.mode shouldBe ReaderWindowMode.NORMAL
        applied.distinct().isNotEmpty() shouldBe true
    }
}
