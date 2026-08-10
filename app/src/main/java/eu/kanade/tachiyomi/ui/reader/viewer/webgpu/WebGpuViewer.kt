package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
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
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.min

open class WebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
    val pager: ImageView = ImageView(activity, isVertical = isVertical),
) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    // Single lock for all page cache and queue operations
    private val lock = Object()

    // Page cache - all pages we know about
    private val pageCache = mutableListOf<ViewerPage>()

    // Decode queue - pages waiting to be decoded, processed LIFO (last = highest priority)
    private val decodeQueue = ArrayDeque<ViewerPage>()

    // Helper to create a stable key for a page
    private fun pageKey(page: ViewerPage): String = when (page) {
        is ViewerReaderPage -> "R:${page.page.chapter.chapter.id}:${page.page.index}"
        is TransitionPage -> "T:${page.prevChapter?.chapter?.id}:${page.nextChapter?.chapter?.id}"
        else -> ""
    }

    private fun findInCache(key: String): ViewerPage? = pageCache.find { pageKey(it) == key }

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
                (lock as Object).notify()
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
        scope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    val page = synchronized(lock) {
                        while (decodeQueue.isEmpty()) {
                            (lock as Object).wait()
                        }
                        decodeQueue.removeLast().also { it.state = PageState.DECODING }
                    }

                    // Verify page is still valid (not evicted and doesn't have a decoded image yet)
                    val shouldProcess = synchronized(lock) {
                        page in pageCache && page.state == PageState.DECODING && !page.imagePage.isDecoded
                    }

                    if (!shouldProcess) {
                        synchronized(lock) {
                            if (page in pageCache) page.state = PageState.IDLE
                        }
                        continue
                    }

                    try {
                        when (page) {
                            is ViewerReaderPage -> decodeReaderPage(page)
                            is TransitionPage -> createTransitionPage(page)
                        }
                    } catch (e: Exception) {
                        Log.e("WebGpuViewer", "Decode error: ${pageKey(page)}", e)
                        synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
                    }
                }
            } catch (e: InterruptedException) {
                // Normal shutdown
            } catch (e: Exception) {
                Log.e("WebGpuViewer", "Decode worker died", e)
            }
        }
    }

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope)

    var viewerChapters: ViewerChapters? = null

    val pages: List<ReaderPage>? get() = (currentPage as? ViewerReaderPage)?.page?.chapter?.pages

    @Volatile
    var currentPage: ViewerPage? = null

    val preloadCount = 2
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
        val candidates = pageCache.filter { it !== current }.toMutableSet()
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
                    it is ViewerReaderPage && it.page.chapter.chapter.id == chapterId &&
                        it.page.index == prevIndex
                }
                    ?: candidates.find { it is TransitionPage && it.nextChapter?.chapter?.id == chapterId }
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

        pageCache.remove(toRemove)
        decodeQueue.remove(toRemove)
        toRemove.state = PageState.IDLE
        toRemove.imagePage.cleanup()
    }

    /**
     * Gets or creates a page. Thread-safe.
     * @param referencePage The page to use as reference for eviction (defaults to currentPage)
     */
    fun getPage(page: ReaderPage, referencePage: ViewerPage? = null): ViewerPage {
        val key = "R:${page.chapter.chapter.id}:${page.index}"
        return synchronized(lock) {
            findInCache(key) ?: ViewerReaderPage(page).also { newPage ->
                pageCache.add(newPage)
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
        val key = "T:${prevChapter?.chapter?.id}:${nextChapter?.chapter?.id}"
        return synchronized(lock) {
            findInCache(key) ?: TransitionPage(prevChapter, nextChapter).also { newPage ->
                pageCache.add(newPage)
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
        open var imagePage: ImagePage = ImagePage.Dummy(400, 400).apply {
            minScale = 1f
            maxScale = 1f
        }
    }

    inner class TransitionPage(override val prevChapter: ReaderChapter?, override val nextChapter: ReaderChapter?) :
        ViewerPage() {
        override val prev: ViewerPage?
            get() = prevChapter?.pages?.lastOrNull()?.let { getPage(it) }

        override val next: ViewerPage?
            get() = nextChapter?.pages?.firstOrNull()?.let { getPage(it) }
    }

    inner class ViewerReaderPage(val page: ReaderPage) : ViewerPage() {
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
                        CoroutineScope(Dispatchers.Default).launch {
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
                        CoroutineScope(Dispatchers.Default).launch {
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

    fun nextPage(count: Int): ViewerPage? {
        if (count == 0) return currentPage

        var currentPage = currentPage ?: return null

        for (i in 0 until abs(count)) {
            currentPage = if (count > 0) {
                currentPage.next
            } else {
                currentPage.prev
            } ?: return null
        }

        return currentPage
    }

    init {
        pager.state.apply {
            fetchPage = fetch@{ index ->
                val i = if (isReversed) -index else index
                val page = nextPage(i) ?: return@fetch null
                page.imagePage
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

            onLongTap = { offset ->
                if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                    (currentPage as? ViewerReaderPage)?.let { activity.onPageLongTap(it.page) }
                }
            }
        }

//        config.dualPageSplitChangedListener = { enabled ->
//            if (!enabled) {
//                cleanupPageSplit()
//            }
//        }

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
                pageCache.forEach {
                    it.state = PageState.IDLE
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
        synchronized(lock) {
            decodeQueue.clear()
            pageCache.forEach {
                it.state = PageState.IDLE
                it.imagePage.cleanup()
            }
            pageCache.clear()
        }
        scope.cancel()
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
            synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
            return
        }

        // If page is already ready, just re-queue immediately
        if (page.page.status == Page.State.Ready) {
            synchronized(lock) {
                if (page in pageCache && !page.imagePage.isDecoded) {
                    page.state = PageState.IDLE
                    queueForDecode(page, prioritize = currentPage?.let { pageKey(it) == pageKey(page) } ?: false)
                } else if (page in pageCache) {
                    page.state = PageState.IDLE
                }
            }
            return
        }

        // Transition to LOADING state
        synchronized(lock) {
            if (page !in pageCache) return
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
                            if (page !in pageCache || page.imagePage !is ImagePage.Dummy) return@collect
                        }

                        if (page.imagePage.image == null) {
                            page.imagePage = ImagePage(400, 400).apply {
                                parent = pager.state
                                minScale = homeScale
                                maxScale = homeScale
                                scale = homeScale

                                WebGpuRenderer.withContext {
                                    (this@apply as ImagePage.Draw).texture?.let { texture ->
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
                            Log.e("WebGpuViewer", "Page load error: ${state.error}")
                            false
                        }

                        Page.State.Ready -> false
                    }
                }.collect {}

                downloadProgressJob.cancel()

                // Re-queue for decoding if ready
                synchronized(lock) {
                    if (page in pageCache && page.state == PageState.LOADING) {
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
                Log.e("WebGpuViewer", "startPageLoad error", e)
                synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
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
            synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
            return
        }

        var imagePage: ImagePage? = null
        try {
            stream.use { input ->
                // Check if still valid before decoding (not evicted and doesn't have decoded image yet)
                synchronized(lock) {
                    if (page !in pageCache || page.imagePage.isDecoded) {
                        if (page in pageCache) page.state = PageState.IDLE
                        return
                    }
                }

                val dec = ImageDecoder.new(input)
                val decoded = (0 until dec.pages).map { dec.decodeNext() }

                if (decoded.isEmpty()) {
                    Log.e("WebGpuViewer", "decodeReaderPage: no frames decoded")
                    synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
                    return
                }

                // For first frame, create Image with trim in single GPU context switch
                val trimColor = if (config.imageCropBorders) floatArrayOf(1f, 1f, 1f, 0.15f) else null
                val firstFrame = decoded[0]
                val (firstImage, trimRect) = Image.createWithTrim(
                    firstFrame.image,
                    firstFrame.width,
                    firstFrame.height,
                    createMipMaps = true,
                    trimColor = trimColor,
                )

                // Create ImagePage early so its cleanup handles all frames
                imagePage = ImagePage(firstImage).apply {
                    trim = trimRect
                    parent = pager.state
                    x = homeX
                    y = homeY
                    scale = homeScale
                }

                // Create remaining frames for animation
                if (decoded.size > 1) {
                    val frames = mutableListOf<Pair<Image, Int>>()
                    frames.add(Pair(firstImage, firstFrame.duration))
                    // Assign pages early so cleanup() will handle all frames if creation fails partway
                    imagePage.pages = frames
                    for (i in 1 until decoded.size) {
                        val frame = decoded[i]
                        frames.add(Pair(Image(frame.image, frame.width, frame.height), frame.duration))
                    }
                    imagePage.startAnimationLoop(frames) { pager.state.invalidate() }
                }

                synchronized(lock) {
                    if (page in pageCache && !page.imagePage.isDecoded && !page.imagePage.destroyed) {
                        val oldImagePage = page.imagePage
                        page.imagePage = imagePage!!
                        imagePage = null
                        page.state = PageState.IDLE
                        if (oldImagePage !is ImagePage.Dummy) {
                            oldImagePage.cleanup()
                        }
                        pager.state.invalidate()
                    } else {
                        if (page in pageCache) page.state = PageState.IDLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebGpuViewer", "decodeReaderPage error", e)
            synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
        } finally {
            imagePage?.cleanup()
        }
    }

    private suspend fun createTransitionPage(page: TransitionPage) {
        try {
            // Check if still valid
            synchronized(lock) {
                if (page !in pageCache || page.imagePage.isDecoded) {
                    if (page in pageCache) page.state = PageState.IDLE
                    return
                }
            }

            val bitmap = createBitmap(pager.state.width, pager.state.height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
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

            val imagePage = ImagePage(bitmap, createMipMaps = false).apply {
                minScale = 1f
                maxScale = 1f
            }

            synchronized(lock) {
                if (page in pageCache && !page.imagePage.isDecoded && !page.imagePage.destroyed) {
                    val oldImagePage = page.imagePage
                    page.imagePage = imagePage
                    page.state = PageState.IDLE
                    if (oldImagePage !is ImagePage.Dummy) {
                        oldImagePage.cleanup()
                    }
                    pager.state.invalidate()
                } else {
                    if (page in pageCache) page.state = PageState.IDLE
                    imagePage.cleanup()
                }
            }
        } catch (e: Exception) {
            Log.e("WebGpuViewer", "createTransitionPage error", e)
            synchronized(lock) { if (page in pageCache) page.state = PageState.IDLE }
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

        // Add current page last with priority flag (highest priority in LIFO)
        preloadPage(cachedPage, prioritize = true)
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        this.viewerChapters = chapters

        val requestedPage = pages[min(chapters.currChapter.requestedPage, pages.lastIndex)]

        val page = currentPage ?: getPage(requestedPage)
        currentPage = page
        (page as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
        preloadPages(page)

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                activity.hideMenu()

                val delta = if (isReversed) -delta else delta

                nextPage(delta)?.let { newPage ->
                    currentPage = newPage
                    (newPage as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
                    preloadPages(newPage)

                    (currentPage as? TransitionPage)?.let { transitionPage ->
                        if (transitionPage.prevChapter == null || transitionPage.nextChapter == null) {
                            activity.showMenu()
                        }
                    }
                }
            }

            invalidate()
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        moveToPage(getPage(page))
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

            is TransitionPage if newPage is ViewerReaderPage -> if (previousPage.nextChapter ==
                newPage.page.chapter
            ) {
                1
            } else {
                -1
            }

            is ViewerReaderPage if newPage is TransitionPage -> if (previousPage.page.chapter ==
                newPage.prevChapter
            ) {
                1
            } else {
                -1
            }

            else -> 0
        }

        if (direction != 0) {
            // Set the transition "from" page to animate from actual previous page (for far navigation)
            pager.state.transitionFromPage = previousPage.imagePage
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
            if (config.navigateToPan && !page.atHome) {
                val maxX = pager.state.maxX(page.width, page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x - c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }
            nextPage(1)?.let { moveToPage(it) }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        pager.state.getPage(0)?.let { page ->
            if (config.navigateToPan && !page.atHome) {
                val maxX = pager.state.maxX(page.width, page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x + c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }
            nextPage(-1)?.let { moveToPage(it) }
        }
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
