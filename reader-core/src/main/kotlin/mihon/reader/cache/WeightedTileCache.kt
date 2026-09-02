package mihon.reader.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mihon.reader.image.DecodedTile
import mihon.reader.image.TileKey
import mihon.reader.model.PageId
import mihon.reader.source.ReaderLimits
import java.io.Closeable

/**
 * Access-order weighted LRU over decoded tiles whose bytes are tracked by the shared reader
 * reservation ledger (see `mihon.reader.memory.BoundedReaderMemoryBudget`).
 *
 * - Weights are the tile's lease byte counts; resident and pinned sums use checked `Long`
 *   arithmetic so an accounting overflow fails fast instead of wrapping.
 * - [pin] returns a visible-key pin lease. Pinned entries are never evicted, not even by
 *   [relievePressure]; [invalidatePage]/[invalidateChapter] are explicit invalidations and do
 *   remove pinned entries (outstanding pin handles then become no-ops).
 * - [getOrLoad] is single-flight: concurrent callers for an absent key share one load. If the
 *   loading caller is cancelled, the produced tile is closed and a waiting caller transparently
 *   becomes the next leader instead of observing a bogus cancellation.
 * - Every tile close (eviction, invalidation, shutdown, cancelled insert) happens outside the
 *   map lock so `BufferedImage.flush` and lease drains never run under it.
 * - A load whose decoded tile cannot be retained because every resident byte is pinned is still
 *   delivered ([LoadedTile.resident] is false); the caller then owns that tile and must close it.
 *   Its bytes stay on the shared ledger until that close — no uncached bypass may exceed the
 *   ledger.
 *
 * Wire [relievePressure] as the budget's `onPressure` callback so a reservation that cannot be
 * granted immediately drops unpinned residents (eldest first) and lets FIFO waiters proceed.
 */
