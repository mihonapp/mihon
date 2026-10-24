package eu.kanade.tachiyomi.ui.reader.viewer.compose.webtoon

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.viewer.LongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderThemedContent
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionPage
import eu.kanade.tachiyomi.ui.reader.viewer.TransitionDownloadState
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.collectChanges
import eu.kanade.tachiyomi.ui.reader.viewer.compose.detectReaderTaps
import eu.kanade.tachiyomi.ui.reader.viewer.transitionDownloadState
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.SimpleLongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonItems
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonPageTracker
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages in a Compose vertical list.
 */
class ComposeWebtoonViewer(val activity: ReaderActivity, val isContinuous: Boolean = true) : Viewer {

    val graph by lazy { activity.appGraph }
    val downloadManager by lazy { graph.downloadManager }
    val readerPreferences by lazy { graph.readerPreferences }

    private val scope = MainScope()

    // Immediate, so a page change is handled before the items can change under its position. Not for the settings,
    // whose first values would arrive before the viewer is set up.
    private val immediateScope = scope + Dispatchers.Main.immediate

    /**
     * Distance to scroll when the user taps on one side of the list.
     */
    private val scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    private val settingsViewModel = activity.settingsViewModel

    /**
     * Settings every long strip viewer uses, like crop image borders.
     */
    val longStrip: LongStripSettings
        get() = settingsViewModel.longStrip.value

    /**
     * Settings only the webtoon viewers use, like side padding or tap zones.
     */
    val simpleLongStrip: SimpleLongStripSettings
        get() = settingsViewModel.simpleLongStrip.value

    /**
     * Settings shared by every viewer, like allow taps or page transitions.
     */
    val general: GeneralSettings
        get() = settingsViewModel.general.value

    /**
     * Items of the list.
     */
    private val webtoonItems = WebtoonItems()

    private val tracker = WebtoonPageTracker(
        activity,
        webtoonItems,
        readerPreferences.readerHideThreshold.get().threshold,
    )

    private val listState = LazyListState()

    private val zoom = WebtoonZoom(canPanVertically = ::isAtEitherEnd)

    /**
     * Scrolls the list by deltas on screen, which are smaller on the list while it's zoomed in.
     */
    private val scrollableState = ScrollableState { delta ->
        val scale = zoom.scale
        val consumed = listState.dispatchRawDelta(delta / scale)
        if (consumed != 0f) onScrolledBy(consumed)
        consumed * scale
    }

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext by mutableStateOf(activity.createReaderThemeContext())

    /**
     * Part of every page key, bumped to recreate the pages when an image configuration is changed.
     */
    private var pagesGeneration by mutableIntStateOf(0)

    /**
     * Each page's width over height by key, once known: a page made again after leaving the list's cache takes
     * it at once. The key holds the generation, so a page recreated for other settings starts afresh.
     */
    val pageRatios = HashMap<String, Float>()

    /** The shown list's [ItemKeys], for [windowOf]. */
    private var itemKeys by mutableStateOf(ItemKeys(emptyList(), 0) { "" })

    private var sidePaddingPercent by mutableIntStateOf(simpleLongStrip.page.sidePadding)

    private var transitionDownloadStates by mutableStateOf(emptyMap<ChapterTransition, TransitionDownloadState?>())

    /**
     * Whether the list is composed, which it is once a chapter is set.
     */
    private var isListVisible by mutableStateOf(false)

    private var hasLaidOut = false

    private var isGestureDetectorEnabled = true

    /**
     * Where page turns animate: the composition, whose frame clock they step with. Without one they jump.
     */
    private var animationScope: CoroutineScope? = null

    /**
     * Whether the last press landed while the list was still scrolling, which it stops rather than navigating.
     */
    private var isDownDuringScroll = false

    /**
     * Height of the viewer, which pages use as their height while loading.
     */
    val viewportHeight: Int
        get() = zoom.viewportHeight

