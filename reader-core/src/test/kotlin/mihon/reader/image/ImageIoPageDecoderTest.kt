package mihon.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.source.ArchiveFixtures
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterExpansionBudget
import mihon.reader.source.ChapterSource
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits
import mihon.reader.source.ZipChapterSource
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream
import kotlin.concurrent.thread

class ImageIoPageDecoderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val budget = BoundedReaderMemoryBudget()
    private val decoder = ImageIoPageDecoder(budget)

    private class CloseCountingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        private val closed = AtomicBoolean(false)
        var closeCount = 0
            private set

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                closeCount += 1
                super.close()
            }
        }
    }

    private fun trackedInput(bytes: ByteArray): Pair<BoundedPageInput, CloseCountingInputStream> {
        val stream = CloseCountingInputStream(ByteArrayInputStream(bytes))
        return BoundedPageInput(stream, bytes.size.toLong()) to stream
    }

    @Test
    fun `probe reports jpeg dimensions without decoding the raster`() = runTest {
        val bytes = ImageFixtures.opaqueJpeg(40, 24)
        val metadata = decoder.probe(trackedInput(bytes).first)
        metadata shouldBe ImageMetadata(
            40,
            24,
            frameCount = 1,
            frameDurationsMillis = listOf(0),
            format = ReaderImageFormat.JPEG,
        )
    }

    @Test
    fun `probe reports png dimensions`() = runTest {
        val bytes = ImageFixtures.alphaPng(7, 5)
        val metadata = decoder.probe(trackedInput(bytes).first)
        metadata shouldBe ImageMetadata(
            7,
            5,
            frameCount = 1,
            frameDurationsMillis = listOf(0),
            format = ReaderImageFormat.PNG,
            hasAlpha = true,
        )
    }

    @Test
    fun `probe exposes gif frame count and clamped per frame durations`() = runTest {
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)
        metadata.width shouldBe 4
        metadata.height shouldBe 4
        metadata.frameCount shouldBe 4
        metadata.frameDurationsMillis shouldBe listOf(50L, 10_000L, 20L, 1_000L)
        metadata.isAnimated shouldBe true
        metadata.supportsRegionDecode shouldBe true
    }

    @Test
    fun `probe marks gif region decode unavailable when the canvas cannot reserve its peak`() = runTest {
        val tinyBudgetDecoder = ImageIoPageDecoder(BoundedReaderMemoryBudget(64))
        val metadata = tinyBudgetDecoder.probe(trackedInput(ImageFixtures.animatedGif()).first)
        metadata.supportsRegionDecode shouldBe false
    }

    @Test
    fun `probe rejects a forged oversized dimension header with a typed limit failure`() = runTest {
        val forged = temporaryDirectory.resolve("forged.png")
        ImageFixtures.writeForgedPngHeader(forged, width = 200_001, height = 10)
        val bytes = Files.readAllBytes(forged)
        shouldThrow<ReaderFailure.LimitExceeded> {
            decoder.probe(trackedInput(bytes).first)
        }
    }

    @Test
    fun `probe rejects corrupt image bytes with a typed failure`() = runTest {
        shouldThrow<ReaderFailure.CorruptImage> {
            decoder.probe(trackedInput(ImageFixtures.corruptPngBytes()).first)
        }
    }

    @Test
    fun `probe rejects bytes with no image reader`() = runTest {
        shouldThrow<ReaderFailure.UnsupportedImage> {
            decoder.probe(trackedInput(ImageFixtures.notAnImageBytes()).first)
        }
    }

    @Test
    fun `probe consumes and closes the supplied input exactly once`() = runTest {
        val (input, stream) = trackedInput(ImageFixtures.alphaPng(7, 5))
        decoder.probe(input)
        stream.closeCount shouldBe 1
    }

    @Test
    fun `full decode of an opaque jpeg returns a premultiplied argb tile`() = runTest {
        val pageId = PageId("1", "page.jpg")
        val bytes = ImageFixtures.opaqueJpeg(40, 24)
        val metadata = decoder.probe(trackedInput(bytes).first)

        val (input, stream) = trackedInput(bytes)
        val tile = decoder.decodeFull(input, metadata, FrameId(pageId, 0))

        tile.image.width shouldBe 40
        tile.image.height shouldBe 24
        tile.image.type shouldBe BufferedImage.TYPE_INT_ARGB_PRE
        ((tile.image.getRGB(0, 0) ushr 24) and 0xFF) shouldBe 0xFF
        ((tile.image.getRGB(39, 23) ushr 24) and 0xFF) shouldBe 0xFF
        tile.key shouldBe TileKey(pageId, null, IntRect(0, 0, 40, 24))
        tile.outputReservation.kind shouldBe MemoryKind.DECODED_OUTPUT
        tile.outputReservation.byteCount shouldBe 40L * 24L * 4L
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 40L * 24L * 4L, 0)
        stream.closeCount shouldBe 1
        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `full decode preserves png alpha`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.alphaPng(7, 5)
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))

        tile.image.type shouldBe BufferedImage.TYPE_INT_ARGB_PRE
        (tile.image.getRGB(0, 0) ushr 24) shouldBe 0
        (tile.image.getRGB(1, 0) ushr 24) shouldBe 0xFF
        tile.close()
    }

    @Test
    fun `full decode adopts into cache residency without a reservation gap`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.solidPng(32, 16)
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))
        val rasterBytes = 32L * 16L * 4L
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, rasterBytes, 0)

        tile.adoptAsCacheResident()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, rasterBytes)

        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `full decode succeeds when the conservative peak exactly equals the budget`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.solidPng(1024, 1024)
        val exactBudget = BoundedReaderMemoryBudget(2L * 1024L * 1024L * 4L)
        val exactDecoder = ImageIoPageDecoder(exactBudget)
        val metadata = exactDecoder.probe(trackedInput(bytes).first)

        val tile = exactDecoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))
        tile.image.width shouldBe 1024
        exactBudget.metrics shouldBe ReaderMemoryMetrics(2L * 1024L * 1024L * 4L, 1024L * 1024L * 4L, 0)
        tile.close()
        exactBudget.metrics shouldBe ReaderMemoryMetrics(2L * 1024L * 1024L * 4L, 0, 0)
    }

    @Test
    fun `full decode exceeding the budget by one byte is rejected and releases everything`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.solidPng(1024, 1024)
        val shortBudget = BoundedReaderMemoryBudget(2L * 1024L * 1024L * 4L - 1L)
        val shortDecoder = ImageIoPageDecoder(shortBudget)
        val metadata = shortDecoder.probe(trackedInput(bytes).first)
        val (input, stream) = trackedInput(bytes)

        shouldThrow<ReaderFailure.LimitExceeded> {
            shortDecoder.decodeFull(input, metadata, FrameId(pageId, 0))
        }
        shortBudget.metrics shouldBe ReaderMemoryMetrics(2L * 1024L * 1024L * 4L - 1L, 0, 0)
        stream.closeCount shouldBe 1
    }

    @Test
    fun `full decode beyond the sixteen mib output cap is rejected before allocation`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.solidPng(2048, 2049)
        val metadata = decoder.probe(trackedInput(bytes).first)
        metadata.width * metadata.height * 4L shouldBe 2048L * 2049L * 4L

        val (input, stream) = trackedInput(bytes)
        shouldThrow<ReaderFailure.LimitExceeded> {
            decoder.decodeFull(input, metadata, FrameId(pageId, 0))
        }
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        stream.closeCount shouldBe 1
    }

    @Test
    fun `full decode of corrupt bytes fails typed and releases every reservation`() = runTest {
        val pageId = PageId("1", "page.png")
        shouldThrow<ReaderFailure.CorruptImage> {
            decoder.probe(trackedInput(ImageFixtures.corruptPngBytes()).first)
        }
        shouldThrow<ReaderFailure.CorruptImage> {
            decoder.decodeFull(
                trackedInput(ImageFixtures.corruptPngBytes()).first,
                ImageMetadata(8, 8),
                FrameId(pageId, 0),
            )
        }
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `region decode returns the exact clipped source region`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.coordinatePng(64, 64)
        val metadata = decoder.probe(trackedInput(bytes).first)
        val key = TileKey(pageId, null, IntRect(8, 16, 40, 48))

        val tile = decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 32, 32))

        tile.image.width shouldBe 32
        tile.image.height shouldBe 32
        tile.image.type shouldBe BufferedImage.TYPE_INT_ARGB_PRE
        tile.image.getRGB(0, 0) shouldBe (0xFF shl 24 or (8 shl 16) or (16 shl 8)).toInt()
        tile.image.getRGB(31, 31) shouldBe (0xFF shl 24 or (39 shl 16) or (47 shl 8)).toInt()
        tile.outputReservation.byteCount shouldBe 32L * 32L * 4L
        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `region decode chooses power of two subsampling from the target size`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.coordinatePng(64, 64)
        val metadata = decoder.probe(trackedInput(bytes).first)
        val key = TileKey(pageId, null, IntRect(0, 0, 64, 64), sampleSize = 4)

        val tile = decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 16, 16))

        tile.image.width shouldBe 16
        tile.image.height shouldBe 16
        tile.image.getRGB(3, 5) shouldBe (0xFF shl 24 or (12 shl 16) or (20 shl 8)).toInt()
        tile.close()
    }

    @Test
    fun `region decode honors a planner produced edge tile clipped to the image`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.coordinatePng(100, 50)
        val metadata = decoder.probe(trackedInput(bytes).first)
        val plan = TilePlanner.plan(pageId, null, 100, 50, IntRect(64, 0, 100, 50))
        plan.size shouldBe 1

        val tile = decoder.decodeRegion(trackedInput(bytes).first, metadata, plan.single())

        tile.image.width shouldBe 100
        tile.image.height shouldBe 50
        tile.image.getRGB(99, 49) shouldBe (0xFF shl 24 or (99 shl 16) or (49 shl 8)).toInt()
        tile.close()
    }

    @Test
    fun `region decode rejects a request outside the image`() = runTest {
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.coordinatePng(64, 64)
        val metadata = decoder.probe(trackedInput(bytes).first)
        val key = TileKey(pageId, null, IntRect(64, 64, 128, 128))

        shouldThrow<ReaderFailure.RegionUnavailable> {
            decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 64, 64))
        }
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `region decode rejects a reader returning a raster larger than the request`() = runTest {
        val spi = OversizedReaderSpi()
        val registry = IIORegistry.getDefaultInstance()
        registry.registerServiceProvider(spi)
        try {
            val pageId = PageId("1", "page.ovr")
            val bytes = byteArrayOf(9, 9, 9, 9, 1, 2, 3, 4)
            val metadata = decoder.probe(trackedInput(bytes).first)
            metadata shouldBe ImageMetadata(
                16,
                16,
                frameCount = 1,
                frameDurationsMillis = listOf(0),
                hasAlpha = true,
            )
            val key = TileKey(pageId, null, IntRect(0, 0, 8, 8))

            shouldThrow<ReaderFailure.CorruptImage> {
                decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 8, 8))
            }
            budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        } finally {
            registry.deregisterServiceProvider(spi)
        }
    }

    @Test
    fun `gif frame zero composites onto the logical screen`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))

        tile.image.width shouldBe 4
        tile.image.height shouldBe 4
        tile.image.type shouldBe BufferedImage.TYPE_INT_ARGB_PRE
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                tile.image.getRGB(x, y) shouldBe 0xFFFF0000.toInt()
            }
        }
        tile.key shouldBe TileKey(pageId, FrameId(pageId, 0), IntRect(0, 0, 4, 4))
        tile.outputReservation.byteCount shouldBe 64L
        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `gif frame one composites transparency over the previous frame`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 1))

        tile.image.getRGB(1, 1) shouldBe 0xFFFF0000.toInt()
        tile.image.getRGB(2, 1) shouldBe 0xFF0000FF.toInt()
        tile.image.getRGB(1, 2) shouldBe 0xFF0000FF.toInt()
        tile.image.getRGB(2, 2) shouldBe 0xFF0000FF.toInt()
        tile.image.getRGB(0, 0) shouldBe 0xFFFF0000.toInt()
        tile.image.getRGB(3, 3) shouldBe 0xFFFF0000.toInt()
        tile.close()
    }

    @Test
    fun `gif frame two applies restore to background before compositing`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 2))

        tile.image.getRGB(0, 0) shouldBe 0xFF00FF00.toInt()
        tile.image.getRGB(1, 0) shouldBe 0xFF00FF00.toInt()
        tile.image.getRGB(0, 1) shouldBe 0xFF00FF00.toInt()
        tile.image.getRGB(1, 1) shouldBe 0xFF00FF00.toInt()
        tile.image.getRGB(2, 1) shouldBe 0
        tile.image.getRGB(1, 2) shouldBe 0
        tile.image.getRGB(2, 2) shouldBe 0
        tile.image.getRGB(3, 3) shouldBe 0xFFFF0000.toInt()
        tile.close()
    }

    @Test
    fun `gif frame three restores the snapshot from restore to previous`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)

        val tile = decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 3))

        tile.image.getRGB(0, 0) shouldBe 0xFFFF0000.toInt()
        tile.image.getRGB(1, 1) shouldBe 0
        tile.image.getRGB(2, 1) shouldBe 0
        tile.image.getRGB(1, 2) shouldBe 0
        tile.image.getRGB(2, 2) shouldBe 0
        tile.image.getRGB(3, 3) shouldBe 0xFFFFFF00.toInt()
        tile.close()
    }

    @Test
    fun `gif region decode crops the composited canvas`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)
        val key = TileKey(pageId, FrameId(pageId, 1), IntRect(1, 1, 3, 3))

        val tile = decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 2, 2))

        tile.image.width shouldBe 2
        tile.image.height shouldBe 2
        tile.image.getRGB(0, 0) shouldBe 0xFFFF0000.toInt()
        tile.image.getRGB(1, 0) shouldBe 0xFF0000FF.toInt()
        tile.image.getRGB(0, 1) shouldBe 0xFF0000FF.toInt()
        tile.image.getRGB(1, 1) shouldBe 0xFF0000FF.toInt()
        tile.outputReservation.byteCount shouldBe 16L
        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `gif region decode is rejected when the canvas peak exceeds the budget`() = runTest {
        val tinyBudget = BoundedReaderMemoryBudget(64)
        val tinyDecoder = ImageIoPageDecoder(tinyBudget)
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = tinyDecoder.probe(trackedInput(bytes).first)
        metadata.supportsRegionDecode shouldBe false
        val key = TileKey(pageId, FrameId(pageId, 1), IntRect(0, 0, 2, 2))
        val (input, stream) = trackedInput(bytes)

        shouldThrow<ReaderFailure.RegionUnavailable> {
            tinyDecoder.decodeRegion(input, metadata, TileRequest(key, 2, 2))
        }
        tinyBudget.metrics shouldBe ReaderMemoryMetrics(64, 0, 0)
        stream.closeCount shouldBe 1
    }

    @Test
    fun `probe marks gif region decode unavailable when the canvas exceeds the sixteen mib cap`() = runTest {
        val bytes = ImageFixtures.forgedGifCanvas(4096, 2048)

        val metadata = decoder.probe(trackedInput(bytes).first)

        metadata.width shouldBe 4096
        metadata.height shouldBe 2048
        metadata.width * metadata.height * 4L shouldBe 32L * 1024L * 1024L
        metadata.supportsRegionDecode shouldBe false
    }

    @Test
    fun `gif region decode rejects a canvas above the sixteen mib cap without compositing`() = runTest {
        val pageId = PageId("1", "big.gif")
        val bytes = ImageFixtures.forgedGifCanvas(4096, 2048)
        // Even a caller-supplied metadata claiming region support must not open the region path.
        val metadata = ImageMetadata(4096, 2048)
        val key = TileKey(pageId, null, IntRect(0, 0, 1024, 1024))
        val (input, stream) = trackedInput(bytes)

        shouldThrow<ReaderFailure.RegionUnavailable> {
            decoder.decodeRegion(input, metadata, TileRequest(key, 1024, 1024))
        }
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        stream.closeCount shouldBe 1
    }

    @Test
    fun `probe rejects a forged pixel count above four billion with a typed limit failure`() = runTest {
        val forged = temporaryDirectory.resolve("forged-pixels.png")
        ImageFixtures.writeForgedPngHeader(forged, width = 100_000, height = 100_000)
        val bytes = Files.readAllBytes(forged)

        val failure = shouldThrow<ReaderFailure.LimitExceeded> {
            decoder.probe(trackedInput(bytes).first)
        }
        failure.limitName shouldBe "image pixel count"
        failure.limitBytes shouldBe ReaderLimits.MAX_IMAGE_PIXELS
    }

    @Test
    fun `full decode wraps a runtime exception from a broken reader as a typed corrupt image`() = runTest {
        val spi = RuntimeThrowingReaderSpi { throw IndexOutOfBoundsException("simulated JDK reader bug") }
        val registry = IIORegistry.getDefaultInstance()
        registry.registerServiceProvider(spi)
        try {
            val pageId = PageId("1", "page.rte")
            val bytes = byteArrayOf(8, 8, 8, 8, 1, 2, 3, 4)
            val metadata = decoder.probe(trackedInput(bytes).first)
            metadata shouldBe ImageMetadata(
                8,
                8,
                frameCount = 1,
                frameDurationsMillis = listOf(0),
                hasAlpha = true,
            )

            shouldThrow<ReaderFailure.CorruptImage> {
                decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))
            }
            budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        } finally {
            registry.deregisterServiceProvider(spi)
        }
    }

    @Test
    fun `region decode wraps a runtime exception from a broken reader as a typed corrupt image`() = runTest {
        val spi = RuntimeThrowingReaderSpi { throw IllegalArgumentException("simulated JDK reader bug") }
        val registry = IIORegistry.getDefaultInstance()
        registry.registerServiceProvider(spi)
        try {
            val pageId = PageId("1", "page.rte")
            val bytes = byteArrayOf(8, 8, 8, 8, 1, 2, 3, 4)
            val metadata = decoder.probe(trackedInput(bytes).first)
            val key = TileKey(pageId, null, IntRect(0, 0, 8, 8))

            shouldThrow<ReaderFailure.CorruptImage> {
                decoder.decodeRegion(trackedInput(bytes).first, metadata, TileRequest(key, 8, 8))
            }
            budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        } finally {
            registry.deregisterServiceProvider(spi)
        }
    }

    @Test
    fun `a reader cancellation is never swallowed as a typed corrupt image`() = runTest {
        val spi = RuntimeThrowingReaderSpi { throw CancellationException("reader cancelled") }
        val registry = IIORegistry.getDefaultInstance()
        registry.registerServiceProvider(spi)
        try {
            val pageId = PageId("1", "page.rte")
            val bytes = byteArrayOf(8, 8, 8, 8, 1, 2, 3, 4)
            val metadata = decoder.probe(trackedInput(bytes).first)

            shouldThrow<CancellationException> {
                decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 0))
            }
            budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
        } finally {
            registry.deregisterServiceProvider(spi)
        }
    }

    @Test
    fun `gif frame index beyond the frame count is rejected`() = runTest {
        val pageId = PageId("1", "anim.gif")
        val bytes = ImageFixtures.animatedGif()
        val metadata = decoder.probe(trackedInput(bytes).first)

        shouldThrow<IllegalArgumentException> {
            decoder.decodeFull(trackedInput(bytes).first, metadata, FrameId(pageId, 4))
        }
        budget.metrics shouldBe ReaderMemoryMetrics(ReaderLimits.READER_MEMORY_BYTES, 0, 0)
    }

    @Test
    fun `probe and each decode receive a fresh input and archive accounting covers every open`() = runTest {
        val png = ImageFixtures.coordinatePng(64, 64)
        ArchiveFixtures.writeZip(temporaryDirectory.resolve("chapter.cbz"), listOf("page.png" to png))
        val expansionBudget = ChapterExpansionBudget()
        val counting = CountingChapterSource(
            ZipChapterSource(
                ArchiveFixtures.asset(temporaryDirectory, "chapter.cbz"),
                expansionBudget = expansionBudget,
            ),
        )
        val pageId = counting.pages().single().id

        val metadata = decoder.probe(counting.open(pageId))
        metadata shouldBe ImageMetadata(
            64,
            64,
            frameCount = 1,
            frameDurationsMillis = listOf(0),
            format = ReaderImageFormat.PNG,
            hasAlpha = true,
        )
        val full = decoder.decodeFull(counting.open(pageId), metadata, FrameId(pageId, 0))
        full.image.getRGB(63, 63) shouldBe (0xFF shl 24 or (63 shl 16) or (63 shl 8)).toInt()
        full.close()
        val regionKey = TileKey(pageId, null, IntRect(0, 0, 32, 32))
        val region = decoder.decodeRegion(counting.open(pageId), metadata, TileRequest(regionKey, 32, 32))
        region.image.getRGB(31, 31) shouldBe (0xFF shl 24 or (31 shl 16) or (31 shl 8)).toInt()
        region.close()

        counting.openCount shouldBe 3
        counting.closedInputs.get() shouldBe 3
        (expansionBudget.usedBytes > png.size.toLong()) shouldBe true
        (expansionBudget.usedBytes <= 3L * png.size.toLong()) shouldBe true
        counting.close()
    }

    @Test
    fun `gif frames are addressed by frame id and never become chapter pages`() = runTest {
        ArchiveFixtures.writeZip(
            temporaryDirectory.resolve("animated.cbz"),
            listOf("anim.gif" to ImageFixtures.animatedGif()),
        )
        ZipChapterSource(ArchiveFixtures.asset(temporaryDirectory, "animated.cbz")).use { source ->
            val pages = source.pages()
            pages.size shouldBe 1
            val metadata = decoder.probe(source.open(pages.single().id))
            metadata.frameCount shouldBe 4
            val tile = decoder.decodeFull(source.open(pages.single().id), metadata, FrameId(pages.single().id, 1))
            tile.key.frameId shouldBe FrameId(pages.single().id, 1)
            tile.close()
            source.pages().size shouldBe 1
        }
    }

    @Test
    fun `cancellation while waiting for memory closes the active input and releases nothing twice`() = runTest {
        val limit = 2L * 64 * 64 * 4
        val waitingForMemory = CompletableDeferred<Unit>()
        val smallBudget = BoundedReaderMemoryBudget(limit) { waitingForMemory.complete(Unit) }
        val smallDecoder = ImageIoPageDecoder(smallBudget)
        val pageId = PageId("1", "page.png")
        val bytes = ImageFixtures.coordinatePng(64, 64)
        val metadata = smallDecoder.probe(trackedInput(bytes).first)
        val blocker = requireNotNull(smallBudget.tryReserve(MemoryKind.DECODED_OUTPUT, limit))
        val (input, stream) = trackedInput(bytes)

        val job = async { smallDecoder.decodeFull(input, metadata, FrameId(pageId, 0)) }
        waitingForMemory.await()
        job.cancelAndJoin()

        job.isCancelled shouldBe true
        stream.closeCount shouldBe 1
        smallBudget.metrics shouldBe ReaderMemoryMetrics(limit, limit, 0)
        blocker.close()
        smallBudget.metrics shouldBe ReaderMemoryMetrics(limit, 0, 0)
    }

    @Tag("extreme-image")
    @Test
    fun `extreme image tests run on a heap constrained to 384 mib`() {
        Runtime.getRuntime().maxMemory() shouldBe 384L * 1024L * 1024L
    }

    @Tag("extreme-image")
    @Test
    fun `streamed 20000 by 20000 png probes and region decodes within the shared budget`(): Unit = runBlocking {
        val path = temporaryDirectory.resolve("extreme.png")
        ImageFixtures.writeStreamedGrayscalePng(path, width = 20_000, height = 20_000)
        val limit = ReaderLimits.READER_MEMORY_BYTES
        val extremeBudget = BoundedReaderMemoryBudget(limit)
        val extremeDecoder = ImageIoPageDecoder(extremeBudget)
        val pageId = PageId("1", "extreme.png")
        val highWaterBytes = AtomicLong(0)
        val polling = AtomicBoolean(true)
        val poller = thread(start = true) {
            while (polling.get()) {
                val metrics = extremeBudget.metrics
                highWaterBytes.accumulateAndGet(metrics.reservedBytes + metrics.cacheBytes) { a, b -> maxOf(a, b) }
                Thread.sleep(2)
            }
        }
        try {
            val metadata = extremeDecoder.probe(fileInput(path))
            metadata.width shouldBe 20_000
            metadata.height shouldBe 20_000
            metadata.supportsRegionDecode shouldBe true

            shouldThrow<ReaderFailure.LimitExceeded> {
                extremeDecoder.decodeFull(fileInput(path), metadata, FrameId(pageId, 0))
            }

            val topLeft = extremeDecoder.decodeRegion(
                fileInput(path),
                metadata,
                TileRequest(TileKey(pageId, null, IntRect(0, 0, 1024, 1024)), 1024, 1024),
            )
            topLeft.image.width shouldBe 1024
            topLeft.image.height shouldBe 1024
            (topLeft.image.getRGB(0, 0) and 0xFF) shouldBe 0
            (topLeft.image.getRGB(0, 1023) and 0xFF) shouldBe 1023 % 251
            topLeft.close()

            val bottomRight = extremeDecoder.decodeRegion(
                fileInput(path),
                metadata,
                TileRequest(TileKey(pageId, null, IntRect(18_976, 18_976, 20_000, 20_000)), 1024, 1024),
            )
            bottomRight.image.width shouldBe 1024
            bottomRight.image.height shouldBe 1024
            (bottomRight.image.getRGB(0, 0) and 0xFF) shouldBe 18_976 % 251
            (bottomRight.image.getRGB(1023, 1023) and 0xFF) shouldBe 19_999 % 251
            bottomRight.close()

            extremeBudget.metrics shouldBe ReaderMemoryMetrics(limit, 0, 0)
        } finally {
            polling.set(false)
            poller.join()
        }
        val highWater = highWaterBytes.get()
        (highWater <= limit) shouldBe true
        (highWater >= 1024L * 1024L * 4L) shouldBe true
    }

    private fun fileInput(path: Path): BoundedPageInput =
        BoundedPageInput(Files.newInputStream(path), Files.size(path))
}

