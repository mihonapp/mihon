package mihon.desktop.reader.codec

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.reader.image.ImageMetadata
import mihon.reader.image.ReaderImageFormat
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path

class PackagedCodecPageDecoderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `probe parses packaged codec metadata and removes encoded input`() = runTest {
        val runner = FakeCodecRunner(
            CodecCommandResult(0, "2\t3\t7\tsrgba\n".encodeToByteArray(), byteArrayOf()),
        )
        val decoder = PackagedCodecPageDecoder(
            budget = BoundedReaderMemoryBudget(1024 * 1024),
            executable = temporaryDirectory.resolve("codec/magick.exe"),
            runner = runner,
            temporaryRoot = temporaryDirectory,
        )

        val metadata = decoder.probe(input(byteArrayOf(1, 2, 3), 3))

        metadata.format shouldBe ReaderImageFormat.UNKNOWN
        metadata.width shouldBe 2
        metadata.height shouldBe 3
        runner.commands.single().arguments shouldContain "-auto-orient"
        metadata.orientationApplied shouldBe true
        temporaryDirectory.toFile().walkTopDown().count { it.isFile } shouldBe 0
    }

    @Test
    fun `full decode returns premultiplied pixels under the shared budget`() = runTest {
        val pixels = byteArrayOf(
            0x20,
            0x40,
            0x80.toByte(),
            0x80.toByte(),
            0x00,
            0x00,
            0xff.toByte(),
            0xff.toByte(),
        )
        val runner = FakeCodecRunner(CodecCommandResult(0, pixels, byteArrayOf()))
        val budget = BoundedReaderMemoryBudget(1024 * 1024)
        val decoder = PackagedCodecPageDecoder(
            budget = budget,
            executable = temporaryDirectory.resolve("codec/magick.exe"),
            runner = runner,
            temporaryRoot = temporaryDirectory,
        )
        val pageId = PageId("chapter", "page.avif")
        val metadata = ImageMetadata(2, 1, format = ReaderImageFormat.AVIF)

        decoder.decodeFull(input(byteArrayOf(4, 5, 6), 3), metadata, FrameId(pageId, 0)).use { tile ->
            tile.image.type shouldBe java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE
            tile.image.getRGB(1, 0) shouldBe 0xffff0000.toInt()
            budget.metrics.reservedBytes shouldBe 8
        }
        budget.metrics.reservedBytes shouldBe 0
        runner.commands.single().maxStdoutBytes shouldBe 8
    }

    private fun input(bytes: ByteArray, count: Long) =
        BoundedPageInput(ByteArrayInputStream(bytes), count)

    private class FakeCodecRunner(private vararg val results: CodecCommandResult) : CodecCommandRunner {
        val commands = mutableListOf<CodecCommand>()
        private var index = 0

        override suspend fun run(command: CodecCommand): CodecCommandResult {
            commands += command
            return results[index++]
        }
    }
}