    init {
        zoom.viewportHeight = activity.resources.displayMetrics.heightPixels

        snapshotFlow { listState.layoutInfo }
            .filter { it.visibleItemsInfo.isNotEmpty() }
            .onEach {
                hasLaidOut = true
                onScrolled()
            }
            .launchIn(immediateScope)

        settingsViewModel.general.collectChanges(scope) { old, new ->
            if (old != null && old.theme != new.theme) {
                refreshPages()
            }
        }
        settingsViewModel.longStrip.collectChanges(scope) { old, new ->
            if (old != null && old.cropBorders != new.cropBorders) {
                refreshPages()
            }
            if (old?.zoomOutDisabled != new.zoomOutDisabled) {
                zoom.zoomOutDisabled = new.zoomOutDisabled
            }
        }
        settingsViewModel.simpleLongStrip.collectChanges(scope) { old, new ->
            if (old != null && old.page != new.page) {
                sidePaddingPercent = new.page.sidePadding
                refreshPages()
            }
            if (old?.doubleTapZoom != new.doubleTapZoom) {
                zoom.doubleTapZoom = new.doubleTapZoom
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        if (!isListVisible) return

        val haptics = LocalHapticFeedback.current
        val compositionScope = rememberCoroutineScope()
        SideEffect {
            animationScope = compositionScope
            zoom.animationScope = compositionScope
        }
        // A share of the window's width, so pages are as wide on a split screen as the setting looks
        val sidePaddingPixels = LocalWindowInfo.current.containerSize.width * sidePaddingPercent / 100
        SideEffect { zoom.sidePaddingPixels = sidePaddingPixels }
        val sidePadding = with(LocalDensity.current) { sidePaddingPixels.toDp() }
        val bottomPadding = if (isContinuous) 0.dp else 15.dp

        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                // Before the scrollable stops a fling on the same press
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        isDownDuringScroll = scrollableState.isScrollInProgress
                    }
                }
                .scrollable(scrollableState, Orientation.Vertical, reverseDirection = true)
                .pointerInput(zoom) { with(zoom) { detectZoomGestures(isEnabled = { isGestureDetectorEnabled }) } }
                .pointerInput(Unit) {
                    detectReaderTaps(
                        // A tap that stops a fling isn't navigation
                        isEnabled = { isGestureDetectorEnabled && !isDownDuringScroll },
                        onTap = ::onTap,
                        onLongTap = { if (onLongTap(it)) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    )
                },
        ) {
            val items = webtoonItems.items
            val generation = pagesGeneration
            val keys = remember(items, generation) { ItemKeys(items, generation, ::pageKey) }
            SideEffect { itemKeys = keys }
            // One theme for the list: per item, each prefetched page built its own colour scheme.
            ReaderThemedContent(readerThemedContext) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            zoom.viewportWidth = constraints.maxWidth
                            zoom.viewportHeight = constraints.maxHeight
                            val listHeight = zoom.listHeight
                            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, listHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                // Taller while zoomed out, centered so it fills the viewer once scaled
                                placeable.place(0, (constraints.maxHeight - listHeight) / 2)
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.translationX
                            translationY = zoom.translationY
                        },
                    userScrollEnabled = false,
                    cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.75f, behindFraction = 0.75f) },
                ) {
                    items(
                        count = items.size,
                        key = { keys.keys[it] },
                        contentType = { keys.contentTypes[it] },
                    ) { index ->
                        when (val item = items[index]) {
                            is ReaderPage -> {
                                val key = keys.keys[index]
                                val holder = remember(key) { WebtoonPageHolder(this@ComposeWebtoonViewer, item, key) }
                                DisposableEffect(holder) { onDispose(holder::recycle) }
                                holder.Content(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = sidePadding, end = sidePadding, bottom = bottomPadding),
                                )
                            }
                            is ChapterTransition -> {
                                ReaderTransitionPage(
                                    transition = item,
                                    downloadState = transitionDownloadStates[item],
                                    onRetry = activity::requestPreloadChapter,
                                    onButtonPressChange = { pressed -> setGestureDetectorEnabled(!pressed) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp, vertical = 128.dp),
                                    statusPadding = PaddingValues(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Where the item with [key] is relative to the screen, in its own pixels, so a page knows which part of it to
     * decode and at what resolution. Reads the list's layout and the zoom, so callers observe both.
     */
    fun windowOf(key: String): PageWindow? {
        val info = listState.layoutInfo
        val scale = zoom.scale
        val listHeight = zoom.listHeight
        val listTop = (zoom.viewportHeight - listHeight) / 2f

        // The inverse of the list's scale about its center and its translation
        fun listY(y: Float) = (y - zoom.translationY - listTop - listHeight / 2f) / scale + listHeight / 2f
        val top = listY(0f)
        val bottom = listY(zoom.viewportHeight.toFloat())

        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        if (item != null) return PageWindow.Visible(top - item.offset, bottom - item.offset, scale)

        val visible = info.visibleItemsInfo.takeIf { it.isNotEmpty() } ?: return null
        val index = itemKeys.indices[key] ?: return null
        return PageWindow.Near(
            isBelow = index > visible.last().index,
            screenHeight = zoom.viewportHeight.toFloat(),
            scale = scale,
        )
    }

    private fun pageKey(item: Any): String {
        val key = when (item) {
            is ReaderPage -> "${item.chapter.chapter.id}-${item.index}"
            is ChapterTransition.Prev -> "prev-${item.from.chapter.id}"
            is ChapterTransition.Next -> "next-${item.from.chapter.id}"
            else -> throw NotImplementedError("Page for ${item.javaClass} not implemented")
        }
        return "$pagesGeneration-$key"
    }

    /**
     * Each item's [pageKey] and content type, and each key's index, built once per list: the list asks per item and
     * per prefetch, and [windowOf] per page each frame.
     */
    private class ItemKeys(items: List<Any>, generation: Int, pageKey: (Any) -> String) {
        val keys = items.map(pageKey)

        // The generation keeps a recreated page from reusing a slot of the one before.
        val contentTypes = items.map { "$generation-${it is ReaderPage}" }

        val indices = keys.withIndex().associate { (index, key) -> key to index }
    }

    private fun onTap(position: Offset) {
        activity.onViewerTap(Offset(position.x / zoom.viewportWidth, position.y / zoom.viewportHeight))
    }

    private fun onLongTap(position: Offset): Boolean {
        if (activity.viewModel.state.value.menuVisible || general.longTapEnabled) {
            val item = itemAt(position.y)
            if (item is ReaderPage) {
                activity.onPageLongTap(item)
                return true
            }
        }
        return false
    }

    /**
     * Returns the item drawn at [y] on the viewer.
     */
    private fun itemAt(y: Float): Any? {
        val listHeight = zoom.listHeight
        val listTop = (zoom.viewportHeight - listHeight) / 2
        val listY = (y - zoom.translationY - listTop - listHeight / 2f) / zoom.scale + listHeight / 2f
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { listY >= it.offset && listY < it.offset + it.size }
            ?: return null
        return webtoonItems.items.getOrNull(info.index)
    }

    /**
     * Enables or disables the gesture detection.
     */
    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }

    private fun isAtEitherEnd(): Boolean {
        val info = listState.layoutInfo
        val visibleItems = info.visibleItemsInfo
        return visibleItems.isNotEmpty() &&
            (visibleItems.first().index == 0 || visibleItems.last().index == info.totalItemsCount - 1)
    }

    /**
     * Returns the position of the last item whose end is visible.
     */
    private fun LazyListLayoutInfo.lastEndVisibleItemPosition(): Int {
        return visibleItemsInfo
            .lastOrNull { it.offset + it.size <= viewportEndOffset || it.offset < viewportStartOffset }
            ?.index
            ?: -1
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val forceTransition = general.alwaysShowChapterTransition || tracker.currentPage is ChapterTransition
        webtoonItems.setChapters(chapters, forceTransition)
        updateTransitionDownloadStates()

        if (!isListVisible) {
            logcat { "List first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            isListVisible = true
        }
    }

    private fun updateTransitionDownloadStates() {
        val manga = activity.viewModel.manga
        val source = activity.viewModel.source
        transitionDownloadStates = webtoonItems.items
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
        val position = webtoonItems.items.indexOf(page)
        if (position != -1) {
            listState.requestScrollToItem(position)
            if (!hasLaidOut) {
                onScrolled(pos = position)
            }
        } else {
            logcat { "Page $page not found in list" }
        }
    }

    /**
     * Called when the list scrolled by [dy] pixels.
     */
    private fun onScrolledBy(dy: Float) {
        val info = listState.layoutInfo
        tracker.onScrolledBy(
            dy = dy,
            firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1,
            lastEndVisible = info.lastEndVisibleItemPosition(),
        )
    }

    private fun onScrolled(pos: Int? = null) {
        tracker.onScrolled(pos ?: listState.layoutInfo.lastEndVisibleItemPosition())
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    override fun moveBackward() {
        scrollBy(-scrollDistance)
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    override fun moveForward() {
        scrollBy(scrollDistance)
    }

    private fun scrollBy(distance: Int) {
        val screenDistance = distance * zoom.scale
        val animationScope = animationScope
        if (general.usePageTransitions && animationScope != null) {
            animationScope.launch { scrollableState.animateScrollBy(screenDistance) }
        } else {
            scrollableState.dispatchRawDelta(screenDistance)
        }
    }

    /**
     * Recreates all the pages. Used when an image configuration is changed.
     */
    private fun refreshPages() {
        readerThemedContext = activity.createReaderThemeContext()
        pagesGeneration++
    }
}
