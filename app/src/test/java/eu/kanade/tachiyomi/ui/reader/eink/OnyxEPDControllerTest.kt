package eu.kanade.tachiyomi.ui.reader.eink

import eu.kanade.tachiyomi.ui.reader.eink.epd.OnyxEPDController
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OnyxEPDControllerTest {
    @Test
    fun `onyx full mode is full update plus wait plus gc16`() {
        with(OnyxEPDController()) {
            getPlatform() shouldBe "qualcomm"
            getMode() shouldBe "full-only"
            getWaveformFull() shouldBe 98 // 32 + 64 + 2
            getWaveformPartial() shouldBe 2
            getWaveformFast() shouldBe 1
        }
    }
}
