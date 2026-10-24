package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.graphics.ColorFilter
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.collectChanges
import eu.kanade.tachiyomi.ui.reader.viewer.mouseWheelPaging
import eu.kanade.tachiyomi.ui.reader.viewer.position
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val graph by lazy { activity.appGraph }
    val downloadManager by lazy { graph.downloadManager }

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    private val settingsViewModel = activity.settingsViewModel

    /**
     * Settings every paged viewer uses, like scale mode on images or tap zones.
     */
    val paged: PagedSettings
        get() = settingsViewModel.paged.value

    /**
     * Settings only the pager viewers use, like splitting double pages.
     */
    val simplePaged: SimplePagedSettings
        get() = settingsViewModel.simplePaged.value

    /**
     * Settings shared by every viewer, like allow taps or page transitions.
     */
    val general: GeneralSettings
        get() = settingsViewModel.general.value

    val imageZoomType: ZoomStartPosition
        get() = paged.page.zoomStart.position(
            auto = when (this) {
                is L2RPagerViewer -> ZoomStartPosition.LEFT
                is R2LPagerViewer -> ZoomStartPosition.RIGHT
                else -> ZoomStartPosition.CENTER
            },
        )

    private val pagerItems = PagerItems(isReversed = this is R2LPagerViewer)

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this, pagerItems)

    private val tracker = PagerPageTracker(activity, pagerItems, ::setChaptersInternal) { page, forward ->
        getPageHolder(page)?.onPageSelected(forward)
    }

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            if (!activity.isScrollingThroughPages) {
                activity.hideMenu()
            }
            tracker.onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            tracker.isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.isFocusable = false
        pager.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = { event ->
            val viewPosition = IntArray(2)
            pager.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            pager.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = Offset(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
            )
            activity.onViewerTap(pos)
        }
        pager.longTapListener = f@{
            if (activity.viewModel.state.value.menuVisible || general.longTapEnabled) {
                val item = adapter.items.getOrNull(pager.currentItem)
                if (item is ReaderPage) {
                    activity.onPageLongTap(item)
                    return@f true
                }
            }
            false
        }

        settingsViewModel.general.collectChanges(scope) { old, new ->
            if (old != null && old.theme != new.theme) {
                refreshAdapter()
            }
        }
        settingsViewModel.simplePaged.collectChanges(scope) { old, new ->
            if (old != null) {
                refreshAdapter()
            }
            if (old?.dualPageSplit != new.dualPageSplit && !new.dualPageSplit) {
                cleanupPageSplit()
            }
        }
        settingsViewModel.paged.collectChanges(scope) { old, new ->
            if (old != null && old.page != new.page) {
                refreshAdapter()
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

    /** The page images' colours, for holders made later - see [LocalPageColorFilter]. */
    var pageColorFilter: ColorFilter? = null
        private set

    @Composable
    override fun Content(modifier: Modifier) {
        val filter = LocalPageColorFilter.current
        val pageFilter = remember(filter) { filter?.asAndroidColorFilter() }
        SideEffect {
            pageColorFilter = pageFilter
            // The pager keeps every page it holds as a child.
            pager.children.filterIsInstance<PagerPageHolder>().forEach { it.pageColorFilter = pageFilter }
        }
        AndroidView(
            factory = { context -> DisallowInterceptBarrier(context).apply { addView(pager) } },
            modifier = modifier.mouseWheelPaging(this),
        )
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item == page }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        tracker.setChapters(chapters)
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        // Remove listener so the change in item doesn't trigger it
        pager.removeOnPageChangeListener(pagerListener)

        val forceTransition = general.alwaysShowChapterTransition ||
            adapter.items.getOrNull(pager.currentItem) is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            pager.isVisible = true
        }

        pager.addOnPageChangeListener(pagerListener)
        // Manually call onPageChange to update the UI
        tracker.onPageChange(pager.currentItem)
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
                tracker.onPageChange(position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    /**
     * Whether pages run right to left, so moving forward goes left.
     */
    protected open val isReversed = false

    override fun moveForward() = if (isReversed) moveLeft() else moveRight()

    override fun moveBackward() = if (isReversed) moveRight() else moveLeft()

    /**
     * Moves to the page at the right.
     */
    private fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (tracker.currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && paged.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, general.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    private fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (tracker.currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && paged.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, general.usePageTransitions)
            }
        }
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    private fun refreshAdapter() {
        val currentItem = pager.currentItem
        adapter.refresh()
        pager.adapter = adapter
        pager.setCurrentItem(currentItem, false)
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

/**
 * Keeps [requestDisallowInterceptTouchEvent] from reaching Compose. Its interop filter cancels the
 * gesture when a child re-allows intercepting mid-drag, which stops a zoomed page from handing the
 * drag to the pager at its edge.
 */
private class DisallowInterceptBarrier(context: Context) : FrameLayout(context) {
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
}