class WeightedTileCache(
    private val capacityBytes: Long = ReaderLimits.READER_MEMORY_BYTES,
) : Closeable {
    init {
        require(capacityBytes >= 0) { "capacityBytes must not be negative" }
    }

    /** Outcome of [getOrLoad]. A non-[resident] tile is not owned by the cache: close it when done. */
    data class LoadedTile(
        val tile: DecodedTile,
        val resident: Boolean,
    )

    /** A visible-key pin lease. Closing is idempotent; pinned entries cannot be evicted. */
    interface TilePin : Closeable {
        val key: TileKey

        override fun close()
    }

    private val lock = Any()

    // Insertion-order LinkedHashMap: iteration is eldest-first. Recency is refreshed only by
    // explicit touches on cache hits; bookkeeping lookups (pin accounting, invalidation
    // predicates) must never change eviction order.
    private val entries = LinkedHashMap<TileKey, Entry>()
    private val pins = HashMap<TileKey, Int>()
    private val inFlight = HashMap<TileKey, CompletableDeferred<LoadedTile>>()
    private var residentBytes = 0L
    private var pinnedBytes = 0L
    private var hitCount = 0L
    private var missCount = 0L
    private var loadCount = 0L
    private var evictionCount = 0L
    private var highWaterBytes = 0L
    private var closed = false

    val metrics: CacheMetrics
        get() = synchronized(lock) {
            CacheMetrics(
                residentBytes = residentBytes,
                pinnedBytes = pinnedBytes,
                entryCount = entries.size,
                hitCount = hitCount,
                missCount = missCount,
                loadCount = loadCount,
                evictionCount = evictionCount,
                highWaterBytes = highWaterBytes,
            )
        }

    /** Returns the resident tile and refreshes its recency, or null. Borrowed: never close it. */
    fun get(key: TileKey): DecodedTile? = synchronized(lock) {
        checkOpenLocked()
        val entry = entries[key]
        if (entry != null) {
            hitCount++
            touchLocked(key)
        } else {
            missCount++
        }
        entry?.tile
    }

    /** Pins [key] (resident or not yet loaded) so eviction skips it until every pin closes. */
    fun pin(key: TileKey): TilePin {
        synchronized(lock) {
            checkOpenLocked()
            val count = pins[key] ?: 0
            pins[key] = count + 1
            if (count == 0) {
                entries[key]?.let { pinnedBytes = Math.addExact(pinnedBytes, it.weight) }
            }
        }
        return PinHandle(key)
    }

    /**
     * Returns the resident tile for [key] or loads it once, adopting its lease into cache
     * residency on success. A cancellation check runs after [loader] returns and before the
     * tile is inserted; a cancelled insert closes the tile and releases its lease.
     *
     * [insertGuard] is evaluated under the map lock immediately before insertion; when it
     * returns false the tile is closed instead of inserted and the flight ends in
     * [CancellationException]. It must not acquire locks that could invert lock order.
     */
    suspend fun getOrLoad(
        key: TileKey,
        insertGuard: () -> Boolean = { true },
        loader: suspend () -> DecodedTile,
    ): LoadedTile {
        while (true) {
            when (val action = synchronized(lock) { nextActionLocked(key) }) {
                is Lookup.Hit -> return LoadedTile(action.tile, resident = true)
                is Lookup.Join -> {
                    try {
                        return action.deferred.await()
                    } catch (cancel: CancellationException) {
                        // Rethrow when this caller was cancelled; otherwise the leader was
                        // cancelled and this caller becomes (or joins) the next flight.
                        currentCoroutineContext().ensureActive()
                        continue
                    }
                }
                is Lookup.Lead -> return loadAndPublish(key, action.deferred, insertGuard, loader)
            }
        }
    }

    /** Drops every tile and frame of [pageId], even pinned ones; outstanding pins become no-ops. */
    fun invalidatePage(pageId: PageId) = invalidateWhere { it.pageId == pageId }

    /** Drops every cached entry of [chapterId]; outstanding pins for them become no-ops. */
    fun invalidateChapter(chapterId: String) = invalidateWhere { it.pageId.chapterId == chapterId }

    /**
     * Evicts every unpinned entry, eldest first, closing tiles outside the map lock. Intended as
     * the budget pressure callback: the freed leases drain FIFO reservation waiters.
     */
    fun relievePressure() {
        val victims = synchronized(lock) {
            if (closed) return
            evictWhereLocked { key -> (pins[key] ?: 0) == 0 }
        }
        victims.forEach { it.close() }
    }

    override fun close() {
        val (victims, abandoned) = synchronized(lock) {
            if (closed) return
            closed = true
            val tiles = entries.values.map { it.tile }
            entries.clear()
            residentBytes = 0L
            pinnedBytes = 0L
            pins.clear()
            val flights = inFlight.values.toList()
            inFlight.clear()
            tiles to flights
        }
        victims.forEach { it.close() }
        abandoned.forEach { it.completeExceptionally(IllegalStateException(CLOSED_MESSAGE)) }
    }

    private fun nextActionLocked(key: TileKey): Lookup {
        checkOpenLocked()
        val entry = entries[key]
        if (entry != null) {
            hitCount++
            touchLocked(key)
            return Lookup.Hit(entry.tile)
        }
        val pending = inFlight[key]
        if (pending != null) {
            missCount++
            return Lookup.Join(pending)
        }
        missCount++
        loadCount++
        val deferred = CompletableDeferred<LoadedTile>()
        inFlight[key] = deferred
        return Lookup.Lead(deferred)
    }

    private suspend fun loadAndPublish(
        key: TileKey,
        deferred: CompletableDeferred<LoadedTile>,
        insertGuard: () -> Boolean,
        loader: suspend () -> DecodedTile,
    ): LoadedTile {
        val tile = try {
            loader()
        } catch (error: Throwable) {
            synchronized(lock) { inFlight.remove(key) }
            deferred.completeExceptionally(error)
            throw error
        }
        try {
            currentCoroutineContext().ensureActive()
        } catch (cancel: CancellationException) {
            synchronized(lock) { inFlight.remove(key) }
            deferred.completeExceptionally(cancel)
            tile.close()
            throw cancel
        }
        return publish(key, tile, deferred, insertGuard)
    }

    private fun publish(
        key: TileKey,
        tile: DecodedTile,
        deferred: CompletableDeferred<LoadedTile>,
        insertGuard: () -> Boolean,
    ): LoadedTile {
        val victims = mutableListOf<DecodedTile>()
        var rejected = false
        var resident = false
        synchronized(lock) {
            if (closed || !insertGuard()) {
                rejected = true
                inFlight.remove(key)
            } else {
                val weight = tile.outputReservation.byteCount
                val iterator = entries.iterator()
                while (residentBytes + weight > capacityBytes && iterator.hasNext()) {
                    val eldest = iterator.next()
                    if ((pins[eldest.key] ?: 0) > 0) continue
                    iterator.remove()
                    residentBytes -= eldest.value.weight
                    evictionCount++
                    victims += eldest.value.tile
                }
                resident = residentBytes + weight <= capacityBytes
                if (resident) {
                    tile.adoptAsCacheResident()
                    entries[key] = Entry(tile, weight)
                    residentBytes = Math.addExact(residentBytes, weight)
                    if ((pins[key] ?: 0) > 0) pinnedBytes = Math.addExact(pinnedBytes, weight)
                    if (residentBytes > highWaterBytes) highWaterBytes = residentBytes
                }
                inFlight.remove(key)
            }
        }
        victims.forEach { it.close() }
        if (rejected) {
            tile.close()
            val failure = if (closed) {
                IllegalStateException(CLOSED_MESSAGE)
            } else {
                CancellationException("load superseded before cache insertion")
            }
            deferred.completeExceptionally(failure)
            throw failure
        }
        val loaded = LoadedTile(tile, resident)
        deferred.complete(loaded)
        return loaded
    }

    private fun invalidateWhere(predicate: (TileKey) -> Boolean) {
        val victims = synchronized(lock) {
            if (closed) return
            // Explicit invalidation also removes pinned entries and drops their pin counts, so
            // pinned-byte accounting must read the pin map before the count is removed.
            evictWhereLocked { key ->
                val matches = predicate(key)
                if (matches) pins.remove(key)
                matches
            }
        }
        victims.forEach { it.close() }
    }

    /** Removes matching entries and returns their tiles; pinned-byte accounting stays consistent. */
    private fun evictWhereLocked(predicate: (TileKey) -> Boolean): List<DecodedTile> {
        val victims = mutableListOf<DecodedTile>()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val eldest = iterator.next()
            // Read pin state before the predicate runs: invalidation predicates drop pin counts.
            val wasPinned = (pins[eldest.key] ?: 0) > 0
            if (!predicate(eldest.key)) continue
            iterator.remove()
            residentBytes -= eldest.value.weight
            if (wasPinned) pinnedBytes -= eldest.value.weight
            evictionCount++
            victims += eldest.value.tile
        }
        return victims
    }

    private fun checkOpenLocked() {
        if (closed) throw IllegalStateException(CLOSED_MESSAGE)
    }

    /** Moves [key] to the most-recently-used position; only called on cache hits. */
    private fun touchLocked(key: TileKey) {
        val entry = entries.remove(key) ?: return
        entries[key] = entry
    }

    private class Entry(
        val tile: DecodedTile,
        val weight: Long,
    )

    private sealed interface Lookup {
        data class Hit(val tile: DecodedTile) : Lookup

        data class Join(val deferred: CompletableDeferred<LoadedTile>) : Lookup

        data class Lead(val deferred: CompletableDeferred<LoadedTile>) : Lookup
    }

    private inner class PinHandle(
        override val key: TileKey,
    ) : TilePin {
        private var active = true

        override fun close() {
            synchronized(lock) {
                if (!active) return
                active = false
                val count = pins[key] ?: return
                if (count <= 1) {
                    pins.remove(key)
                    entries[key]?.let { pinnedBytes -= it.weight }
                } else {
                    pins[key] = count - 1
                }
            }
        }
    }

    private companion object {
        const val CLOSED_MESSAGE = "WeightedTileCache is closed"
    }
}
