package eu.kanade.tachiyomi.ui.reader.eink

import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.AUTO
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FAST
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
    fun `generic onyx id creates the onyx backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.ONYX_GENERIC)
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

    @Test
    fun `nook glowlight uses freescale full backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.NOOK)
        backend.platform shouldBe "freescale"
        backend.supportedModes shouldBe setOf(FULL)
    }

    @Test
    fun `nook emperor exposes all three modes`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.NOOK_GL4PLUS)
        backend.platform shouldBe "freescale"
        backend.supportedModes shouldBe setOf(FULL, FAST, AUTO)
    }

    @Test
    fun `tolino vision 5 exposes all three modes`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.TOLINO_VISION5)
        backend.platform shouldBe "freescale"
        backend.supportedModes shouldBe setOf(FULL, FAST, AUTO)
    }

    @Test
    fun `crema 0660l exposes all three modes`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.CREMA_0660L)
        backend.platform shouldBe "freescale"
        backend.supportedModes shouldBe setOf(FULL, FAST, AUTO)
    }

    @Test
    fun `moaan w7 uses sunxi backend with all modes`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.MOAAN_W7)
        backend.platform shouldBe "sunxi"
        backend.supportedModes shouldBe setOf(FULL, FAST, AUTO)
    }

    @Test
    fun `iflytek ainote uses full-only backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.IFLYTEK_AINOTE_X1)
        backend.platform shouldBe "iflytek"
        backend.supportedModes shouldBe setOf(FULL)
    }

    @Test
    fun `lenovo smart paper uses full-only rockchip backend`() {
        val backend = EinkBackendFactory.create(EinkDeviceInfo.Id.LENOVO_SMARTPAPER)
        backend.platform shouldBe "rockchip"
        backend.supportedModes shouldBe setOf(FULL)
    }
}
