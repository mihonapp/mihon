package mihon.reader.session

import mihon.reader.cache.CacheMetrics
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode

sealed interface ReaderLoadState {
    data object Idle : ReaderLoadState
    data class Loading(val generation: Long) : ReaderLoadState {
        init {
            require(generation >= 0L) { "generation must not be negative" }
        }
    }
    data object Ready : ReaderLoadState
    data class Failed(val error: ReaderSessionError) : ReaderLoadState
    data object Closed : ReaderLoadState
}

data class ReaderSessionError(
    val code: ReaderErrorCode,
    val cause: Throwable? = null,
)

data class ReaderState(
    val loadState: ReaderLoadState = ReaderLoadState.Idle,
    val chapterId: Long? = null,
    val pages: List<PageDescriptor> = emptyList(),
    val selectedIndex: Int = 0,
    val viewportAnchor: ReaderPosition = ReaderPosition(0),
    val mode: ReadingMode = ReadingMode.SINGLE_LTR,
    val coverOffset: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FIT_WIDTH,
    val zoom: Float = 1f,
    val pan: ReaderPan = ReaderPan(0f, 0f),
    val viewport: ReaderViewport? = null,
    val visiblePages: List<PageId> = emptyList(),
    val visibleTileLeases: List<AutoCloseable> = emptyList(),
    val error: ReaderSessionError? = null,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val cacheMetrics: CacheMetrics = EMPTY_CACHE_METRICS,
    val foreground: Boolean = true,
    val contentVisible: Boolean = true,
) {
    init {
        require(selectedIndex >= 0) { "selectedIndex must not be negative" }
        require(selectedIndex < pages.size || pages.isEmpty()) { "selectedIndex must address an open page" }
        require(zoom.isFinite()) { "zoom must be finite" }
        require(zoom in ReaderLayout.MIN_ZOOM..ReaderLayout.MAX_ZOOM) { "zoom must be in reader bounds" }
        require(visiblePages.all { page -> pages.any { it.id == page } }) { "visible page is not in the chapter" }
    }

    fun reduce(action: ReaderAction): ReaderState = when (action) {
        is ReaderAction.ChangeMode -> copy(mode = action.mode)
        is ReaderAction.SetCoverOffset -> copy(coverOffset = action.enabled)
        is ReaderAction.SetScaleMode -> copy(scaleMode = action.scaleMode)
        is ReaderAction.SetZoom -> copy(zoom = ReaderLayout.clampZoom(action.zoom))
        is ReaderAction.SetPan -> copy(pan = action.pan)
        is ReaderAction.SetViewport -> copy(viewport = action.viewport)
        is ReaderAction.SelectPage -> {
            require(action.index in pages.indices) { "selected index ${action.index} is outside ${pages.size} pages" }
            copy(
                selectedIndex = action.index,
                viewportAnchor = ReaderPosition(action.index),
                visiblePages = listOf(pages[action.index].id),
                pan = if (action.index == selectedIndex) pan else ReaderPan(0f, 0f),
            )
        }
        is ReaderAction.SetViewportAnchor -> {
            require(action.position.pageIndex in pages.indices) { "anchor is outside ${pages.size} pages" }
            copy(
                selectedIndex = action.position.pageIndex,
                viewportAnchor = action.position,
                visiblePages = listOf(pages[action.position.pageIndex].id),
                pan = if (action.position.pageIndex == selectedIndex) pan else ReaderPan(0f, 0f),
            )
        }
        is ReaderAction.SetVisiblePages -> copy(visiblePages = action.pageIds.toList())
        is ReaderAction.SetForeground -> copy(foreground = action.foreground)
        is ReaderAction.SetContentVisible -> copy(contentVisible = action.visible)
        ReaderAction.Next,
        ReaderAction.Previous,
        -> this
    }

    companion object {
        fun ready(
            chapterId: Long,
            pages: List<PageDescriptor>,
            selectedIndex: Int,
            viewport: ReaderViewport? = null,
        ): ReaderState {
            require(chapterId >= 0L) { "chapterId must not be negative" }
            require(pages.isNotEmpty()) { "pages must not be empty" }
            return ReaderState(
                loadState = ReaderLoadState.Ready,
                chapterId = chapterId,
                pages = pages.toList(),
                selectedIndex = selectedIndex,
                viewportAnchor = ReaderPosition(selectedIndex),
                viewport = viewport,
                visiblePages = listOf(pages[selectedIndex].id),
            )
        }
    }
}

private val EMPTY_CACHE_METRICS = CacheMetrics(0L, 0L, 0, 0L, 0L, 0L, 0L, 0L)
