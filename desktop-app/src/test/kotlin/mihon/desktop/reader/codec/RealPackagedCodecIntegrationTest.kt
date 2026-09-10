package mihon.desktop.reader.codec

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.reader.image.ApngPageDecoder
import mihon.reader.image.CompositePageDecoder
import mihon.reader.image.ImageIoPageDecoder
import mihon.reader.image.IntRect
import mihon.reader.image.ReaderImageFormat
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RealPackagedCodecIntegrationTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `packaged codec probes and decodes modern still formats by magic`() = runTest {
        val executable = PackagedReaderCodec.executablePath()
        assumeTrue(Files.isRegularFile(executable), "run downloadReaderCodec before the packaged codec matrix")
        val budget = BoundedReaderMemoryBudget(4L * 1024 * 1024)
        val decoder = CompositePageDecoder(
            ImageIoPageDecoder(budget),
            PackagedCodecPageDecoder(budget, executable, temporaryRoot = temporaryDirectory.resolve("requests")),
            ApngPageDecoder(budget),
        )
        val formats = listOf(
            Triple("avif", ReaderImageFormat.AVIF, "page.dat"),
            Triple("jxl", ReaderImageFormat.JXL, "page.jpg"),
            Triple("webp", ReaderImageFormat.WEBP, "page.png"),
        )

        formats.forEach { (extension, expectedFormat, disguisedName) ->
            val encoded = temporaryDirectory.resolve("fixture.$extension")
            generate(executable, encoded)
            val bytes = Files.readAllBytes(encoded)
            val pageId = PageId("chapter", disguisedName)
            val metadata = decoder.probe(input(bytes))
            metadata.format shouldBe expectedFormat
            metadata.width shouldBe 3
            metadata.height shouldBe 2
            decoder.decodeFull(input(bytes), metadata, FrameId(pageId, 0)).use { tile ->
                (tile.image.getRGB(0, 0) ushr 16 and 0xff) shouldBeGreaterThan 150
            }
        }
        budget.close()
    }

    @Test
    fun `packaged codec exposes apng and animated webp frames`() = runTest {
        val executable = PackagedReaderCodec.executablePath()
        assumeTrue(Files.isRegularFile(executable), "run downloadReaderCodec before the packaged codec matrix")
        val budget = BoundedReaderMemoryBudget(4L * 1024 * 1024)
        val decoder = CompositePageDecoder(
            ImageIoPageDecoder(budget),
            PackagedCodecPageDecoder(budget, executable, temporaryRoot = temporaryDirectory.resolve("requests")),
            ApngPageDecoder(budget),
        )
        val fixtures = listOf(
            generateAnimation(executable, temporaryDirectory.resolve("animated.png"), "APNG") to listOf(50L, 50L),
            generateAnimation(executable, temporaryDirectory.resolve("animated.webp"), null) to listOf(50L, 70L),
        )

        fixtures.forEach { (encoded, expectedDurations) ->
            val bytes = Files.readAllBytes(encoded)
            val pageId = PageId("chapter", encoded.fileName.toString())
            val metadata = decoder.probe(input(bytes))
            metadata.frameCount shouldBe 2
            metadata.frameDurationsMillis shouldBe expectedDurations
            decoder.decodeFull(input(bytes), metadata, FrameId(pageId, 1)).use { tile ->
                (tile.image.getRGB(0, 0) and 0xff) shouldBeGreaterThan 150
            }
        }
        budget.close()
    }

    @Test
    fun `packaged codec reads real heic fixture by signature`() = runTest {
        val executable = PackagedReaderCodec.executablePath()
        val fixture = Path.of(requireNotNull(System.getProperty("mihon.reader.heicFixture")))
        assumeTrue(Files.isRegularFile(executable), "packaged codec is required")
        assumeTrue(Files.isRegularFile(fixture), "pinned libheif fixture is required")
        val budget = BoundedReaderMemoryBudget(8L * 1024 * 1024)
        val decoder = CompositePageDecoder(
            ImageIoPageDecoder(budget),
            PackagedCodecPageDecoder(budget, executable, temporaryRoot = temporaryDirectory.resolve("heic")),
            ApngPageDecoder(budget),
        )
        val bytes = Files.readAllBytes(fixture)
        val pageId = PageId("chapter", "cover.jpg")
        val metadata = decoder.probe(input(bytes))
        metadata.format shouldBe ReaderImageFormat.HEIF
        decoder.decodeRegion(
            input(bytes),
            metadata,
            TileRequest(
                TileKey(pageId, null, IntRect(0, 0, minOf(128, metadata.width), minOf(128, metadata.height)), 2),
                64,
                64,
            ),
        ).use { tile ->
            tile.image.width shouldBe 64
            tile.image.height shouldBe 64
        }
        budget.close()
    }

    @Test
    fun `packaged codec normalizes exif orientation and cmyk jpeg`() = runTest {
        val executable = PackagedReaderCodec.executablePath()
        assumeTrue(Files.isRegularFile(executable), "packaged codec is required")
        val budget = BoundedReaderMemoryBudget(4L * 1024 * 1024)
        val decoder = CompositePageDecoder(
            ImageIoPageDecoder(budget),
            PackagedCodecPageDecoder(budget, executable, temporaryRoot = temporaryDirectory.resolve("jpeg")),
            ApngPageDecoder(budget),
        )

        val oriented = temporaryDirectory.resolve("oriented.jpg")
        generate(executable, oriented, "2x3")
        val orientedBytes = addExifOrientation(Files.readAllBytes(oriented), 6)
        val orientedMetadata = decoder.probe(input(orientedBytes))
        orientedMetadata.width shouldBe 3
        orientedMetadata.height shouldBe 2
        orientedMetadata.orientationApplied shouldBe true
        decoder.decodeFull(
            input(orientedBytes),
            orientedMetadata,
            FrameId(PageId("chapter", "oriented.jpg"), 0),
        ).use { tile ->
            tile.image.width shouldBe 3
            tile.image.height shouldBe 2
        }

        val cmyk = temporaryDirectory.resolve("cmyk.jpg")
        val process = ProcessBuilder(
            executable.toString(),
            "-size",
            "3x2",
            "xc:#cc3311",
            "-colorspace",
            "CMYK",
            cmyk.toString(),
        ).directory(requireNotNull(executable.parent).toFile()).inheritIO().start()
        check(process.waitFor() == 0) { "failed to generate CMYK JPEG" }
        val cmykBytes = Files.readAllBytes(cmyk)
        val cmykMetadata = decoder.probe(input(cmykBytes))
        decoder.decodeFull(
            input(cmykBytes),
            cmykMetadata,
            FrameId(PageId("chapter", "cmyk.jpg"), 0),
        ).use { tile ->
            (tile.image.getRGB(0, 0) ushr 16 and 0xff) shouldBeGreaterThan 150
        }
        budget.close()
    }

    private fun generate(executable: Path, output: Path, size: String = "3x2") {
        val process = ProcessBuilder(
            executable.toString(),
            "-size",
            size,
            "xc:#cc3311",
            output.toString(),
        ).directory(requireNotNull(executable.parent).toFile()).inheritIO().start()
        check(process.waitFor() == 0) { "failed to generate ${output.fileName}" }
    }

    private fun generateAnimation(executable: Path, output: Path, coder: String?): Path {
        val target = coder?.let { "$it:$output" } ?: output.toString()
        val process = ProcessBuilder(
            executable.toString(),
            "-delay",
            "5",
            "-size",
            "3x2",
            "xc:red",
            "-delay",
            "7",
            "-size",
            "3x2",
            "xc:blue",
            "-loop",
            "0",
            target,
        ).directory(requireNotNull(executable.parent).toFile()).inheritIO().start()
        check(process.waitFor() == 0) { "failed to generate ${output.fileName}" }
        return output
    }

    private fun input(bytes: ByteArray) = BoundedPageInput(bytes.inputStream(), bytes.size.toLong())

    private fun addExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        check(jpeg.size >= 2 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
        val payload = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0,
            0x4d, 0x4d, 0, 0x2a, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1,
            0, orientation.toByte(), 0, 0,
            0, 0, 0, 0,
        )
        val length = payload.size + 2
        return jpeg.copyOfRange(0, 2) +
            byteArrayOf(0xff.toByte(), 0xe1.toByte(), (length ushr 8).toByte(), length.toByte()) +
            payload + jpeg.copyOfRange(2, jpeg.size)
    }
}