private class CountingChapterSource(
    private val delegate: ChapterSource,
) : ChapterSource {
    var openCount = 0
        private set
    val closedInputs = AtomicInteger()

    override val asset: ReaderChapterAsset get() = delegate.asset

    override suspend fun pages(): List<PageDescriptor> = delegate.pages()

    override suspend fun open(pageId: PageId): BoundedPageInput {
        openCount += 1
        val input = delegate.open(pageId)
        val stream = object : FilterInputStream(input.input) {
            private val closed = AtomicBoolean(false)

            override fun close() {
                if (closed.compareAndSet(false, true)) {
                    closedInputs.incrementAndGet()
                    super.close()
                }
            }
        }
        return BoundedPageInput(stream, input.byteCount)
    }

    override fun close() = delegate.close()
}

private class RuntimeThrowingReaderSpi(
    private val failure: () -> Nothing,
) : ImageReaderSpi(
    "mihon-test",
    "1.0",
    arrayOf("runtime-throwing-test"),
    arrayOf("rte"),
    null,
    RuntimeThrowingReader::class.java.name,
    arrayOf(ImageInputStream::class.java),
    null,
    false,
    null,
    null,
    null,
    null,
    false,
    null,
    null,
    null,
    null,
) {
    override fun canDecodeInput(source: Any): Boolean {
        if (source !is ImageInputStream) return false
        val header = ByteArray(4)
        source.mark()
        val count = source.read(header)
        source.reset()
        return count == 4 && header.contentEquals(byteArrayOf(8, 8, 8, 8))
    }

    override fun createReaderInstance(extension: Any?): ImageReader = RuntimeThrowingReader(this, failure)

    override fun getDescription(locale: Locale?): String = "test reader throwing runtime exceptions"
}

