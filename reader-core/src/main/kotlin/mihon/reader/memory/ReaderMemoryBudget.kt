package mihon.reader.memory

import java.io.Closeable

enum class MemoryKind {
    DECODED_OUTPUT,
    CACHE_RESIDENT,
}

data class ReaderMemoryMetrics(
    val limitBytes: Long,
    val reservedBytes: Long,
    val cacheBytes: Long,
) {
    init {
        require(limitBytes >= 0) { "limitBytes must not be negative" }
        require(reservedBytes >= 0) { "reservedBytes must not be negative" }
        require(cacheBytes >= 0) { "cacheBytes must not be negative" }
        require(cacheBytes <= limitBytes) { "memory usage must not exceed limit" }
        require(reservedBytes <= limitBytes - cacheBytes) { "memory usage must not exceed limit" }
    }
}

interface MemoryLease : Closeable {
    val kind: MemoryKind
    val byteCount: Long

    fun transferTo(kind: MemoryKind): MemoryLease

    override fun close()
}

interface ReaderMemoryBudget : Closeable {
    val metrics: ReaderMemoryMetrics

    fun tryReserve(kind: MemoryKind, byteCount: Long): MemoryLease?

    override fun close()
}
