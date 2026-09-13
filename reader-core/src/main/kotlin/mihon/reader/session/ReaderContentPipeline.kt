package mihon.reader.session

import mihon.reader.cache.CacheMetrics
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.source.ChapterSource
import mihon.reader.source.ReaderChapterAsset

data class ReaderContentPosition(
    val selectedIndex: Int,
    val visiblePages: List<PageId>,
    val mode: ReadingMode,
    val direction: NavigationDirection,
    val foreground: Boolean,
    val contentVisible: Boolean,
)

data class AdjacentChapterWarmup(
    val asset: ReaderChapterAsset,
    val preloadFirstUnit: Boolean,
)

/**
 * Session-scoped image pipeline. Implementations may coordinate decoded cache, background
 * preloading, and an adjacent chapter, while [DefaultReaderSession] remains the source owner.
 */
interface ReaderContentPipeline : AutoCloseable {
    suspend fun open(source: ChapterSource): List<PageDescriptor>

    fun updatePosition(position: ReaderContentPosition)

    suspend fun loadVisible(pageId: PageId): AutoCloseable?

    suspend fun retry(pageId: PageId): AutoCloseable?

    fun metrics(): CacheMetrics

    fun warmAdjacent(request: AdjacentChapterWarmup?)

    fun closeChapter()
}

internal class CallbackReaderContentPipeline(
    private val visibleContent: suspend (PageId) -> AutoCloseable?,
) : ReaderContentPipeline {
    override suspend fun open(source: ChapterSource): List<PageDescriptor> = source.pages()

    override fun updatePosition(position: ReaderContentPosition) = Unit

    override suspend fun loadVisible(pageId: PageId): AutoCloseable? = visibleContent(pageId)

    override suspend fun retry(pageId: PageId): AutoCloseable? = visibleContent(pageId)

    override fun metrics(): CacheMetrics = EMPTY_CONTENT_CACHE_METRICS

    override fun warmAdjacent(request: AdjacentChapterWarmup?) = Unit

    override fun closeChapter() = Unit

    override fun close() = Unit
}

private val EMPTY_CONTENT_CACHE_METRICS = CacheMetrics(0L, 0L, 0, 0L, 0L, 0L, 0L, 0L)
