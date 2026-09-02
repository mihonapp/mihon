package mihon.reader.prefetch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mihon.reader.cache.CacheMetrics
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.DecodedTile
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.source.ChapterSource
import mihon.reader.source.ReaderFailure
import java.io.Closeable

/**
 * Coordinates page loading on top of [WeightedTileCache]: visible-page priority, mode/direction
 * aware prefetch ([PrefetchPolicy]), single-flight per tile, typed error memoization, and
 * deterministic cancellation.
 *
 * - [loadVisible]/[loadRegion]/[retry] run the load pipeline inline in the caller's coroutine so
 *   caller cancellation aborts the work at the next checkpoint; prefetch runs in [scope] jobs
 *   that first yield and then wait for visible demand to drain, so visible work always wins.
 * - Every decode request path checks cancellation before opening input, after probe, after
 *   decode, and before cache insertion (the last two inside the cache's single-flight leader).
 * - GIF region decode ignores `sampleSize` and always returns full-resolution crops (Task 4
 *   constraint), so animated pages are always requested at `sampleSize = 1`; anything else would
 *   double-cache identical rasters under different keys and misstate tile weights.
 * - A page failure is memoized against the chapter asset modification tuple (sizeBytes,
 *   modifiedAt) and served without touching the source until [retry] is called or the chapter
 *   is reopened with a changed tuple. Cancellation is never memoized.
 * - [retry] invalidates every cached tile/frame of the page (even pinned ones) and starts a
 *   fresh single flight.
 *
 * Mutating calls ([openChapter], [updatePosition], [retry]) are expected from the session's
 * serial context. The coordinator does not own sources or the cache: it never closes them.
 * Returned tiles are borrowed; a [WeightedTileCache.LoadedTile] with `resident = false` is owned
 * by the caller and must be closed by it (the coordinator closes any still tracked at chapter
 * end as a backstop).
 */
