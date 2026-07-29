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
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    // Non-private so the adapter's one-shot offset auto-detect can run on the viewer's lifecycle.
    internal val scope = MainScope()

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
     * Set to the chapter transition a navigation press was stranded on because its destination
     * chapter hadn't loaded yet (see [onEdgeTransition]); consumed by [setChaptersInternal] once that
     * chapter loads, so the dropped press is honored. Cleared whenever the reader lands back on a page.
     */
    private var pendingChapterCross: ChapterTransition? = null

    /**
     * The item shiftSpreadPairing is currently anchored on, kept across a single toggle so a
     * second toggle right after the first lands back where it started. Either a ReaderPage (see
     * asComparablePage) or, while sitting on a chapter-transition screen, that same
     * ChapterTransition instance; see shiftSpreadPairing.
     */
    private var spreadShiftAnchor: Any? = null

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
        pager.longTapListener = f@{ event ->
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val page = pressedPage(adapter.items.getOrNull(pager.currentItem), event)
                if (page != null) {
                    activity.onPageLongTap(page)
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
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
        adapter.recyclePool()
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
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .flatMap { if (it is PagerSpreadHolder) listOf(it.leftHolder, it.rightHolder) else listOf(it) }
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item == page }

    /**
     * A [PageSpread] is compared/tracked by its later, higher-index page: reaching a spread
     * means the reader has effectively reached that page.
     */
    private fun Any.asComparablePage(): ReaderPage? = PagerNavigation.comparablePage(this)

    /**
     * The page a long-tap acts on. A spread resolves to the pressed half so either page is reachable
     * and the action hits the one under the finger; a lone page resolves to itself. Distinct from
     * [asComparablePage], which reduces a spread to its later page for progress tracking; a per-page
     * action must instead target exactly the page pressed. Falls back to the tracked page if the
     * spread's view isn't attached (it always is for the current item; defensive only).
     */
    private fun pressedPage(item: Any?, event: MotionEvent): ReaderPage? = when (item) {
        is PageSpread ->
            pager.children.filterIsInstance<PagerSpreadHolder>()
                .firstOrNull { it.item == item }
                ?.pageAt(event.rawX)
                ?: item.secondPage
        else -> item?.asComparablePage()
    }

    /**
     * The anchor for relocating the reader after the item list is rebuilt under a fixed numeric index
     * (a chapter-window rebuild or a pairing change). A [ChapterTransition] anchors on itself (a rebuild
     * constructs a fresh instance its own `equals` re-finds); anything else anchors on the leading
     * page of the current spread, not the later one, because a shape change at the reading position can
     * leave the leading page solo, and anchoring on the later page would skip past it onto the next
     * spread. When the shape is unchanged, both resolve to the same spread.
     */
    private fun Any.asShiftAnchor(): Any? = this as? ChapterTransition ?: PagerNavigation.leadingPage(this)

    /**
     * Finds [anchor] in the adapter's current items and moves the pager there if found; a
     * no-op if [anchor] is null or no longer present. [ChapterTransition] is matched structurally
     * (its own `equals`, since a rebuild always constructs fresh instances); a page is matched by
     * reference, either standalone or as either half of a [PageSpread] (which pairing may have
     * regrouped it into or out of since [anchor] was captured).
     */
    private fun relocateTo(anchor: Any?) {
        if (anchor == null) return
        val position = PagerNavigation.positionOfAnchor(adapter.items, anchor)
        if (position != -1) {
            pager.setCurrentItem(position, false)
        }
    }

    /**
     * Called when a new page (a [ReaderPage], [PageSpread] or [ChapterTransition]) is marked as active
     */
    private fun onPageChange(position: Int) {
        // Invalidate any pending spread-shift anchor on every position change; shiftSpreadPairing
        // restores it immediately afterward when it's the one calling this, so it only actually
        // stays cleared when some *other* navigation happened. See shiftSpreadPairing for why.
        spreadShiftAnchor = null
        val page = adapter.items.getOrNull(position)
        // Back on real content: any stranded-cross intent is moot (a turn away from the transition,
        // or the successful cross itself).
        if (page is ReaderPage) pendingChapterCross = null
        if (page != null && currentPage != page) {
            val comparablePage = page.asComparablePage()
            val previousComparablePage = currentPage?.asComparablePage()
            val allowPreload = checkAllowPreload(comparablePage)
            val forward = PagerNavigation.isForwardTurn(
                previous = previousComparablePage,
                current = comparablePage,
                cameFromPrevTransition = currentPage is ChapterTransition.Prev,
            )
            currentPage = page
            when (page) {
                is ReaderPage -> onReaderPageSelected(page, allowPreload, forward)
                is PageSpread -> onReaderPageSelected(
                    page.secondPage,
                    allowPreload,
                    forward,
                    extraHolderPage = page.firstPage,
                )
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            (currentPage as? ReaderPage)?.chapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(
        page: ReaderPage,
        allowPreload: Boolean,
        forward: Boolean,
        extraHolderPage: ReaderPage? = null,
    ) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        // Track/persist progress against the later page actually reached (page), so a chapter
        // that ends on a full spread still hits the exact last-page-index match that marks it
        // read, but show the spread's earlier page number on screen (extraHolderPage), which
        // matches what other readers do and reads more naturally as "the page I turned to."
        activity.onPageSelected(page, displayPage = extraHolderPage ?: page)

        // Notify holder of page change
        getPageHolder(page)?.onPageSelected(forward)
        extraHolderPage?.let { getPageHolder(it)?.onPageSelected(forward) }

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

        val currentItem = adapter.items.getOrNull(pager.currentItem)
        val forceTransition = config.alwaysShowChapterTransition || currentItem is ChapterTransition
        // adapter.setChapters() rebuilds the item list, which can change how many items precede
        // whatever's currently on screen, e.g. a transition only included here because
        // forceTransition is currently true stops being included once the read position moves off
        // it, shifting every later index back by one. Value equality on the item types
        // (ChapterTransition, PageSpread) normally lets ViewPager's own dataSetChanged() find the
        // current item's new position and retarget pager.currentItem to follow it. This anchor is
        // a belt-and-suspenders relocate for the one case that can't: an item that changed
        // shape across the rebuild (a page that went solo <-> paired), which no longer
        // compares equal to anything, so ViewPager would clamp the stale index instead.
        val anchor = currentItem?.asShiftAnchor()
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set.
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            pager.isVisible = true
        } else {
            relocateTo(anchor)
        }

        pager.addOnPageChangeListener(pagerListener)
        // Manually call onPageChange to update the UI
        onPageChange(pager.currentItem)

        // A press stranded on a chapter transition whose chapter hadn't loaded yet was remembered;
        // that chapter has now arrived and the transition is no longer the terminal item, so carry
        // the reader on across the boundary, honoring the dropped press instead of leaving the
        // reader parked on the transition needing another press.
        val position = pager.currentItem
        val item = adapter.items.getOrNull(position)
        if (pendingChapterCross != null && item is ChapterTransition &&
            position != 0 && position != adapter.count - 1
        ) {
            pendingChapterCross = null
            when (item) {
                is ChapterTransition.Next -> moveToNext()
                is ChapterTransition.Prev -> moveToPrevious()
            }
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        // Record the navigation target as the chapter's requestedPage, the single source of truth for
        // where a chapter lays out. During a decide-before-display hold the page-change listener is
        // detached, so this direct write is the only signal that survives; without it a chapter reached
        // mid-hold (e.g. chapter-forward into a still-detecting spread) lays out on a stale requestedPage
        // when detection settles, landing on the wrong page.
        page.chapter.requestedPage = page.index
        jumpToPage(page, smooth = true)
    }

    /**
     * Moves the pager to [page] (wrapped in a [PageSpread] or standalone). [smooth] animates the
     * scroll; pass false to jump instantly, used for the first layout of a chapter, where an
     * animation is wrong for a chapter open.
     */
    private fun jumpToPage(page: ReaderPage, smooth: Boolean) {
        val position = PagerNavigation.positionOfPage(adapter.items, page)
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, smooth)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
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
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        } else {
            onEdgeTransition()
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        } else {
            onEdgeTransition()
        }
    }

    /**
     * A navigation press (key or tap) landed on the terminal pager item and can't scroll further. If
     * that item is a chapter transition whose destination chapter simply hasn't loaded yet (the
     * boundary was reached faster than the preload completed), the press would otherwise be silently
     * dropped and the reader appears stuck on the transition, needing an extra press to cross once the
     * chapter arrives. This is symmetric at either end (a forward [ChapterTransition.Next] or a
     * backward [ChapterTransition.Prev]). Instead, make sure the load is running and remember the
     * intent; [setChaptersInternal] carries the reader across the boundary as soon as the chapter
     * lands, so the press isn't lost. A transition with no destination (the first or last chapter) is
     * a genuine content edge and stays a no-op.
     */
    private fun onEdgeTransition() {
        val transition = adapter.items.getOrNull(pager.currentItem) as? ChapterTransition ?: return
        val to = transition.to ?: return
        pendingChapterCross = transition
        activity.requestPreloadChapter(to)
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
        val currentItem = pager.currentItem
        adapter.refresh()
        // A display property changed (crop borders, scale type, …), so pooled halves are decoded under a
        // config that no longer applies. Drop them and don't re-pool the torn-down ones, so every spread
        // re-decodes with the new config (as a single page already does). See holderPool's reuse invariant.
        adapter.recyclePool()
        adapter.withoutSalvaging {
            pager.adapter = adapter
        }
        pager.setCurrentItem(currentItem, false)
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
            KeyEvent.KEYCODE_D -> {
                if (this is VerticalPagerViewer) return false
                if (isUp) activity.toggleSpread()
            }
            KeyEvent.KEYCODE_O -> {
                if (this is VerticalPagerViewer || !config.spread) return false
                if (isUp) shiftSpreadPairing()
            }
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

    fun onSpreadPageWide(spread: PageSpread) {
        activity.runOnUiThread {
            // breakSpread reshapes the item list in place, so re-anchor to the leading page of the
            // current view; the raw index would otherwise land on the ex-partner half. The same
            // relocate-by-anchor the shift reflow uses; a no-op unless the broken spread is current.
            val anchor = currentPage?.asShiftAnchor()
            adapter.breakSpread(spread)
            relocateTo(anchor)
            // The item at the current position changed shape without a pager scroll event, so progress
            // tracking needs to be told explicitly.
            onPageChange(pager.currentItem)
        }
    }

    /**
     * Whether the current chapter's spreads are paired starting from its second page rather than
     * its first (see [shiftSpreadPairing]). Drives which of the two shift-pairing icon states the
     * bottom bar shows.
     */
    val spreadShifted: Boolean
        get() = adapter.spreadShifted

    /**
     * Toggles whether the current chapter's spreads pair starting from its first page or its second
     * (leaving the first page standing alone), to correct scans that start their spreads on the
     * "wrong" page. Goes through [withShiftAnchor], the single anchored path for a parity change.
     */
    fun shiftSpreadPairing() = withShiftAnchor { adapter.toggleSpreadShift() }

    /**
     * Re-applies a settings-driven per-manga shift change to the current chapter (see
     * [PagerViewerAdapter.reapplyShift]) through the same anchored path as the manual
     * [shiftSpreadPairing], so a parity change from the settings sheet reflows in place instead of
     * restoring by raw page index (which walks the reader forward).
     */
    fun reapplyShift() = withShiftAnchor { adapter.reapplyShift() }

    /**
     * The single anchored reflow for a parity change; [shiftSpreadPairing] and [reapplyShift] both
     * route through here. A parity change reflows the whole chapter, so the position is restored by
     * relocating to a captured anchor, not a numeric index. Across immediately consecutive calls the
     * same anchor is reused, so a second change right after the first lands back on the page the first
     * started from (re-deriving from where the pager landed would drift off it). The anchor may be a
     * [ChapterTransition] (see [asShiftAnchor]); not persisted: any other navigation or a chapter
     * change clears it.
     */
    private fun withShiftAnchor(mutate: () -> Unit) {
        val anchor = spreadShiftAnchor ?: currentPage?.asShiftAnchor()
        mutate()
        relocateTo(anchor)
        // Same as onSpreadPageWide: the item at the current position changed shape without a pager
        // scroll event, so progress tracking needs to be told explicitly. This also clears
        // spreadShiftAnchor as a side effect (see onPageChange), so it's restored right after.
        onPageChange(pager.currentItem)
        spreadShiftAnchor = anchor
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
}
