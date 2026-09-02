package mihon.reader.image

import mihon.reader.memory.MemoryKind
import mihon.reader.memory.MemoryLease
import java.awt.image.BufferedImage

class BudgetedDecodedTile(
    override val key: TileKey,
    override val image: BufferedImage,
    lease: MemoryLease,
) : DecodedTile {
    init {
        require(lease.kind == MemoryKind.DECODED_OUTPUT) { "decoded tiles start with a DECODED_OUTPUT lease" }
    }

    private val lock = Any()
    private var currentLease: MemoryLease = lease
    private var adopted = false
    private var closed = false

    override val outputReservation: MemoryLease
        get() = synchronized(lock) { currentLease }

    override fun adoptAsCacheResident() {
        synchronized(lock) {
            check(!closed) { "Decoded tile is closed" }
            check(!adopted) { "Decoded tile has already been adopted as cache resident" }
            adopted = true
            currentLease = currentLease.transferTo(MemoryKind.CACHE_RESIDENT)
        }
    }

    override fun close() {
        val toRelease = synchronized(lock) {
            if (closed) return
            closed = true
            currentLease
        }
        try {
            toRelease.close()
        } finally {
            image.flush()
        }
    }
}