private class RuntimeThrowingReader(
    owner: ImageReaderSpi,
    private val failure: () -> Nothing,
) : ImageReader(owner) {
    override fun getNumImages(allowSearch: Boolean): Int = 1

    override fun getWidth(imageIndex: Int): Int = 8

    override fun getHeight(imageIndex: Int): Int = 8

    override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> =
        listOf(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()

    override fun getStreamMetadata(): IIOMetadata? = null

    override fun getImageMetadata(imageIndex: Int): IIOMetadata? = null

    override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage = failure()
}

private class OversizedReaderSpi : ImageReaderSpi(
    "mihon-test",
    "1.0",
    arrayOf("oversized-test"),
    arrayOf("ovr"),
    null,
    OversizedReader::class.java.name,
    arrayOf(ImageInputStream::class.java),
    null,
    false,
    null,
    null,
    null,
    null,
    false,
    null,
    null,
    null,
    null,
) {
    override fun canDecodeInput(source: Any): Boolean {
        if (source !is ImageInputStream) return false
        val header = ByteArray(4)
        source.mark()
        val count = source.read(header)
        source.reset()
        return count == 4 && header.contentEquals(byteArrayOf(9, 9, 9, 9))
    }

    override fun createReaderInstance(extension: Any?): ImageReader = OversizedReader(this)

    override fun getDescription(locale: Locale?): String = "test reader returning oversized rasters"
}

private class OversizedReader(owner: ImageReaderSpi) : ImageReader(owner) {
    override fun getNumImages(allowSearch: Boolean): Int = 1

    override fun getWidth(imageIndex: Int): Int = 16

    override fun getHeight(imageIndex: Int): Int = 16

    override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> =
        listOf(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()

    override fun getStreamMetadata(): IIOMetadata? = null

    override fun getImageMetadata(imageIndex: Int): IIOMetadata? = null

    override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage =
        BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
}