class PageLoadCoordinator(
    private val decoder: PageDecoder,
    private val cache: WeightedTileCache,
    scope: CoroutineScope,
) : Closeable {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + job)

    private val lock = Any()
    private val flights = HashMap<FlightKey, Flight>()
    private val metadata = HashMap<PageId, ImageMetadata>()
    private val pageErrors = HashMap<PageId, RecordedError>()
    private val visiblePins = HashMap<TileKey, WeightedTileCache.TilePin>()
    private val visibleDemand = HashMap<PageId, Int>()
    private val uncachedTiles = ArrayList<DecodedTile>()
    private var idleSignal = CompletableDeferred(Unit)
    private var chapterSource: ChapterSource? = null
    private var chapterPages = listOf<PageDescriptor>()
    private var chapterPageIds = emptySet<PageId>()
    private var prefetchSet = emptySet<PageId>()
    private var chapterEpoch = 0
    private var closed = false

    val cacheMetrics: CacheMetrics
        get() = cache.metrics

    /**
     * Switches the coordinator to [source]: cancels every in-flight flight, releases visible
     * pins, evicts the previous chapter's unpinned cache entries, and clears memoized errors and
     * probed metadata — unless the chapter id and the asset modification tuple are unchanged, in
     * which case both survive the reopen.
     */
    suspend fun openChapter(source: ChapterSource): List<PageDescriptor> {
        val drained = synchronized(lock) {
            checkOpenLocked()
            chapterEpoch++
            val previous = drainStateLocked()
            val previousAsset = chapterSource?.asset
            val sameChapter = previousAsset?.chapterId == source.asset.chapterId
            val sameTuple = previousAsset != null &&
                previousAsset.sizeBytes == source.asset.sizeBytes &&
                previousAsset.modifiedAt == source.asset.modifiedAt
            if (!(sameChapter && sameTuple)) {
                pageErrors.clear()
                metadata.clear()
            }
            chapterSource = source
            previous
        }
        drained.cancelAndClose()
        drained.chapterIds.forEach { cache.invalidateChapter(it) }

        val pages = source.pages()
        synchronized(lock) {
            if (chapterSource !== source) throw CancellationException("chapter superseded by a newer open")
            chapterPages = pages
            chapterPageIds = pages.mapTo(HashSet()) { it.id }
        }
        return pages
    }

    /**
     * Records the visible pages and recomputes the prefetch window: pins for pages that left the
     * visible set are released, flights for pages outside the new window are cancelled, and new
     * prefetch flights launch (visible demand first, then two units ahead, one behind).
     */
    fun updatePosition(
        selectedIndex: Int,
        visiblePages: List<PageId>,
        mode: ReadingMode,
        direction: NavigationDirection,
    ) {
        val pinsToClose = mutableListOf<WeightedTileCache.TilePin>()
        val toCancel = mutableListOf<Flight>()
        val toLaunch = mutableListOf<Pair<FlightKey.FullPage, Flight>>()
        synchronized(lock) {
            checkOpenLocked()
            val pages = chapterPages
            require(pages.isNotEmpty()) { "no chapter is open" }
            require(selectedIndex in pages.indices) { "selectedIndex $selectedIndex out of ${pages.size} pages" }
            visiblePages.forEach { page ->
                require(page in chapterPageIds) { "visible page is not part of the open chapter" }
            }

            val visibleSet = visiblePages.toSet()
            val stalePins = visiblePins.keys.filter { it.pageId !in visibleSet }
            stalePins.forEach { pinsToClose += visiblePins.remove(it)!! }

            val wanted = PrefetchPolicy.plan(pages.size, selectedIndex, mode, direction)
                .map { pages[it].id }
                .filter { it !in visibleSet }
                .toSet()
            prefetchSet = wanted

            val iterator = flights.entries.iterator()
            while (iterator.hasNext()) {
                val (key, flight) = iterator.next()
                when {
                    flight.cancelled -> iterator.remove()
                    flight.prefetchWanted && key.pageId !in wanted -> {
                        flight.prefetchWanted = false
                        if (flight.visibleWaiters == 0) {
                            flight.cancelled = true
                            iterator.remove()
                            toCancel += flight
                        }
                    }
                }
            }
            for (pageId in wanted) {
                val frame = FlightKey.FullPage(pageId, frameIndex = 0)
                val existing = flights[frame]
                if (existing != null) {
                    existing.prefetchWanted = true
                } else {
                    val flight = Flight(prefetchWanted = true)
                    flights[frame] = flight
                    toLaunch += frame to flight
                }
            }
        }
        pinsToClose.forEach { it.close() }
        toCancel.forEach { it.cancel("prefetch superseded by a new position") }
        toLaunch.forEach { (frame, flight) -> launchPrefetch(frame, flight) }
    }

    /** Loads the full [frameIndex] frame of [pageId] with visible priority, pinning the tile. */
    suspend fun loadVisible(pageId: PageId, frameIndex: Int = 0): WeightedTileCache.LoadedTile {
        require(frameIndex >= 0) { "frameIndex must not be negative" }
        return runVisibleFlight(FlightKey.FullPage(pageId, frameIndex))
    }

    /**
     * Loads a region tile of [pageId]. Animated pages are always requested at `sampleSize = 1`
     * regardless of [sampleSize] because GIF region decode ignores sampling (see class KDoc).
     */
    suspend fun loadRegion(
        pageId: PageId,
        bounds: IntRect,
        frameIndex: Int = 0,
        sampleSize: Int = 1,
    ): WeightedTileCache.LoadedTile {
        require(frameIndex >= 0) { "frameIndex must not be negative" }
        require(sampleSize > 0) { "sampleSize must be positive" }
        return runVisibleFlight(FlightKey.Region(pageId, bounds, frameIndex, sampleSize))
    }

    /**
     * Drops the memoized error for [pageId], invalidates every cached tile/frame of the page
     * (even pinned ones), cancels its in-flight flights, and starts a fresh single flight.
     */
    suspend fun retry(pageId: PageId, frameIndex: Int = 0): WeightedTileCache.LoadedTile {
        val (staleFlights, stalePins) = synchronized(lock) {
            checkOpenLocked()
            pageErrors.remove(pageId)
            metadata.remove(pageId)
            val staleF = flights.filterKeys { it.pageId == pageId }
            staleF.values.forEach { it.cancelled = true }
            staleF.keys.forEach { flights.remove(it) }
            val staleP = visiblePins.filterKeys { it.pageId == pageId }
            staleP.keys.forEach { visiblePins.remove(it) }
            staleF.values.toList() to staleP.values.toList()
        }
        staleFlights.forEach { it.cancel("explicit retry") }
        stalePins.forEach { it.close() }
        cache.invalidatePage(pageId)
        val staleUncached = synchronized(lock) {
            val matches = uncachedTiles.filter { it.key.pageId == pageId }
            uncachedTiles.removeAll(matches)
            matches
        }
        staleUncached.forEach { it.close() }
        return loadVisible(pageId, frameIndex)
    }

    override fun close() {
        val drained = synchronized(lock) {
            if (closed) return
            closed = true
            chapterEpoch++
            drainStateLocked()
        }
        drained.cancelAndClose()
        job.cancel()
    }

    private suspend fun runVisibleFlight(key: FlightKey): WeightedTileCache.LoadedTile {
        demandUp(key.pageId)
        try {
            val epoch = synchronized(lock) {
                checkOpenLocked()
                if (chapterSource == null) throw ReaderFailure.SourceClosed()
                chapterEpoch
            }
            while (true) {
                val (flight, lead) = synchronized(lock) {
                    checkOpenLocked()
                    if (chapterEpoch != epoch) throw CancellationException("chapter changed")
                    memoizedFailureLocked(key.pageId)?.let { throw it }
                    val existing = flights[key]
                    if (existing != null && !existing.cancelled) {
                        existing.visibleWaiters++
                        existing.visible = true
                        existing to false
                    } else {
                        val fresh = Flight(
                            visible = true,
                            prefetchWanted = key is FlightKey.FullPage &&
                                key.frameIndex == 0 &&
                                key.pageId in prefetchSet,
                        )
                        fresh.visibleWaiters = 1
                        flights[key] = fresh
                        fresh to true
                    }
                }
                if (lead) runFlight(key, flight)
                try {
                    return flight.deferred.await()
                } catch (cancel: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    synchronized(lock) {
                        if (chapterEpoch != epoch) throw CancellationException("chapter changed")
                        if (flights[key] === flight && flight.cancelled) flights.remove(key)
                    }
                    continue
                } finally {
                    synchronized(lock) { flight.visibleWaiters-- }
                }
            }
        } finally {
            demandDown(key.pageId)
        }
    }

    private suspend fun runFlight(key: FlightKey, flight: Flight) {
        val pageId = key.pageId
        try {
            val source = synchronized(lock) { chapterSource } ?: throw ReaderFailure.SourceClosed()
            synchronized(lock) { memoizedFailureLocked(pageId) }?.let { throw it }
            val meta = metadataFor(source, flight, pageId)
            val loaded = when (key) {
                is FlightKey.FullPage -> loadFullPage(source, flight, key, meta)
                is FlightKey.Region -> loadRegionTile(source, flight, key, meta)
            }
            if (!loaded.resident) {
                synchronized(lock) { uncachedTiles += loaded.tile }
            }
            flight.deferred.complete(loaded)
        } catch (error: Throwable) {
            if (error !is CancellationException && error !is ReaderFailure.SourceClosed) {
                memoize(pageId, error)
            }
            flight.deferred.completeExceptionally(error)
        } finally {
            synchronized(lock) { if (flights[key] === flight) flights.remove(key) }
        }
    }

    private suspend fun loadFullPage(
        source: ChapterSource,
        flight: Flight,
        key: FlightKey.FullPage,
        meta: ImageMetadata,
    ): WeightedTileCache.LoadedTile {
        val pageId = key.pageId
        require(meta.isAnimated || key.frameIndex == 0) { "static pages expose only frame zero" }
        val frameId = if (meta.isAnimated) FrameId(pageId, key.frameIndex) else null
        // Full-page tiles are inherently full resolution, i.e. sampleSize = 1.
        val tileKey = TileKey(pageId, frameId, IntRect(0, 0, meta.width, meta.height), sampleSize = 1)
        synchronized(lock) { if (flight.visible) pinVisibleLocked(tileKey) }
        return cache.getOrLoad(tileKey, insertGuard = { !flight.cancelled }) {
            flight.checkpoint()
            val tile = decoder.decodeFull(source.open(pageId), meta, FrameId(pageId, key.frameIndex))
            try {
                flight.checkpoint()
            } catch (cancel: CancellationException) {
                tile.close()
                throw cancel
            }
            tile
        }
    }

    private suspend fun loadRegionTile(
        source: ChapterSource,
        flight: Flight,
        key: FlightKey.Region,
        meta: ImageMetadata,
    ): WeightedTileCache.LoadedTile {
        val pageId = key.pageId
        // GIF region decode ignores sampleSize (Task 4): force 1 for animated pages so the same
        // raster is never cached under two keys and tile weights stay honest.
        val sampleSize = if (meta.isAnimated) 1 else key.sampleSize
        val frameId = if (meta.isAnimated) FrameId(pageId, key.frameIndex) else null
        val tileKey = TileKey(pageId, frameId, key.bounds, sampleSize)
        val request = TileRequest(
            key = tileKey,
            targetWidth = (key.bounds.width + sampleSize - 1) / sampleSize,
            targetHeight = (key.bounds.height + sampleSize - 1) / sampleSize,
        )
        synchronized(lock) { if (flight.visible) pinVisibleLocked(tileKey) }
        return cache.getOrLoad(tileKey, insertGuard = { !flight.cancelled }) {
            flight.checkpoint()
            val tile = decoder.decodeRegion(source.open(pageId), meta, request)
            try {
                flight.checkpoint()
            } catch (cancel: CancellationException) {
                tile.close()
                throw cancel
            }
            tile
        }
    }

    private suspend fun metadataFor(source: ChapterSource, flight: Flight, pageId: PageId): ImageMetadata {
        synchronized(lock) { metadata[pageId] }?.let { return it }
        flight.checkpoint()
        val probed = decoder.probe(source.open(pageId))
        flight.checkpoint()
        synchronized(lock) { metadata[pageId] = probed }
        return probed
    }

    private fun launchPrefetch(frame: FlightKey.FullPage, flight: Flight) {
        flight.job = scope.launch {
            // Yield once so visible requests queued behind this job can register demand first,
            // then wait out any active visible load before touching the source or the ledger.
            yield()
            awaitVisibleIdle()
            if (!flight.cancelled) runFlight(frame, flight)
        }
    }

    private suspend fun awaitVisibleIdle() {
        while (true) {
            val signal = synchronized(lock) {
                if (visibleDemand.isEmpty()) return
                idleSignal
            }
            signal.await()
        }
    }

    private fun demandUp(pageId: PageId) {
        synchronized(lock) {
            if (visibleDemand.isEmpty()) idleSignal = CompletableDeferred()
            visibleDemand.merge(pageId, 1, Int::plus)
        }
    }

    private fun demandDown(pageId: PageId) {
        synchronized(lock) {
            val count = (visibleDemand[pageId] ?: 0) - 1
            if (count <= 0) visibleDemand.remove(pageId) else visibleDemand[pageId] = count
            if (visibleDemand.isEmpty()) idleSignal.complete(Unit)
        }
    }

    private fun pinVisibleLocked(key: TileKey) {
        if (key !in visiblePins) visiblePins[key] = cache.pin(key)
    }

    private fun memoizedFailureLocked(pageId: PageId): Throwable? {
        val recorded = pageErrors[pageId] ?: return null
        val asset = chapterSource?.asset ?: return null
        return if (recorded.sizeBytes == asset.sizeBytes && recorded.modifiedAt == asset.modifiedAt) {
            recorded.failure
        } else {
            pageErrors.remove(pageId)
            null
        }
    }

    private fun memoize(pageId: PageId, failure: Throwable) {
        synchronized(lock) {
            val asset = chapterSource?.asset ?: return
            pageErrors[pageId] = RecordedError(failure, asset.sizeBytes, asset.modifiedAt)
        }
    }

    private fun checkOpenLocked() {
        if (closed) throw IllegalStateException("PageLoadCoordinator is closed")
    }

    /** Collects and clears all per-chapter state; callers cancel/close the results outside the lock. */
    private fun drainStateLocked(): DrainedState {
        val drained = DrainedState(
            flights = flights.values.toList(),
            pins = visiblePins.values.toList(),
            uncached = uncachedTiles.toList(),
            chapterIds = buildSet {
                chapterPages.mapTo(this) { it.id.chapterId }
                chapterSource?.let { add(it.asset.chapterId.toString()) }
            },
        )
        flights.values.forEach { it.cancelled = true }
        flights.clear()
        visiblePins.clear()
        uncachedTiles.clear()
        prefetchSet = emptySet()
        chapterPages = emptyList()
        chapterPageIds = emptySet()
        return drained
    }

    private class DrainedState(
        val flights: List<Flight>,
        val pins: List<WeightedTileCache.TilePin>,
        val uncached: List<DecodedTile>,
        val chapterIds: Set<String>,
    ) {
        fun cancelAndClose() {
            flights.forEach { it.cancel("chapter state drained") }
            pins.forEach { it.close() }
            uncached.forEach { it.close() }
        }
    }

    private sealed interface FlightKey {
        val pageId: PageId

        data class FullPage(
            override val pageId: PageId,
            val frameIndex: Int,
        ) : FlightKey

        data class Region(
            override val pageId: PageId,
            val bounds: IntRect,
            val frameIndex: Int,
            val sampleSize: Int,
        ) : FlightKey
    }

    private class RecordedError(
        val failure: Throwable,
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    /** A single-flight load. [cancel] is idempotent and safe to call from any thread. */
    private class Flight(
        var prefetchWanted: Boolean,
        @Volatile var visible: Boolean = false,
    ) {
        val deferred = CompletableDeferred<WeightedTileCache.LoadedTile>()
        var job: Job? = null
        var visibleWaiters = 0

        @Volatile var cancelled = false

        suspend fun checkpoint() {
            currentCoroutineContext().ensureActive()
            if (cancelled) throw CancellationException("load superseded")
        }

        fun cancel(reason: String) {
            cancelled = true
            job?.cancel()
            deferred.completeExceptionally(CancellationException(reason))
        }
    }
}
