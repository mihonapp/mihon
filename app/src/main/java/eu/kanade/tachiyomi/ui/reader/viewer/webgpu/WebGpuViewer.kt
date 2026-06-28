package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.util.fastCoerceAtLeast
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.Image
import ca.mpreg.webgpuviewer.Trim
import ca.mpreg.webgpuviewer.WebGpuImageView
import ca.mpreg.webgpuviewer.WebGpuImageViewerPage
import ca.mpreg.webgpuviewer.WebGpuRenderer
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

class WebGpuViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = WebGpuImageView(activity)

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope)

    var prevChapter: ReaderChapter? = null
    var nextChapter: ReaderChapter? = null

    //
    init {
        pager.state.apply {
//            transition = ImageShaderStackRight::render

            fetchPage = fetch@{ index ->
                val currentPage = currentPage ?: return@fetch null

                val pages = currentPage.chapter.pages ?: return@fetch null

                val currentPageIndex = pages.indexOf(currentPage)

                when (index) {
                    0 -> loadPage(currentPage)

                    1 -> if (currentPageIndex + 1 < pages.size) {
                        pages[currentPageIndex + 1]
                    } else {
                        nextChapter?.pages?.first()
                    }?.let { loadPage(it) }

                    -1 -> if (currentPageIndex - 1 >= 0) {
                        pages[currentPageIndex - 1]
                    } else {
                        prevChapter?.pages?.last()
                    }?.let { loadPage(it) }

                    else -> null
                }
            }
        }

//        pager.tapListener = { event ->
//            val viewPosition = IntArray(2)
//            pager.getLocationOnScreen(viewPosition)
//            val viewPositionRelativeToWindow = IntArray(2)
//            pager.getLocationInWindow(viewPositionRelativeToWindow)
//            val pos = PointF(
//                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
//                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
//            )

//        activity.onPageSelected(page)
//            when (config.navigator.getAction(pos)) {
//                NavigationRegion.MENU -> activity.toggleMenu()
//                NavigationRegion.NEXT -> moveToNext()
//                NavigationRegion.PREV -> moveToPrevious()
//                NavigationRegion.RIGHT -> moveRight()
//                NavigationRegion.LEFT -> moveLeft()
//            }
//        }
//        pager.longTapListener = f@{
//            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
//                val item = adapter.items.getOrNull(pager.currentItem)
//                if (item is ReaderPage) {
//                    activity.onPageLongTap(item)
//                    return@f true
//                }
//            }
//            false
//        }

//        config.dualPageSplitChangedListener = { enabled ->
//            if (!enabled) {
//                cleanupPageSplit()
//            }
//        }
//
//        config.imagePropertyChangedListener = {
//            refreshAdapter()
//        }
//
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

    var pages: List<ReaderPage>? = null

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

    private suspend fun createPage(page: ReaderPage): WebGpuImageViewerPage? {
        val res = withContext(Dispatchers.Default) {
            page.stream?.invoke()?.let {
                val dec = ImageDecoder.new(it)
                dec.decodeNext()
            }
        } ?: return null

        return WebGpuImageViewerPage(Image(res.image, res.width, res.height)).apply {
            trim = Trim.find(image, 1f, 1f, 1f, 10f / 255)

            parent = pager.state
            x = homeX
            y = homeY
            scale = homeScale
        }
    }

    var currentPage: ReaderPage? = null

    val pageCache = mutableMapOf<ReaderPage, WebGpuImageViewerPage?>()

    var decodeJob: Job? = null
    val decodeQueue = mutableListOf<ReaderPage>()

    val preloadCount = 3

    private suspend fun loadPage(page: ReaderPage): WebGpuImageViewerPage? {
        return pageCache[page] ?: withContext(Dispatchers.Default) {
            page.stream?.invoke()?.let {
                val dec = ImageDecoder.new(it)
                dec.decodeNext()
            }
        }?.let {
            withContext(WebGpuRenderer.dispatcher) {
                WebGpuImageViewerPage(Image(it.image, it.width, it.height)).apply {
                    trim = Trim.find(image, 1f, 1f, 1f, 10f / 255)

                    parent = pager.state
                    x = homeX
                    y = homeY
                    scale = homeScale
                }
            }
        }?.also {
            pageCache[page] = it
        }
    }

    @Synchronized
    private fun preloadPages(page: ReaderPage) {
        val pages = page.chapter.pages ?: return

        val index = pages.indexOf(page)

        decodeQueue.clear()

        for (i in (index..pages.lastIndex).take(preloadCount)) {
            decodeQueue.add(pages[i])
        }

        for (i in ((index - 3).fastCoerceAtLeast(0) until index).take(preloadCount)) {
            decodeQueue.add(pages[i])
        }

        prevChapter?.pages?.firstOrNull()?.let { decodeQueue.add(it) }
        nextChapter?.pages?.firstOrNull()?.let { decodeQueue.add(it) }

        decodeJob ?: CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    decodeQueue.removeFirstOrNull()?.let { loadPage(it) } ?: return@launch
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
            chapters.prevChapter?.let { activity.requestPreloadChapter(it) }
        }

        currentPage?.let { preloadPages(it) }

        pager.state.apply {
            val currentPageIndex = pages.indexOf(currentPage)
            if (currentPageIndex < pages.lastIndex || nextChapter != null) {
                havePrev = true
            }
            if (currentPageIndex > 0 || chapters.prevChapter != null) {
                haveNext = true
            }

            onPageChange = onPageChange@{ index ->
                val currentPage = currentPage ?: return@onPageChange

                val pages = currentPage.chapter.pages ?: return@onPageChange

                val currentPageIndex = pages.indexOf(currentPage)

                if (index == 1) {
                    if (currentPageIndex + 1 < pages.size) {
                        pages[currentPageIndex + 1]
                    } else {
                        nextChapter?.pages?.first()
                    }
                } else {
                    if (currentPageIndex - 1 >= 0) {
                        pages[currentPageIndex - 1]
                    } else {
                        prevChapter?.pages?.last()
                    }
                }?.let { newPage ->
                    val pages = newPage.chapter.pages ?: return@onPageChange

                    this@WebGpuViewer.currentPage = newPage
                    activity.onPageSelected(newPage)

                    val currentPageIndex = pages.indexOf(newPage)

                    if (currentPageIndex < pages.lastIndex || nextChapter != null) {
                        havePrev = true
                    }
                    if (currentPageIndex > 0 || prevChapter != null) {
                        haveNext = true
                    }
                    if (currentPageIndex == 0) {
                        prevChapter?.let { activity.requestPreloadChapter(it) }
                    }
                    if (currentPageIndex == pages.size - 1) {
                        nextChapter?.let { activity.requestPreloadChapter(it) }
                    }
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

        val currentPageIndex = pages.indexOf(page)

        if (currentPageIndex < pages.lastIndex || nextChapter != null) {
            pager.state.havePrev = true
        }
        if (currentPageIndex > 0 || prevChapter != null) {
            pager.state.haveNext = true
        }
        if (currentPageIndex == 0) {
            prevChapter?.let { activity.requestPreloadChapter(it) }
        }
        if (currentPageIndex == pages.size - 1) {
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
                if (isUp) activity.toggleMenu()
            }
        }


//        when (event.keyCode) {
//            KeyEvent.KEYCODE_VOLUME_DOWN -> {
//                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
//                    return false
//                } else if (isUp) {
//                    if (!config.volumeKeysInverted) moveDown() else moveUp()
//                }
//            }
//            KeyEvent.KEYCODE_VOLUME_UP -> {
//                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
//                    return false
//                } else if (isUp) {
//                    if (!config.volumeKeysInverted) moveUp() else moveDown()
//                }
//            }
//            KeyEvent.KEYCODE_DPAD_RIGHT -> {
//                if (isUp) {
//                    if (ctrlPressed) moveToNext() else moveRight()
//                }
//            }
//            KeyEvent.KEYCODE_DPAD_LEFT -> {
//                if (isUp) {
//                    if (ctrlPressed) moveToPrevious() else moveLeft()
//                }
//            }
//            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
//            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
//            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
//            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
//            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
//            else -> return false
//        }
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
