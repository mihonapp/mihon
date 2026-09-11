package mihon.desktop.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.reader.image.ImageMetadata
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId

/** Intrinsic pixel dimensions of one reader page image. */
data class PageSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    companion object {
        /**
         * Portrait placeholder used when reader-core chapter sources only expose the 1x1
         * "not probed yet" descriptor. Keeping a plausible aspect ratio prevents the first
         * layout from rendering a square that is corrected a frame later.
         */
        val PLACEHOLDER = PageSize(width = 2, height = 3)
    }
}

/**
 * Reader-core chapter sources expand page lists before any image is decoded, so every page starts
 * as a 1x1 [PageDescriptor]. Replace that sentinel with a probed [PageSize] when available, or a
 * portrait placeholder so paged/continuous layout never treats an unknown page as square.
 */
fun PageDescriptor.withIntrinsicSize(intrinsicSize: PageSize?): PageDescriptor {
    val resolved = intrinsicSize ?: if (width == 1 && height == 1) PageSize.PLACEHOLDER else return this
    return copy(width = resolved.width, height = resolved.height)
}

/**
 * Bounded, observable LRU cache of intrinsic page sizes recorded by
 * [mihon.desktop.reader.DesktopReaderFactory.loadFrame]. The least recently used entry is evicted
 * once the bound is reached, so closing a chapter/session cannot retain an unbounded page-size map.
 */
class IntrinsicPageSizeCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val lock = Any()
    private val entries = LinkedHashMap<PageId, PageSize>(maxEntries, 0.75f, true)
    private val _sizes = MutableStateFlow<Map<PageId, PageSize>>(emptyMap())

    /** Latest bounded snapshot; collected by the reader UI to correct placeholder layouts. */
    val sizes: StateFlow<Map<PageId, PageSize>> = _sizes.asStateFlow()

    fun record(pageId: PageId, size: PageSize) {
        synchronized(lock) {
            entries[pageId] = size
            while (entries.size > maxEntries) {
                val oldest = entries.keys.first()
                entries.remove(oldest)
            }
            _sizes.value = entries.toMap()
        }
    }

    fun record(pageId: PageId, metadata: ImageMetadata): PageSize {
        val size = PageSize(metadata.width, metadata.height)
        record(pageId, size)
        return size
    }

    fun sizeOf(pageId: PageId): PageSize? = synchronized(lock) { entries[pageId] }

    fun snapshot(): Map<PageId, PageSize> = synchronized(lock) { entries.toMap() }

    fun clearChapter(chapterId: String) {
        synchronized(lock) {
            entries.keys.removeAll { it.chapterId == chapterId }
            _sizes.value = entries.toMap()
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            _sizes.value = emptyMap()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 128
    }
}

/** Receives probed page sizes so [ReaderScreen] can promote them into its layout state. */
fun interface ReaderPageSizeSink {
    fun onPageSize(pageId: PageId, size: PageSize)
}

/**
 * Provided by [ReaderScreen] and consumed by page content such as
 * [mihon.desktop.ui.reader.DecodedReaderPage]. Null means the page content is rendered outside the
 * reader shell (for example in isolated tests), where recording is simply skipped.
 */
val LocalReaderPageSizeSink = staticCompositionLocalOf<ReaderPageSizeSink?> { null }
