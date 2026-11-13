package eu.kanade.tachiyomi.ui.reader.eink

import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FULL
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EinkBackendFactoryTest {
    @Test
    fun `onyx id creates the onyx backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.ONYX_NOVA3)
        backend.platform shouldBe "qualcomm"
        backend.supportedModes shouldBe setOf(FULL)
    }

    @Test
    fun `unknown id creates the no-op backend`() {
        EinkBackendFactory.create(EinkDeviceInfo.Id.NONE) shouldBe NoOpEinkRefreshBackend
    }

    @Test
    fun `xiaomi reader uses rk3566 backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.XIAOMI_READER)
        backend.platform shouldBe "rockchip"
        backend.supportedModes shouldBe setOf(FULL)
    }

    @Test
    fun `boyue t61 uses rk3026 backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.BOYUE_T61)
        backend.platform shouldBe "rockchip"
        backend.supportedModes shouldBe setOf(FULL)
    }
}
