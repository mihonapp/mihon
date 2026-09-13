package mihon.reader.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderPosition
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import mihon.reader.source.ReaderFailure

/**
 * Session-owned chapter lifetime and durable position coordination. It deliberately has no UI,
 * persistence, or platform dependency: callers provide catalog, sources, and progress writing.
 */
class DefaultReaderSession(
    private val scope: CoroutineScope,
    private val catalog: ReaderChapterCatalog,
    private val sourceFactory: ChapterSourceFactory,
    private val progressSink: ReaderProgressSink,
    private val generationSource: ReaderGenerationSource,
    private val settings: ReaderSettings = ReaderSettings(),
    private val monotonicClock: ReaderMonotonicClock = ReaderMonotonicClock { System.nanoTime() / NANOS_PER_MILLI },
    private val epochMillis: () -> Long = System::currentTimeMillis,
    private val visibleContent: suspend (PageId) -> AutoCloseable? = { null },
    private val animationCoordinator: AnimationCoordinator? = null,
    private val invalidateContent: suspend (PageId) -> Unit = {},
    private val contentPipeline: ReaderContentPipeline = CallbackReaderContentPipeline(visibleContent),
) : ReaderSession {
    private val sessionLock = Mutex()
    private val _state = MutableStateFlow(
        ReaderState(
            mode = settings.mode,
            coverOffset = settings.coverOffset,
            scaleMode = settings.scaleMode,
            zoom = settings.zoom,
        ),
    )
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val progress = ProgressDebouncer(scope) { progressSink.record(it) }
    private var source: ChapterSource? = null
    private var visibleLease: AutoCloseable? = null
    private var visibleJob: Job? = null
    private var generation = -1L
    private var sequence = 0L
    private var pendingDurationMillis = 0L
    private var lastClockMillis: Long? = null
    private var navigationDirection = NavigationDirection.FORWARD
    private var contentPipelineClosed = false
    private var closed = false

    override suspend fun open(chapterId: Long) {
        require(chapterId >= 0L) { "chapterId must not be negative" }
        sessionLock.withLock {
            check(!closed) { "reader session is closed" }
            openLocked(chapterId, landAtEnd = false)
        }
    }

    override fun dispatch(action: ReaderAction) {
        scope.launch {
            sessionLock.withLock {
                if (closed) return@withLock
                when (action) {
                    ReaderAction.Next -> moveLocked(NavigationDirection.FORWARD)
                    ReaderAction.Previous -> moveLocked(NavigationDirection.BACKWARD)
                    else -> {
                        accrueDurationLocked()
                        val previousIndex = _state.value.selectedIndex
                        _state.update { it.reduce(action) }
                        val updatedIndex = _state.value.selectedIndex
                        if (updatedIndex != previousIndex) {
                            navigationDirection = if (updatedIndex > previousIndex) {
                                NavigationDirection.FORWARD
                            } else {
                                NavigationDirection.BACKWARD
                            }
                        }
                        when (action) {
                            is ReaderAction.SetForeground -> animationCoordinator?.setForeground(action.foreground)
                            is ReaderAction.SetContentVisible -> animationCoordinator?.setContentVisible(action.visible)
                            is ReaderAction.SelectPage,
                            is ReaderAction.SetViewportAnchor,
                            -> animationCoordinator?.cancelForPageOrChapterChange()
                            else -> Unit
                        }
                        if (
                            action is ReaderAction.SelectPage ||
                            action is ReaderAction.SetViewportAnchor ||
                            action is ReaderAction.SetVisiblePages ||
                            action is ReaderAction.ChangeMode ||
                            action is ReaderAction.SetForeground ||
                            action is ReaderAction.SetContentVisible
                        ) {
                            updateContentPositionLocked()
                        }
                        if (action is ReaderAction.SelectPage || action is ReaderAction.SetViewportAnchor) {
                            requestVisibleLocked()
                            submitProgressLocked()
                        }
                        resetClockLocked()
                    }
                }
            }
        }
    }

    override suspend fun retry(pageId: PageId) {
        sessionLock.withLock {
            val failedState = _state.value
            require(pageId in failedState.pages.map { it.id }) { "page is not in the open chapter" }
            visibleJob?.cancelAndJoin()
            visibleLease?.close()
            visibleLease = null
            invalidateContent(pageId)
            if (failedState.loadState is ReaderLoadState.Failed) {
                val chapterId = requireNotNull(failedState.chapterId) { "failed reader has no chapter" }
                val retryIndex = failedState.pages.indexOfFirst { it.id == pageId }
                openLocked(chapterId, landAtEnd = false)
                val reopened = _state.value
                if (reopened.loadState is ReaderLoadState.Ready && retryIndex in reopened.pages.indices &&
                    reopened.selectedIndex != retryIndex
                ) {
                    _state.value = reopened.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(retryIndex)))
                    requestVisibleLocked(pageId)
                }
                return@withLock
            }
            requestPipelineRetryLocked(pageId)
        }
    }

    override suspend fun flushProgress() {
        sessionLock.withLock {
            if (closed || _state.value.loadState !is ReaderLoadState.Ready) return
            submitProgressLocked()
            progress.flush()
        }
    }

    override suspend fun closeAndFlush() {
        sessionLock.withLock {
            if (closed) return
            if (_state.value.loadState is ReaderLoadState.Ready) submitProgressLocked()
            progress.closeAndFlush()
            closeChapterLocked()
            closeContentPipelineLocked()
            closed = true
            _state.value =
                _state.value.copy(
                    loadState = ReaderLoadState.Closed,
                    visiblePages = emptyList(),
                    visibleTileLeases = emptyList(),
                )
        }
    }

    override fun cancelWithoutFlush() {
        scope.launch {
            sessionLock.withLock {
                if (closed) return@withLock
                visibleJob?.cancel()
                progress.cancelWithoutFlush()
                closeChapterLocked()
                closeContentPipelineLocked()
                closed = true
                _state.value =
                    _state.value.copy(
                        loadState = ReaderLoadState.Closed,
                        visiblePages = emptyList(),
                        visibleTileLeases = emptyList(),
                    )
            }
        }
    }

    private suspend fun openLocked(chapterId: Long, landAtEnd: Boolean) {
        flushAndCloseChapterLocked()
        generation = generationSource.nextGeneration()
        sequence = 0L
        pendingDurationMillis = 0L
        lastClockMillis = null
        _state.value =
            _state.value.copy(loadState = ReaderLoadState.Loading(generation), error = null, visiblePages = emptyList())

        val asset = catalog.chapterAsset(chapterId)
        if (asset == null) {
            failLocked(ReaderErrorCode.SOURCE_UNAVAILABLE)
            return
        }
        val opened = try {
            sourceFactory.create(asset)
        } catch (failure: Throwable) {
            failLocked(ReaderErrorCode.SOURCE_UNAVAILABLE, failure)
            return
        }
        val pages = try {
            contentPipeline.open(opened)
        } catch (failure: Throwable) {
            contentPipeline.closeChapter()
            opened.close()
            failLocked(ReaderErrorCode.SOURCE_UNAVAILABLE, failure)
            return
        }
        if (pages.isEmpty()) {
            contentPipeline.closeChapter()
            opened.close()
            failLocked(ReaderErrorCode.EMPTY_CHAPTER, ReaderFailure.EmptyChapter())
            return
        }

        source = opened
        val selected = if (landAtEnd) {
            pages.lastIndex
        } else {
            asset.lastPageRead.coerceIn(
                0L,
                pages.lastIndex.toLong(),
            ).toInt()
        }
        val previous = catalog.adjacentReadableChapter(chapterId, ChapterDirection.PREVIOUS) != null
        val next = catalog.adjacentReadableChapter(chapterId, ChapterDirection.NEXT) != null
        _state.value = ReaderState.ready(chapterId, pages, selected, _state.value.viewport).copy(
            mode = _state.value.mode,
            coverOffset = _state.value.coverOffset,
            scaleMode = _state.value.scaleMode,
            zoom = _state.value.zoom,
            pan = _state.value.pan,
            hasPreviousChapter = previous,
            hasNextChapter = next,
            foreground = _state.value.foreground,
            contentVisible = _state.value.contentVisible,
        )
        navigationDirection = NavigationDirection.FORWARD
        updateContentPositionLocked()
        resetClockLocked()
        requestVisibleLocked()
    }

    private suspend fun moveLocked(direction: NavigationDirection) {
        val current = _state.value
        if (current.loadState !is ReaderLoadState.Ready) return
        accrueDurationLocked()
        val nextIndex = nextLogicalIndex(current, direction)
        if (nextIndex in current.pages.indices) {
            navigationDirection = direction
            _state.value = current.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(nextIndex)))
            updateContentPositionLocked()
            requestVisibleLocked()
            submitProgressLocked()
            resetClockLocked()
            return
        }
        submitProgressLocked()
        progress.flush()
        val adjacent = catalog.adjacentReadableChapter(
            requireNotNull(current.chapterId),
            if (direction == NavigationDirection.FORWARD) ChapterDirection.NEXT else ChapterDirection.PREVIOUS,
        ) ?: return
        openLocked(adjacent.chapterId, landAtEnd = direction == NavigationDirection.BACKWARD)
    }

    private suspend fun requestVisibleLocked(pageId: PageId = _state.value.visiblePages.single()) {
        visibleJob?.cancel()
        visibleLease?.close()
        visibleLease = null
        _state.update { it.copy(visibleTileLeases = emptyList()) }
        visibleJob = scope.launch {
            try {
                val lease = contentPipeline.loadVisible(pageId)
                sessionLock.withLock {
                    if (!closed && _state.value.visiblePages.singleOrNull() == pageId) {
                        visibleLease = lease
                        _state.update {
                            it.copy(
                                visibleTileLeases = listOfNotNull(lease),
                                cacheMetrics = contentPipeline.metrics(),
                            )
                        }
                    } else {
                        lease?.close()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                sessionLock.withLock { failLocked(ReaderErrorCode.PAGE_DECODE_FAILED, failure) }
            }
        }
    }

    private suspend fun submitProgressLocked() {
        val state = _state.value
        val chapterId = state.chapterId ?: return
        val page = if (ReaderLayoutPolicy.isContinuous(state)) state.viewportAnchor.pageIndex else state.selectedIndex
        val update = ReaderProgressUpdate(
            chapterId = chapterId,
            pageIndex = page.toLong(),
            completed = page == state.pages.lastIndex,
            lastReadEpochMillis = epochMillis().coerceAtLeast(0L),
            readDurationDeltaMillis = pendingDurationMillis,
            generation = generation,
            sequence = sequence++,
        )
        pendingDurationMillis = 0L
        progress.submit(update)
    }

    private fun accrueDurationLocked() {
        val now = monotonicClock.nowMillis()
        val prior = lastClockMillis
        lastClockMillis = now
        val state = _state.value
        if (prior == null || state.loadState !is ReaderLoadState.Ready || !state.foreground ||
            !state.contentVisible
        ) {
            return
        }
        pendingDurationMillis = (pendingDurationMillis + (now - prior).coerceIn(0L, MAX_DURATION_DELTA_MILLIS))
            .coerceAtMost(Long.MAX_VALUE)
    }

    private fun resetClockLocked() {
        lastClockMillis = monotonicClock.nowMillis()
    }

    private fun nextLogicalIndex(state: ReaderState, direction: NavigationDirection): Int {
        val step = if (direction == NavigationDirection.FORWARD) 1 else -1
        if (!state.mode.isDualPage) return state.selectedIndex + step
        return when {
            direction == NavigationDirection.FORWARD && state.coverOffset && state.selectedIndex == 0 -> 1
            direction == NavigationDirection.BACKWARD && state.coverOffset && state.selectedIndex == 1 -> 0
            else -> state.selectedIndex + step * 2
        }
    }

    private fun failLocked(code: ReaderErrorCode, cause: Throwable? = null) {
        closeChapterLocked()
        _state.value = _state.value.copy(
            loadState = ReaderLoadState.Failed(ReaderSessionError(code, cause)),
            error = ReaderSessionError(code, cause),
            visiblePages = emptyList(),
            visibleTileLeases = emptyList(),
        )
    }

    private suspend fun flushAndCloseChapterLocked() {
        if (_state.value.loadState is ReaderLoadState.Ready) {
            submitProgressLocked()
            progress.flush()
        }
        closeChapterLocked()
    }

    private fun closeChapterLocked() {
        animationCoordinator?.cancelForPageOrChapterChange()
        visibleJob?.cancel()
        visibleJob = null
        visibleLease?.close()
        visibleLease = null
        if (source != null) contentPipeline.closeChapter()
        source?.close()
        source = null
        contentPipeline.warmAdjacent(null)
        lastClockMillis = null
    }

    private fun updateContentPositionLocked() {
        val state = _state.value
        if (state.loadState !is ReaderLoadState.Ready || state.pages.isEmpty()) return
        contentPipeline.updatePosition(
            ReaderContentPosition(
                selectedIndex = state.selectedIndex,
                visiblePages = state.visiblePages,
                mode = state.mode,
                direction = navigationDirection,
                foreground = state.foreground,
                contentVisible = state.contentVisible,
            ),
        )
        val chapterId = requireNotNull(state.chapterId)
        val remainingPages = state.pages.size - state.selectedIndex
        val warmup = if (navigationDirection == NavigationDirection.FORWARD &&
            remainingPages <= NEXT_CHAPTER_WARMUP_PAGES
        ) {
            catalog.adjacentReadableChapter(chapterId, ChapterDirection.NEXT)?.let { adjacent ->
                AdjacentChapterWarmup(
                    asset = adjacent,
                    preloadFirstUnit = state.selectedIndex == state.pages.lastIndex,
                )
            }
        } else {
            null
        }
        contentPipeline.warmAdjacent(warmup)
        _state.update { it.copy(cacheMetrics = contentPipeline.metrics()) }
    }

    private fun requestPipelineRetryLocked(pageId: PageId) {
        visibleJob?.cancel()
        visibleLease?.close()
        visibleLease = null
        _state.update { it.copy(visibleTileLeases = emptyList()) }
        visibleJob = scope.launch {
            try {
                val lease = contentPipeline.retry(pageId)
                sessionLock.withLock {
                    if (!closed && _state.value.visiblePages.singleOrNull() == pageId) {
                        visibleLease = lease
                        _state.update {
                            it.copy(
                                visibleTileLeases = listOfNotNull(lease),
                                cacheMetrics = contentPipeline.metrics(),
                            )
                        }
                    } else {
                        lease?.close()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                sessionLock.withLock { failLocked(ReaderErrorCode.PAGE_DECODE_FAILED, failure) }
            }
        }
    }

    private fun closeContentPipelineLocked() {
        if (contentPipelineClosed) return
        contentPipelineClosed = true
        contentPipeline.close()
    }

    private object ReaderLayoutPolicy {
        fun isContinuous(state: ReaderState) = state.mode == mihon.reader.model.ReadingMode.VERTICAL ||
            state.mode == mihon.reader.model.ReadingMode.WEBTOON
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_DURATION_DELTA_MILLIS = 60_000L
        private const val NEXT_CHAPTER_WARMUP_PAGES = 5
    }
}
