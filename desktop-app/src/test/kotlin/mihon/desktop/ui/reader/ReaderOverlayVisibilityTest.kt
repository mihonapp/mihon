package mihon.desktop.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderOverlayVisibilityTest {
    @Test
    fun `edge reveal shows chrome and pointer movement restores cursor`() {
        val hidden = ReaderOverlayVisibilityState(chromeVisible = false, cursorVisible = false)

        val revealed = reduceReaderOverlayVisibility(hidden, ReaderOverlayEvent.PointerAtEdge)
        revealed.chromeVisible shouldBe true
        revealed.cursorVisible shouldBe true

        reduceReaderOverlayVisibility(hidden, ReaderOverlayEvent.PointerMoved).cursorVisible shouldBe true
    }

    @Test
    fun `reading input keeps the pointer visible until idle timeout`() {
        val visible = ReaderOverlayVisibilityState(chromeVisible = true, cursorVisible = false)

        val active = reduceReaderOverlayVisibility(visible, ReaderOverlayEvent.ReadingInput)
        active.chromeVisible shouldBe true
        active.cursorVisible shouldBe true

        val idle = reduceReaderOverlayVisibility(active, ReaderOverlayEvent.IdleTimeout)
        idle.chromeVisible shouldBe false
        idle.cursorVisible shouldBe false
    }

    @Test
    fun `controls retain chrome and cursor across idle timeout`() {
        val activeControl = ReaderOverlayVisibilityState(
            chromeVisible = true,
            cursorVisible = true,
            pointerOverControls = true,
        )

        reduceReaderOverlayVisibility(activeControl, ReaderOverlayEvent.IdleTimeout) shouldBe activeControl
    }

    @Test
    fun `leaving controls lets the next idle timeout hide overlays`() {
        val activeControl = ReaderOverlayVisibilityState(
            chromeVisible = true,
            cursorVisible = true,
            pointerOverControls = true,
        )

        val page = reduceReaderOverlayVisibility(activeControl, ReaderOverlayEvent.ControlExited)
        page.pointerOverControls shouldBe false
        reduceReaderOverlayVisibility(page, ReaderOverlayEvent.IdleTimeout) shouldBe
            ReaderOverlayVisibilityState(chromeVisible = false, cursorVisible = false)
    }
}
