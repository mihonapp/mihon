package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderImageDecoderTest {

    @Test
    fun `webtoon images that cannot use a hardware bitmap use the subsampling decoder`() {
        assertTrue(
            shouldUseSubsamplingDecoder(
                isWebtoon = true,
                alwaysDecodeLongStripWithSSIV = false,
                canUseHardwareBitmap = false,
            ),
        )
    }

    @Test
    fun `compatible webtoon images keep the default decoder`() {
        assertFalse(
            shouldUseSubsamplingDecoder(
                isWebtoon = true,
                alwaysDecodeLongStripWithSSIV = false,
                canUseHardwareBitmap = true,
            ),
        )
    }

    @Test
    fun `the legacy preference always selects the subsampling decoder`() {
        assertTrue(
            shouldUseSubsamplingDecoder(
                isWebtoon = true,
                alwaysDecodeLongStripWithSSIV = true,
                canUseHardwareBitmap = true,
            ),
        )
    }
}
