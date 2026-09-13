package mihon.reader.prefetch

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mihon.reader.cache.CacheMetrics
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.BudgetedDecodedTile
import mihon.reader.image.DecodedTile
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterSource
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class PageLoadCoordinatorTest {
    private class FlushCountingImage(
        width: Int,
        height: Int,
    ) : BufferedImage(width, height, TYPE_INT_ARGB_PRE) {
        var flushCount = 0
            private set

        override fun flush() {
            flushCount += 1
            super.flush()
        }
    }

    private class FakeChapterSource(
        chapterId: Long,
        pageCount: Int,
        sizeBytes: Long = 100,
        modifiedAt: Long = 1,
    ) : ChapterSource {
        override val asset = ReaderChapterAsset(
            mangaId = 1,
            chapterId = chapterId,
            mangaTitle = "manga",
            chapterName = "chapter",
            storageRoot = Path.of("."),
            relativePath = Path.of("chapter-$chapterId"),
            assetKind = "directory",
            sizeBytes = sizeBytes,
            modifiedAt = modifiedAt,
            lastPageRead = 0,
            read = false,
        )
        val descriptors = (1..pageCount).map { PageDescriptor(PageId(chapterId.toString(), "p$it.png"), 4, 4) }
        private val lock = Any()
        private val failures = HashMap<String, Throwable>()
        private val openCounts = HashMap<String, Int>()
        var closed = false
            private set

        fun pageId(index: Int) = descriptors[index].id

        fun failWith(entry: String, failure: Throwable?) = synchronized(lock) {
            if (failure == null) failures.remove(entry) else failures[entry] = failure
        }

        fun openCount(entry: String) = synchronized(lock) { openCounts[entry] ?: 0 }

        override suspend fun pages(): List<PageDescriptor> = descriptors

        override suspend fun open(pageId: PageId): BoundedPageInput {
            synchronized(lock) {
                openCounts.merge(pageId.entryName, 1, Int::plus)
                failures[pageId.entryName]?.let { throw it }
            }
            val bytes = pageId.entryName.toByteArray(Charsets.UTF_8)
            return BoundedPageInput(ByteArrayInputStream(bytes), bytes.size.toLong())
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeDecoder(
        private val budget: BoundedReaderMemoryBudget,
        private val weightBytes: Long = 256,
    ) : PageDecoder {
        private val lock = Any()
        var frameCount = 1
        var imageWidth = 4
        var imageHeight = 4
        var probeGate: CompletableDeferred<Unit>? = null
        var decodeGate: CompletableDeferred<Unit>? = null
        var postDecodeGate: CompletableDeferred<Unit>? = null
        var decodeFailure: Throwable? = null
        var probeCount = 0
            private set
        var decodeAttempts = 0
            private set
        var peakLedgerBytes = 0L
            private set
        private val produced = mutableListOf<String>()
        private val producedImages = mutableListOf<FlushCountingImage>()
        private val regions = mutableListOf<TileRequest>()

        fun producedNames(): List<String> = synchronized(lock) { produced.toList() }

        fun producedImage(index: Int) = synchronized(lock) { producedImages[index] }

        fun regionRequests(): List<TileRequest> = synchronized(lock) { regions.toList() }

        override suspend fun probe(input: BoundedPageInput): ImageMetadata {
            input.close()
            // Non-cancellable wait: job cancellation must surface at the post-probe checkpoint.
            probeGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            val frames = synchronized(lock) {
                probeCount++
                frameCount
            }
            return ImageMetadata(imageWidth, imageHeight, frames, List(frames) { 0L }, supportsRegionDecode = true)
        }

        override suspend fun decodeFull(
            input: BoundedPageInput,
            metadata: ImageMetadata,
            frameId: FrameId,
        ): DecodedTile {
            val name = input.input.readBytes().toString(Charsets.UTF_8)
            input.close()
            synchronized(lock) { decodeAttempts++ }
            // Cancellable wait: job cancellation during decode aborts before any reservation.
            decodeGate?.await()
            synchronized(lock) { decodeFailure }?.let { throw it }
            val lease = budget.reserve(MemoryKind.DECODED_OUTPUT, weightBytes)
            val key = TileKey(
                pageId = frameId.pageId,
                frameId = frameId.takeIf { metadata.isAnimated },
                bounds = IntRect(0, 0, metadata.width, metadata.height),
            )
            val image = FlushCountingImage(metadata.width, metadata.height)
            synchronized(lock) {
                val metrics = budget.metrics
                peakLedgerBytes = maxOf(peakLedgerBytes, metrics.reservedBytes + metrics.cacheBytes)
                produced += name
                producedImages += image
            }
            // Non-cancellable wait: cancellation must surface at the post-decode checkpoint.
            postDecodeGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return BudgetedDecodedTile(key, image, lease)
        }

        override suspend fun decodeRegion(
            input: BoundedPageInput,
            metadata: ImageMetadata,
            request: TileRequest,
        ): DecodedTile {
            input.close()
            synchronized(lock) { regions += request }
            val lease = budget.reserve(MemoryKind.DECODED_OUTPUT, weightBytes)
            return BudgetedDecodedTile(
                request.key,
                FlushCountingImage(request.targetWidth, request.targetHeight),
                lease,
            )
        }
    }

    private class Harness(
        val capacity: Long,
        tileWeight: Long = 256,
    ) {
        lateinit var cache: WeightedTileCache
        val budget = BoundedReaderMemoryBudget(capacity) { cache.relievePressure() }
        val decoder = FakeDecoder(budget, tileWeight)

        fun coordinator(scope: kotlinx.coroutines.CoroutineScope): PageLoadCoordinator {
            cache = WeightedTileCache(capacityBytes = capacity)
            return PageLoadCoordinator(decoder, cache, scope)
        }
    }

    @Test
    fun `visible page is probed decoded pinned and cached`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)

        val page = source.pageId(0)
        val loaded = coordinator.loadVisible(page)

        loaded.resident shouldBe true
        val expectedKey = TileKey(page, null, IntRect(0, 0, 4, 4))
        loaded.tile.key shouldBe expectedKey
        harness.decoder.probeCount shouldBe 1
        harness.decoder.producedNames() shouldBe listOf("p1.png")
        harness.cache.get(expectedKey) shouldBe loaded.tile
        harness.cache.metrics.pinnedBytes shouldBe 256

        val second = coordinator.loadVisible(page)
        second.tile shouldBe loaded.tile
        harness.decoder.probeCount shouldBe 1
        harness.decoder.producedNames() shouldBe listOf("p1.png")
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 256)

        coordinator.close()
        harness.cache.metrics.pinnedBytes shouldBe 0
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 256)
    }

    @Test
    fun `full page loading downsamples static images to the configured pixel ceiling`() = runTest {
        val harness = Harness(capacity = 4096)
        harness.decoder.imageWidth = 8
        harness.decoder.imageHeight = 8
        harness.cache = WeightedTileCache(capacityBytes = 4096)
        val coordinator = PageLoadCoordinator(
            harness.decoder,
            harness.cache,
            backgroundScope,
            maxFullPagePixels = 16,
        )
        val source = FakeChapterSource(1, 1)
        coordinator.openChapter(source)

        val loaded = coordinator.loadVisible(source.pageId(0))

        loaded.tile.key.sampleSize shouldBe 2
        harness.decoder.regionRequests().single().targetWidth shouldBe 4
        harness.decoder.regionRequests().single().targetHeight shouldBe 4
        coordinator.close()
    }

    @Test
    fun `prefetch loads four pages ahead and one behind in priority order`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        // backgroundScope jobs are driven by runCurrent, not advanceUntilIdle, in this
        // coroutines-test version.
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p5.png", "p6.png", "p7.png", "p8.png", "p3.png")
        harness.cache.metrics.entryCount shouldBe 5
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 1280)
    }

    @Test
    fun `visible work overtakes queued prefetch`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        val visible = async { coordinator.loadVisible(source.pageId(3)) }
        runCurrent()

        harness.decoder.producedNames() shouldBe emptyList<String>()
        gate.complete(Unit)
        visible.await()
        runCurrent()

        harness.decoder.producedNames() shouldBe
            listOf("p4.png", "p5.png", "p6.png", "p7.png", "p8.png", "p3.png")
    }

    @Test
    fun `concurrent duplicate visible requests share one flight`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate

        val page = source.pageId(0)
        val first = async { coordinator.loadVisible(page) }
        runCurrent()
        val second = async { coordinator.loadVisible(page) }
        runCurrent()

        harness.decoder.decodeAttempts shouldBe 1
        gate.complete(Unit)

        first.await().tile shouldBe second.await().tile
        harness.decoder.probeCount shouldBe 1
        harness.decoder.producedNames() shouldBe listOf("p1.png")
        harness.cache.metrics.entryCount shouldBe 1
    }

    @Test
    fun `concurrent different page prefetches all complete within the ledger`() = runTest {
        val harness = Harness(capacity = 1024)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()

        harness.decoder.decodeAttempts shouldBe 5
        harness.decoder.producedNames() shouldBe emptyList<String>()
        gate.complete(Unit)
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p5.png", "p6.png", "p7.png", "p8.png", "p3.png")
        // Pressure relief may evict earlier unpinned prefetched tiles while the fifth decode
        // waits for budget, but every requested page still completes within the ledger.
        harness.cache.metrics.entryCount shouldBe 1
        (harness.decoder.peakLedgerBytes <= 1024) shouldBe true
        (harness.cache.metrics.highWaterBytes <= 1024) shouldBe true
        harness.budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 256)
    }

    @Test
    fun `archive reservation contention delays prefetch until the archive lease closes`() = runTest {
        val harness = Harness(capacity = 1024)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val archiveLease = requireNotNull(harness.budget.tryReserve(MemoryKind.DECODED_OUTPUT, 800))

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()

        // 800 held by archive work leaves only 224 ledger bytes, so each 256-byte page
        // decode parks behind the lease instead of exceeding the ledger.
        harness.decoder.producedNames() shouldBe emptyList<String>()
        harness.budget.metrics shouldBe ReaderMemoryMetrics(1024, 800, 0)

        archiveLease.close()
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p5.png", "p6.png", "p7.png", "p8.png")
        (harness.decoder.peakLedgerBytes <= 1024) shouldBe true
        (harness.cache.metrics.highWaterBytes <= 1024) shouldBe true
        harness.budget.metrics.reservedBytes shouldBe 0
        (harness.budget.metrics.cacheBytes <= 1024) shouldBe true
    }

    @Test
    fun `failed visible load memoizes the error and does not hit the source again`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val failure = ReaderFailure.CorruptImage()
        source.failWith("p1.png", failure)

        val page = source.pageId(0)
        val first = runCatching { coordinator.loadVisible(page) }.exceptionOrNull()
        val second = runCatching { coordinator.loadVisible(page) }.exceptionOrNull()

        first shouldBe failure
        second shouldBe failure
        source.openCount("p1.png") shouldBe 1
        harness.cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 0, 0, 0, 0)
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 0)
    }

    @Test
    fun `explicit retry invalidates every cached tile of the page and starts a fresh flight`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)

        val page = source.pageId(0)
        val original = coordinator.loadVisible(page)
        original.resident shouldBe true
        val key = TileKey(page, null, IntRect(0, 0, 4, 4))

        val failure = ReaderFailure.CorruptImage()
        source.failWith("p1.png", failure)
        val retryFailure = runCatching { coordinator.retry(page) }.exceptionOrNull()

        retryFailure shouldBe failure
        harness.decoder.producedImage(0).flushCount shouldBe 1
        harness.cache.get(key) shouldBe null
        // The fresh failure is memoized again.
        runCatching { coordinator.loadVisible(page) }.exceptionOrNull() shouldBe failure

        source.failWith("p1.png", null)
        val reloaded = coordinator.retry(page)
        reloaded.resident shouldBe true
        (reloaded.tile !== original.tile) shouldBe true
        harness.cache.get(key) shouldBe reloaded.tile
        harness.decoder.producedNames() shouldBe listOf("p1.png", "p1.png")
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 256)
    }

    @Test
    fun `memoized error survives reopen with an unchanged asset tuple but clears when it changes`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val firstSource = FakeChapterSource(1, 5, sizeBytes = 100, modifiedAt = 1)
        coordinator.openChapter(firstSource)
        val failure = ReaderFailure.CorruptImage()
        firstSource.failWith("p1.png", failure)

        val page = firstSource.pageId(0)
        runCatching { coordinator.loadVisible(page) }.exceptionOrNull() shouldBe failure
        firstSource.openCount("p1.png") shouldBe 1

        val reopened = FakeChapterSource(1, 5, sizeBytes = 100, modifiedAt = 1)
        coordinator.openChapter(reopened)
        runCatching { coordinator.loadVisible(page) }.exceptionOrNull() shouldBe failure
        reopened.openCount("p1.png") shouldBe 0

        val modified = FakeChapterSource(1, 5, sizeBytes = 100, modifiedAt = 2)
        coordinator.openChapter(modified)
        coordinator.loadVisible(page).resident shouldBe true
        modified.openCount("p1.png") shouldBe 2
    }

    @Test
    fun `cancellation before opening input abandons prefetch without touching the source`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        val visible = async { coordinator.loadVisible(source.pageId(3)) }
        runCurrent()
        // The visible load holds the decode gate; prefetch jobs wait behind visible demand.
        coordinator.updatePosition(7, listOf(source.pageId(7)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()

        source.openCount("p5.png") shouldBe 0
        source.openCount("p6.png") shouldBe 0
        source.openCount("p3.png") shouldBe 0

        gate.complete(Unit)
        visible.await()
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p4.png", "p7.png", "p9.png", "p10.png")
        harness.budget.metrics.reservedBytes shouldBe 0
    }

    @Test
    fun `cancellation after probe stops before decode and is not memoized`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val probeGate = CompletableDeferred<Unit>()
        harness.decoder.probeGate = probeGate

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()
        source.openCount("p5.png") shouldBe 1

        coordinator.updatePosition(7, listOf(source.pageId(7)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        probeGate.complete(Unit)
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p7.png", "p9.png", "p10.png")
        harness.cache.get(TileKey(source.pageId(2), null, IntRect(0, 0, 4, 4))) shouldBe null
        harness.budget.metrics.reservedBytes shouldBe 0

        // A cancelled load leaves no memoized error behind.
        coordinator.loadVisible(source.pageId(2)).resident shouldBe true
    }

    @Test
    fun `cancellation after decode closes the tile and skips cache insertion`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val postDecode = CompletableDeferred<Unit>()
        harness.decoder.postDecodeGate = postDecode

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()
        // The non-cancellable post-decode gate parks all five prefetch decodes.
        harness.decoder.producedNames() shouldBe listOf("p5.png", "p6.png", "p7.png", "p8.png", "p3.png")

        coordinator.updatePosition(7, listOf(source.pageId(7)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        postDecode.complete(Unit)
        runCurrent()

        // Every superseded tile surfaces cancellation at the post-decode checkpoint: the
        // raster is flushed and the cache never sees it.
        harness.decoder.producedImage(0).flushCount shouldBe 1
        harness.decoder.producedImage(1).flushCount shouldBe 1
        harness.decoder.producedImage(3).flushCount shouldBe 1
        harness.decoder.producedImage(4).flushCount shouldBe 1
        harness.cache.get(TileKey(source.pageId(2), null, IntRect(0, 0, 4, 4))) shouldBe null
        harness.decoder.producedNames() shouldBe
            listOf("p5.png", "p6.png", "p7.png", "p8.png", "p3.png", "p9.png", "p10.png")
        harness.cache.metrics shouldBe CacheMetrics(768, 0, 3, 0, 8, 7, 0, 768)
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 768)

        coordinator.loadVisible(source.pageId(2)).resident shouldBe true
    }

    @Test
    fun `direction reversal cancels obsolete prefetch and keeps wanted pages`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate

        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()
        harness.decoder.decodeAttempts shouldBe 5

        // Backward plan from index 3 wants indexes 2, 1, and 0 ahead plus index 4 behind;
        // the other forward flights become obsolete mid-decode.
        coordinator.updatePosition(3, listOf(source.pageId(3)), ReadingMode.SINGLE_LTR, NavigationDirection.BACKWARD)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        harness.decoder.producedNames() shouldBe listOf("p5.png", "p3.png", "p2.png", "p1.png")
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 1024)

        // Cancellation left no memoized error for the obsolete page.
        coordinator.loadVisible(source.pageId(5)).resident shouldBe true
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 1280)
    }

    @Test
    fun `chapter transition cancels old work evicts old pages and clears pins`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val sourceA = FakeChapterSource(1, 10)
        coordinator.openChapter(sourceA)
        val first = coordinator.loadVisible(sourceA.pageId(0))
        first.resident shouldBe true

        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate
        coordinator.updatePosition(0, listOf(sourceA.pageId(0)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()
        harness.decoder.decodeAttempts shouldBe 5

        val sourceB = FakeChapterSource(2, 5)
        coordinator.openChapter(sourceB)
        gate.complete(Unit)
        runCurrent()

        // Old chapter prefetch never produced; the visible page was evicted and flushed.
        harness.decoder.producedNames() shouldBe listOf("p1.png")
        harness.decoder.producedImage(0).flushCount shouldBe 1
        harness.cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 5, 5, 1, 256)
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 0)

        val loaded = coordinator.loadVisible(sourceB.pageId(0))
        loaded.resident shouldBe true
        harness.decoder.producedNames() shouldBe listOf("p1.png", "p1.png")
        harness.budget.metrics shouldBe ReaderMemoryMetrics(4096, 0, 256)
    }

    @Test
    fun `animated pages key full frames and region requests are forced to sample size one`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        harness.decoder.frameCount = 3
        val source = FakeChapterSource(1, 5)
        coordinator.openChapter(source)

        val page = source.pageId(0)
        val frame = coordinator.loadVisible(page, frameIndex = 2)
        frame.resident shouldBe true
        val frameKey = TileKey(page, FrameId(page, 2), IntRect(0, 0, 4, 4))
        frame.tile.key shouldBe frameKey
        harness.cache.get(frameKey) shouldBe frame.tile
        harness.cache.metrics.pinnedBytes shouldBe 256

        // GIF region decode ignores sampleSize (Task 4): the coordinator must request GIF
        // tiles at sampleSize = 1 so identical rasters are never double-cached.
        val region = coordinator.loadRegion(page, IntRect(0, 0, 4, 4), frameIndex = 1, sampleSize = 8)
        region.resident shouldBe true
        val request = harness.decoder.regionRequests().single()
        request.key.sampleSize shouldBe 1
        request.key.frameId shouldBe FrameId(page, 1)
        request.targetWidth shouldBe 4
        request.targetHeight shouldBe 4
    }

    @Test
    fun `region window releases stale pins while retaining the full page fallback`() = runTest {
        val harness = Harness(capacity = 4096)
        harness.decoder.imageWidth = 2048
        harness.decoder.imageHeight = 1024
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 1)
        coordinator.openChapter(source)

        val page = source.pageId(0)
        coordinator.loadVisible(page)
        val left = coordinator.loadRegion(page, IntRect(0, 0, 1024, 1024)).tile.key
        val right = coordinator.loadRegion(page, IntRect(1024, 0, 2048, 1024)).tile.key
        harness.cache.metrics.pinnedBytes shouldBe 768

        coordinator.retainVisibleRegions(page, setOf(right))

        harness.cache.metrics.pinnedBytes shouldBe 512
        harness.cache.relievePressure()
        harness.cache.get(left) shouldBe null
        harness.cache.get(right) shouldBe harness.cache.get(right)
    }

    @Test
    fun `close cancels in-flight prefetch releases pins and rejects further calls`() = runTest {
        val harness = Harness(capacity = 4096)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, 10)
        coordinator.openChapter(source)
        coordinator.loadVisible(source.pageId(0))
        harness.cache.metrics.pinnedBytes shouldBe 256

        val gate = CompletableDeferred<Unit>()
        harness.decoder.decodeGate = gate
        coordinator.updatePosition(0, listOf(source.pageId(0)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        runCurrent()

        coordinator.close()
        gate.complete(Unit)
        runCurrent()

        harness.cache.metrics.pinnedBytes shouldBe 0
        harness.decoder.producedNames() shouldBe listOf("p1.png")
        harness.budget.metrics.reservedBytes shouldBe 0
        shouldThrow<IllegalStateException> { coordinator.loadVisible(source.pageId(1)) }
        shouldThrow<IllegalStateException> {
            coordinator.updatePosition(0, listOf(source.pageId(0)), ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        }
        shouldThrow<IllegalStateException> { coordinator.openChapter(FakeChapterSource(2, 3)) }
    }

    @Test
    fun `ten thousand page walk keeps resident plus in-flight high water at or below capacity`() = runTest {
        val pageCount = 10_000
        val harness = Harness(capacity = 4096, tileWeight = 512)
        val coordinator = harness.coordinator(backgroundScope)
        val source = FakeChapterSource(1, pageCount)
        coordinator.openChapter(source)

        for (index in 0 until pageCount) {
            coordinator.updatePosition(
                index,
                listOf(source.pageId(index)),
                ReadingMode.SINGLE_LTR,
                NavigationDirection.FORWARD,
            )
            coordinator.loadVisible(source.pageId(index)).resident shouldBe true
            if (index % 64 == 0) runCurrent()
        }
        runCurrent()
        coordinator.close()

        (harness.decoder.peakLedgerBytes <= 4096) shouldBe true
        (harness.cache.metrics.highWaterBytes <= 4096) shouldBe true
        (harness.cache.metrics.residentBytes <= 4096) shouldBe true
        harness.budget.metrics.reservedBytes shouldBe 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchPolicyTest {
    @Test
    fun `single page forward plans four pages ahead and one behind`() {
        PrefetchPolicy.plan(10, 3, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD) shouldBe
            listOf(4, 5, 6, 7, 2)
    }

    @Test
    fun `single page backward mirrors the forward plan`() {
        PrefetchPolicy.plan(10, 6, ReadingMode.SINGLE_RTL, NavigationDirection.BACKWARD) shouldBe
            listOf(5, 4, 3, 2, 7)
    }

    @Test
    fun `dual page forward skips the visible spread and plans four images ahead`() {
        PrefetchPolicy.plan(10, 2, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD) shouldBe
            listOf(4, 5, 6, 7, 1)
    }

    @Test
    fun `dual page backward mirrors images and RTL matches LTR logically`() {
        PrefetchPolicy.plan(10, 6, ReadingMode.DUAL_RTL, NavigationDirection.BACKWARD) shouldBe
            listOf(4, 3, 2, 1, 7)
        PrefetchPolicy.plan(10, 4, ReadingMode.DUAL_RTL, NavigationDirection.FORWARD) shouldBe
            PrefetchPolicy.plan(10, 4, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD)
    }

    @Test
    fun `continuous modes use single page units`() {
        PrefetchPolicy.plan(10, 3, ReadingMode.VERTICAL, NavigationDirection.FORWARD) shouldBe
            listOf(4, 5, 6, 7, 2)
        PrefetchPolicy.plan(10, 3, ReadingMode.WEBTOON, NavigationDirection.FORWARD) shouldBe
            listOf(4, 5, 6, 7, 2)
    }

    @Test
    fun `plan clamps at chapter edges`() {
        PrefetchPolicy.plan(3, 0, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD) shouldBe listOf(1, 2)
        PrefetchPolicy.plan(3, 2, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD) shouldBe listOf(1)
        PrefetchPolicy.plan(5, 4, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD) shouldBe listOf(3)
        PrefetchPolicy.plan(2, 0, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD) shouldBe emptyList<Int>()
    }

    @Test
    fun `plan rejects invalid indexes`() {
        shouldThrow<IllegalArgumentException> {
            PrefetchPolicy.plan(0, 0, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        }
        shouldThrow<IllegalArgumentException> {
            PrefetchPolicy.plan(3, 3, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        }
        shouldThrow<IllegalArgumentException> {
            PrefetchPolicy.plan(3, -1, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD)
        }
    }
}
