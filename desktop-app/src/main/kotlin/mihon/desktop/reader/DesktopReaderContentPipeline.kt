package mihon.desktop.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.ui.reader.ComposeTileBridge
import mihon.desktop.ui.reader.IntrinsicPageSizeCache
import mihon.desktop.ui.reader.SmartBorderCropper
import mihon.reader.cache.CacheMetrics
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.prefetch.PageLoadCoordinator
import mihon.reader.session.AdjacentChapterWarmup
import mihon.reader.session.ReaderContentPipeline
import mihon.reader.session.ReaderContentPosition
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import java.util.concurrent.atomic.AtomicBoolean

/** The session-owned production bridge from reader state to bounded decode/cache coordination. */
class DesktopReaderContentPipeline(
    private val decoder: PageDecoder,
    private val cache: WeightedTileCache,
    private val scope: CoroutineScope,
    private val sourceFactory: ChapterSourceFactory,
    private val maxFullPagePixels: Long = DEFAULT_MAX_FULL_PAGE_PIXELS,
) : ReaderContentPipeline {
    private val lock = Any()
    private var coordinator: PageLoadCoordinator? = null
    private var warmupRequest: AdjacentChapterWarmup? = null
    private var warmupJob: Job? = null
    private val closed = AtomicBoolean(false)

    override suspend fun open(source: ChapterSource): List<PageDescriptor> {
        check(!closed.get()) { "reader content pipeline is closed" }
        closeChapter()
        val next = newCoordinator()
        synchronized(lock) { coordinator = next }
        return try {
            next.openChapter(source)
        } catch (failure: Throwable) {
            synchronized(lock) { if (coordinator === next) coordinator = null }
            next.close()
            throw failure
        }
    }

    override fun updatePosition(position: ReaderContentPosition) {
        val active = activeCoordinator()
        if (position.foreground && position.contentVisible) {
            active.updatePosition(
                selectedIndex = position.selectedIndex,
                visiblePages = position.visiblePages,
                mode = position.mode,
                direction = position.direction,
            )
        } else {
            active.cancelPrefetch()
        }
    }

    override suspend fun loadVisible(pageId: PageId): AutoCloseable? {
        val loaded = loadTile(pageId, frameIndex = 0)
        return if (loaded.tile.resident) null else AutoCloseable { loaded.tile.tile.close() }
    }

    override suspend fun retry(pageId: PageId): AutoCloseable? {
        val loaded = activeCoordinator().retry(pageId)
        return if (loaded.resident) null else AutoCloseable { loaded.tile.close() }
    }

    override fun metrics(): CacheMetrics = cache.metrics

    override fun warmAdjacent(request: AdjacentChapterWarmup?) {
        if (closed.get()) return
        val prior = synchronized(lock) {
            if (warmupRequest == request) return
            warmupRequest = request
            val old = warmupJob
            warmupJob = request?.let { target ->
                scope.launch { runWarmup(target) }
            }
            old
        }
        prior?.cancel()
    }

    override fun closeChapter() {
        val (active, warm) = synchronized(lock) {
            val oldCoordinator = coordinator
            coordinator = null
            val oldWarmup = warmupJob
            warmupJob = null
            warmupRequest = null
            oldCoordinator to oldWarmup
        }
        warm?.cancel()
        active?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeChapter()
    }

    internal suspend fun loadTile(pageId: PageId, frameIndex: Int): LoadedDesktopTile {
        val active = activeCoordinator()
        val loaded = active.loadVisible(pageId, frameIndex)
        val metadata = requireNotNull(active.loadedMetadata(pageId)) { "loaded page metadata is missing" }
        return LoadedDesktopTile(loaded, metadata)
    }

    private fun activeCoordinator(): PageLoadCoordinator = synchronized(lock) {
        check(!closed.get()) { "reader content pipeline is closed" }
        checkNotNull(coordinator) { "reader content pipeline has no open chapter" }
    }

    private fun newCoordinator() = PageLoadCoordinator(
        decoder = decoder,
        cache = cache,
        scope = scope,
        maxFullPagePixels = maxFullPagePixels,
    )

    private suspend fun runWarmup(request: AdjacentChapterWarmup) {
        val source = try {
            sourceFactory.create(request.asset)
        } catch (_: Exception) {
            return
        }
        try {
            if (request.preloadFirstUnit) {
                val warmer = newCoordinator()
                try {
                    val pages = warmer.openChapter(source)
                    pages.firstOrNull()?.let { page -> warmer.loadVisible(page.id) }
                } finally {
                    warmer.close()
                }
            } else {
                source.pages()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Adjacent warmup is opportunistic and must never fail the current visible page.
        } finally {
            source.close()
        }
    }

    companion object {
        const val DEFAULT_MAX_FULL_PAGE_PIXELS = 4L * 1024L * 1024L
    }
}

internal data class LoadedDesktopTile(
    val tile: WeightedTileCache.LoadedTile,
    val metadata: ImageMetadata,
)

/** Compose-facing content handle backed by the same coordinator used by its reader session. */
class DesktopReaderContent internal constructor(
    private val pipeline: DesktopReaderContentPipeline,
    private val bridge: ComposeTileBridge,
    val pageSizes: IntrinsicPageSizeCache,
) {
    suspend fun loadFrame(
        pageId: PageId,
        frameIndex: Int,
        cropBorders: Boolean = false,
    ): DesktopReaderPageFrame = withContext(Dispatchers.IO) {
        val loaded = pipeline.loadTile(pageId, frameIndex)
        try {
            val imageToBridge = if (cropBorders) {
                SmartBorderCropper.crop(loaded.tile.tile.image)
            } else {
                loaded.tile.tile.image
            }
            val wasCropped = imageToBridge.width != loaded.tile.tile.image.width ||
                imageToBridge.height != loaded.tile.tile.image.height
            val sourceKey = loaded.tile.tile.key
            val bridgeKey = if (wasCropped) {
                sourceKey.copy(bounds = IntRect(1, 1, loaded.metadata.width, loaded.metadata.height))
            } else {
                sourceKey
            }
            val displayMetadata = if (wasCropped) {
                loaded.metadata.copy(
                    width = Math.multiplyExact(imageToBridge.width, sourceKey.sampleSize),
                    height = Math.multiplyExact(imageToBridge.height, sourceKey.sampleSize),
                )
            } else {
                loaded.metadata
            }
            pageSizes.record(pageId, displayMetadata)
            DesktopReaderPageFrame(bridge.acquire(bridgeKey, imageToBridge), displayMetadata)
        } finally {
            if (!loaded.tile.resident) loaded.tile.tile.close()
        }
    }
}

data class DesktopReaderPageFrame(
    val tile: ComposeTileBridge.BridgeTile,
    val metadata: ImageMetadata,
)

data class DesktopReaderHandle(
    val session: mihon.reader.session.ReaderSession,
    val content: DesktopReaderContent,
)
