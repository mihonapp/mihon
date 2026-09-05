package mihon.desktop.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.ui.reader.ComposeTileBridge
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ImageIoPageDecoder
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.session.AtomicReaderGenerationSource
import mihon.reader.session.DefaultReaderSession
import mihon.reader.session.ReaderGenerationSource
import mihon.reader.session.ReaderSession
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.LocalChapterSourceFactory

/** Owns process-wide reader resources while giving each open reader its own coroutine lifetime. */
class DesktopReaderFactory(
    private val applicationScope: CoroutineScope,
    private val library: ReaderLibraryPort,
    private val settings: DesktopReaderSettingsStore,
) {
    val cache = WeightedTileCache()
    val memoryBudget = BoundedReaderMemoryBudget { cache.relievePressure() }
    val sourceFactory: ChapterSourceFactory = LocalChapterSourceFactory(memoryBudget)
    val decoder: PageDecoder = ImageIoPageDecoder(memoryBudget)
    val bridge = ComposeTileBridge()
    private val generationSource: ReaderGenerationSource = AtomicReaderGenerationSource()
    private val lock = Any()
    private val sessions = linkedSetOf<TrackedReaderSession>()
    private var acceptingSessions = true

    fun createSession(): ReaderSession {
        val sessionScope = synchronized(lock) {
            check(acceptingSessions) { "reader runtime is shutting down" }
            CoroutineScope(applicationScope.coroutineContext + SupervisorJob(applicationScope.coroutineContext[Job]))
        }
        val session = DefaultReaderSession(
            scope = sessionScope,
            catalog = library,
            sourceFactory = sourceFactory,
            progressSink = library,
            generationSource = generationSource,
            settings = settings.load().toCoreSettings(),
            visibleContent = { loadFrame(it, 0).tile },
        )
        return TrackedReaderSession(session, sessionScope).also { tracked ->
            synchronized(lock) {
                check(acceptingSessions) { "reader runtime is shutting down" }
                sessions += tracked
            }
        }
    }

    fun nextGeneration(): Long = generationSource.nextGeneration()

    data class PageFrame(val tile: ComposeTileBridge.BridgeTile, val metadata: ImageMetadata)

    /** Resolve every read through the secure local source, including chapter-boundary navigation. */
    suspend fun loadFrame(pageId: PageId, frameIndex: Int): PageFrame = withContext(Dispatchers.IO) {
        val asset = requireNotNull(library.chapterAsset(pageId.chapterId.toLong()))
        sourceFactory.create(asset).use { source ->
            val metadata = source.open(pageId).use { decoder.probe(it) }
            val frame = FrameId(pageId, frameIndex)
            // Bound the display copy as well as the decoder output for very tall pages.
            var sample = 1
            while ((metadata.width.toLong() / sample) * (metadata.height.toLong() / sample) > MAX_DISPLAY_PIXELS) {
                sample *= 2
            }
            val bounds = IntRect(0, 0, metadata.width, metadata.height)
            val key = TileKey(pageId, frame, bounds, sample)
            cache.pin(key).use {
                val loaded = cache.getOrLoad(key) {
                    source.open(pageId).use { input ->
                        if (sample == 1) {
                            decoder.decodeFull(input, metadata, frame)
                        } else {
                            decoder.decodeRegion(
                                input,
                                metadata,
                                TileRequest(
                                    key,
                                    (metadata.width + sample - 1) / sample,
                                    (metadata.height + sample - 1) / sample,
                                ),
                            )
                        }
                    }
                }
                try {
                    PageFrame(bridge.acquire(key, loaded.tile.image), metadata)
                } finally {
                    if (!loaded.resident) loaded.tile.close()
                }
            }
        }
    }

    suspend fun shutdown() {
        val active = synchronized(lock) {
            acceptingSessions = false
            sessions.toList()
        }
        var failure: Throwable? = null
        active.forEach { session ->
            try {
                session.closeAndFlush()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
        }
        failure?.let { throw it }
    }

    fun closeServices() {
        bridge.close()
        cache.close()
        memoryBudget.close()
        applicationScope.coroutineContext[Job]?.cancel()
    }

    private inner class TrackedReaderSession(
        private val delegate: ReaderSession,
        private val scope: CoroutineScope,
    ) : ReaderSession by delegate {
        override suspend fun closeAndFlush() {
            try {
                delegate.closeAndFlush()
            } finally {
                synchronized(lock) { sessions.remove(this) }
                scope.coroutineContext[Job]?.cancel()
            }
        }

        override fun cancelWithoutFlush() {
            delegate.cancelWithoutFlush()
            synchronized(lock) { sessions.remove(this) }
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

private const val MAX_DISPLAY_PIXELS = 4L * 1024L * 1024L

private fun Throwable?.append(error: Throwable): Throwable = this?.also { it.addSuppressed(error) } ?: error
