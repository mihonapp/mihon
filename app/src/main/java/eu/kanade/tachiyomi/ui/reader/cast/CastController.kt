package eu.kanade.tachiyomi.ui.reader.cast

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.core.content.getSystemService
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * App-scoped casting session.
 *
 * The reader is the single source of truth for the reading position: it reports pages and scroll
 * offsets through [onReaderPageSelected] / [onReaderScroll] and the cast targets (an external
 * display or the web receiver) mirror that position with their own presentation settings
 * (orientation, size, background...). Commands coming from a target, the remote sheet or the
 * notification are forwarded to the reader as [CastEvent]s.
 */
@Inject
@SingleIn(AppScope::class)
class CastController(
    private val context: Context,
    private val castPreferences: CastPreferences,
    private val chapterCache: ChapterCache,
) : CastPageSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutableState = MutableStateFlow(
        CastState(
            orientation = castPreferences.orientation.get(),
            scaleMode = castPreferences.scaleMode.get(),
            zoomPercent = castPreferences.zoomPercent.get(),
            stripWidthPercent = castPreferences.stripWidthPercent.get(),
            background = castPreferences.background.get(),
        ),
    )
    val state: StateFlow<CastState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<CastEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CastEvent> = mutableEvents.asSharedFlow()

    private val mutableDisplays = MutableStateFlow<List<CastDisplayInfo>>(emptyList())

    /** Presentation displays currently available (screen casting sessions, Miracast, HDMI...). */
    val displays: StateFlow<List<CastDisplayInfo>> = mutableDisplays.asStateFlow()

    private val mutableWebInfo = MutableStateFlow<CastWebInfo?>(null)

    /** Information about the running web receiver, null when it is not running. */
    val webInfo: StateFlow<CastWebInfo?> = mutableWebInfo.asStateFlow()

    val images = CastImageRepository(this)

    val preferences: CastPreferences
        get() = castPreferences

    @Volatile
    private var chapterPages: Map<Long, List<ReaderPage>> = emptyMap()

    private var displayTarget: CastDisplayTarget? = null
    private var webServer: CastWebServer? = null

    /** Wakes the single dimension-probe coroutine; conflated so bursts collapse into one pass. */
    private val dimensionProbeRequests = Channel<Unit>(Channel.CONFLATED)

    /** Set once the attached viewer reported scroll positions; those then own the position. */
    @Volatile
    private var scrollReportsSeen = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayManager: DisplayManager? = context.getSystemService()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplays()

        override fun onDisplayChanged(displayId: Int) = refreshDisplays()

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplays()
            if (displayTarget?.displayId == displayId) {
                stopCasting(MR.strings.cast_display_disconnected)
            }
        }
    }

    val isActive: Boolean
        get() = state.value.active

    init {
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        refreshDisplays()
        scope.launch(Dispatchers.IO) {
            for (request in dimensionProbeRequests) probeDimensionsAroundPosition()
        }

        castPreferences.orientation.changes()
            .onEach { value -> update { it.copy(orientation = value) } }
            .launchIn(scope)
        castPreferences.scaleMode.changes()
            .onEach { value -> update { it.copy(scaleMode = value, panX = 0f, panY = 0f) } }
            .launchIn(scope)
        castPreferences.zoomPercent.changes()
            .onEach { value ->
                update { it.copy(zoomPercent = value.coerceIn(CastPreferences.ZOOM_MIN, CastPreferences.ZOOM_MAX)) }
            }
            .launchIn(scope)
        castPreferences.stripWidthPercent.changes()
            .onEach { value ->
                update {
                    it.copy(
                        stripWidthPercent = value.coerceIn(
                            CastPreferences.STRIP_WIDTH_MIN,
                            CastPreferences.STRIP_WIDTH_MAX,
                        ),
                    )
                }
            }
            .launchIn(scope)
        castPreferences.background.changes()
            .onEach { value -> update { it.copy(background = value) } }
            .launchIn(scope)
    }

    // region Reader bridge (main thread)

    /** Called by the reader once its viewer is created (and again after it is recreated). */
    fun attachReader(mangaId: Long, mangaTitle: String, layoutMode: CastLayoutMode, rtl: Boolean) {
        scrollReportsSeen = false
        update {
            it.copy(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                layoutMode = layoutMode,
                rtl = rtl,
                panX = 0f,
                panY = 0f,
            )
        }
    }

    /** Called when the reader leaves for good. Stops casting. */
    fun detachReader() {
        stopCasting(null)
        chapterPages = emptyMap()
        // The reader recycles its page loaders next; make sure nothing is still reading them.
        images.clear(join = true)
        update {
            it.copy(
                chapterId = -1L,
                chapterName = "",
                pages = emptyList(),
                position = CastPosition.Page(0),
                autoScrollRunning = false,
            )
        }
    }

    /** Keeps the page lookup table in sync with the reader's loaded chapters. */
    fun setChapters(chapters: ViewerChapters) {
        chapterPages = buildMap {
            listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter).forEach { chapter ->
                val id = chapter.chapter.id ?: return@forEach
                chapter.pages?.let { put(id, it) }
            }
        }
        val current = state.value
        val castChapter = listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter)
            .firstOrNull { it.chapter.id == current.chapterId }
        val chapter = castChapter ?: chapters.currChapter
        if (castChapter == null && current.chapterId != -1L) {
            // The chapter we were showing is gone (e.g. reading mode changed); follow the reader.
            update {
                it.withChapter(chapter).copy(position = CastPosition.Page(chapter.requestedPage.coerceAtLeast(0)))
            }
        } else if (castChapter == null || current.pages.size != (chapter.pages?.size ?: 0)) {
            update { it.withChapter(chapter) }
        }
        scheduleDimensionProbe()
    }

    /** Reader reports the active page (paged layouts, or a chapter change in continuous ones). */
    fun onReaderPageSelected(page: ReaderPage) {
        if (page is InsertPage) return
        val chapterId = page.chapter.chapter.id ?: return
        val current = state.value
        // Continuous viewers report the page at the top of the viewport through onReaderScroll;
        // the "read" page they select here is the bottom-most one and may belong to the next
        // chapter while the top is still in the previous one.
        if (current.layoutMode == CastLayoutMode.CONTINUOUS && scrollReportsSeen && current.chapterId != -1L) {
            return
        }
        update { current ->
            val base = if (current.chapterId != chapterId) current.withChapter(page.chapter) else current
            val keepScrollPosition = base.layoutMode == CastLayoutMode.CONTINUOUS &&
                current.chapterId == chapterId &&
                current.position is CastPosition.Page
            if (keepScrollPosition) base else base.copy(position = CastPosition.Page(page.index))
        }
        scheduleDimensionProbe()
    }

    /**
     * Reader reports the scroll position of a continuous viewer: [page] is the page at the top of
     * the viewport and [offset] how far (0..1) the viewport top is inside it.
     */
    fun onReaderScroll(page: ReaderPage, offset: Float) {
        if (page is InsertPage) return
        val chapterId = page.chapter.chapter.id ?: return
        scrollReportsSeen = true
        val position = CastPosition.Page(page.index, offset.coerceIn(0f, 1f))
        val current = state.value
        if (!current.active && current.chapterId == chapterId && current.currentPageIndex == page.index) {
            // Nobody is watching: don't churn the state on every scrolled pixel.
            return
        }
        update { current ->
            val base = if (current.chapterId != chapterId) current.withChapter(page.chapter) else current
            if (base.position == position) base else base.copy(position = position)
        }
        scheduleDimensionProbe()
    }

    /** Reader is showing a chapter transition between [from] and [to]. */
    fun onReaderTransition(from: ReaderChapter, to: ReaderChapter?, forward: Boolean) {
        val title = when {
            to == null -> context.stringResource(MR.strings.cast_transition_end)
            forward -> context.stringResource(MR.strings.cast_transition_next, to.chapter.name)
            else -> context.stringResource(MR.strings.cast_transition_prev, to.chapter.name)
        }
        update { it.copy(position = CastPosition.Transition(title, from.chapter.name, forward)) }
    }

    fun setAutoScrollRunning(running: Boolean) {
        update { it.copy(autoScrollRunning = running) }
    }

    // endregion

    // region Targets

    fun refreshDisplays() {
        val list = displayManager
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .orEmpty()
            .filter { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
            .map { display ->
                val mode = display.mode
                CastDisplayInfo(
                    displayId = display.displayId,
                    name = display.name.ifBlank { context.stringResource(MR.strings.cast_display_unnamed) },
                    width = mode.physicalWidth,
                    height = mode.physicalHeight,
                )
            }
        mutableDisplays.value = list
    }

    /** Starts showing the reader on the external display with the given id. */
    fun startDisplayCast(displayId: Int): Boolean {
        val display = displayManager?.getDisplay(displayId) ?: return false
        stopTargets()
        val target = CastDisplayTarget(context.applicationContext, this)
        if (!target.start(display)) {
            logcat(LogPriority.ERROR) { "Unable to show cast presentation on display $displayId" }
            return false
        }
        displayTarget = target
        update {
            it.copy(
                active = true,
                targetType = CastTargetType.DISPLAY,
                targetName = display.name.ifBlank { context.stringResource(MR.strings.cast_display_unnamed) },
            )
        }
        scheduleDimensionProbe()
        CastService.start(context)
        return true
    }

    /** Starts the embedded web receiver; returns its URL on success. */
    fun startWebCast(): Result<CastWebInfo> {
        stopTargets()
        val server = CastWebServer(
            context = context.applicationContext,
            controller = this,
            port = castPreferences.webPort.get(),
            quality = castPreferences.webImageQuality,
        )
        return runCatching { server.start() }
            .onSuccess { info ->
                webServer = server
                mutableWebInfo.value = info
                update {
                    it.copy(
                        active = true,
                        targetType = CastTargetType.WEB,
                        targetName = info.url,
                    )
                }
                scheduleDimensionProbe()
                CastService.start(context)
            }
            .onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Unable to start cast web server" }
                server.stop()
            }
    }

    /** Stops whatever target is active. [messageRes] is shown to the user when not null. */
    fun stopCasting(messageRes: StringResource? = null) {
        val wasActive = state.value.active || displayTarget != null || webServer != null
        stopTargets()
        if (!wasActive) return
        update {
            it.copy(
                active = false,
                targetType = null,
                targetName = null,
                panX = 0f,
                panY = 0f,
            )
        }
        images.clear()
        CastService.stop(context)
        emit(CastEvent.Stopped(messageRes))
    }

    private fun stopTargets() {
        displayTarget?.stop()
        displayTarget = null
        webServer?.stop()
        webServer = null
        mutableWebInfo.value = null
    }

    /** Called by the web server when the number of connected receivers changes. */
    fun onWebClientsChanged(count: Int) {
        mutableWebInfo.update { it?.copy(clientCount = count) }
    }

    // endregion

    // region Presentation settings

    fun setOrientation(orientation: CastOrientation) = castPreferences.orientation.set(orientation)

    fun setScaleMode(mode: CastScaleMode) = castPreferences.scaleMode.set(mode)

    fun setZoomPercent(percent: Int) {
        castPreferences.zoomPercent.set(percent.coerceIn(CastPreferences.ZOOM_MIN, CastPreferences.ZOOM_MAX))
    }

    fun setStripWidthPercent(percent: Int) {
        castPreferences.stripWidthPercent.set(
            percent.coerceIn(CastPreferences.STRIP_WIDTH_MIN, CastPreferences.STRIP_WIDTH_MAX),
        )
    }

    fun setBackground(background: CastBackground) = castPreferences.background.set(background)

    /** Pans an over-sized page on the target by the given fractions of the overflow. */
    fun panBy(dx: Float, dy: Float) {
        update {
            it.copy(
                panX = (it.panX + dx).coerceIn(-1f, 1f),
                panY = (it.panY + dy).coerceIn(-1f, 1f),
            )
        }
    }

    fun resetPan() = update { it.copy(panX = 0f, panY = 0f) }

    // endregion

    // region Remote commands

    fun remoteNextPage() = emit(CastEvent.NextPage)

    fun remotePreviousPage() = emit(CastEvent.PreviousPage)

    fun remoteNextChapter() = emit(CastEvent.NextChapter)

    fun remotePreviousChapter() = emit(CastEvent.PreviousChapter)

    fun remoteToggleAutoScroll() = emit(CastEvent.ToggleAutoScroll)

    /** Scrolls continuous viewers by [deltaPx] phone pixels; pager viewers turn a page by sign. */
    fun remoteScrollBy(deltaPx: Float) = emit(CastEvent.ScrollBy(deltaPx))

    /** Executes a textual command coming from the web receiver (`/api/cmd?do=`). */
    fun remoteCommand(command: String): Boolean {
        val screenStep = context.resources.displayMetrics.heightPixels * 0.75f
        when (command) {
            "next" -> remoteNextPage()
            "prev" -> remotePreviousPage()
            "down" -> remoteScrollBy(screenStep)
            "up" -> remoteScrollBy(-screenStep)
            "next_chapter" -> remoteNextChapter()
            "prev_chapter" -> remotePreviousChapter()
            "toggle" -> remoteToggleAutoScroll()
            else -> return false
        }
        return true
    }

    // endregion

    // region CastPageSource

    override fun findPage(chapterId: Long, index: Int): ReaderPage? {
        return chapterPages[chapterId]?.getOrNull(index)
    }

    override suspend fun awaitReady(page: ReaderPage): Boolean {
        val chapter = page.chapter
        val loader = chapter.pageLoader ?: return false
        if (loader.isRecycled || chapter.state !is ReaderChapter.State.Loaded) return false
        // Mirror what the reader's page holders do: retry failed pages and re-download images
        // that fell out of the chapter cache instead of trusting a stale Ready status.
        val imageUrl = page.imageUrl
        if (page.status is Page.State.Error ||
            (
                page.status == Page.State.Ready && !loader.isLocal && imageUrl != null &&
                    !chapterCache.isImageInCache(imageUrl)
                )
        ) {
            page.status = Page.State.Queue
        }
        if (page.status == Page.State.Ready && page.stream != null) return true
        val result = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) {
            coroutineScope {
                // HttpPageLoader.loadPage never returns; it is cancelled once the page settles.
                val job = launch(Dispatchers.IO) {
                    try {
                        loader.loadPage(page)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e) { "Cast page load failed" }
                    }
                }
                try {
                    merge(
                        page.statusFlow.filter { it is Page.State.Ready || it is Page.State.Error },
                        // The chapter being recycled (reader closed, reading mode changed) ends the wait.
                        chapter.stateFlow.filter { it !is ReaderChapter.State.Loaded }.map { CHAPTER_UNLOADED },
                    ).first()
                } finally {
                    job.cancel()
                }
            }
        }
        return result is Page.State.Ready && page.stream != null && !loader.isRecycled
    }

    // endregion

    // region Internals

    private inline fun update(block: (CastState) -> CastState) {
        mutableState.update { current ->
            val next = block(current)
            if (next == current) current else next.copy(version = current.version + 1)
        }
    }

    private fun emit(event: CastEvent) {
        if (!mutableEvents.tryEmit(event)) {
            scope.launch { mutableEvents.emit(event) }
        }
    }

    private fun CastState.withChapter(chapter: ReaderChapter): CastState {
        val id = chapter.chapter.id ?: return this
        val pages = chapter.pages.orEmpty().map { page ->
            val info = CastPageInfo(id, page.index)
            val dimensions = images.dimensions(info)
            if (dimensions != null) info.copy(width = dimensions.width, height = dimensions.height) else info
        }
        return copy(
            chapterId = id,
            chapterName = chapter.chapter.name,
            pages = pages,
        )
    }

    /**
     * Probes the dimensions of the pages around the current position so receivers can lay out
     * continuous strips before the images arrive.
     */
    private fun scheduleDimensionProbe() {
        if (!state.value.active) return
        dimensionProbeRequests.trySend(Unit)
    }

    private suspend fun probeDimensionsAroundPosition() {
        val snapshot = state.value
        if (!snapshot.active) return
        val center = snapshot.currentPageIndex.coerceAtLeast(0)
        val range = (center - 1).coerceAtLeast(0)..(center + 3).coerceAtMost(snapshot.pages.lastIndex)
        for (index in range) {
            val info = snapshot.pages.getOrNull(index) ?: continue
            if (info.hasDimensions) continue
            val dimensions = try {
                images.probeDimensions(info)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                null
            } ?: continue
            update { current ->
                if (current.chapterId != info.chapterId) return@update current
                val pages = current.pages.toMutableList()
                val existing = pages.getOrNull(index) ?: return@update current
                if (existing.hasDimensions) return@update current
                pages[index] = existing.copy(width = dimensions.width, height = dimensions.height)
                current.copy(pages = pages)
            }
        }
    }

    // endregion

    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 60_000L
        private val CHAPTER_UNLOADED: Page.State = Page.State.Error(IllegalStateException("Chapter unloaded"))
    }
}
