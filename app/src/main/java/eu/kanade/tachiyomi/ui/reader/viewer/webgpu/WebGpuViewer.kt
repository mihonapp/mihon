package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.ImageView
import ca.mpreg.webgpuviewer.draw.Draw
import ca.mpreg.webgpuviewer.draw.clear
import ca.mpreg.webgpuviewer.draw.line
import ca.mpreg.webgpuviewer.renderer.Image
import ca.mpreg.webgpuviewer.renderer.WebGpuRenderer
import ca.mpreg.webgpuviewer.transition.TransitionBasic
import ca.mpreg.webgpuviewer.transition.TransitionCube
import ca.mpreg.webgpuviewer.transition.TransitionCubeOuter
import ca.mpreg.webgpuviewer.transition.TransitionFade
import ca.mpreg.webgpuviewer.transition.TransitionFadeWhite
import ca.mpreg.webgpuviewer.transition.TransitionFlipLeft
import ca.mpreg.webgpuviewer.transition.TransitionFlipRight
import ca.mpreg.webgpuviewer.transition.TransitionSphere
import ca.mpreg.webgpuviewer.transition.TransitionStackDown
import ca.mpreg.webgpuviewer.transition.TransitionStackLeft
import ca.mpreg.webgpuviewer.transition.TransitionStackRight
import ca.mpreg.webgpuviewer.transition.TransitionStackUp
import ca.mpreg.webgpuviewer.viewer.ImagePage
import com.google.android.material.color.MaterialColors
import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.android.readMetadata
import de.stefan_oltmann.kim.format.tiff.constant.TiffTag
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

