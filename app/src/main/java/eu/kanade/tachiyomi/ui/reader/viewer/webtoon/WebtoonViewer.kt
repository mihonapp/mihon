package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewConfiguration
import androidx.core.app.ActivityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implementation of a [Viewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(val activity: ReaderActivity, val isContinuous: Boolean = true) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private val scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity, scrollDistance)

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    /**
     * Adapter of the recycler view.
     */
    private val adapter = WebtoonAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    private val viewLocationOnScreen = IntArray(2)
    private val viewLocationInWindow = IntArray(2)
    private val navigationTouchPoint = PointF()

    private var holdScrollPointerId: Int? = null
    private var holdScrollJob: Job? = null
    private var holdScrollDirection: HoldScrollDirection? = null
    private var isHoldScrollEngaged: Boolean = false

    private val threshold: Int =
        Injekt.get<ReaderPreferences>()
            .readerHideThreshold()
            .get()
            .threshold

    init {
        recycler.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onScrolled()

                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = { event ->
            when (resolveNavigationRegion(event, event.actionIndex)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
                else -> Unit
            }
        }
        recycler.setOnTouchListener { _, event ->
            handleHoldScroll(event)
            false
        }
        recycler.longTapListener = f@{ event ->
            if (isHoldScrollEngaged) {
                return@f false
            }

            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        activity.onPageLongTap(item)
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.themeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
        }

        config.zoomPropertyChangedListener = {
            frame.zoomOutDisabled = it
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return frame
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        stopHoldScroll()
        scope.cancel()
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            logcat { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            recycler.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(pos = position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    private fun scrollUp() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    private fun scrollDown() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollDown() else scrollUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollUp() else scrollDown()
                }
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    private fun handleHoldScroll(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (activity.viewModel.state.value.menuVisible || event.pointerCount > 1) {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    return
                }

                val direction = holdScrollDirectionForIndex(event, event.actionIndex)
                if (direction != null) {
                    holdScrollPointerId = event.getPointerId(event.actionIndex)
                    startHoldScroll(direction)
                    isHoldScrollEngaged = true
                } else {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    isHoldScrollEngaged = false
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                stopHoldScroll()
                holdScrollPointerId = null
                isHoldScrollEngaged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (activity.viewModel.state.value.menuVisible) {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    isHoldScrollEngaged = false
                    return
                }

                val pointerId = holdScrollPointerId ?: return
                val pointerIndex = event.findPointerIndex(pointerId)
                if (pointerIndex < 0) {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    isHoldScrollEngaged = false
                    return
                }

                val expectedDirection = holdScrollDirection
                val currentDirection = holdScrollDirectionForIndex(event, pointerIndex)
                if (expectedDirection == null || currentDirection != expectedDirection) {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    isHoldScrollEngaged = false
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = holdScrollPointerId ?: return
                val actionPointerId = event.getPointerId(event.actionIndex)
                if (pointerId == actionPointerId) {
                    stopHoldScroll()
                    holdScrollPointerId = null
                    isHoldScrollEngaged = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopHoldScroll()
                holdScrollPointerId = null
                isHoldScrollEngaged = false
            }
        }
    }

    private fun startHoldScroll(direction: HoldScrollDirection) {
        if (holdScrollDirection == direction && holdScrollJob?.isActive == true) return

        holdScrollJob?.cancel()
        holdScrollDirection = direction
        holdScrollJob = scope.launch {
            delay(ViewConfiguration.getTapTimeout().toLong())
            val multiplier = config.holdScrollSpeedMultiplier.coerceIn(
                ReaderPreferences.WEBTOON_HOLD_SCROLL_SPEED_MIN,
                ReaderPreferences.WEBTOON_HOLD_SCROLL_SPEED_MAX,
            )
            val density = activity.resources.displayMetrics.density
            val pixelsPerSecond = HOLD_SCROLL_BASE_SPEED_DP_PER_SEC * density * multiplier
            val step = (pixelsPerSecond * HOLD_SCROLL_FRAME_DELAY_MS / 1000f)
                .coerceAtLeast(1f)
                .roundToInt()
            val signedStep = if (direction == HoldScrollDirection.DOWN) step else -step

            while (isActive) {
                recycler.scrollBy(0, signedStep)
                delay(HOLD_SCROLL_FRAME_DELAY_MS)
            }
        }
    }

    private fun stopHoldScroll() {
        holdScrollJob?.cancel()
        holdScrollJob = null
        holdScrollDirection = null
        isHoldScrollEngaged = false
    }

    private fun resolveNavigationRegion(event: MotionEvent, pointerIndex: Int = 0): NavigationRegion? {
        if (pointerIndex < 0 || pointerIndex >= event.pointerCount) return null
        if (recycler.width == 0 || recycler.originalHeight == 0) return null

        recycler.getLocationOnScreen(viewLocationOnScreen)
        recycler.getLocationInWindow(viewLocationInWindow)

        val rawX = viewLocationOnScreen[0] + event.getX(pointerIndex)
        val rawY = viewLocationOnScreen[1] + event.getY(pointerIndex)

        val normalizedX = ((rawX - viewLocationOnScreen[0]) + viewLocationInWindow[0]) / recycler.width.toFloat()
        val normalizedY = ((rawY - viewLocationOnScreen[1]) + viewLocationInWindow[1]) / recycler.originalHeight.toFloat()

        navigationTouchPoint.set(normalizedX, normalizedY)
        return config.navigator.getAction(navigationTouchPoint)
    }

    private fun holdScrollDirectionForIndex(
        event: MotionEvent,
        pointerIndex: Int,
    ): HoldScrollDirection? {
        return when (resolveNavigationRegion(event, pointerIndex)) {
            NavigationRegion.NEXT, NavigationRegion.RIGHT -> HoldScrollDirection.DOWN
            NavigationRegion.PREV, NavigationRegion.LEFT -> HoldScrollDirection.UP
            else -> null
        }
    }

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.refresh()
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
    }
}

private enum class HoldScrollDirection {
    UP,
    DOWN,
}

// Double the cache size to reduce rebinds/recycles incurred by the extra layout space on scroll direction changes
private const val RECYCLER_VIEW_CACHE_SIZE = 4
private const val HOLD_SCROLL_FRAME_DELAY_MS = 16L
private const val HOLD_SCROLL_BASE_SPEED_DP_PER_SEC = 80f
