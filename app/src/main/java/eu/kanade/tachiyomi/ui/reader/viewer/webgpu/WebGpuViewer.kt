package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.PointF
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.ImageView
import ca.mpreg.webgpuviewer.Trim
import ca.mpreg.webgpuviewer.renderer.Image
import ca.mpreg.webgpuviewer.transition.TransitionBasic
import ca.mpreg.webgpuviewer.transition.TransitionCube
import ca.mpreg.webgpuviewer.transition.TransitionCubeOuter
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
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
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
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class WebGpuViewer(
    val activity: ReaderActivity,
    val isReversed: Boolean,
    val isVertical: Boolean,
    val pager: ImageView = ImageView(activity, isVertical = isVertical),
) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope)

    var viewerChapters: ViewerChapters? = null

    val pages: List<ReaderPage>? get() = currentPage?.chapter?.pages

    fun nextPage(count: Int): ReaderPage? {
        val chapters = viewerChapters ?: return null
        val currentPage = currentPage ?: return null
        val chapter = currentPage.chapter
        val pages = chapter.pages ?: return null
        val n = currentPage.index + count

        val currChapter = chapters.currChapter
        val prevChapter = chapters.prevChapter
        val nextChapter = chapters.nextChapter

        return when (chapter) {
            currChapter -> if (n >= 0 && n <= pages.lastIndex) {
                pages[n]
            } else if (count > 0) {
                nextChapter?.pages?.get(max(0, count - 1 - (pages.lastIndex - currentPage.index)))
            } else {
                prevChapter?.pages?.takeLast(max(0, abs(currentPage.index + count)))?.firstOrNull()
            }

            prevChapter -> if (n >= 0 && n <= pages.lastIndex) {
                pages[n]
            } else if (count > 0) {
                currChapter.pages?.get(max(0, count - 1 - (pages.lastIndex - currentPage.index)))
            } else {
                null
            }

            nextChapter -> if (n >= 0 && n <= pages.lastIndex) {
                pages[n]
            } else if (count < 0) {
                currChapter.pages?.takeLast(max(0, abs(currentPage.index + count)))?.firstOrNull()
            } else {
                null
            }

            else -> null
        }
    }

    open fun updateTransitionAnimation() {
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
            }
        }
    }

    init {
        updateTransitionAnimation()

        pager.state.apply {
            fetchPage = fetch@{ index ->
                val i = if (isReversed) -index else index
                nextPage(i)?.let { page ->
                    val id = ((page.chapter.chapter.id ?: 0) shl 32) + page.index
                    synchronized(pageCache) {
                        pageCache[id]
                    } ?: ImagePage.Dummy(400, 400).also {
                        preloadPage(page)
                    }
                }
            }

            onTap = { offset ->
                when (config.navigator.getAction(PointF(offset.x, offset.y))) {
                    NavigationRegion.MENU -> activity.toggleMenu()
                    NavigationRegion.NEXT -> moveToNext()
                    NavigationRegion.PREV -> moveToPrevious()
                    NavigationRegion.RIGHT -> moveRight()
                    NavigationRegion.LEFT -> moveLeft()
                }
            }

            onLongTap = { offset ->
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
            synchronized(pageCache) {
                pageCache.clear()
            }

            pager.state.invalidate()

            updateTransitionAnimation()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        Log.i("WebGpuViewer", "destroy")
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
    open val cacheSize = 9

    val pageCache = LinkedHashMap<Long, ImagePage?>(cacheSize, 0.75f, true)

    var decodeJob: Job? = null
    val decodeQueue = mutableListOf<ReaderPage>()

    private suspend fun loadPage(page: ReaderPage): InputStream? {
        val loader = page.chapter.pageLoader ?: return null

        CoroutineScope(Dispatchers.IO).launch {
            loader.loadPage(page)
        }

        val downloadProgressJob = CoroutineScope(Dispatchers.Default).launch {
            page.progressFlow.collectLatest { value ->
//                Log.i("WebGpuViewer", "DownloadImage: $value")
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

    private fun popPreload(): ReaderPage? {
        synchronized(decodeQueue) {
            return decodeQueue.removeFirstOrNull()
        }
    }

    private fun startDecodeQueue() {
        decodeJob = decodeJob ?: CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    val page = popPreload() ?: return@launch

                    val id = ((page.chapter.chapter.id ?: 0) shl 32) + page.index
                    synchronized(pageCache) {
                        if (pageCache[id] != null) continue
                    }

                    withContext(Dispatchers.Default) {
                        loadPage(page)?.use {
                            Log.i("WebGpuViewer", "createPage: ${page.chapter.chapter.id} ${page.index}")

                            val dec = ImageDecoder.new(it)
                            dec.decodeNext()
                        }?.let { res ->
                            ImagePage(Image(res.image, res.width, res.height)).apply {
                                if (config.imageCropBorders) {
                                    trim = Trim.find(image!!, 1f, 1f, 1f, 10f / 255)
                                }

                                parent = pager.state
                                x = homeX
                                y = homeY
                                scale = homeScale
                            }
                        }.also {
                            synchronized(pageCache) {
                                pageCache[id] = it
                                while (pageCache.size > cacheSize) {
                                    pageCache.remove(pageCache.keys.first())
                                }
                            }
                        }
                        pager.state.invalidate()
                    }
                }
            } finally {
                decodeJob = null
            }
        }
    }

    protected fun preloadPage(page: ReaderPage) {
        synchronized(decodeQueue) {
            decodeQueue.add(page)
            startDecodeQueue()
        }
    }

    protected fun preloadPages(page: ReaderPage) {
        val pages = page.chapter.pages ?: return

        synchronized(decodeQueue) {
            decodeQueue.clear()

            for (i in page.index until min(page.index + preloadCount, pages.size)) {
                decodeQueue.add(pages[i])
            }

            for (i in max(0, page.index - preloadCount) until page.index) {
                decodeQueue.add(pages[i])
            }

            viewerChapters?.prevChapter?.let { prevChapter ->
                if (prevChapter.state !is ReaderChapter.State.Loaded && page.index - preloadCount < 0) {
                    prevChapter.let { chapter ->
                        CoroutineScope(Dispatchers.Default).launch {
                            activity.viewModel.preload(chapter)
                            preloadPages(page)
                        }
                    }
                }
            }

            viewerChapters?.nextChapter?.let { nextChapter ->
                if (nextChapter.state !is ReaderChapter.State.Loaded && page.index + preloadCount > pages.lastIndex) {
                    nextChapter.let { chapter ->
                        CoroutineScope(Dispatchers.Default).launch {
                            activity.viewModel.preload(chapter)
                            preloadPages(page)
                        }
                    }
                }
            }

            viewerChapters?.prevChapter?.pages?.takeLast(max(0, preloadCount - page.index))?.forEach { page ->
                decodeQueue.add(page)
            }

            viewerChapters?.nextChapter?.pages?.take(max(0, preloadCount - (pages.lastIndex - page.index)))
                ?.forEach { page ->
                    decodeQueue.add(page)
                }

            startDecodeQueue()
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        this.viewerChapters = chapters

        if (currentPage == null) {
            currentPage = pages[min(chapters.currChapter.requestedPage, pages.lastIndex)]
            chapters.prevChapter?.let {
                CoroutineScope(Dispatchers.Default).launch {
                    activity.viewModel.preload(it)
                    currentPage?.let { page -> preloadPages(page) }
                }
            }

            currentPage?.let { activity.onPageSelected(it) }
        }

        currentPage?.let { preloadPages(it) }

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                if (!activity.isScrollingThroughPages) {
                    activity.hideMenu()
                }

                val delta = if (isReversed) -delta else delta

                nextPage(delta)?.let { newPage ->
                    this@WebGpuViewer.currentPage = newPage

                    activity.onPageSelected(newPage)
                    preloadPages(newPage)
                }
            }

            invalidate()
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val previousPage = currentPage
        val pages = page.chapter.pages ?: return

        if (page.index == 0) {
            viewerChapters?.prevChapter?.let { activity.requestPreloadChapter(it) }
        }
        if (page.index == pages.size - 1) {
            viewerChapters?.nextChapter?.let { activity.requestPreloadChapter(it) }
        }

        val direction = when {
            previousPage == null -> 0
            previousPage.chapter == page.chapter -> (page.index - previousPage.index).coerceIn(-1, 1)
            else -> {
                val chapters = viewerChapters
                when (page.chapter) {
                    chapters?.nextChapter -> 1
                    chapters?.prevChapter -> -1
                    else -> 0
                }
            }
        }

        if (direction == 0) {
            currentPage = page
            activity.onPageSelected(page)
            pager.state.invalidate()
            preloadPages(page)
            return
        }

        currentPage = page
        activity.onPageSelected(page)
        preloadPages(page)

        pager.state.animatePageTurn(if (isReversed) direction else -direction)
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
            if (config.navigateToPan && (!page.atHome)) {
                val maxX = pager.state.maxX(page.width, page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x - c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }

            nextPage(1)?.let { page -> moveToPage(page) }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        pager.state.getPage(0)?.let { page ->
            if (config.navigateToPan && (!page.atHome)) {
                val maxX = pager.state.maxX(page.width, page.scale)
                val c = if (isReversed) -1 else 1
                val x = (page.x + c / page.scale).coerceIn(-maxX, maxX)
                if (x != page.x) {
                    page.animateTo(targetX = x, targetY = page.y)
                    return
                }
            }

            nextPage(-1)?.let { page -> moveToPage(page) }
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
}
