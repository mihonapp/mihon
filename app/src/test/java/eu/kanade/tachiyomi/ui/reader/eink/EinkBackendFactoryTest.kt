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
}
