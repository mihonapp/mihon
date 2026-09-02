package mihon.reader.memory

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits

class BoundedReaderMemoryBudget(
    private val limitBytes: Long = ReaderLimits.READER_MEMORY_BYTES,
    private val onPressure: (ReaderMemoryMetrics) -> Unit = {},
) : ReaderMemoryBudget {
    init {
        require(limitBytes >= 0) { "limitBytes must not be negative" }
    }

    private val lock = Any()
    private val waiters = ArrayDeque<Waiter>()
    private var reservedBytes = 0L
    private var cacheBytes = 0L
    private var closed = false

    override val metrics: ReaderMemoryMetrics
        get() = synchronized(lock) { metricsLocked() }

    override fun tryReserve(kind: MemoryKind, byteCount: Long): MemoryLease? {
        validateRequest(byteCount)
        var pressure: ReaderMemoryMetrics? = null
        val lease = synchronized(lock) {
            checkOpenLocked()
            if (waiters.isEmpty() && fitsLocked(byteCount)) {
                createLeaseLocked(kind, byteCount)
            } else {
                pressure = metricsLocked()
                null
            }
        }
        pressure?.let(onPressure)
        return lease
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun reserve(kind: MemoryKind, byteCount: Long): MemoryLease {
        validateRequest(byteCount)
        val deferred = CompletableDeferred<MemoryLease>(currentCoroutineContext()[Job])
        var immediate: MemoryLease? = null
        var pressure: ReaderMemoryMetrics? = null
        synchronized(lock) {
            checkOpenLocked()
            if (waiters.isEmpty() && fitsLocked(byteCount)) {
                immediate = createLeaseLocked(kind, byteCount)
            } else {
                waiters.addLast(Waiter(kind, byteCount, deferred))
                pressure = metricsLocked()
            }
        }
        try {
            pressure?.let(onPressure)
        } catch (error: Throwable) {
            synchronized(lock) {
                waiters.removeAll { it.deferred === deferred }
            }
            deferred.cancel()
            // The callback may have drained this waiter (e.g. by closing leases to evict),
            // in which case the deferred already holds a granted lease that must be released.
            if (deferred.isCompleted && !deferred.isCancelled) {
                runCatching { deferred.getCompleted().close() }
            }
            throw error
        }
        immediate?.let {
            if (deferred.complete(it)) return it
            it.close()
            return deferred.await()
        }
        try {
            return deferred.await()
        } catch (error: Throwable) {
            val orphanedLease = if (deferred.isCompleted && !deferred.isCancelled) {
                runCatching { deferred.getCompleted() }.getOrNull()
            } else {
                null
            }
            val grants = synchronized(lock) {
                waiters.removeAll { it.deferred === deferred }
                drainLocked()
            }
            orphanedLease?.close()
            completeGrants(grants)
            throw error
        }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            waiters.toList().also { waiters.clear() }
        }
        pending.forEach { it.deferred.completeExceptionally(ReaderFailure.MemoryBudgetClosed()) }
    }

    private fun validateRequest(byteCount: Long) {
        require(byteCount >= 0) { "byteCount must not be negative" }
        if (byteCount > limitBytes) {
            throw ReaderFailure.LimitExceeded("reader memory", limitBytes, byteCount)
        }
    }

    private fun checkOpenLocked() {
        if (closed) throw ReaderFailure.MemoryBudgetClosed()
    }

    private fun metricsLocked() = ReaderMemoryMetrics(limitBytes, reservedBytes, cacheBytes)

    private fun fitsLocked(byteCount: Long): Boolean = byteCount <= limitBytes - reservedBytes - cacheBytes

    private fun createLeaseLocked(kind: MemoryKind, byteCount: Long): Lease {
        when (kind) {
            MemoryKind.DECODED_OUTPUT -> reservedBytes += byteCount
            MemoryKind.CACHE_RESIDENT -> cacheBytes += byteCount
        }
        return Lease(kind, byteCount)
    }

    private fun drainLocked(): List<Pair<Waiter, Lease>> {
        val grants = mutableListOf<Pair<Waiter, Lease>>()
        while (waiters.isNotEmpty()) {
            val waiter = waiters.first()
            if (!waiter.deferred.isActive) {
                waiters.removeFirst()
                continue
            }
            if (!fitsLocked(waiter.byteCount)) break
            waiters.removeFirst()
            grants += waiter to createLeaseLocked(waiter.kind, waiter.byteCount)
        }
        return grants
    }

    private fun completeGrants(grants: List<Pair<Waiter, Lease>>) {
        grants.forEach { (waiter, lease) ->
            if (!waiter.deferred.complete(lease)) lease.close()
        }
    }

    private inner class Lease(
        override val kind: MemoryKind,
        override val byteCount: Long,
    ) : MemoryLease {
        private var active = true

        override fun transferTo(kind: MemoryKind): MemoryLease {
            synchronized(lock) {
                if (!active) throw IllegalStateException("Memory lease has already been released or transferred")
                when (this.kind) {
                    MemoryKind.DECODED_OUTPUT -> reservedBytes -= byteCount
                    MemoryKind.CACHE_RESIDENT -> cacheBytes -= byteCount
                }
                when (kind) {
                    MemoryKind.DECODED_OUTPUT -> reservedBytes += byteCount
                    MemoryKind.CACHE_RESIDENT -> cacheBytes += byteCount
                }
                active = false
                return Lease(kind, byteCount)
            }
        }

        override fun close() {
            val grants = synchronized(lock) {
                if (!active) return
                active = false
                when (kind) {
                    MemoryKind.DECODED_OUTPUT -> reservedBytes -= byteCount
                    MemoryKind.CACHE_RESIDENT -> cacheBytes -= byteCount
                }
                drainLocked()
            }
            completeGrants(grants)
        }
    }

    private data class Waiter(
        val kind: MemoryKind,
        val byteCount: Long,
        val deferred: CompletableDeferred<MemoryLease>,
    )
}
