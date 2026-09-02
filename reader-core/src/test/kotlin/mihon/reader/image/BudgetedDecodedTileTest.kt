package mihon.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class BudgetedDecodedTileTest {
    private val pageId = PageId("1", "page.png")
    private val key = TileKey(pageId, null, IntRect(0, 0, 8, 8))

    private class FlushCountingImage : BufferedImage(8, 8, TYPE_INT_ARGB_PRE) {
        var flushCount = 0
            private set

        override fun flush() {
            flushCount += 1
            super.flush()
        }
    }

    @Test
    fun `tile owns the final decode lease and close releases it and flushes exactly once`() {
        val budget = BoundedReaderMemoryBudget(1024)
        val lease = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 256))
        val image = FlushCountingImage()
        val tile = BudgetedDecodedTile(key, image, lease)

        tile.key shouldBe key
        tile.image shouldBe image
        tile.outputReservation.kind shouldBe MemoryKind.DECODED_OUTPUT
        tile.outputReservation.byteCount shouldBe 256
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 256, 0)

        tile.close()
        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 0)
        image.flushCount shouldBe 1
    }

    @Test
    fun `adoption transfers the lease to cache residency without a reservation gap`() {
        val budget = BoundedReaderMemoryBudget(1024)
        val lease = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 256))
        val tile = BudgetedDecodedTile(key, FlushCountingImage(), lease)

        tile.adoptAsCacheResident()
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 256)
        tile.outputReservation.kind shouldBe MemoryKind.CACHE_RESIDENT
        tile.outputReservation.byteCount shouldBe 256

        shouldThrow<IllegalStateException> { tile.adoptAsCacheResident() }
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 256)

        tile.close()
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 0)
    }

    @Test
    fun `adoption after close is rejected and cannot resurrect released bytes`() {
        val budget = BoundedReaderMemoryBudget(1024)
        val lease = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 256))
        val tile = BudgetedDecodedTile(key, FlushCountingImage(), lease)

        tile.close()
        shouldThrow<IllegalStateException> { tile.adoptAsCacheResident() }
        budget.metrics shouldBe ReaderMemoryMetrics(1024, 0, 0)
    }
}
