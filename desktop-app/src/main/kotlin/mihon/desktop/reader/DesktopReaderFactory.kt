package mihon.desktop.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ImageIoPageDecoder
import mihon.reader.image.PageDecoder
import mihon.reader.memory.BoundedReaderMemoryBudget
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
        )
        return TrackedReaderSession(session, sessionScope).also { tracked ->
            synchronized(lock) {
                check(acceptingSessions) { "reader runtime is shutting down" }
                sessions += tracked
            }
        }
    }

    fun nextGeneration(): Long = generationSource.nextGeneration()

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

private fun Throwable?.append(error: Throwable): Throwable = this?.also { it.addSuppressed(error) } ?: error