open class WebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
    val pager: ImageView = ImageView(activity, isVertical = isVertical),
) : Viewer {

    open val isContinuous: Boolean = false

    val readerPreferences by lazy { activity.appGraph.readerPreferences }

    private fun readerBackgroundColor(): Int = activity.baseContext.readerBackgroundColor(config.theme)

    private fun readerOnBackgroundColor(): Int = MaterialColors.getColor(
        activity.createReaderThemeContext(),
        com.google.android.material.R.attr.colorOnBackground,
        Color.WHITE,
    )

    private val scope = MainScope()

    // Dedicated thread for decode worker to avoid blocking Dispatchers.Default pool
    private val decodeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WebGpuViewer-Decode").apply { isDaemon = true }
    }
    private val decodeDispatcher = decodeExecutor.asCoroutineDispatcher()

    // Single lock for all page cache and queue operations
    private val lock = Object()

    // Page cache - keyed by stable PageKey for O(1) lookup
    private val pageCache = LinkedHashMap<PageKey, ViewerPage>()

    // Decode queue - pages waiting to be decoded, processed LIFO (last = highest priority)
    private val decodeQueue = ArrayDeque<ViewerPage>()

    // Stable key types for page identity - data classes provide correct equals/hashCode
    private sealed class PageKey {
        data class Reader(val chapterId: Long?, val index: Int) : PageKey()
        data class Transition(val prevId: Long?, val nextId: Long?) : PageKey()
    }

    private fun pageKey(page: ViewerPage): PageKey = when (page) {
        is ViewerReaderPage -> PageKey.Reader(page.page.chapter.chapter.id, page.page.index)
        is TransitionPage -> PageKey.Transition(page.prevChapter?.chapter?.id, page.nextChapter?.chapter?.id)
        else -> PageKey.Transition(null, null)
    }

    private fun findInCache(key: PageKey): ViewerPage? = pageCache[key]

    /** Check if a page is in the cache by identity. O(1) via key lookup. */
    private fun pageInCache(page: ViewerPage): Boolean = pageCache[pageKey(page)] === page

    /**
     * Queue a page for decoding if not already queued/loading/decoded.
     * If prioritize=true and page is already queued, moves it to front.
     * Must be called while holding lock.
     */
    private fun queueForDecode(page: ViewerPage, prioritize: Boolean = false) {
        // Already has a decoded image
        if (page.imagePage.isDecoded) return

        when (page.state) {
            PageState.IDLE -> {
                page.state = PageState.QUEUED
                if (prioritize) {
                    decodeQueue.addLast(page)
                } else {
                    decodeQueue.addFirst(page)
                }
                lock.notify()
            }

            PageState.QUEUED -> {
                // Already queued - move to front if prioritizing
                if (prioritize && decodeQueue.remove(page)) {
                    decodeQueue.addLast(page)
                }
            }

            PageState.LOADING, PageState.DECODING -> {
                // Already being processed
            }
        }
    }

    init {
        // Decode worker thread - processes pages from the queue
        scope.launch(decodeDispatcher) {
            try {
                while (true) {
                    val page = synchronized(lock) {
                        while (decodeQueue.isEmpty()) {
                            lock.wait()
                        }
                        decodeQueue.removeLast().also { it.state = PageState.DECODING }
                    }

                    // Verify page is still valid (not evicted and doesn't have a decoded image yet)
                    val shouldProcess = synchronized(lock) {
                        pageInCache(page) && page.state == PageState.DECODING && !page.imagePage.isDecoded
                    }

                    if (!shouldProcess) {
                        synchronized(lock) {
                            if (pageInCache(page)) page.state = PageState.IDLE
                        }
                        continue
                    }

                    try {
                        when (page) {
                            is ViewerReaderPage -> decodeReaderPage(page)
                            is TransitionPage -> createTransitionPage(page)
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Decode error: ${pageKey(page)}" }
                        synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
                    }
                }
            } catch (_: InterruptedException) {
                // Normal shutdown
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Decode worker died" }
            }
        }
    }

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope, readerPreferences)

    var viewerChapters: ViewerChapters? = null

    val pages: List<ReaderPage>? get() = (currentPage as? ViewerReaderPage)?.page?.chapter?.pages

    @Volatile
    var currentPage: ViewerPage? = null

    val preloadCount = 3
    open val cacheSize = 9

    /**
     * Page processing state
     */
    enum class PageState {
        IDLE,
        QUEUED,
        LOADING,
        DECODING,
    }

    /**
     * Evicts the page farthest from reference. Must be called while holding lock.
     * @param reference The page to use as reference (defaults to currentPage)
     */
    private fun evictFarthestPage(reference: ViewerPage? = null) {
        val current = reference ?: currentPage ?: return
        val candidates = pageCache.values.filter { it !== current }.toMutableSet()
        if (candidates.isEmpty()) return

        fun findNext(page: ViewerPage): ViewerPage? = when (page) {
            is ViewerReaderPage -> {
                val chapterId = page.page.chapter.chapter.id
                val nextIndex = page.page.index + 1
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId && it.page.index == nextIndex
                } ?: candidates.find { it is TransitionPage && it.prevChapter?.chapter?.id == chapterId }
            }

            is TransitionPage -> {
                val nextChapterId = page.nextChapter?.chapter?.id
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == nextChapterId && it.page.index == 0
                }
            }

            else -> null
        }

        fun findPrev(page: ViewerPage): ViewerPage? = when (page) {
            is ViewerReaderPage -> {
                val chapterId = page.page.chapter.chapter.id
                val prevIndex = page.page.index - 1
                candidates.find {
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId && it.page.index == prevIndex
                } ?: candidates.find { it is TransitionPage && it.nextChapter?.chapter?.id == chapterId }
            }

            is TransitionPage -> {
                val prevChapterId = page.prevChapter?.chapter?.id
                page.prevChapter?.pages?.lastIndex?.let { lastIndex ->
                    candidates.find {
                        it is ViewerReaderPage && it.page.chapter.chapter.id == prevChapterId &&
                            it.page.index == lastIndex
                    }
                }
            }

            else -> null
        }

        var farthest: ViewerPage? = null
        var forward: ViewerPage? = current
        var backward: ViewerPage? = current

        for (i in 0 until cacheSize) {
            if (candidates.isEmpty()) break
            forward = forward?.let { findNext(it) }
            backward = backward?.let { findPrev(it) }
            if (forward == null && backward == null) break
            if (forward != null && candidates.remove(forward)) farthest = forward
            if (backward != null && candidates.remove(backward)) farthest = backward
        }

        val toRemove = candidates.firstOrNull() ?: farthest ?: return

        pageCache.remove(pageKey(toRemove))
        decodeQueue.remove(toRemove)
        toRemove.state = PageState.IDLE
        (toRemove as? ViewerReaderPage)?.spreadPage?.cleanup()
        toRemove.imagePage.cleanup()
    }

    /**
     * Gets or creates a page. Thread-safe.
     * @param referencePage The page to use as reference for eviction (defaults to currentPage)
     */
    fun getPage(page: ReaderPage, referencePage: ViewerPage? = null): ViewerPage {
        val key = PageKey.Reader(page.chapter.chapter.id, page.index)
        return synchronized(lock) {
            findInCache(key) ?: ViewerReaderPage(page).also { newPage ->
                pageCache[key] = newPage
                while (pageCache.size > cacheSize) {
                    evictFarthestPage(referencePage ?: newPage)
                }
            }
        }
    }

    fun getPage(
        prevChapter: ReaderChapter?,
        nextChapter: ReaderChapter?,
        referencePage: ViewerPage? = null,
    ): ViewerPage {
        val key = PageKey.Transition(prevChapter?.chapter?.id, nextChapter?.chapter?.id)
        return synchronized(lock) {
            findInCache(key) ?: TransitionPage(prevChapter, nextChapter).also { newPage ->
                pageCache[key] = newPage
                while (pageCache.size > cacheSize) {
                    evictFarthestPage(referencePage ?: newPage)
                }
            }
        }
    }

    abstract class ViewerPage {
        abstract val prevChapter: ReaderChapter?
        abstract val nextChapter: ReaderChapter?
        abstract val prev: ViewerPage?
        abstract val next: ViewerPage?

        @Volatile
        var state: PageState = PageState.IDLE

        @Volatile
        open var imagePage: ImagePage = ImagePage.Dummy(400, 400)
    }

    inner class TransitionPage(override val prevChapter: ReaderChapter?, override val nextChapter: ReaderChapter?) :
        ViewerPage() {
        override val prev: ViewerPage?
            get() = prevChapter?.pages?.lastOrNull()?.let { getPage(it) }

        override val next: ViewerPage?
            get() = nextChapter?.pages?.firstOrNull()?.let { getPage(it) }
    }

    inner class ViewerReaderPage(val page: ReaderPage) : ViewerPage() {
        /** Cached spread ImagePage when this page is the anchor of a dual-page spread */
        var spreadPage: ImagePage? = null

        override val prevChapter: ReaderChapter?
            get() = when (page.chapter) {
                viewerChapters?.currChapter -> viewerChapters?.prevChapter
                viewerChapters?.nextChapter -> viewerChapters?.currChapter
                else -> null
            }

        override val nextChapter: ReaderChapter?
            get() = when (page.chapter) {
                viewerChapters?.currChapter -> viewerChapters?.nextChapter
                viewerChapters?.prevChapter -> viewerChapters?.currChapter
                else -> null
            }

        override val prev: ViewerPage?
            get() = page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index - 1)?.let { getPage(it) } ?: prevChapter?.let { prevChapter ->
                    if (prevChapter.state !is ReaderChapter.State.Loaded) {
                        scope.launch(Dispatchers.Default) {
                            activity.viewModel.preload(prevChapter)
                            currentPage?.let { preloadPages(it) }
                        }
                    }
                    if (config.alwaysShowChapterTransition) {
                        getPage(prevChapter, page.chapter)
                    } else {
                        prevChapter.pages?.lastOrNull()?.let { getPage(it) }
                    }
                } ?: getPage(null, page.chapter)
            }

        override val next: ViewerPage?
            get() = page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index + 1)?.let { getPage(it) } ?: nextChapter?.let { nextChapter ->
                    if (nextChapter.state !is ReaderChapter.State.Loaded) {
                        scope.launch(Dispatchers.Default) {
                            activity.viewModel.preload(nextChapter)
                            currentPage?.let { preloadPages(it) }
                        }
                    }
                    if (config.alwaysShowChapterTransition) {
                        getPage(page.chapter, nextChapter)
                    } else {
                        nextChapter.pages?.firstOrNull()?.let { getPage(it) }
                    }
                } ?: getPage(page.chapter, null)
            }
    }

    /**
     * Check if dual page mode is currently active based on config and view dimensions.
     * Dual page is never active for continuous (scrolling) viewers.
     */
    private fun isDualPageMode(): Boolean {
        if (isContinuous) return false
        return when (config.dualPageView) {
            ReaderPreferences.DualPageView.NEVER -> false
            ReaderPreferences.DualPageView.ALWAYS -> true
            ReaderPreferences.DualPageView.WIDE -> {
                val width = pager.state.width
                val height = pager.state.height
                width > 0 && height > 0 && width.toFloat() / height > 1f
            }
        }
    }

    /**
     * Check if the given page can form a spread with the next page.
     * Uses image.position to determine: anchor + partner = spread
     * RTL: RIGHT is anchor, looks for LEFT on next
     * LTR: LEFT is anchor, looks for RIGHT on next
     */
    private fun canFormSpread(page: ViewerReaderPage): Boolean {
        if (!isDualPageMode()) return false
        val anchorPosition = if (isReversed) Image.Position.RIGHT else Image.Position.LEFT
        val partnerPosition = if (isReversed) Image.Position.LEFT else Image.Position.RIGHT
        if (page.imagePage.image?.position != anchorPosition) return false
        val next = page.next as? ViewerReaderPage ?: return false
        if (next.page.chapter != page.page.chapter) return false
        return next.imagePage.image?.position == partnerPosition
    }

    /**
     * Get the anchor page for a spread.
     * RTL: anchor is RIGHT, for LEFT page returns previous RIGHT
     * LTR: anchor is LEFT, for RIGHT page returns previous LEFT
     */
    private fun getSpreadAnchor(page: ViewerPage): ViewerPage {
        if (!isDualPageMode()) return page
        if (page !is ViewerReaderPage) return page

        val anchorPosition = if (isReversed) Image.Position.RIGHT else Image.Position.LEFT
        val partnerPosition = if (isReversed) Image.Position.LEFT else Image.Position.RIGHT

        // If this is a partner page, check if previous is anchor
        if (page.imagePage.image?.position == partnerPosition) {
            val prev = page.prev as? ViewerReaderPage ?: return page
            if (prev.page.chapter == page.page.chapter && prev.imagePage.image?.position == anchorPosition) {
                return prev
            }
        }

        // This page is the anchor or standalone
        return page
    }

    /**
     * Build an ImagePage for the given page, potentially combining with adjacent page for spread.
     * RTL: RIGHT anchor + LEFT partner
     * LTR: LEFT anchor + RIGHT partner
     */
    private fun buildSpreadPage(page: ViewerPage): ImagePage {
        // For TransitionPage, return its imagePage directly
        if (page !is ViewerReaderPage) {
            return page.imagePage
        }

        val image = page.imagePage.image

        // Only form spreads in dual page mode
        if (!isDualPageMode()) {
            return page.imagePage
        }

        val anchorPosition = if (isReversed) Image.Position.RIGHT else Image.Position.LEFT
        val partnerPosition = if (isReversed) Image.Position.LEFT else Image.Position.RIGHT

        // Anchor pages look for partner on next page
        if (image?.position == anchorPosition) {
            val nextReaderPage = (page.next as? ViewerReaderPage)?.takeIf { it.page.chapter == page.page.chapter }
            val partnerImage = nextReaderPage?.imagePage?.image?.takeIf { it.position == partnerPosition }

            if (partnerImage != null) {
                // Reuse existing spread if images match - preserves transform state
                val existing = page.spreadPage
                if (existing != null && existing.images.getOrNull(0) === image &&
                    existing.images.getOrNull(1) === partnerImage
                ) {
                    return existing
                }

                // Create new spread: [anchor, partner]
                val spread = ImagePage(image, partnerImage)
                spread.ownsImages = false
                page.spreadPage = spread
                return spread
            }
        }

        // Single page or no spread partner - clear any cached spread
        page.spreadPage = null
        return page.imagePage
    }

    init {
        pager.state.apply {
            fetchPage = fetch@{ index ->
                val i = if (isReversed) -index else index
                val current = currentPage ?: return@fetch null

                // For index 0, return the current spread
                if (i == 0) {
                    return@fetch buildSpreadPage(getSpreadAnchor(current))
                }

                // Navigate by spreads from current
                var page = current
                val step = if (i > 0) 1 else -1
                repeat(abs(i)) {
                    page = nextPage(page, step) ?: return@fetch null
                }

                return@fetch buildSpreadPage(page)
            }

            onTap = { offset ->
                when (config.navigator.getAction(PointF(offset.x, offset.y))) {
                    NavigationRegion.MENU -> activity.toggleMenu()
                    NavigationRegion.NEXT -> if (isReversed) moveToPrevious() else moveToNext()
                    NavigationRegion.PREV -> if (isReversed) moveToNext() else moveToPrevious()
                    NavigationRegion.RIGHT -> if (isReversed) moveLeft() else moveRight()
                    NavigationRegion.LEFT -> if (isReversed) moveRight() else moveLeft()
                }
            }

            onLongTap = { _ ->
                if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                    (currentPage as? ViewerReaderPage)?.let { activity.onPageLongTap(it.page) }
                }
            }
        }

        config.imagePropertyChangedListener = {
            pager.state.apply {
                transition = when (config.transitionAnimation) {
                    TransitionAnimation.DEFAULT -> if (isVertical) TransitionBasic.Vertical else TransitionBasic
                    TransitionAnimation.FLIP_LEFT -> TransitionFlipLeft
                    TransitionAnimation.FLIP_RIGHT -> TransitionFlipRight
                    TransitionAnimation.STACK_LEFT -> TransitionStackLeft
                    TransitionAnimation.STACK_RIGHT -> TransitionStackRight
                    TransitionAnimation.STACK_UP -> TransitionStackUp
                    TransitionAnimation.STACK_DOWN -> TransitionStackDown
                    TransitionAnimation.SPHERE -> TransitionSphere
                    TransitionAnimation.CUBE_INSIDE -> TransitionCube
                    TransitionAnimation.CUBE_OUTSIDE -> TransitionCubeOuter
                    TransitionAnimation.FADE -> TransitionFade
                    TransitionAnimation.FADE_WHITE -> TransitionFadeWhite
                }

                when (config.cutoutMode) {
                    ReaderPreferences.CutoutMode.IGNORE -> avoidCutout = false
                    ReaderPreferences.CutoutMode.AVOID -> {
                        avoidCutout = true
                        alwaysAvoidCutout = false
                    }

                    ReaderPreferences.CutoutMode.SHIFT -> {
                        avoidCutout = true
                        alwaysAvoidCutout = true
                    }
                }
            }

            synchronized(lock) {
                decodeQueue.clear()
                pageCache.values.forEach {
                    it.state = PageState.IDLE
                    (it as? ViewerReaderPage)?.spreadPage?.cleanup()
                    it.imagePage.cleanup()
                }
                pageCache.clear()

                currentPage = (currentPage as? ViewerReaderPage)?.page?.let { getPage(it) }
                    ?: (currentPage as? TransitionPage)?.let {
                        getPage(it.prevChapter, it.nextChapter)
                    }

                currentPage?.let { preloadPages(it) }
            }

            pager.state.invalidate()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        // Cancel scope first to stop any new operations
        scope.cancel()

        // Shutdown decode executor with interrupt to wake up the worker from wait()
        decodeExecutor.shutdownNow()
        decodeDispatcher.close()

        // Now clean up pages (cleanup() launches fire-and-forget coroutines on Dispatchers.Default)
        synchronized(lock) {
            decodeQueue.clear()
            pageCache.values.forEach {
                it.state = PageState.IDLE
                (it as? ViewerReaderPage)?.spreadPage?.cleanup()
                it.imagePage.cleanup()
            }
            pageCache.clear()
            // Notify in case worker is waiting (though it should be interrupted)
            lock.notifyAll()
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View = pager

    /**
     * Start loading a page and set up listener to re-queue when ready.
     * Called when decode worker encounters a page that isn't downloaded yet.
     */
    private fun startPageLoad(page: ViewerReaderPage) {
        val loader = page.page.chapter.pageLoader ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        // If page is already ready, just re-queue immediately
        if (page.page.status == Page.State.Ready) {
            synchronized(lock) {
                if (pageInCache(page) && !page.imagePage.isDecoded) {
                    page.state = PageState.IDLE
                    queueForDecode(page, prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false)
                } else if (pageInCache(page)) {
                    page.state = PageState.IDLE
                }
            }
            return
        }

        // Transition to LOADING state
        synchronized(lock) {
            if (!pageInCache(page)) return
            page.state = PageState.LOADING
        }

        // Start the download
        if (page.page.status == Page.State.Queue) {
            scope.launch(Dispatchers.IO) {
                loader.loadPage(page.page)
            }
        }

        // Set up progress indicator and re-queue when ready
        scope.launch {
            try {
                val downloadProgressJob = launch {
                    page.page.progressFlow.collect { value ->
                        // Check if page was evicted or already decoded
                        synchronized(lock) {
                            if (!pageInCache(page) || page.imagePage !is ImagePage.Dummy) return@collect
                        }

                        if (page.imagePage.image == null) {
                            page.imagePage = ImagePage.drawable(400, 400).apply {
                                WebGpuRenderer.withContext {
                                    this@apply.texture?.let { texture ->
                                        Draw.submit { encoder ->
                                            clear(encoder, texture, 0x00000000)
                                            line(encoder, texture, 0.1f, 0.5f, 0.9f, 0.5f, 0xFF101010.toInt(), 30f)
                                        }
                                    }
                                }
                            }
                        } else {
                            WebGpuRenderer.withContext {
                                (page.imagePage as ImagePage.Draw?)?.texture?.let { texture ->
                                    Draw.submit { encoder ->
                                        val x2 = 0.1f + (value / 100f) * 0.8f
                                        line(encoder, texture, 0.1f, 0.5f, x2, 0.5f, 0xFFFFFFFF.toInt(), 20f)
                                    }
                                }
                                pager.state.invalidate()
                            }
                        }
                    }
                }

                page.page.statusFlow.takeWhile { state ->
                    when (state) {
                        Page.State.Queue, Page.State.LoadPage, Page.State.DownloadImage -> true
                        is Page.State.Error -> {
                            logcat(LogPriority.ERROR) { "Page load error: ${state.error}" }
                            false
                        }

                        Page.State.Ready -> false
                    }
                }.collect {}

                downloadProgressJob.cancel()

                // Re-queue for decoding if ready
                synchronized(lock) {
                    if (pageInCache(page) && page.state == PageState.LOADING) {
                        page.state = PageState.IDLE
                        if (page.page.status == Page.State.Ready && !page.imagePage.isDecoded) {
                            queueForDecode(
                                page,
                                prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "startPageLoad error" }
                synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            }
        }
    }

    private suspend fun decodeReaderPage(page: ViewerReaderPage) {
        // If page isn't downloaded yet, start loading
        if (page.page.status != Page.State.Ready) {
            startPageLoad(page)
            return
        }

        val stream = page.page.stream?.invoke() ?: run {
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
            return
        }

        var imagePage: ImagePage? = null
        try {
            stream.use { input ->
                // Check if still valid before decoding (not evicted and doesn't have decoded image yet)
                synchronized(lock) {
                    if (!pageInCache(page) || page.imagePage.isDecoded) {
                        if (pageInCache(page)) page.state = PageState.IDLE
                        return
                    }
                }

                // Buffer file to detect spread position tag, then decode.
                // When not in dual page mode, skip Kim entirely.
                val bytes = if (isDualPageMode()) input.readBytes() else null

                val position = if (bytes != null) {
                    val tag = Kim.readMetadata(bytes.inputStream(), bytes.size.toLong())
                        ?.findStringValue(TiffTag.TIFF_TAG_PAGE_NAME)
                    when (tag) {
                        "Left" -> Image.Position.LEFT
                        "Right" -> Image.Position.RIGHT
                        null -> if (isReversed) { // TODO: heuristics, use image size
                            if (page.page.index % 2 == 0) Image.Position.LEFT else Image.Position.RIGHT
                        } else {
                            if (page.page.index % 2 == 0) Image.Position.RIGHT else Image.Position.LEFT
                        }

                        else -> Image.Position.SINGLE
                    }
                } else {
                    Image.Position.SINGLE
                }

                val dec = try {
                    ImageDecoder.new(bytes?.inputStream() ?: input)
                } catch (e: ImageDecoder.DecodeException) {
                    logcat(LogPriority.ERROR, e) { "ImageDecoder.new failed: ${e.message}" }
                    val errorMessage = e.message ?: "Failed to decode image"
                    val bitmap = createBitmap(pager.state.width.coerceAtLeast(1), pager.state.height.coerceAtLeast(1))
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(readerBackgroundColor())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = readerOnBackgroundColor()
                        textSize = 36f
                        textAlign = Paint.Align.CENTER
                    }
                    val maxWidth = bitmap.width * 0.8f
                    val words = errorMessage.split(" ")
                    val lines = mutableListOf<String>()
                    var currentLine = StringBuilder()
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (paint.measureText(testLine) <= maxWidth) {
                            currentLine = StringBuilder(testLine)
                        } else {
                            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                            currentLine = StringBuilder(word)
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                    val lineHeight = 40f
                    var y = bitmap.height / 2f - lines.size * lineHeight / 2
                    for (line in lines) {
                        canvas.drawText(line, bitmap.width / 2f, y, paint)
                        y += lineHeight
                    }
                    val errorPage = ImagePage(bitmap, createMipMaps = false).also {
                        it.image?.position = Image.Position.SINGLE
                    }
                    synchronized(lock) {
                        if (pageInCache(page) && !page.imagePage.isDecoded && !page.imagePage.destroyed) {
                            val oldImagePage = page.imagePage
                            page.imagePage = errorPage
                            page.state = PageState.IDLE
                            if (oldImagePage !is ImagePage.Dummy) oldImagePage.cleanup()
                            pager.state.invalidate()
                        } else {
                            if (pageInCache(page)) page.state = PageState.IDLE
                            errorPage.cleanup()
                        }
                    }
                    return
                }
                val pageCount = dec.pages

                if (pageCount == 0) {
                    logcat(LogPriority.ERROR) { "decodeReaderPage: no frames decoded" }
                    synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
                    return
                }

                // Decode first frame immediately; defer rest only if animated
                val firstFrame = dec.decodeNext()

                // For first frame, create Image with trim in single GPU context switch
                val trimColors = if (config.imageCropBorders) {
                    listOf(
                        floatArrayOf(1f, 1f, 1f),
                        floatArrayOf(0f, 0f, 0f),
                    )
                } else {
                    null
                }

                val backgroundColor = if (config.automaticBackground) {
                    null
                } else {
                    readerBackgroundColor()
                }

                val firstImage = Image.createWithTrim(
                    firstFrame.image,
                    firstFrame.width,
                    firstFrame.height,
                    createMipMaps = true,
                    trimColors = trimColors,
                    trimThreshold = 0.15f,
                    backgroundColor = backgroundColor,
                )

                // Set position for dual page spreads based on reading direction:
                // RTL (isReversed): Cover on LEFT, even=LEFT, odd=RIGHT
                // LTR (!isReversed): Cover on RIGHT, even=RIGHT, odd=LEFT
                firstImage.position = position

                // Create ImagePage early so its cleanup handles all frames
                imagePage = ImagePage(firstImage)

                // Create remaining frames for animation (only for animated images)
                if (pageCount > 1) {
                    val frames = ArrayList<Pair<Image, Int>>(pageCount)
                    frames.add(Pair(firstImage, firstFrame.duration))
                    for (i in 1 until pageCount) {
                        val frame = dec.decodeNext()
                        frames.add(Pair(Image(frame.image, frame.width, frame.height), frame.duration))
                    }
                    imagePage.startAnimationLoop(frames) {
                        if (currentPage === page) pager.state.invalidate()
                    }
                }

                synchronized(lock) {
                    if (pageInCache(page) && !page.imagePage.isDecoded && !page.imagePage.destroyed) {
                        val oldImagePage = page.imagePage
                        page.imagePage = imagePage!!
                        imagePage = null
                        page.state = PageState.IDLE
                        if (oldImagePage !is ImagePage.Dummy) {
                            oldImagePage.cleanup()
                        }
                        applyWideZoomIfNeeded(page)
                        applyFitModeAnchor(page.imagePage)
                        pager.state.invalidate()
                    } else {
                        if (pageInCache(page)) page.state = PageState.IDLE
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "decodeReaderPage error" }
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
        } finally {
            imagePage?.cleanup()
        }
    }

    private suspend fun createTransitionPage(page: TransitionPage) {
        try {
            // Check if still valid
            synchronized(lock) {
                if (!pageInCache(page) || page.imagePage.isDecoded) {
                    if (pageInCache(page)) page.state = PageState.IDLE
                    return
                }
            }

            val bitmap = createBitmap(pager.state.width, pager.state.height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(readerBackgroundColor())

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = readerOnBackgroundColor()
                textSize = 48f
                textAlign = Paint.Align.CENTER
            }

            val maxWidth = bitmap.width * 0.8f
            val lineHeight = 48f

            fun wrapText(text: String): List<String> {
                val words = text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = StringBuilder()
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) <= maxWidth) {
                        currentLine = StringBuilder(testLine)
                    } else {
                        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                return lines
            }

            val lines = mutableListOf<Pair<String, Float>>()
            page.prevChapter?.chapter?.let { chapter ->
                lines.add(Pair("Previous:", lineHeight))
                wrapText(chapter.name).forEach { lines.add(Pair(it, lineHeight)) }
                page.nextChapter?.chapter?.let { lines.add(Pair("", lineHeight)) }
            }
            page.nextChapter?.chapter?.let { chapter ->
                lines.add(Pair("Next:", lineHeight))
                wrapText(chapter.name).forEach { lines.add(Pair(it, lineHeight)) }
            }

            val x = bitmap.width / 2f
            var y = bitmap.height / 2f - lines.sumOf { it.second.toDouble() }.toFloat() / 2
            lines.forEach {
                canvas.drawText(it.first, x, y + it.second, paint)
                y += it.second
            }

            val imagePage = ImagePage(bitmap, createMipMaps = false)
            imagePage.image?.position = Image.Position.SINGLE

            synchronized(lock) {
                if (pageInCache(page) && !page.imagePage.isDecoded && !page.imagePage.destroyed) {
                    val oldImagePage = page.imagePage
                    page.imagePage = imagePage
                    page.state = PageState.IDLE
                    if (oldImagePage !is ImagePage.Dummy) {
                        oldImagePage.cleanup()
                    }
                    pager.state.invalidate()
                } else {
                    if (pageInCache(page)) page.state = PageState.IDLE
                    imagePage.cleanup()
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "createTransitionPage error" }
            synchronized(lock) { if (pageInCache(page)) page.state = PageState.IDLE }
        }
    }

    private fun applyWideZoomIfNeeded(page: ViewerReaderPage) {
        if (!config.landscapeZoom) return
        val imagePage = page.imagePage
        val image = imagePage.image ?: return
        if (image.position != Image.Position.SINGLE) return

        val screenW = pager.state.width
        val screenH = pager.state.height
        if (screenW <= 0 || screenH <= 0) return

        // Wide page: half the image width is wider than the screen aspect ratio
        if (image.width.toFloat() / image.height <= 2f * screenW.toFloat() / screenH) return

        // Scale to fit half the image width to the full screen width
        val wideScale = screenW.toFloat() / (image.width / 2f)

        val halfOffset = (image.width / 4f) / screenW
        val startX = when (config.imageZoomType) {
            ZoomStartPosition.LEFT -> halfOffset
            ZoomStartPosition.RIGHT -> -halfOffset
            ZoomStartPosition.CENTER -> 0f
        }

        imagePage.homeScaleOverride = wideScale
        imagePage.homeXOverride = startX
        imagePage.scale = wideScale
        imagePage.x = startX

        imagePage.y = run {
            val cutoutTopPx = pager.state.cutoutTopPx
            if (cutoutTopPx <= 0f) return@run 0f
            val trimTop = image.trim?.top ?: 0
            val imageOnScreen = image.height * wideScale
            val imageTopY = (screenH - imageOnScreen) / 2f
            val trimTopY = imageTopY + trimTop * wideScale
            if (trimTopY < cutoutTopPx) (cutoutTopPx - trimTopY) / (wideScale * screenH) else 0f
        }
    }

    private fun applyFitModeAnchor(page: ImagePage) {
        if (page.homeScaleOverride != null) return

        val scaleType = config.imageScaleType
        if (scaleType != 3 && scaleType != 4 && scaleType != 5) return

        val image = page.image ?: return
        if (image.position != Image.Position.SINGLE) return

        val screenW = pager.state.width
        val screenH = pager.state.height
        if (screenW <= 0 || screenH <= 0) return

        val w = page.trimWidth.toFloat()
        val h = page.trimHeight.toFloat()
        if (w <= 0f || h <= 0f) return

        val cutoutTopPx = pager.state.cutoutTopPx
        val contentW = screenW.toFloat()
        val contentH = if (pager.state.avoidCutout && cutoutTopPx > 0f) screenH - cutoutTopPx else screenH.toFloat()

        val homeScale = when (scaleType) {
            3 -> contentW / w
            4 -> contentH / h
            else -> 1f // original size
        }.coerceAtLeast(0.01f)
        page.homeScaleOverride = homeScale

        if (scaleType == 5) { // original size
            val minScaleComputed = minOf(contentW / page.width, contentH / page.height).coerceAtLeast(0.01f)
            if (homeScale < minScaleComputed) {
                page.minScale = homeScale
            }
        }

        // zoom start for fit height/original size
        page.homeXOverride = if (scaleType == 4 || scaleType == 5) {
            val maxX = maxOf(0f, (page.width.toFloat() / screenW - 1f / homeScale) / 2f)
            when (config.imageZoomType) {
                ZoomStartPosition.LEFT -> maxX
                ZoomStartPosition.RIGHT -> -maxX
                ZoomStartPosition.CENTER -> 0f
            }
        } else {
            null
        }

        // push below cutout for fit width/original size
        val trimTop = image.trim?.top ?: 0
        val imageTopY = (screenH - page.height * homeScale) / 2f
        val trimTopY = imageTopY + trimTop * homeScale
        page.homeYOverride = if ((scaleType == 3 || scaleType == 5) && h * homeScale > screenH) {
            val target = if (pager.state.avoidCutout && cutoutTopPx > 0f) {
                if (pager.state.alwaysAvoidCutout) cutoutTopPx / 2f else cutoutTopPx
            } else {
                0f
            }
            maxOf(0f, (target - trimTopY) / (homeScale * screenH))
        } else {
            null
        }
    }

    /**
     * Queue a page for decoding. If prioritize=true, moves existing queued page to front.
     */
    protected fun preloadPage(page: ViewerPage, prioritize: Boolean = false) {
        synchronized(lock) {
            val cachedPage = findInCache(pageKey(page)) ?: return
            queueForDecode(cachedPage, prioritize)
        }
    }

    protected fun preloadPages(page: ViewerPage) {
        // Get the canonical page from cache to ensure we're working with current data
        val key = pageKey(page)
        val cachedPage = synchronized(lock) { findInCache(key) } ?: return

        // Priority order: current (highest), next1, next2, prev1, prev2 (lowest)
        // Add in reverse for LIFO, current page gets prioritized

        // Add prev pages (lowest priority)
        val prevPages = mutableListOf<ViewerPage>()
        var p: ViewerPage? = cachedPage
        for (i in 0 until preloadCount) {
            p = p?.prev ?: break
            prevPages.add(p)
        }
        prevPages.asReversed().forEach { preloadPage(it) }

        // Add next pages (medium priority)
        val nextPages = mutableListOf<ViewerPage>()
        p = cachedPage
        for (i in 0 until preloadCount) {
            p = p?.next ?: break
            nextPages.add(p)
        }
        nextPages.asReversed().forEach { preloadPage(it) }

        // Add current spread last with priority flag (highest priority in LIFO)
        // Also preload the paired page
        cachedPage.next?.let { preloadPage(it, prioritize = true) }
        preloadPage(cachedPage, prioritize = true)
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        this.viewerChapters = chapters

        val requestedIndex = min(chapters.currChapter.requestedPage, pages.lastIndex)
        val requestedPage = pages[requestedIndex]

        // Get the page and align to spread anchor if needed
        val page = currentPage ?: getPage(requestedPage)
        currentPage = getSpreadAnchor(page)
        (currentPage as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(currentPage!!)

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                activity.hideMenu()

                // The viewer already showed the page at fetchPage(delta).
                // We need to update currentPage to match that.
                val direction = if (isReversed) -delta else delta
                val current = currentPage ?: return@onPageChange

                // Navigate the same way fetchPage does
                var page = current
                val step = if (direction > 0) 1 else -1
                repeat(abs(direction)) {
                    page = nextPage(page, step) ?: return@onPageChange
                }

                currentPage = page
                (page as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
                preloadPages(page)

                (page as? TransitionPage)?.let { transitionPage ->
                    if (transitionPage.prevChapter == null || transitionPage.nextChapter == null) {
                        activity.showMenu()
                    }
                }
            }

            invalidate()
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     * In dual page mode, aligns to the start of the spread containing the page.
     */
    override fun moveToPage(page: ReaderPage) {
        // Get the page and align to spread anchor based on image position
        moveToPage(getSpreadAnchor(getPage(page)))
    }

    private fun moveToPage(newPage: ViewerPage) {
        val previousPage = currentPage

        currentPage = newPage
        (newPage as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(newPage)

        (newPage as? TransitionPage)?.let { transitionPage ->
            if (transitionPage.prevChapter == null || transitionPage.nextChapter == null) {
                activity.showMenu()
            }
        }

        if (previousPage == null) return

        val direction = when (previousPage) {
            is ViewerReaderPage if newPage is ViewerReaderPage -> if (previousPage.page.chapter ==
                newPage.page.chapter
            ) {
                (newPage.page.index - previousPage.page.index).coerceIn(-1, 1)
            } else if (previousPage.page.chapter == newPage.prevChapter) {
                1
            } else {
                -1
            }

            is TransitionPage if newPage is ViewerReaderPage -> if (previousPage.nextChapter == newPage.page.chapter) {
                1
            } else {
                -1
            }

            is ViewerReaderPage if newPage is TransitionPage -> if (previousPage.page.chapter == newPage.prevChapter) {
                1
            } else {
                -1
            }

            else -> 0
        }

        if (direction != 0) {
            pager.state.transitionFromPage = buildSpreadPage(previousPage)
            pager.state.animatePageTurn(if (isReversed) direction else -direction)
        } else {
            pager.state.invalidate()
        }
    }

    /**
     * Moves to the next page.
     */
    fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        pager.state.getPage(0)?.let { page ->
            val isWidePage = page.homeScaleOverride != null
            if (config.navigateToPan && (!page.atHome || isWidePage)) {
                val maxX = page.maxX(page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x - c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    if (page.animationJob?.isActive == true && page.animationTargetX == x) {
                        page.animationJob?.cancel()
                    } else {
                        page.animateTo(targetX = x, targetY = page.y)
                        return
                    }
                }
            }

            navigateSpread(1)
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        pager.state.getPage(0)?.let { page ->
            val isWidePage = page.homeScaleOverride != null
            if (config.navigateToPan && (!page.atHome || isWidePage)) {
                val maxX = page.maxX(page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x + c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    if (page.animationJob?.isActive == true && page.animationTargetX == x) {
                        page.animationJob?.cancel()
                    } else {
                        page.animateTo(targetX = x, targetY = page.y)
                        return
                    }
                }
            }

            navigateSpread(-1)
        }
    }

    /**
     * Get the target page when navigating by spreads from the given page.
     * @param from Starting page
     * @param direction Positive = forward in page numbers, negative = backward
     * @return Target page or null if navigation not possible
     */
    private fun nextPage(from: ViewerPage, direction: Int): ViewerPage? {
        var page = getSpreadAnchor(from)

        page = if (direction > 0) {
            // Going forward (next spread)
            if (page is ViewerReaderPage && canFormSpread(page)) {
                page.next?.next ?: return null
            } else {
                page.next ?: return null
            }
        } else {
            // Going backward (prev spread)
            page.prev ?: return null
        }

        return getSpreadAnchor(page)
    }

    /**
     * Navigate by spreads from current page.
     * @param direction Positive = forward in page numbers, negative = backward
     */
    private fun navigateSpread(direction: Int) {
        val target = currentPage?.let { nextPage(it, direction) } ?: return
        moveToPage(target)
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected fun moveDown() {
        moveToNext()
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) if (ctrlPressed) moveToNext() else moveRight()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) if (ctrlPressed) moveToPrevious() else moveLeft()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }
}
