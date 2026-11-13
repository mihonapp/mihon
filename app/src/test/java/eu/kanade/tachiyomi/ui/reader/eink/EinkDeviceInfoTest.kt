package eu.kanade.tachiyomi.ui.reader.eink

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EinkDeviceInfoTest {
    @Test
    fun `detects onyx nova 3`() {
        EinkDeviceInfo.detect(
            EinkBuildInfo("onyx", "", "", "nova3", "nova3", ""),
        ) shouldBe EinkDeviceInfo.Id.ONYX_NOVA3
    }

    @Test
    fun `detects xiaomi reader`() {
        EinkDeviceInfo.detect(
            EinkBuildInfo("xiaomi", "xiaomi", "xiaomi_reader", "rk3566_eink", "", "rk30board"),
        ) shouldBe EinkDeviceInfo.Id.XIAOMI_READER
    }

    @Test
    fun `detects moaan w7`() {
        EinkDeviceInfo.detect(
            EinkBuildInfo("", "allwinner", "epd103", "virgo-perf1", "", "sun8iw15p1"),
        ) shouldBe EinkDeviceInfo.Id.MOAAN_W7
    }

    @Test
    fun `unknown build maps to none`() {
        EinkDeviceInfo.detect(EinkBuildInfo("", "", "", "", "", "")) shouldBe EinkDeviceInfo.Id.NONE
    }
}
