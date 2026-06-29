package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.PointF
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.Image
import ca.mpreg.webgpuviewer.Trim
import ca.mpreg.webgpuviewer.WebGpuImageView
import ca.mpreg.webgpuviewer.WebGpuImageViewerPage
import ca.mpreg.webgpuviewer.WebGpuRenderer
import ca.mpreg.webgpuviewer.transitions.TransitionBasic
import ca.mpreg.webgpuviewer.transitions.TransitionFlipLeft
import ca.mpreg.webgpuviewer.transitions.TransitionFlipRight
import ca.mpreg.webgpuviewer.transitions.TransitionSphere
import ca.mpreg.webgpuviewer.transitions.TransitionStackLeft
import ca.mpreg.webgpuviewer.transitions.TransitionStackRight
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class WebGpuViewer(val activity: ReaderActivity, val isReversed: Boolean, val isVertical: Boolean) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = WebGpuImageView(activity, isVertical=isVertical)

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope)

    var prevChapter: ReaderChapter? = null
    var nextChapter: ReaderChapter? = null

    val pages: List<ReaderPage>? get() = currentPage?.chapter?.pages
    val currentPageIndex: Int get() = currentPage?.index ?: 0

    val nextPage: ReaderPage?
        get() = pages?.let { pages ->
            if (currentPageIndex + 1 < pages.size) {
                pages[currentPageIndex + 1]
            } else {
                nextChapter?.pages?.first()
            }
        }

    val prevPage: ReaderPage?
        get() = pages?.let { pages ->
            if (currentPageIndex - 1 >= 0) {
                pages[currentPageIndex - 1]
            } else {
                prevChapter?.pages?.last()
            }
        }

    fun updateTransitionAnimation() {
        pager.state.apply {
            transition = when (config.transitionAnimation) {
                TransitionAnimation.DEFAULT -> if (isVertical) TransitionBasic.Vertical else TransitionBasic
                TransitionAnimation.FLIP_LEFT -> TransitionFlipLeft
                TransitionAnimation.FLIP_RIGHT -> TransitionFlipRight
                TransitionAnimation.STACK_LEFT -> TransitionStackLeft
                TransitionAnimation.STACK_RIGHT -> TransitionStackRight
                TransitionAnimation.SPHERE -> TransitionSphere
            }
        }
    }

    init {
        updateTransitionAnimation()

        pager.state.apply {
            fetchPage = fetch@{ index ->
                val i = if (isReversed) -index else index
                when (i) {
                    0 -> currentPage?.let { createPage(it) }
                    1 -> nextPage?.let { createPage(it) }
                    -1 -> prevPage?.let { createPage(it) }
                    else -> null
                }
            }

            onTap = { offset ->
                Log.i("WebGpuViewer", "onTap $offset")
                when (config.navigator.getAction(PointF(offset.x, offset.y))) {
                    NavigationRegion.MENU -> activity.toggleMenu()
                    NavigationRegion.NEXT -> moveToNext()
                    NavigationRegion.PREV -> moveToPrevious()
                    NavigationRegion.RIGHT -> moveRight()
                    NavigationRegion.LEFT -> moveLeft()
                }
            }

            onLongTap = { offset ->
                Log.i("WebGpuViewer", "onLongTap $offset")
                if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                    currentPage?.let {
                        activity.onPageLongTap(it)
                    }
                }
            }
        }

