package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderItemPair
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(this, scope)

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersInternal(viewerChapters)
                    awaitingIdleViewerChapters = null
                    if (viewerChapters.currChapter.pages?.size == 1) {
                        adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
                    }
                }
            }
        }

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            if (!activity.isScrollingThroughPages) {
                activity.hideMenu()
            }
            onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.isFocusable = false
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = { event ->
            val viewPosition = IntArray(2)
            pager.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            pager.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT -> moveToNext()
                NavigationRegion.PREV -> moveToPrevious()
                NavigationRegion.RIGHT -> moveRight()
                NavigationRegion.LEFT -> moveLeft()
            }
        }
        pager.longTapListener = f@{
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val item = adapter.items.getOrNull(pager.currentItem)
                val first = (item as? ReaderItemPair)?.first ?: item
                if (first is ReaderPage) {
                    activity.onPageLongTap(first)
                    return@f true
                }
            }
            false
        }

        config.dualPageSplitChangedListener = { enabled ->
            if (!enabled) {
                cleanupPageSplit()
            }
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
        
        // Force re-measure of children when Pager size changes (Spanning)
        pager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = (right - left) != (oldRight - oldLeft)
            if (widthChanged) {
                pager.post { refreshAdapter() }
            }
        }
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return pager
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? {
        pager.children.forEach { child ->
            if (child is PagerPageHolder && child.item == page) {
                return child
            }
            if (child is PagerPagePairHolder) {
                child.children.filterIsInstance(PagerPageHolder::class.java).forEach {
                    if (it.item == page) return it
                }
            }
        }
        return null
    }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    private fun onPageChange(position: Int) {
        val page = adapter.items.getOrNull(position)
        if (page != null && currentPage != page) {
            val allowPreload = checkAllowPreload(page as? ReaderPage)
            val forward = when {
                currentPage is ReaderPage && page is ReaderPage -> {
                    // if both pages have the same number, it's a split page with an InsertPage
                    if (page.number == (currentPage as ReaderPage).number) {
                        // the InsertPage is always the second in the reading direction
                        page is InsertPage
                    } else {
                        page.number > (currentPage as ReaderPage).number
                    }
                }
                currentPage is ChapterTransition.Prev && page is ReaderPage ->
                    false
                else -> true
            }
            currentPage = page
            when (page) {
                is ReaderPage -> {
                    onReaderPageSelected(page, allowPreload, forward)
                    // Notify callback for dual-screen sync
                    onPageChangedCallback?.invoke(page.index)
                }
                is ReaderItemPair -> {
                    // In Side-by-Side View, we notify for the first page of the pair
                    // Mihon usually handles progress based on one page
                    val first = page.first
                    if (first is ReaderPage) {
                        onReaderPageSelected(first, allowPreload, forward)
                        onPageChangedCallback?.invoke(first.index)
                        
                        // Also notify the second page if it exists so it can start loading/rendering
                        val second = page.second
                        if (second is ReaderPage) {
                            getPageHolder(second)?.onPageSelected(forward)
                        }
                    } else if (first is ChapterTransition) {
                        onTransitionSelected(first)
                    }
                }
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val currentChapter = (currentPage as? ReaderPage)?.chapter 
            ?: (currentPage as? ReaderItemPair)?.first.let { (it as? ReaderPage)?.chapter }

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            currentChapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Notify holder of page change
        getPageHolder(page)?.onPageSelected(forward)

        // Skip preload on inserts it causes unwanted page jumping
        if (page is InsertPage) {
            return
        }

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
        }
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
        if (isIdle) {
            setChaptersInternal(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        // Remove listener so the change in item doesn't trigger it
        pager.removeOnPageChangeListener(pagerListener)

        val forceTransition = config.alwaysShowChapterTransition ||
            adapter.items.getOrNull(pager.currentItem) is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            pager.isVisible = true
        } else {
            // Restore position based on the requested page of the new current chapter
            // This fixes the issue where switching chapters via "preview pages" leaves the ViewPager 
            // at a stale index (e.g. index 10) which maps to a random page in the new adapter.
            val requestedPage = chapters.currChapter.pages?.getOrNull(chapters.currChapter.requestedPage)
            if (requestedPage != null) {
                val newPosition = adapter.items.indexOf(requestedPage)
                if (newPosition != -1) {
                    pager.setCurrentItem(newPosition, false)
                } else {
                    // Try to find it in a pair
                    val pairPosition = adapter.items.indexOfFirst { 
                        it is ReaderItemPair && (it.first == requestedPage || it.second == requestedPage) 
                    }
                    if (pairPosition != -1) {
                        pager.setCurrentItem(pairPosition, false)
                    }
                }
            }
        }

        pager.addOnPageChangeListener(pagerListener)
        // Manually call onPageChange to update the UI
        onPageChange(pager.currentItem)
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, true)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            }
        } else {
            val pairPosition = adapter.items.indexOfFirst { it is ReaderItemPair && (it.first == page || it.second == page) }
            if (pairPosition != -1) {
                val currentPosition = pager.currentItem
                pager.setCurrentItem(pairPosition, true)
                if (currentPosition == pairPosition) {
                    // Manually trigger onReaderPageSelected if we're moving to a page within the same pair
                    // or if ViewPager didn't trigger a change.
                    onReaderPageSelected(page, checkAllowPreload(page), true)
                }
            } else {
                logcat { "Page $page not found in adapter" }
            }
        }
    }

    /**
     * Tells this viewer to move to the next page/pair.
     */
    override fun moveToNext(): Boolean {
        return if (pager.currentItem != adapter.count - 1) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)
            
            if (config.navigateToPan && (firstHolder?.canPanRight() == true || secondHolder?.canPanRight() == true)) {
                if (firstHolder?.canPanRight() == true) firstHolder.panRight()
                else secondHolder?.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
            true
        } else {
            false
        }
    }

    /**
     * Tells this viewer to move to the previous page/pair.
     */
    override fun moveToPrevious(): Boolean {
        return if (pager.currentItem != 0) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)

            if (config.navigateToPan && (firstHolder?.canPanLeft() == true || secondHolder?.canPanLeft() == true)) {
                if (secondHolder?.canPanLeft() == true) secondHolder.panLeft()
                else firstHolder?.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
            true
        } else {
            false
        }
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNextPage() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPreviousPage() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    fun moveRight(): Boolean {
        return if (pager.currentItem != adapter.count - 1) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)
            
            if (config.navigateToPan && (firstHolder?.canPanRight() == true || secondHolder?.canPanRight() == true)) {
                if (firstHolder?.canPanRight() == true) firstHolder.panRight()
                else secondHolder?.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
            true
        } else {
            false
        }
    }

    /**
     * Moves to the page at the left.
     */
    fun moveLeft(): Boolean {
        return if (pager.currentItem != 0) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)

            if (config.navigateToPan && (firstHolder?.canPanLeft() == true || secondHolder?.canPanLeft() == true)) {
                if (secondHolder?.canPanLeft() == true) secondHolder.panLeft()
                else firstHolder?.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
            true
        } else {
            false
        }
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        activity.loadPreviousPage()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        activity.loadNextPage()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    internal fun refreshAdapter() {
        // Capture the current item before refreshing
        val currentItem = (currentPage as? ReaderItemPair)?.first ?: currentPage
        
        adapter.refresh()
        // Nuclear option: re-set adapter to clear ViewPager view cache
        pager.adapter = null
        pager.adapter = adapter
        
        // Force a re-grouping of items if chapters are loaded
        activity.viewModel.state.value.viewerChapters?.let {
            // Force transition false to preserve current chapter structure unless it was already a transition
            val wasTransition = adapter.items.getOrNull(pager.currentItem) is ChapterTransition
            adapter.setChapters(it, wasTransition)
        }
        
        // Restore Position
        if (currentItem != null) {
            when (currentItem) {
                is ReaderPage -> moveToPage(currentItem)
                is ChapterTransition -> {
                    // Robustly find the matching transition in the new adapter
                    val newIndex = adapter.items.indexOfFirst { item ->
                        val unwrapped = (item as? ReaderItemPair)?.first ?: item
                        unwrapped is ChapterTransition && 
                        // Compare type (Prev/Next) and To/From chapter IDs to ensure identity
                        unwrapped::class == currentItem::class &&
                        unwrapped.to?.chapter?.id == currentItem.to?.chapter?.id
                    }
                    
                    if (newIndex != -1) {
                        pager.setCurrentItem(newIndex, false)
                        onPageChange(newIndex)
                    }
                }
            }
        }
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
                    if (ctrlPressed) activity.loadNextPage() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) activity.loadPreviousPage() else moveLeft()
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
            adapter.onPageSplit(currentPage, newPage)
        }
    }

    private fun cleanupPageSplit() {
        adapter.cleanupPageSplit()
    }

    /**
     * Checks if the current page is zoomed in
     */
    fun isCurrentPageZoomed(): Boolean {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            val first = (pair.first as? ReaderPage)?.let(::getPageHolder)?.isZoomed() == true
            val second = (pair.second as? ReaderPage)?.let(::getPageHolder)?.isZoomed() == true
            return first || second
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        return holder?.isZoomed() == true
    }

    /**
     * Callback invoked when the page changes. Used for dual-screen synchronization.
     */
    var onPageChangedCallback: ((Int) -> Unit)? = null

    /**
     * Called when an external pan event is received (e.g. from a dual-screen touchpad)
     */
    override fun handleExternalPan(dx: Float, dy: Float) {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            (pair.first as? ReaderPage)?.let(::getPageHolder)?.panBy(dx, dy)
            (pair.second as? ReaderPage)?.let(::getPageHolder)?.panBy(dx, dy)
            return
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        holder?.panBy(dx, dy)
    }

    /**
     * Called when zoom should be reset to default (e.g. double-tap on touchpad)
     */
    override fun handleExternalZoomReset() {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            (pair.first as? ReaderPage)?.let(::getPageHolder)?.resetZoom()
            (pair.second as? ReaderPage)?.let(::getPageHolder)?.resetZoom()
            return
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        holder?.resetZoom()
    }
}