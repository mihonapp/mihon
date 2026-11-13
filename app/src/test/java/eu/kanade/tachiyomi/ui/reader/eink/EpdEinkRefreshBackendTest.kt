package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.AUTO
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FAST
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FULL
import eu.kanade.tachiyomi.ui.reader.eink.epd.EPDInterface
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpdEinkRefreshBackendTest {
    @Test
    fun `full-only backend exposes only full`() {
        EpdEinkRefreshBackend(FakeEpd("full-only")).supportedModes shouldBe setOf(FULL)
    }

    @Test
    fun `all-mode backend exposes full fast and auto`() {
        EpdEinkRefreshBackend(FakeEpd("all")).supportedModes shouldBe setOf(FULL, FAST, AUTO)
    }

    @Test
    fun `mode mapping uses full waveform and EPD_FULL`() {
        val backend = EpdEinkRefreshBackend(FakeEpd("all"))
        backend.waveformFor(FULL) shouldBe 100
        backend.epdModeFor(FULL) shouldBe "EPD_FULL"
    }

    @Test
    fun `mode mapping uses fast waveform and EPD_A2`() {
        val backend = EpdEinkRefreshBackend(FakeEpd("all"))
        backend.waveformFor(FAST) shouldBe 101
        backend.epdModeFor(FAST) shouldBe "EPD_A2"
    }

    @Test
    fun `mode mapping uses partial waveform and EPD_AUTO`() {
        val backend = EpdEinkRefreshBackend(FakeEpd("all"))
        backend.waveformFor(AUTO) shouldBe 102
        backend.epdModeFor(AUTO) shouldBe "EPD_AUTO"
    }
}

private class FakeEpd(private val mode: String) : EPDInterface {
    override fun getMode(): String = mode

    override fun getPlatform(): String = "fake"

    override fun getWaveformFull(): Int = 100

    override fun getWaveformPartial(): Int = 102

    override fun getWaveformFullUi(): Int = 0

    override fun getWaveformPartialUi(): Int = 0

    override fun getWaveformFast(): Int = 101

    override fun getWaveformDelay(): Int = 0

    override fun getWaveformDelayUi(): Int = 0

    override fun getWaveformDelayFast(): Int = 0

    override fun needsView(): Boolean = false

    override fun setEpdMode(
        targetView: View,
        mode: Int,
        delay: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        epdMode: String?,
    ) = Unit

    override fun resume() = Unit

    override fun pause() = Unit
}
