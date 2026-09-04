package mihon.desktop.reader.input

import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import org.junit.jupiter.api.Test

class ReaderInputMapperTest {
    @Test fun `directional keys map every LTR and RTL navigation key`() {
        val mapper = ReaderInputMapper()
        val ltr = ReaderInputContext(ReadingMode.SINGLE_LTR)
        val rtl = ReaderInputContext(ReadingMode.SINGLE_RTL)
        listOf(ReaderInputKey.LEFT, ReaderInputKey.A).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
            mapper.mapKey(key, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        }
        listOf(ReaderInputKey.RIGHT, ReaderInputKey.D).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
            mapper.mapKey(key, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
        }
        mapper.mapKey(ReaderInputKey.PAGE_UP, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapKey(ReaderInputKey.PAGE_DOWN, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapKey(ReaderInputKey.HOME, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.SelectPage(0))
        mapper.mapKey(ReaderInputKey.END, ReaderInputContext(ReadingMode.SINGLE_LTR, pageCount = 8)).action shouldBe
            ReaderInputCommand.Core(ReaderAction.SelectPage(7))
    }

    @Test fun `modifiers and focused controls take precedence and mapped events alone consume`() {
        val mapper = ReaderInputMapper()
        mapper.mapKey(
            ReaderInputKey.RIGHT,
            ReaderInputContext(ReadingMode.SINGLE_LTR, focus = ReaderInputFocus.TEXT),
        ).consumed shouldBe
            false
        mapper.mapKey(ReaderInputKey.RIGHT, ReaderInputContext(ReadingMode.SINGLE_LTR, ctrl = true)).consumed shouldBe
            false
        mapper.mapKey(ReaderInputKey.F, ReaderInputContext(ReadingMode.SINGLE_LTR, ctrl = true)).consumed shouldBe false
        mapper.mapKey(ReaderInputKey.F, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Fullscreen
        mapper.mapKey(ReaderInputKey.B, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Borderless
        mapper.mapKey(ReaderInputKey.ESCAPE, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Escape
        mapper.mapKey(ReaderInputKey.X, ReaderInputContext(ReadingMode.SINGLE_LTR)).consumed shouldBe false
    }

    @Test fun `wheel uses zoom before scroll and pages only after threshold and rate limit`() {
        val mapper = ReaderInputMapper(nowMillis = { now })
        val paged = ReaderInputContext(ReadingMode.SINGLE_LTR, wheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION)
        mapper.mapWheel(40f, paged, ctrl = true, centroid = InputPoint(.4f, .6f)).action shouldBe
            ReaderInputCommand.ZoomBy(1.1f, InputPoint(.4f, .6f))
        mapper.mapWheel(50f, paged).consumed shouldBe false
        mapper.mapWheel(70f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapWheel(120f, paged).consumed shouldBe false
        now += 200
        mapper.mapWheel(120f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
    }

    @Test fun `continuous wheel moves the anchor and touchpad pinch preserves centroid`() {
        val mapper = ReaderInputMapper()
        val continuous = ReaderInputContext(
            mode = ReadingMode.VERTICAL,
            wheelBehavior = ReaderWheelBehavior.SCROLL,
            anchor = ReaderPosition(2, 40),
        )
        mapper.mapWheel(32f, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.SetViewportAnchor(ReaderPosition(2, 72)))
        mapper.mapSideButton(ReaderSideButton.BACK, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapSideButton(ReaderSideButton.FORWARD, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapPinch(1.25f, InputPoint(.2f, .7f)).action shouldBe
            ReaderInputCommand.ZoomBy(1.25f, InputPoint(.2f, .7f))
    }

    private var now = 0L
}
