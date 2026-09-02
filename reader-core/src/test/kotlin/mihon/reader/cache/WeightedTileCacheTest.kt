package mihon.reader.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mihon.reader.image.BudgetedDecodedTile
import mihon.reader.image.IntRect
import mihon.reader.image.TileKey
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

@OptIn(ExperimentalCoroutinesApi::class)
class WeightedTileCacheTest {
    private val pageOne = PageId("1", "p1.png")
    private val pageTwo = PageId("1", "p2.png")
    private val pageThree = PageId("1", "p3.png")

    private class FlushCountingImage : BufferedImage(2, 2, TYPE_INT_ARGB_PRE) {
        var flushCount = 0
            private set

        override fun flush() {
            flushCount += 1
            super.flush()
        }
    }

    private fun key(pageId: PageId, left: Int = 0) = TileKey(pageId, null, IntRect(left, 0, left + 2, 2))

    private fun decodedTile(
        budget: BoundedReaderMemoryBudget,
        key: TileKey,
        weight: Long,
        image: FlushCountingImage = FlushCountingImage(),
    ): BudgetedDecodedTile {
        val lease = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, weight)) {
            "test setup could not reserve $weight bytes"
        }
        return BudgetedDecodedTile(key, image, lease)
    }

    @Test
    fun `miss then load then hit and metrics track each transition`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)

        cache.get(key(pageOne)) shouldBe null
        cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 1, 0, 0, 0)

        val tile = cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 64) }
        tile.resident shouldBe true
        cache.metrics shouldBe CacheMetrics(
            residentBytes = 64,
            pinnedBytes = 0,
            entryCount = 1,
            hitCount = 0,
            missCount = 2,
            loadCount = 1,
            evictionCount = 0,
            highWaterBytes = 64,
        )

        cache.get(key(pageOne)) shouldBe tile.tile
        cache.getOrLoad(key(pageOne)) { error("must not load twice") }.tile shouldBe tile.tile
        cache.metrics shouldBe CacheMetrics(
            residentBytes = 64,
            pinnedBytes = 0,
            entryCount = 1,
            hitCount = 2,
            missCount = 2,
            loadCount = 1,
            evictionCount = 0,
            highWaterBytes = 64,
        )
    }

    @Test
    fun `exact capacity retains every entry and records the high water`() = runTest {
        val budget = BoundedReaderMemoryBudget(128)
        val cache = WeightedTileCache(capacityBytes = 100)

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 60) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 40) }

        cache.metrics shouldBe CacheMetrics(100, 0, 2, 0, 2, 2, 0, 100)
        budget.metrics shouldBe ReaderMemoryMetrics(128, 0, 100)
    }

    @Test
    fun `one byte over capacity evicts the least recently used unpinned entry`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 100)
        val evictedImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 60, evictedImage) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 40) }
        // 100 resident + 41 exceeds the 100 byte capacity by one: page one (LRU) must go.
        val kept = cache.getOrLoad(key(pageThree)) { decodedTile(budget, key(pageThree), 41) }

        kept.resident shouldBe true
        evictedImage.flushCount shouldBe 1
        cache.metrics shouldBe CacheMetrics(81, 0, 2, 0, 3, 3, 1, 100)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 81)
        cache.get(key(pageOne)) shouldBe null
    }

    @Test
    fun `access order touch protects the recently used entry from eviction`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 100)
        val pageTwoImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 40) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 40, pageTwoImage) }
        cache.get(key(pageOne)) // touch: page two becomes the eldest entry
        cache.getOrLoad(key(pageThree)) { decodedTile(budget, key(pageThree), 41) }

        pageTwoImage.flushCount shouldBe 1
        cache.metrics shouldBe CacheMetrics(81, 0, 2, 1, 3, 3, 1, 81)
        cache.get(key(pageTwo)) shouldBe null
    }

    @Test
    fun `pinned entries survive eviction while unpinned entries are evicted first`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 100)
        val pinnedImage = FlushCountingImage()
        val victimImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 50, pinnedImage) }
        val pin = cache.pin(key(pageOne))
        cache.metrics.pinnedBytes shouldBe 50

        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 50, victimImage) }
        cache.getOrLoad(key(pageThree)) { decodedTile(budget, key(pageThree), 41) }

        victimImage.flushCount shouldBe 1
        pinnedImage.flushCount shouldBe 0
        cache.get(key(pageTwo)) shouldBe null
        // No get(pageOne) touch here: the pinned entry must stay the eldest so that the
        // unpin below demonstrably makes it evictable again.
        cache.metrics shouldBe CacheMetrics(91, 50, 2, 0, 4, 3, 1, 100)

        pin.close()
        pin.close()
        cache.metrics.pinnedBytes shouldBe 0
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 50) }
        pinnedImage.flushCount shouldBe 1
        cache.metrics.entryCount shouldBe 2
        cache.metrics.residentBytes shouldBe 91
    }

    @Test
    fun `when every resident is pinned an oversized load is delivered without retention`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 100)
        val deliveredImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 60) }
        val pin = cache.pin(key(pageOne))

        val bypass = cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 60, deliveredImage) }

        bypass.resident shouldBe false
        cache.get(key(pageTwo)) shouldBe null
        cache.metrics shouldBe CacheMetrics(60, 60, 1, 0, 3, 2, 0, 60)
        // The uncached bypass still holds ledger bytes until its lease ends.
        budget.metrics shouldBe ReaderMemoryMetrics(256, 60, 60)

        bypass.tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 60)
        deliveredImage.flushCount shouldBe 1
        pin.close()
    }

    @Test
    fun `concurrent duplicate requests share a single load`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loadCalls = 0

        val first = async {
            cache.getOrLoad(key(pageOne)) {
                loadCalls += 1
                started.complete(Unit)
                release.await()
                decodedTile(budget, key(pageOne), 64)
            }
        }
        runCurrent()
        started.await()
        val second = async { cache.getOrLoad(key(pageOne)) { error("duplicate load") } }
        runCurrent()

        loadCalls shouldBe 1
        release.complete(Unit)

        first.await().tile shouldBe second.await().tile
        cache.metrics shouldBe CacheMetrics(64, 0, 1, 0, 2, 1, 0, 64)
    }

    @Test
    fun `a load waiting for space is granted only after unpinned eviction frees it`() = runTest {
        lateinit var cache: WeightedTileCache
        val budget = BoundedReaderMemoryBudget(100) { cache.relievePressure() }
        cache = WeightedTileCache(capacityBytes = 100)
        val evictedImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 60, evictedImage) }

        val pending = async {
            cache.getOrLoad(key(pageTwo)) {
                val lease = budget.reserve(MemoryKind.DECODED_OUTPUT, 60)
                BudgetedDecodedTile(key(pageTwo), FlushCountingImage(), lease)
            }
        }
        runCurrent()

        // The 60 byte load cannot reserve while page one holds 60 of 100 bytes; pressure
        // eviction frees the unpinned entry and the FIFO waiter is then granted.
        evictedImage.flushCount shouldBe 1
        val loaded = pending.await()
        loaded.resident shouldBe true
        cache.metrics shouldBe CacheMetrics(60, 0, 1, 0, 2, 2, 1, 60)
        budget.metrics shouldBe ReaderMemoryMetrics(100, 0, 60)
    }

    @Test
    fun `failed load cleans up in-flight state and propagates the failure to joiners`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val failure = ReaderFailure.CorruptImage()

        val leader = async {
            runCatching {
                cache.getOrLoad(key(pageOne)) {
                    started.complete(Unit)
                    release.await()
                    throw failure
                }
            }
        }
        runCurrent()
        started.await()
        val joiner = async { runCatching { cache.getOrLoad(key(pageOne)) { error("duplicate load") } } }
        runCurrent()
        release.complete(Unit)

        leader.await().exceptionOrNull() shouldBe failure
        joiner.await().exceptionOrNull() shouldBe failure
        cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 2, 1, 0, 0)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 0)
    }

    @Test
    fun `failed load is retried cleanly by the next request`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val failure = ReaderFailure.CorruptImage()

        val first = runCatching {
            cache.getOrLoad(key(pageOne)) { throw failure }
        }
        first.exceptionOrNull() shouldBe failure
        cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 1, 1, 0, 0)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 0)

        val second = cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 32) }
        second.resident shouldBe true
        cache.metrics shouldBe CacheMetrics(32, 0, 1, 0, 2, 2, 0, 32)
    }

    @Test
    fun `failed load does not leak bytes reserved by the loader before throwing`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)

        val result = runCatching {
            cache.getOrLoad(key(pageOne)) {
                val lease = budget.reserve(MemoryKind.DECODED_OUTPUT, 64)
                lease.close()
                throw ReaderFailure.CorruptImage()
            }
        }

        result.isFailure shouldBe true
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 0)
        cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 1, 1, 0, 0)
    }

    @Test
    fun `leader cancellation after decode closes the tile and a joiner reloads`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val produced = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelledImage = FlushCountingImage()
        var loadCalls = 0

        val leader = async {
            cache.getOrLoad(key(pageOne)) {
                loadCalls += 1
                val tile = decodedTile(budget, key(pageOne), 64, cancelledImage)
                produced.complete(Unit)
                withContext(NonCancellable) { release.await() }
                tile
            }
        }
        runCurrent()
        produced.await()
        val joiner = async { cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 64) } }
        runCurrent()

        leader.cancel(CancellationException("viewport moved"))
        release.complete(Unit)

        shouldThrow<CancellationException> { leader.await() }
        cancelledImage.flushCount shouldBe 1
        loadCalls shouldBe 1
        joiner.await().resident shouldBe true
        cache.metrics shouldBe CacheMetrics(64, 0, 1, 0, 3, 2, 0, 64)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 64)
    }

    @Test
    fun `invalidate page drops every tile and frame of the page even when pinned`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val frameOne = TileKey(pageOne, FrameId(pageOne, 0), IntRect(0, 0, 2, 2))
        val frameTwo = TileKey(pageOne, FrameId(pageOne, 1), IntRect(0, 0, 2, 2))
        val region = TileKey(pageOne, null, IntRect(0, 0, 1, 1))

        cache.getOrLoad(frameOne) { decodedTile(budget, frameOne, 16) }
        cache.getOrLoad(frameTwo) { decodedTile(budget, frameTwo, 16) }
        cache.getOrLoad(region) { decodedTile(budget, region, 16) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 16) }
        val pin = cache.pin(frameOne)
        cache.metrics.pinnedBytes shouldBe 16

        cache.invalidatePage(pageOne)

        cache.metrics shouldBe CacheMetrics(16, 0, 1, 0, 4, 4, 3, 64)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 16)
        pin.close()
        cache.metrics.pinnedBytes shouldBe 0
    }

    @Test
    fun `invalidate chapter drops only that chapter's entries`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val otherChapter = TileKey(PageId("2", "q1.png"), null, IntRect(0, 0, 2, 2))

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 16) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 16) }
        val kept = cache.getOrLoad(otherChapter) { decodedTile(budget, otherChapter, 16) }

        cache.invalidateChapter("1")

        cache.get(key(pageOne)) shouldBe null
        cache.get(otherChapter) shouldBe kept.tile
        cache.metrics shouldBe CacheMetrics(16, 0, 1, 1, 4, 3, 2, 48)
    }

    @Test
    fun `relieve pressure evicts every unpinned entry and keeps pinned ones`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val evictedImage = FlushCountingImage()
        val pinnedImage = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 64, evictedImage) }
        cache.getOrLoad(key(pageTwo)) { decodedTile(budget, key(pageTwo), 64, pinnedImage) }
        val pin = cache.pin(key(pageTwo))

        cache.relievePressure()

        evictedImage.flushCount shouldBe 1
        pinnedImage.flushCount shouldBe 0
        cache.metrics shouldBe CacheMetrics(64, 64, 1, 0, 2, 2, 1, 128)
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 64)
        pin.close()
    }

    @Test
    fun `close evicts every entry and rejects further loads`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val image = FlushCountingImage()

        cache.getOrLoad(key(pageOne)) { decodedTile(budget, key(pageOne), 64, image) }
        cache.close()
        cache.close()

        image.flushCount shouldBe 1
        budget.metrics shouldBe ReaderMemoryMetrics(256, 0, 0)
        shouldThrow<IllegalStateException> { cache.get(key(pageOne)) }
        shouldThrow<IllegalStateException> { cache.pin(key(pageOne)) }
        shouldThrow<IllegalStateException> {
            cache.getOrLoad(key(pageTwo)) { error("closed cache must not load") }
        }
    }

    @Test
    fun `metrics never contain file paths or titles`() = runTest {
        val budget = BoundedReaderMemoryBudget(256)
        val cache = WeightedTileCache(capacityBytes = 256)
        val secret = TileKey(PageId("secret-chapter-title", "secret-file-name.png"), null, IntRect(0, 0, 2, 2))

        cache.getOrLoad(secret) { decodedTile(budget, secret, 16) }
        cache.pin(secret).close()
        cache.invalidatePage(secret.pageId)

        val rendered = cache.metrics.toString()
        rendered shouldNotContain "secret"
        rendered shouldNotContain ".png"
        cache.metrics shouldBe CacheMetrics(0, 0, 0, 0, 1, 1, 1, 16)
    }
}
