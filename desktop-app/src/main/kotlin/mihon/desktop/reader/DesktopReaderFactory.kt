package mihon.desktop.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.reader.ReaderOnlineChapterCatalog
import mihon.desktop.reader.codec.PackagedCodecPageDecoder
import mihon.desktop.reader.codec.PackagedReaderCodec
import mihon.desktop.ui.reader.ComposeTileBridge
import mihon.desktop.ui.reader.IntrinsicPageSizeCache
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ApngPageDecoder
import mihon.reader.image.CompositePageDecoder
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
import mihon.reader.source.ReaderChapterCatalog
import java.io.File
import java.nio.file.Path

/** Owns process-wide reader resources while giving each open reader its own coroutine lifetime. */
class DesktopReaderFactory(
    private val applicationScope: CoroutineScope,
    private val library: ReaderLibraryPort,
    private val settings: DesktopReaderSettingsStore,
    onlineChapters: ReaderOnlineChapterCatalog? = library as? ReaderOnlineChapterCatalog,
    sourceManager: DesktopSourceManager? = null,
    networkHelper: DesktopNetworkHelper? = null,
    onlineCacheDir: File = File(System.getProperty("java.io.tmpdir"), "mihon-reader-online"),
    codecExecutable: Path = PackagedReaderCodec.executablePath(),
) {
    val cache = WeightedTileCache()
    val memoryBudget = BoundedReaderMemoryBudget { cache.relievePressure() }
    private val catalog: ReaderChapterCatalog = DesktopReaderCatalog(
        local = library,
        online = onlineChapters,
        onlineStorageRoot = onlineCacheDir.toPath().toAbsolutePath().normalize(),
    )
    val sourceFactory: ChapterSourceFactory = DesktopChapterSourceFactory(
        local = LocalChapterSourceFactory(memoryBudget),
        onlineChapters = onlineChapters,
        sourceManager = sourceManager,
        networkHelper = networkHelper,
        onlineCacheDir = onlineCacheDir,
    )
    val decoder: PageDecoder = CompositePageDecoder(
        imageIo = ImageIoPageDecoder(memoryBudget),
        packagedCodec = PackagedCodecPageDecoder(memoryBudget, codecExecutable),
        animatedPng = ApngPageDecoder(memoryBudget),
    )
    val bridge = ComposeTileBridge()

    /**
     * Intrinsic dimensions probed from each opened image, exposed for reader layout. The backing
     * cache is a small LRU so chapter/session close cannot retain an unbounded page map.
     */
    val pageSizes = IntrinsicPageSizeCache()
    private val generationSource: ReaderGenerationSource = AtomicReaderGenerationSource()
    private val lock = Any()
    private val sessions = linkedSetOf<TrackedReaderSession>()
    private var acceptingSessions = true

    fun createSession(isIncognito: Boolean = false): ReaderSession {
        val sessionScope = synchronized(lock) {
            check(acceptingSessions) { "reader runtime is shutting down" }
            CoroutineScope(applicationScope.coroutineContext + SupervisorJob(applicationScope.coroutineContext[Job]))
        }
        val effectiveSink: mihon.reader.session.ReaderProgressSink = if (isIncognito) {
            mihon.reader.session.ReaderProgressSink { mihon.reader.session.ProgressWriteResult.APPLIED }
        } else {
            library
        }
        val session = DefaultReaderSession(
            scope = sessionScope,
            catalog = catalog,
            sourceFactory = sourceFactory,
            progressSink = effectiveSink,
            generationSource = generationSource,
            settings = settings.load().toCoreSettings(),
            visibleContent = { loadFrame(it, 0).tile },
            invalidateContent = { pageId ->
                cache.invalidatePage(pageId)
                val asset = requireNotNull(catalog.chapterAsset(pageId.chapterId.toLong()))
                sourceFactory.create(asset).use { source ->
                    (source as? mihon.desktop.extension.OnlineChapterSource)?.invalidate(pageId)
                }
            },
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

    /** Resolve every read through the local/online chapter catalog, including boundary navigation. */
    suspend fun loadFrame(pageId: PageId, frameIndex: Int, cropBorders: Boolean = false): PageFrame =
        withContext(Dispatchers.IO) {
            val asset = requireNotNull(catalog.chapterAsset(pageId.chapterId.toLong()))
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
                        val imageToBridge = if (cropBorders) {
                            mihon.desktop.ui.reader.SmartBorderCropper.crop(loaded.tile.image)
                        } else {
                            loaded.tile.image
                        }
                        val wasCropped = imageToBridge.width != loaded.tile.image.width ||
                            imageToBridge.height != loaded.tile.image.height
                        val bridgeKey = if (wasCropped) {
                            key.copy(bounds = IntRect(1, 1, metadata.width, metadata.height))
                        } else {
                            key
                        }
                        val displayMetadata = if (wasCropped) {
                            metadata.copy(
                                width = Math.multiplyExact(imageToBridge.width, sample),
                                height = Math.multiplyExact(imageToBridge.height, sample),
                            )
                        } else {
                            metadata
                        }
                        pageSizes.record(pageId, displayMetadata)
                        PageFrame(bridge.acquire(bridgeKey, imageToBridge), displayMetadata)
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
        try {
            active.forEach { session ->
                try {
                    session.closeAndFlush()
                } catch (error: Throwable) {
                    failure = failure.append(error)
                }
            }
        } finally {
            pageSizes.clear()
        }
        failure?.let { throw it }
    }

    fun closeServices() {
        pageSizes.clear()
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
