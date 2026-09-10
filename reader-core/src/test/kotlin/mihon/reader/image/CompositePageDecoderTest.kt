package mihon.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.reader.model.FrameId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class CompositePageDecoderTest {
    @Test
    fun `routes simple formats in process and color managed formats to packaged codec`() = runTest {
        val imageIo = RecordingDecoder()
        val packaged = RecordingDecoder()
        val composite = CompositePageDecoder(imageIo, packaged)

        val jpeg = composite.probe(input(bytes(0xff, 0xd8, 0xff, 0xe0)))
        jpeg.format shouldBe ReaderImageFormat.JPEG
        imageIo.probes shouldBe 0
        packaged.probes shouldBe 1

        val webp = composite.probe(input(("RIFF" + "0000" + "WEBP").encodeToByteArray()))
        webp.format shouldBe ReaderImageFormat.WEBP
        imageIo.probes shouldBe 0
        packaged.probes shouldBe 2

        val avif = composite.probe(input(bmff("avif")))
        avif.format shouldBe ReaderImageFormat.AVIF
        packaged.probes shouldBe 3

        val apng = ByteArray(56).also {
            bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(it)
            "acTL".encodeToByteArray().copyInto(it, 40)
        }
        composite.probe(input(apng)).format shouldBe ReaderImageFormat.PNG
        packaged.probes shouldBe 4
    }

    @Test
    fun `unknown data fails before either decoder is called`() = runTest {
        val imageIo = RecordingDecoder()
        val packaged = RecordingDecoder()
        val composite = CompositePageDecoder(imageIo, packaged)

        shouldThrow<ReaderFailure.UnsupportedImage> {
            composite.probe(input("not an image".encodeToByteArray()))
        }
        imageIo.probes shouldBe 0
        packaged.probes shouldBe 0
    }

    private fun input(bytes: ByteArray) = BoundedPageInput(ByteArrayInputStream(bytes), bytes.size.toLong())

    private fun bmff(brand: String): ByteArray = ByteArray(16).also {
        it[3] = 16
        "ftyp".encodeToByteArray().copyInto(it, 4)
        brand.encodeToByteArray().copyInto(it, 8)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private class RecordingDecoder : PageDecoder {
        var probes = 0

        override suspend fun probe(input: BoundedPageInput): ImageMetadata {
            probes++
            input.use { it.input.readBytes() }
            return ImageMetadata(1, 1)
        }

        override suspend fun decodeFull(
            input: BoundedPageInput,
            metadata: ImageMetadata,
            frameId: FrameId,
        ): DecodedTile = error("not used")

        override suspend fun decodeRegion(
            input: BoundedPageInput,
            metadata: ImageMetadata,
            request: TileRequest,
        ): DecodedTile = error("not used")
    }
}
