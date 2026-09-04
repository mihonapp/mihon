package mihon.desktop.reader.input

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderClickAction
import org.junit.jupiter.api.Test

class ClickRegionPolicyTest {
    @Test fun `regions cover normalized edges without overlap or gaps`() {
        val policy = ClickRegionPolicy.fixedDefaults()
        policy.actionAt(0f) shouldBe ReaderClickAction.PREVIOUS
        policy.actionAt(.249f) shouldBe ReaderClickAction.PREVIOUS
        policy.actionAt(.25f) shouldBe ReaderClickAction.TOGGLE_CHROME
        policy.actionAt(.75f) shouldBe ReaderClickAction.NEXT
        policy.actionAt(1f) shouldBe ReaderClickAction.NEXT
    }

    @Test fun `invalid settings including gaps and overlap are rejected`() {
        shouldThrow<IllegalArgumentException> {
            ClickRegionPolicy(
                listOf(
                    ClickRegion(0f, .2f, ReaderClickAction.PREVIOUS),
                    ClickRegion(.3f, 1f, ReaderClickAction.NEXT),
                ),
            )
        }
        shouldThrow<IllegalArgumentException> {
            ClickRegionPolicy(
                listOf(
                    ClickRegion(0f, .6f, ReaderClickAction.PREVIOUS),
                    ClickRegion(.5f, 1f, ReaderClickAction.NEXT),
                ),
            )
        }
    }
}
