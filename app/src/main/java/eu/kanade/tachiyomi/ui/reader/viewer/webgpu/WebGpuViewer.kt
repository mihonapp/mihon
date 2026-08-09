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
import ca.mpreg.webgpuviewer.draw.Draw
import ca.mpreg.webgpuviewer.draw.clear
import ca.mpreg.webgpuviewer.draw.line
import ca.mpreg.webgpuviewer.renderer.Image
import ca.mpreg.webgpuviewer.renderer.WebGpuRenderer
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
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
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

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = WebGpuConfig(this, scope)

    var viewerChapters: ViewerChapters? = null

    val pages: List<ReaderPage>? get() = (currentPage as? ViewerReaderPage)?.page?.chapter?.pages

    var currentPage: ViewerPage? = null

    val preloadCount = 2
    open val cacheSize = 9

    val pageCache = mutableListOf<ViewerPage>()

    private fun chapterPosition(chapter: ReaderChapter?): Int = when (chapter) {
        viewerChapters?.prevChapter -> -1
        viewerChapters?.currChapter -> 0
        viewerChapters?.nextChapter -> 1
        else -> Int.MAX_VALUE
    }

    private fun pagePosition(page: ViewerPage): Pair<Int, Int> = when (page) {
        is ViewerReaderPage -> Pair(chapterPosition(page.page.chapter), page.page.index)
        is TransitionPage -> {
            val pos = page.prevChapter?.let { chapterPosition(it) } ?: (chapterPosition(page.nextChapter) - 1)
            Pair(pos, Int.MAX_VALUE)
        }

        else -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun evictFarthestPage() {
        val current = currentPage ?: return
        val candidates = pageCache.filter { it !== current }
        if (candidates.isEmpty()) return

        val currentPos = pagePosition(current)
        val sorted =
            candidates.map { it to pagePosition(it) }.sortedWith(compareBy({ it.second.first }, { it.second.second }))

        val currentIdx = sorted.indexOfFirst { (_, pos) ->
            pos.first > currentPos.first || (pos.first == currentPos.first && pos.second > currentPos.second)
        }.let { if (it < 0) sorted.size else it }

        val toRemove = when {
            currentIdx == 0 -> sorted.last()
            currentIdx >= sorted.size -> sorted.first()
            currentIdx > sorted.size / 2 -> sorted.first()
            else -> sorted.last()
        }.first

        pageCache.remove(toRemove)
        toRemove.imagePage.cleanup()
    }

    private inline fun getOrCreatePage(
        crossinline find: () -> ViewerPage?,
        crossinline create: () -> ViewerPage,
    ): ViewerPage {
        return synchronized(pageCache) {
            val r = find() ?: create().also { pageCache.add(it) }
            while (pageCache.size > cacheSize) {
                evictFarthestPage()
            }
            r
        }
    }

    fun getPage(page: ReaderPage): ViewerPage = getOrCreatePage(
        find = { pageCache.find { it is ViewerReaderPage && it.page === page } },
        create = { ViewerReaderPage(page) },
    )

    fun getPage(prevChapter: ReaderChapter?, nextChapter: ReaderChapter?): ViewerPage = getOrCreatePage(
        find = { pageCache.find { it is TransitionPage && it.prevChapter === prevChapter && it.nextChapter === nextChapter } },
        create = { TransitionPage(prevChapter, nextChapter) },
    )

    abstract class ViewerPage {
        abstract val prevChapter: ReaderChapter?
        abstract val nextChapter: ReaderChapter?
        abstract val prev: ViewerPage?
        abstract val next: ViewerPage?

        var isProcessing: Boolean = false

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
                nextPage(i)?.also { preloadPages(it) }?.imagePage
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

            synchronized(pageCache) {
                pageCache.forEach { it.imagePage.cleanup() }
                pageCache.clear()

                currentPage = (currentPage as? ViewerReaderPage)?.page?.let { getPage(it) }
                    ?: (currentPage as? TransitionPage)?.let {
                        getPage(it.prevChapter, it.nextChapter)
                    }
            }

            pager.state.invalidate()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        synchronized(pageCache) {
            pageCache.forEach { it.imagePage.cleanup() }
            pageCache.clear()
        }
        scope.cancel()
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View = pager

    private suspend fun loadPage(page: ViewerReaderPage): InputStream? {
        val loader = page.page.chapter.pageLoader ?: return null

        // TODO: cancellable/queue
        CoroutineScope(Dispatchers.IO).launch {
            loader.loadPage(page.page)
        }

        val downloadProgressJob = CoroutineScope(Dispatchers.Default).launch {
            page.page.progressFlow.collect { value ->
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
                    Log.e("WebGpuViewer", "Error ${state.error}"); false
                }

                Page.State.Ready -> false
            }
        }.collectLatest {}

        downloadProgressJob.cancel()

        return page.page.stream?.invoke()
    }

    private suspend fun decodeReaderPage(page: ViewerReaderPage) {
        synchronized(page) {
            if (page.imagePage !is ImagePage.Dummy) return
            if (page.isProcessing) return
            page.isProcessing = true
        }

        loadPage(page)?.use {
            Log.d("WebGpuViewer", "create page: ${page.page.chapter.chapter.id} ${page.page.index}")

            val dec = ImageDecoder.new(it)
            (0 until dec.pages).map { dec.decodeNext() }
        }?.let { res ->
            val pages = res.map { page -> Pair(Image(page.image, page.width, page.height), page.duration) }
            ImagePage(pages[0].first).apply {
                if (config.imageCropBorders) {
                    trim = Trim.find(image!!, 1f, 1f, 1f, 0.15f)
                }

                parent = pager.state
                x = homeX
                y = homeY
                scale = homeScale

                if (pages.size > 1) {
                    startAnimationLoop(pages, { pager.state.invalidate() })
                }
            }
        }?.also {
            synchronized(pageCache) {
                if (page.imagePage.destroyed) {
                    it.cleanup()
                    return
                }
                page.imagePage = it
                pager.state.invalidate()
            }
        }

        page.isProcessing = false
    }

    private suspend fun createTransitionPage(page: TransitionPage) {
        synchronized(page) {
            if (page.imagePage !is ImagePage.Dummy) return
            if (page.isProcessing) return
            page.isProcessing = true
        }

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

        val imagePage = ImagePage(bitmap, createMipMaps = false).apply {
            minScale = 1f
            maxScale = 1f
        }

        synchronized(pageCache) {
            if (page.imagePage.destroyed) {
                imagePage.cleanup()
                return
            }
            page.imagePage = imagePage
            pager.state.invalidate()
        }

        page.isProcessing = false
    }

    protected fun preloadPage(page: ViewerPage) {
        CoroutineScope(Dispatchers.Default).launch {
            when (page) {
                is ViewerReaderPage -> decodeReaderPage(page)
                is TransitionPage -> createTransitionPage(page)
            }
        }
    }

    protected fun preloadPages(page: ViewerPage) {
        pager.state.post {
            preloadPage(page)

            var p = page
            for (i in 0 until preloadCount) {
                p = p.next ?: break
                preloadPage(p)
            }

            p = page
            for (i in 0 until preloadCount) {
                p = p.prev ?: break
                preloadPage(p)
            }
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val pages = chapters.currChapter.pages ?: return

        this.viewerChapters = chapters

        val requestedPage = pages[min(chapters.currChapter.requestedPage, pages.lastIndex)]

        currentPage = (currentPage ?: getPage(requestedPage)).also { page ->
            (page as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
            preloadPages(page)
        }

        pager.state.apply {
            onPageChange = onPageChange@{ delta ->
                activity.hideMenu()

                val delta = if (isReversed) -delta else delta

                nextPage(delta)?.let { newPage ->
                    currentPage = newPage.also { page ->
                        (page as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
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
        moveToPage(getPage(page))
    }

    private fun moveToPage(newPage: ViewerPage) {
        val previousPage = currentPage

        currentPage = newPage.also { page ->
            (page as? ViewerReaderPage)?.let { activity.onPageSelected(it.page) }
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