//        config.dualPageSplitChangedListener = { enabled ->
//            if (!enabled) {
//                cleanupPageSplit()
//            }
//        }

        config.imagePropertyChangedListener = {
            runBlocking {
                cacheMutex.withLock {
                    pageCache.clear()
                }
            }

            pager.state.post {
                pager.state.render()
            }

            updateTransitionAnimation()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return pager
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters, show menu because the user is probably going to close the reader
            activity.showMenu()
        }
    }

    var currentPage: ReaderPage? = null

    val preloadCount = 3
    val cacheSize = 10

    val cacheMutex = Mutex()
    val pageCache = LinkedHashMap<ReaderPage, WebGpuImageViewerPage?>(cacheSize, 0.75f, true)

    var decodeJob: Job? = null
    val decodeQueue = mutableListOf<ReaderPage>()

    private suspend fun loadPage(page: ReaderPage): InputStream? {
        val loader = page.chapter.pageLoader ?: return null

        CoroutineScope(Dispatchers.IO).launch {
            loader.loadPage(page)
        }

        val downloadProgressJob = CoroutineScope(Dispatchers.Default).launch {
            page.progressFlow.collectLatest { value ->
                Log.i("WebGpuViewer", "DownloadImage: $value")
            }
        }

        page.statusFlow.takeWhile { state ->
            when (state) {
                Page.State.Queue -> true
                Page.State.LoadPage -> true
                Page.State.DownloadImage -> true
                Page.State.Ready -> false
                is Page.State.Error -> {
                    Log.e("WebGpuViewer", "Error ${state.error}")
                    false
                }
            }
        }.collectLatest {}

        downloadProgressJob.cancel()

        return page.stream?.invoke()
    }

    private suspend fun createPage(page: ReaderPage): WebGpuImageViewerPage? {
        return cacheMutex.withLock { pageCache[page] } ?: loadPage(page)?.use {
            Log.i("WebGpuViewer", "createPage: ${page.index} $page")
            withContext(Dispatchers.Default) {
                val dec = ImageDecoder.new(it)
                dec.decodeNext()
            }
        }?.let {
            withContext(WebGpuRenderer.dispatcher) {
                WebGpuImageViewerPage(Image(it.image, it.width, it.height)).apply {
                    if (config.imageCropBorders) {
                        trim = Trim.find(image, 1f, 1f, 1f, 10f / 255)
                    }

                    parent = pager.state
                    x = homeX
                    y = homeY
                    scale = homeScale
                }
            }
        }?.also {
            Log.i("WebGpuViewer", "store in cache: ${page.index} $page")
            cacheMutex.withLock {
                pageCache[page] = it
                while (pageCache.size > cacheSize) {
                    Log.i("WebGpuViewer", "remove from cache: ${pageCache.keys.first().index}")
                    pageCache.remove(pageCache.keys.first())
                }
            }
        }
    }

    @Synchronized
    private fun popPreload(): ReaderPage? {
        return decodeQueue.removeFirstOrNull()
    }

    @Synchronized
    private fun preloadPages(page: ReaderPage) {
        val pages = page.chapter.pages ?: return

        decodeQueue.clear()

        for (i in page.index until min(page.index + preloadCount, pages.size)) {
            decodeQueue.add(pages[i])
        }

        for (i in max(0, page.index - preloadCount) until page.index) {
            decodeQueue.add(pages[i])
        }

        if (prevChapter?.state !is ReaderChapter.State.Loaded && page.index - preloadCount < 0) {
            prevChapter?.let { chapter ->
                CoroutineScope(Dispatchers.Default).launch {
                    activity.viewModel.preload(chapter)
                    preloadPages(page)
                }
            }
        }

        if (nextChapter?.state !is ReaderChapter.State.Loaded && page.index + preloadCount > pages.lastIndex) {
            nextChapter?.let { chapter ->
                CoroutineScope(Dispatchers.Default).launch {
                    activity.viewModel.preload(chapter)
                    preloadPages(page)
                }
            }
        }

        prevChapter?.pages?.takeLast(max(0, preloadCount - page.index))?.forEach { page ->
            decodeQueue.add(page)
        }

        nextChapter?.pages?.take(max(0, preloadCount - (pages.lastIndex - page.index)))?.forEach { page ->
            decodeQueue.add(page)
        }

        decodeJob ?: CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    popPreload()?.let { createPage(it) } ?: return@launch
                }
            } finally {
                decodeJob = null
            }
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        prevChapter = chapters.prevChapter
        nextChapter = chapters.nextChapter

        if (currentPage == null) {
            currentPage = pages.first()
            prevChapter?.let {
                CoroutineScope(Dispatchers.Default).launch {
                    activity.viewModel.preload(it)
                    currentPage?.let { page -> preloadPages(page) }
                }
            }

            currentPage?.let { activity.onPageSelected(it) }
        }

        currentPage?.let { preloadPages(it) }

        pager.state.apply {
            val currentPageIndex = currentPage?.index ?: 0
            if (currentPageIndex < pages.lastIndex || nextChapter != null) {
                if (isReversed) havePrev = true else haveNext = true
            }
            if (currentPageIndex > 0 || chapters.prevChapter != null) {
                if (isReversed) haveNext = true else havePrev = true
            }

            onPageChange = onPageChange@{ delta ->
                val index = if (isReversed) -delta else delta
                if (!activity.isScrollingThroughPages) {
                    activity.hideMenu()
                }
                if (index == 1) {
                    nextPage
                } else {
                    prevPage
                }?.let { newPage ->
                    this@WebGpuViewer.currentPage = newPage

                    val pages = newPage.chapter.pages ?: return@onPageChange

                    if (newPage.index < pages.lastIndex || nextChapter != null) {
                        if (isReversed) havePrev = true else haveNext = true
                    }
                    if (newPage.index > 0 || prevChapter != null) {
                        if (isReversed) haveNext = true else havePrev = true
                    }

                    activity.onPageSelected(newPage)
                    preloadPages(newPage)
                }
            }

            post {
                render()
            }
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        currentPage = page
        activity.onPageSelected(page)

        val pages = page.chapter.pages ?: return

        if (page.index < pages.lastIndex || nextChapter != null) {
            if (isReversed) pager.state.havePrev = true else pager.state.haveNext = true
        }
        if (page.index > 0 || prevChapter != null) {
            if (isReversed) pager.state.haveNext = true else pager.state.havePrev = true
        }
        if (page.index == 0) {
            prevChapter?.let { activity.requestPreloadChapter(it) }
        }
        if (page.index == pages.size - 1) {
            nextChapter?.let { activity.requestPreloadChapter(it) }
        }

        pager.state.render()

        preloadPages(page)
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
    protected fun moveRight() {
    }

    /**
     * Moves to the page at the left.
     */
    protected fun moveLeft() {
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

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
                }
            }

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

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        activity.runOnUiThread {
            // Need to insert on UI thread else images will go blank
//            adapter.onPageSplit(currentPage, newPage)
        }
    }

    private fun cleanupPageSplit() {
//        adapter.cleanupPageSplit()
    }
}
