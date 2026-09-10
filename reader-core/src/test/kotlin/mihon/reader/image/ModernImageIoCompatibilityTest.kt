package mihon.reader.image

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class ModernImageIoCompatibilityTest {
    @Test
    fun `still webp probes and decodes through the reader contract`() = runTest {
        val budget = BoundedReaderMemoryBudget(1024 * 1024)
        val decoder = ImageIoPageDecoder(budget)
        val pageId = PageId("chapter", "renamed.bin")

        val metadata = decoder.probe(input(WEBP_RED_2X2))
        metadata.width shouldBe 2
        metadata.height shouldBe 2
        metadata.format shouldBe ReaderImageFormat.WEBP
        metadata.hasAlpha shouldBe false
        metadata.orientationApplied shouldBe false

        decoder.decodeFull(input(WEBP_RED_2X2), metadata, FrameId(pageId, 0)).use { tile ->
            val pixel = tile.image.getRGB(0, 0)
            (pixel ushr 16 and 0xff) shouldBeGreaterThan 200
            (pixel ushr 24 and 0xff) shouldBe 255
        }
        budget.close()
    }

    private fun input(bytes: ByteArray) = BoundedPageInput(ByteArrayInputStream(bytes), bytes.size.toLong())

    private companion object {
        val WEBP_RED_2X2: ByteArray = Base64.getDecoder().decode(
            "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA=",
        )
    }
}
