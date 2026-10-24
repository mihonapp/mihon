package eu.kanade.tachiyomi.ui.reader.viewer.compose.pager

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderThemedContent
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionPage
import eu.kanade.tachiyomi.ui.reader.viewer.TransitionDownloadState
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.collectChanges
import eu.kanade.tachiyomi.ui.reader.viewer.compose.detectReaderTaps
import eu.kanade.tachiyomi.ui.reader.viewer.compose.pageSettleSpec
import eu.kanade.tachiyomi.ui.reader.viewer.mouseWheelPaging
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerItems
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerPageTracker
import eu.kanade.tachiyomi.ui.reader.viewer.pager.SimplePagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.position
import eu.kanade.tachiyomi.ui.reader.viewer.transitionDownloadState
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a Compose pager.
 */
@Suppress("LeakingThis")
abstract class ComposePagerViewer(
    val activity: ReaderActivity,
    private val isHorizontal: Boolean = true,
) : Viewer {

    val graph by lazy { activity.appGraph }
    val downloadManager by lazy { graph.downloadManager }

    private val scope = MainScope()

    // Immediate, so a page change is handled before the items can change under its position. Not for the settings,
    // whose first values would arrive before the viewer is set up.
    private val immediateScope = scope + Dispatchers.Main.immediate

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
                is ComposeL2RPagerViewer -> ZoomStartPosition.LEFT
                is ComposeR2LPagerViewer -> ZoomStartPosition.RIGHT
                else -> ZoomStartPosition.CENTER
            },
        )

    /**
     * Items of the pager.
     */
    private val pagerItems = PagerItems(isReversed = this is ComposeR2LPagerViewer)

    private val pagerState = PagerState { pagerItems.items.size }

    private val tracker = PagerPageTracker(activity, pagerItems, ::setChaptersInternal) { page, forward ->
        pageHolders[page]?.onPageSelected(forward)
    }

    /** Whether the reader is on [page]. */
    fun isCurrentPage(page: ReaderPage) = tracker.currentPage === page

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext by mutableStateOf(activity.createReaderThemeContext())

    /**
     * Part of every page key, bumped to recreate the pages when an image configuration is changed.
     */
    private var pagesGeneration by mutableIntStateOf(0)

    private val pageHolders = mutableMapOf<ReaderPage, PagerPageHolder>()

    private var transitionDownloadStates by mutableStateOf(emptyMap<ChapterTransition, TransitionDownloadState?>())

    /**
     * Whether the pager is composed, which it is once a chapter is set.
     */
    private var isPagerVisible by mutableStateOf(false)

    private var isGestureDetectorEnabled = true

    /**
     * Where page turns animate: the composition, whose frame clock they step with. Without one they jump.
     */
    private var animationScope: CoroutineScope? = null

    init {
        snapshotFlow { pagerState.isScrollInProgress }
            .onEach { tracker.isIdle = !it }
            .launchIn(immediateScope)

        snapshotFlow { pagerState.settledPage }
            .onEach { position ->
                val page = pagerItems.items.getOrNull(position)
                if (page != null && page != tracker.currentPage && !activity.isScrollingThroughPages) {
                    activity.hideMenu()
                }
                tracker.onPageChange(position)
            }
            .launchIn(immediateScope)

        settingsViewModel.general.collectChanges(scope) { old, new ->
            if (old != null && old.theme != new.theme) {
                refreshPages()
            }
        }
        settingsViewModel.simplePaged.collectChanges(scope) { old, new ->
            if (old != null) {
                refreshPages()
            }
            if (old?.dualPageSplit != new.dualPageSplit && !new.dualPageSplit) {
                cleanupPageSplit()
            }
        }
        settingsViewModel.paged.collectChanges(scope) { old, new ->
            if (old != null && old.page != new.page) {
                refreshPages()
            }
        }
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    @Composable
    override fun Content(modifier: Modifier) {
        if (!isPagerVisible) return

        val haptics = LocalHapticFeedback.current
        val compositionScope = rememberCoroutineScope()
        SideEffect { animationScope = compositionScope }
        val layoutDirection = LocalLayoutDirection.current

        val density = LocalDensity.current
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val flingBehavior = remember(density, touchSlop) { ViewPagerFlingBehavior(pagerState, density, touchSlop) }
        val pagerModifier = modifier
            .fillMaxSize()
            .mouseWheelPaging(this)
            .trackReleaseVelocity(flingBehavior, isHorizontal)
            .pointerInput(Unit) {
                detectReaderTaps(
                    isEnabled = { isGestureDetectorEnabled },
                    onTap = { activity.onViewerTap(Offset(it.x / size.width, it.y / size.height)) },
                    onLongTap = { if (onLongTap()) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
            }
        val pageContent: @Composable PagerScope.(Int) -> Unit = { position ->
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Page(pagerItems.items[position])
            }
        }

        LaunchedEffect(flingBehavior) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .collect { (scrolling, page) -> if (!scrolling) flingBehavior.restingPage = page }
        }

        // Right to left is done by reversing the items, so the pager must not mirror them again
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (isHorizontal) {
                HorizontalPager(
                    state = pagerState,
                    modifier = pagerModifier,
                    beyondViewportPageCount = 1,
                    flingBehavior = flingBehavior,
                    key = { pageKey(pagerItems.items[it]) },
                    pageContent = pageContent,
                )
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = pagerModifier,
                    beyondViewportPageCount = 1,
                    flingBehavior = flingBehavior,
                    key = { pageKey(pagerItems.items[it]) },
                    pageContent = pageContent,
                )
            }
        }
    }

    @Composable
    private fun Page(item: Any) {
        ReaderThemedContent(readerThemedContext) {
            when (item) {
                is ReaderPage -> {
                    val holder = remember(item) { PagerPageHolder(this, item) }
                    DisposableEffect(holder) {
                        pageHolders[item] = holder
                        if (tracker.currentPage == item) holder.onPageSelected(forward = true)
                        onDispose { pageHolders.remove(item, holder) }
                    }
                    holder.Content()
                }
                is ChapterTransition -> ReaderTransitionPage(
                    transition = item,
                    downloadState = transitionDownloadStates[item],
                    onRetry = activity::requestPreloadChapter,
                    onButtonPressChange = { pressed -> setGestureDetectorEnabled(!pressed) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 64.dp),
                )
            }
        }
    }

    private fun pageKey(item: Any): String {
        val key = when (item) {
            is InsertPage -> "${item.chapter.chapter.id}-${item.index}-insert"
            is ReaderPage -> "${item.chapter.chapter.id}-${item.index}"
            is ChapterTransition.Prev -> "prev-${item.from.chapter.id}"
            is ChapterTransition.Next -> "next-${item.from.chapter.id}"
            else -> throw NotImplementedError("Page for ${item.javaClass} not implemented")
        }
        return "$pagesGeneration-$key"
    }

    private fun onLongTap(): Boolean {
        if (activity.viewModel.state.value.menuVisible || general.longTapEnabled) {
            val item = pagerItems.items.getOrNull(pagerState.currentPage)
            if (item is ReaderPage) {
                activity.onPageLongTap(item)
                return true
            }
        }
        return false
    }

    /**
     * Enables or disables the tap and long tap detection.
     */
    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }

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
        val forceTransition = general.alwaysShowChapterTransition ||
            pagerItems.items.getOrNull(pagerState.currentPage) is ChapterTransition
        val update = pagerItems.setChapters(chapters, forceTransition, pagerState.currentPage)
        update.position?.let(::jumpTo)
        // Will skip insert page otherwise
        update.insertPage?.let(::moveToPage)
        updateTransitionDownloadStates()

        // Show the pager once a chapter is being set
        if (!isPagerVisible) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)], animate = false)
            isPagerVisible = true
        }

        // Manually call onPageChange to update the UI
        tracker.onPageChange(pagerState.currentPage)
    }

    private fun updateTransitionDownloadStates() {
        val manga = activity.viewModel.manga
        val source = activity.viewModel.source
        transitionDownloadStates = pagerItems.items
            .filterIsInstance<ChapterTransition>()
            .associateWith { transition ->
                downloadManager.transitionDownloadState(transition, manga, source)
                    ?.also { scope.launch { it.checkDisk() } }
            }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        moveToPage(page, animate = true)
    }

    private fun moveToPage(page: ReaderPage, animate: Boolean) {
        val position = pagerItems.items.indexOf(page)
        if (position != -1) {
            val currentPosition = pagerState.currentPage
            setCurrentItem(position, animate)
            // manually call onPageChange since the pager does not settle again in this case
            if (currentPosition == position) {
                tracker.onPageChange(position)
            }
        } else {
            logcat { "Page $page not found in pager" }
        }
    }

    /**
     * Moves the pager to [position] without animating.
     */
    private fun jumpTo(position: Int) {
        pagerState.requestScrollToPage(position)
    }

    private fun setCurrentItem(position: Int, animate: Boolean) {
        val animationScope = animationScope
        if (animate && animationScope != null) {
            animationScope.launch {
                val pageSize = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                val distance = (position - pagerState.currentPage - pagerState.currentPageOffsetFraction) * pageSize
                pagerState.animateScrollToPage(position, animationSpec = pageSettleSpec(distance, pageSize, 0f))
            }
        } else {
            jumpTo(position)
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
        val position = pagerState.targetPage
        if (position != pagerItems.items.lastIndex) {
            val holder = (tracker.currentPage as? ReaderPage)?.let(pageHolders::get)
            if (holder != null && paged.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                setCurrentItem(position + 1, general.usePageTransitions)
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    private fun moveLeft() {
        val position = pagerState.targetPage
        if (position != 0) {
            val holder = (tracker.currentPage as? ReaderPage)?.let(pageHolders::get)
            if (holder != null && paged.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                setCurrentItem(position - 1, general.usePageTransitions)
            }
        }
    }

    /**
     * Recreates all the pages. Used when a image configuration is changed.
     */
    private fun refreshPages() {
        readerThemedContext = activity.createReaderThemeContext()
        pagesGeneration++
    }

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        // Pages are split while decoding, off the main thread
        immediateScope.launch { pagerItems.onPageSplit(currentPage, newPage) }
    }

    private fun cleanupPageSplit() {
        pagerItems.cleanupPageSplit()
    }
}
