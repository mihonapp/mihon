package mihon.desktop.reader.codec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.reader.image.ReaderImageFormat
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test

class CodecWorkerProtocolTest {
    @Test
    fun `parses frame metadata and converts centiseconds to bounded milliseconds`() {
        val metadata = CodecWorkerProtocol.parseMetadata(
            "2\t3\t0\tsrgba\n2\t3\t7\tsrgba\n2\t3\t9999\tsrgba\n",
            ReaderImageFormat.WEBP,
        )

        metadata.width shouldBe 2
        metadata.height shouldBe 3
        metadata.frameCount shouldBe 3
        metadata.frameDurationsMillis shouldBe listOf(20L, 70L, 10_000L)
        metadata.hasAlpha shouldBe true
        metadata.supportsRegionDecode shouldBe true
    }

    @Test
    fun `rejects inconsistent malformed or oversized metadata`() {
        shouldThrow<ReaderFailure.CorruptImage> {
            CodecWorkerProtocol.parseMetadata("2\t3\t1\tsrgb\n3\t3\t1\tsrgb\n", ReaderImageFormat.AVIF)
        }
        shouldThrow<ReaderFailure.CorruptImage> {
            CodecWorkerProtocol.parseMetadata("not metadata", ReaderImageFormat.AVIF)
        }
        shouldThrow<ReaderFailure.LimitExceeded> {
            CodecWorkerProtocol.parseMetadata("200001\t1\t1\tsrgb\n", ReaderImageFormat.AVIF)
        }
    }

    @Test
    fun `validates exact bgra payload length with checked arithmetic`() {
        CodecWorkerProtocol.validateBgraPayload(2, 3, 24) shouldBe 24
        shouldThrow<ReaderFailure.CorruptImage> {
            CodecWorkerProtocol.validateBgraPayload(2, 3, 23)
        }
        shouldThrow<ReaderFailure.LimitExceeded> {
            CodecWorkerProtocol.validateBgraPayload(100_000, 100_000, 0)
        }
    }
}
