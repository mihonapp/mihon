package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import ca.mpreg.imagedecoder.ImageDecoder
import ca.mpreg.webgpuviewer.Image
import ca.mpreg.webgpuviewer.ImageShaderStackRight
import ca.mpreg.webgpuviewer.Trim
import ca.mpreg.webgpuviewer.WebGpuImageView
import ca.mpreg.webgpuviewer.WebGpuImageViewerPage
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.Dispatchers
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

    init {
        pager.state.apply {
            transition = ImageShaderStackRight::render
            fetchPage = fetch@{ index ->
                val data = this@WebGpuViewer.pages?.get(index) ?: return@fetch null
                val res = withContext(Dispatchers.Default) {
                    data.stream?.invoke()?.let {
                        val dec = ImageDecoder.new(it)
                        dec.decodeNext()
                    }
                } ?: return@fetch null

                WebGpuImageViewerPage(Image(res.image, res.width, res.height)).apply {
                    trim = Trim.find(image, 1f, 1f, 1f, 10f / 255)

                    parent = pager.state
                    x = homeX
                    y = homeY
                    scale = homeScale
                }
            }
            onPageChange = { index ->
                if (!activity.isScrollingThroughPages) {
                    this@WebGpuViewer.scope.launch {
                        activity.hideMenu()
                    }
                }
                this@WebGpuViewer.pages?.get(index)?.let {
                    activity.onPageSelected(it)
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

//    private fun checkAllowPreload(page: ReaderPage?): Boolean {
//        // Page is transition page - preload allowed
//        page ?: return true
//
//        // Initial opening - preload allowed
//        currentPage ?: return true
//
//        // Allow preload for
//        // 1. Going to next chapter from chapter transition
//        // 2. Going between pages of same chapter
//        // 3. Next chapter page
//        return when (page.chapter) {
//            (currentPage as? ChapterTransition.Next)?.to -> true
//            (currentPage as? ReaderPage)?.chapter -> true
//            adapter.nextTransition?.to -> true
//            else -> false
//        }
//    }

    var pages: List<ReaderPage>? = null

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Notify holder of page change
//        getPageHolder(page)?.onPageSelected(forward)

        // Skip preload on inserts it causes unwanted page jumping
        if (page is InsertPage) {
            return
        }

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
//        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
//            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
//            adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
//        }
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

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        setChaptersInternal(chapters)
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        this.pages = chapters.currChapter.pages?.reversed() // TODO: RTL

        pager.state.apply {
            pageCount = this@WebGpuViewer.pages?.size ?: 0
            currentPageIndex = this@WebGpuViewer.pages?.size?.let { it - 1 } ?: 0 // TODO: RTL

            post {
                render()
            }
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        pager.state.currentPageIndex = pages?.indexOf(page) ?: 0
        pager.state.render()
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
//        if (pager.currentItem != adapter.count - 1) {
//            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
//            if (holder != null && config.navigateToPan && holder.canPanRight()) {
//                holder.panRight()
//            } else {
//                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
//            }
//        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
//        if (pager.currentItem != 0) {
//            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
//            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
//                holder.panLeft()
//            } else {
//                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
//            }
//        }
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    private fun refreshAdapter() {
//        val currentItem = pager.currentItem
//        adapter.refresh()
//        pager.adapter = adapter
//        pager.setCurrentItem(currentItem, false)
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
