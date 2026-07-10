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
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.ByteBuffer
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

    val pages: List<ReaderPage>? get() = (currentPage as? ViewerReaderPage)?.page?.chapter?.pages

    abstract class ViewerPage {
        abstract val prevChapter: ReaderChapter?
        abstract val nextChapter: ReaderChapter?
        abstract val prev: ViewerPage?
        abstract val next: ViewerPage?
        abstract val id: String
    }

    inner class TransitionPage(override val prevChapter: ReaderChapter?, override val nextChapter: ReaderChapter?) :
        ViewerPage() {
        override val prev: ViewerPage?
            get() = prevChapter?.pages?.lastOrNull()?.let { ViewerReaderPage(it) }

        override val next: ViewerPage?
            get() = nextChapter?.pages?.firstOrNull()?.let { ViewerReaderPage(it) }

        override val id: String
            get() = "${prevChapter?.chapter?.id ?: ""}:${nextChapter?.chapter?.id ?: ""}"
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
                pages.getOrNull(page.index - 1)?.let { ViewerReaderPage(it) } ?: if (prevChapter != null) {
                    if (config.alwaysShowChapterTransition) {
                        prevChapter?.let { TransitionPage(it, page.chapter) }
                    } else {
                        prevChapter?.pages?.lastOrNull()?.let { ViewerReaderPage(it) }
                    }
                } else TransitionPage(null, page.chapter)
            }

        override val next: ViewerPage?
            get() = page.chapter.pages?.let { pages ->
                pages.getOrNull(page.index + 1)?.let { ViewerReaderPage(it) } ?: if (nextChapter != null) {
                    if (config.alwaysShowChapterTransition) {
                        nextChapter?.let { TransitionPage(page.chapter, it) }
                    } else {
                        nextChapter?.pages?.firstOrNull()?.let { ViewerReaderPage(it) }
                    }
                } else TransitionPage(page.chapter, null)
            }

        override val id: String
            get() = "${page.chapter.chapter.id ?: ""}:${page.index}"
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

    open fun updateTransitionAnimation() {
        pager.state.transition = when (config.transitionAnimation) {
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

    val transitionCache = LinkedHashMap<String, ImagePage?>(2, 0.75f, true)

    init {
        updateTransitionAnimation()

        pager.state.apply {
            fetchPage = fetch@{ index ->
                val i = if (isReversed) -index else index
                nextPage(i)?.let { viewerPage ->
                    if (viewerPage is ViewerReaderPage) {
                        synchronized(pageCache) {
                            pageCache[viewerPage.id]
                        } ?: ImagePage.Dummy(400, 400).also {
                            preloadPage(viewerPage)
                        }
                    } else {
                        synchronized(transitionCache) {
                            transitionCache[viewerPage.id]
                        } ?: ImagePage.Dummy(400, 400).also {
                            CoroutineScope(Dispatchers.Default).launch {
                                preloadPage(viewerPage)
                            }
                        }
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
                    (currentPage as? ViewerReaderPage)?.let {
                        activity.onPageLongTap(it.page)
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
    override fun getView(): View = pager

    var currentPage: ViewerPage? = null

    val preloadCount = 3
    open val cacheSize = 9

    val pageCache = LinkedHashMap<String, ImagePage?>(cacheSize, 0.75f, true)

    var decodeJob: Job? = null
    val decodeQueue = mutableListOf<ViewerPage>()

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

    private fun popPreload(): ViewerPage? {
        synchronized(decodeQueue) {
            return decodeQueue.removeFirstOrNull()
        }
    }

    private suspend fun decodeReaderPage(page: ViewerReaderPage) {
        synchronized(pageCache) {
            if (pageCache[page.id] != null) return
        }

        loadPage(page.page)?.use {
            Log.i("WebGpuViewer", "create page: ${page.page.chapter.chapter.id} ${page.page.index}")

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
                pageCache[page.id] = it
                while (pageCache.size > cacheSize) {
                    pageCache.remove(pageCache.keys.first())
                }
            }
        }
        pager.state.invalidate()
    }

    private suspend fun createTransitionPage(page: TransitionPage) {
        val bitmap = createBitmap(pager.state.width, pager.state.height)

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }

        val x = bitmap.width / 2f
        var y = bitmap.height / 2f

        val lines = mutableListOf<Pair<String, Float>>()

        page.prevChapter?.chapter?.let { chapter ->
            lines.add(Pair("Previous:", 48f))
            lines.add(Pair(chapter.name, 48f))
            page.nextChapter?.chapter?.let { lines.add(Pair("", 48f)) }
        }

        page.nextChapter?.chapter?.let { chapter ->
            lines.add(Pair("Next:", 48f))
            lines.add(Pair(chapter.name, 48f))
        }

        y -= lines.map { it.second }.sum() / 2

        lines.forEach {
            canvas.drawText(it.first, x, y + it.second, paint)
            y += it.second
        }

        val buf = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buf)

        val image = ImagePage(Image(buf, bitmap.width, bitmap.height)).apply {
            minScale = 1f
            maxScale = 1f
        }

        synchronized(transitionCache) {
            transitionCache[page.id] = image
        }

        pager.state.invalidate()
    }

    private fun startDecodeQueue() {
        decodeJob = decodeJob ?: CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    val page = popPreload() ?: return@launch
                    when (page) {
                        is ViewerReaderPage -> decodeReaderPage(page)
                        is TransitionPage -> createTransitionPage(page)
                    }
                }
            } finally {
                synchronized(decodeQueue) {
                    decodeJob = null
                }
            }
        }
    }

    protected fun preloadPage(page: ViewerPage) {
        synchronized(decodeQueue) {
            decodeQueue.add(page)
            startDecodeQueue()
        }
    }

    protected fun preloadPages(page: ViewerPage) {
        val page = page as? ViewerReaderPage ?: return
        val pages = page.page.chapter.pages ?: return

        synchronized(decodeQueue) {
            decodeQueue.clear()

            val index = page.page.index

            for (i in index until min(index + preloadCount, pages.size)) {
                decodeQueue.add(ViewerReaderPage(pages[i]))
            }

            for (i in max(0, index - preloadCount) until index) {
                decodeQueue.add(ViewerReaderPage(pages[i]))
            }

            page.prevChapter?.let { prevChapter ->
                if (prevChapter.state !is ReaderChapter.State.Loaded && index - preloadCount < 0) {
                    prevChapter.let { chapter ->
                        CoroutineScope(Dispatchers.Default).launch {
                            activity.viewModel.preload(chapter)
                            preloadPages(page)
                        }
                    }
                }
            }

            page.nextChapter?.let { nextChapter ->
                if (nextChapter.state !is ReaderChapter.State.Loaded && index + preloadCount > pages.lastIndex) {
                    nextChapter.let { chapter ->
                        CoroutineScope(Dispatchers.Default).launch {
                            activity.viewModel.preload(chapter)
                            preloadPages(page)
                        }
                    }
                }
            }

            page.prevChapter?.pages?.takeLast(max(0, preloadCount - index))?.forEach { page ->
                decodeQueue.add(ViewerReaderPage(page))
            }

            page.nextChapter?.pages?.take(max(0, preloadCount - (pages.lastIndex - index)))?.forEach { page ->
                decodeQueue.add(ViewerReaderPage(page))
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

        currentPage = (currentPage ?: ViewerReaderPage(
            pages[min(chapters.currChapter.requestedPage, pages.lastIndex)],
        )).also { page ->
            (page as? ViewerReaderPage)?.let {
                activity.onPageSelected(it.page)
                preloadPages(it)
            }
        }

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                activity.hideMenu()

                val delta = if (isReversed) -delta else delta

                nextPage(delta)?.let { newPage ->
                    currentPage = newPage.also { page ->
                        (page as? ViewerReaderPage)?.let {
                            activity.onPageSelected(it.page)
                            preloadPages(it)
                        }
                    }

                    (currentPage as? TransitionPage)?.let { currentPage ->
                        if (currentPage.prevChapter == null || currentPage.nextChapter == null) {
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
        moveToPage(ViewerReaderPage(page).also { preloadPages(it) })
    }

    fun moveToPage(newPage: ViewerPage) {
        val previousPage = currentPage

        currentPage = newPage.also { page ->
            (page as? ViewerReaderPage)?.let {
                activity.onPageSelected(it.page)
                preloadPages(it)
            }
        }

        (currentPage as? TransitionPage)?.let { currentPage ->
            if (currentPage.prevChapter == null || currentPage.nextChapter == null) {
                activity.showMenu()
            }
        }

        val direction = when (previousPage) {
            null -> return

            is ViewerReaderPage if newPage is ViewerReaderPage -> if (previousPage.page.chapter == newPage.page.chapter) {
                (newPage.page.index - previousPage.page.index).coerceIn(-1, 1)
            } else if (previousPage.page.chapter == newPage.prevChapter) 1 else -1

            is TransitionPage if newPage is ViewerReaderPage -> if (previousPage.nextChapter == newPage.page.chapter) 1 else -1

            is ViewerReaderPage if newPage is TransitionPage -> if (previousPage.page.chapter == newPage.prevChapter) 1 else -1

            else -> 0
        }

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
