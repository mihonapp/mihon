package eu.kanade.tachiyomi.ui.reader

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.WebtoonLayoutManager
import android.view.GestureDetector
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonRecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonAdapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okio.Buffer
import mihon.core.dualscreen.DualScreenState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

private class TouchInterceptFrameLayout(context: android.content.Context) : FrameLayout(context) {
    var externalGestureDetector: GestureDetector? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        externalGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}

class ReaderPresentation(
    outerContext: Context,
    display: Display,
    private val activity: ReaderActivity,
) : Presentation(outerContext, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = activity.onBackPressedDispatcher

    private var container: TouchInterceptFrameLayout? = null
    private var composeView: androidx.compose.ui.platform.ComposeView? = null

    private var localMenuVisibleState: MutableState<Boolean>? = null
    var localMenuVisible: Boolean
        get() = localMenuVisibleState?.value ?: false
        set(value) {
            localMenuVisibleState?.value = value
        }

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backgroundColor = ContextCompat.getColor(context, R.color.reader_background_dark)
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))

        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        window!!.decorView.setViewTreeLifecycleOwner(this)
        window!!.decorView.setViewTreeSavedStateRegistryOwner(this)
        window!!.decorView.setViewTreeViewModelStoreOwner(this)

        setupGestureDetector()

        container = TouchInterceptFrameLayout(context).apply {
            setBackgroundColor(backgroundColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            externalGestureDetector = gestureDetector
        }
        setContentView(container!!)

        setupComposeOverlay()

        lifecycleScope.launch {
            DualScreenState.rotationEvents.collect {
                container?.post { setupRotation() }
            }
        }

        setupRotation()
    }

    private fun setupComposeOverlay() {
        composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container?.addView(composeView!!)

        composeView?.setComposeContent {
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides this
            ) {
                ReaderContent()
            }
        }
    }

    @Composable
    private fun ReaderContent() {
        val state by activity.viewModel.state.collectAsState()
        val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode(resolveDefault = true))
        val isRtl = readingMode == ReadingMode.RIGHT_TO_LEFT

        val secondaryPageNum = getSecondaryPageNumber(state.currentPage)
        val secondaryPage = state.currentChapter?.pages?.getOrNull(secondaryPageNum - 1)

        val menuState = remember { mutableStateOf(false) }
        localMenuVisibleState = menuState

        if (menuState.value) {
            BackHandler {
                menuState.value = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (readingMode.type == ReadingMode.ViewerType.Webtoon && state.viewer is WebtoonViewer) {
                WebtoonSpannedContent(activity, state.viewer as WebtoonViewer)
            } else {
                AnimatedContent(
                    targetState = secondaryPage,
                    transitionSpec = {
                        val direction = if (readingMode.direction == ReadingMode.Direction.Vertical) {
                            if (targetState != null && initialState != null && targetState!!.index > initialState!!.index) {
                                AnimatedContentTransitionScope.SlideDirection.Up
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Down
                            }
                        } else if (isRtl) {
                            if (targetState != null && initialState != null && targetState!!.index > initialState!!.index) {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            }
                        } else {
                            if (targetState != null && initialState != null && targetState!!.index > initialState!!.index) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                        }

                        if (readingMode.direction == ReadingMode.Direction.Vertical) {
                            (slideIntoContainer(direction, animationSpec = tween(300)) + fadeIn(tween(300)))
                                .togetherWith(slideOutOfContainer(direction, animationSpec = tween(300)) + fadeOut(tween(300)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { if (direction == AnimatedContentTransitionScope.SlideDirection.Left) it else -it } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { if (direction == AnimatedContentTransitionScope.SlideDirection.Left) -it else it } + fadeOut(tween(300)))
                        }
                    },
                    label = "PageTransition"
                ) { page ->
                    if (page != null) {
                        AndroidView(
                            factory = { ctx ->
                                ReaderPageImageView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                }
                            },
                            update = { view ->
                                loadPageIntoView(view, page)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            LaunchedEffect(state.menuVisible) {
                if (state.menuVisible) {
                    menuState.value = false
                }
            }

            if (menuState.value) {
                ReaderAppBars(
                    visible = true,
                    mangaTitle = state.manga?.title,
                    chapterTitle = state.currentChapter?.chapter?.name,
                    navigateUp = { menuState.value = false },
                    onClickTopAppBar = { },
                    bookmarked = state.bookmarked,
                    onToggleBookmarked = { activity.viewModel.toggleChapterBookmark() },
                    onOpenInWebView = null,
                    onOpenInBrowser = null,
                    onShare = null,
                    viewer = null,
                    onNextChapter = { activity.loadNextChapter() },
                    enabledNext = state.viewerChapters?.nextChapter != null,
                    onPreviousChapter = { activity.loadPreviousChapter() },
                    enabledPrevious = state.viewerChapters?.prevChapter != null,
                    currentPage = secondaryPageNum.coerceIn(1, state.totalPages),
                    totalPages = state.totalPages,
                    onPageIndexChange = { newPageIndex ->
                        val currentReadingMode = ReadingMode.fromPreference(
                            activity.viewModel.getMangaReadingMode(resolveDefault = true),
                        )
                        val currentIsRtl = currentReadingMode == ReadingMode.RIGHT_TO_LEFT
                        val primaryPageIndex = if (currentIsRtl) newPageIndex + 1 else newPageIndex - 1
                        activity.moveToPageIndex(primaryPageIndex.coerceIn(0, state.totalPages - 1))
                    },
                    readingMode = readingMode,
                    onClickReadingMode = { activity.viewModel.openReadingModeSelectDialog() },
                    orientation = eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation.fromPreference(
                        activity.viewModel.getMangaOrientation(resolveDefault = false),
                    ),
                    onClickOrientation = { activity.viewModel.openOrientationModeSelectDialog() },
                    cropEnabled = false,
                    onClickCropBorder = { },
                    onClickSettings = { activity.viewModel.openSettingsDialog() },
                    dualScreenModeEnabled = activity.isBookModeEnabled(),
                    onClickDualScreenMode = { activity.setBookMode(!activity.isBookModeEnabled()) },
                )
            }
        }
    }

    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
    }

    @Composable
    private fun WebtoonSpannedContent(
        activity: ReaderActivity,
        primaryViewer: WebtoonViewer,
    ) {
        val context = LocalContext.current
        val secondaryRecycler = remember { WebtoonRecyclerView(context) }
        val isSyncing = remember { mutableStateOf(false) }
        
        // Synchronize scrolling between the primary and secondary screens to create a continuous reading experience
        DisposableEffect(primaryViewer.recycler) {
            
            fun syncSecondaryPosition() {
                if (isSyncing.value) return
                isSyncing.value = true
                try {
                    val primaryWidth = primaryViewer.recycler.width.toFloat()
                    val secondaryWidth = secondaryRecycler.width.toFloat()
                    
                    if (primaryWidth > 0 && secondaryWidth > 0) {
                        val ratio = secondaryWidth / primaryWidth
                        val primaryLayout = primaryViewer.recycler.layoutManager as LinearLayoutManager
                        val secondaryLayout = secondaryRecycler.layoutManager as LinearLayoutManager
                        
                        val firstPos = primaryLayout.findFirstVisibleItemPosition()
                        val firstView = primaryLayout.findViewByPosition(firstPos)
                        
                        if (firstView != null && firstPos != RecyclerView.NO_POSITION) {
                            val primaryOffset = firstView.top
                            val primaryHeight = primaryViewer.recycler.height
                            
                            val targetOffset = (primaryOffset - primaryHeight) * ratio
                            
                            val secFirstPos = secondaryLayout.findFirstVisibleItemPosition()
                            val secFirstView = secondaryLayout.findViewByPosition(secFirstPos)
                            
                            if (secFirstPos == firstPos && secFirstView != null) {
                                val currentOffset = secFirstView.top.toFloat()
                                val diff = targetOffset - currentOffset
                                
                                if (kotlin.math.abs(diff) > 1f) {
                                    secondaryRecycler.scrollBy(0, -diff.toInt())
                                }
                            } else {
                                secondaryLayout.scrollToPositionWithOffset(firstPos, targetOffset.toInt())
                            }
                        }
                    }
                } finally {
                    isSyncing.value = false
                }
            }

            val scrollListener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    syncSecondaryPosition()
                }
            }
            primaryViewer.recycler.addOnScrollListener(scrollListener)
            
            val layoutListener = object : android.view.View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: android.view.View?,
                    left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                     syncSecondaryPosition()
                }
            }
            secondaryRecycler.addOnLayoutChangeListener(layoutListener)
            
            val dataObserver = object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    syncSecondaryPosition()
                }
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    syncSecondaryPosition()
                }
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    syncSecondaryPosition()
                }
                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    syncSecondaryPosition()
                }
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    syncSecondaryPosition()
                }
            }
            primaryViewer.adapter.registerAdapterDataObserver(dataObserver)

            onDispose {
                primaryViewer.recycler.removeOnScrollListener(scrollListener)
                secondaryRecycler.removeOnLayoutChangeListener(layoutListener)
                primaryViewer.adapter.unregisterAdapterDataObserver(dataObserver)
            }
        }

        AndroidView(
            factory = { secondaryRecycler },
            update = { recycler ->
                if (recycler.adapter != primaryViewer.adapter) {
                    recycler.adapter = primaryViewer.adapter
                    recycler.layoutManager = WebtoonLayoutManager(context, context.resources.displayMetrics.heightPixels)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun loadPageIntoView(view: ReaderPageImageView, page: ReaderPage) {
        val stream = page.stream
        val status = page.status

        if (stream != null && status is Page.State.Ready) {
            val config = ReaderPageImageView.Config(
                zoomDuration = 500,
                minimumScaleType = com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
                cropBorders = false,
                zoomStartPosition = ReaderPageImageView.ZoomStartPosition.CENTER,
                landscapeZoom = false,
            )
            lifecycleScope.launch {
                try {
                    val bufferedSource = withIOContext {
                        stream().use { Buffer().readFrom(it) }
                    }
                    val isAnimated = false
                    view.setImage(bufferedSource, isAnimated, config)
                } catch (e: Exception) {
                    logcat { "Error loading page image: ${e.message}" }
                }
            }
        } else if (status is Page.State.Queue || status is Page.State.LoadPage) {
            // Page not ready, trigger load
            val loader = page.chapter.pageLoader
            if (loader != null) {
                launchIO {
                    loader.loadPage(page)
                }
            }
            // Observe page status for when it's ready
            lifecycleScope.launch {
                page.statusFlow.collect { state ->
                    if (state is Page.State.Ready) {
                        loadPageIntoView(view, page)
                    }
                }
            }
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // Must return true for onFling to work
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Handle tap at container level
                handleSecondaryScreenTap()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode(resolveDefault = true))
                if (readingMode.direction == ReadingMode.Direction.Vertical) {
                    activity.handleExternalScroll(distanceY)
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                // Detect swipe direction and navigate accordingly
                val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode(resolveDefault = true))
                
                // Handle vertical swipes
                if (readingMode.direction == ReadingMode.Direction.Vertical) {
                    // For Webtoon/Continuous, fling means kinetic scroll
                    if (readingMode.type == ReadingMode.ViewerType.Webtoon) {
                         activity.handleExternalFling(velocityY)
                         return true
                    }

                    // For Vertical Pager, fling means page turn
                    val minVelocity = 500
                    if (kotlin.math.abs(velocityY) > kotlin.math.abs(velocityX) &&
                        kotlin.math.abs(velocityY) > minVelocity) {
                        if (velocityY > 0) {
                            // Swipe down - previous page
                            activity.loadPreviousPage()
                        } else {
                            // Swipe up - next page
                            activity.loadNextPage()
                        }
                        return true
                    }
                }

                // Handle horizontal swipes for horizontal modes
                val minVelocity = 500
                if (kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY) &&
                    kotlin.math.abs(velocityX) > minVelocity) {
                    
                    val isRtl = readingMode == ReadingMode.RIGHT_TO_LEFT
                    if (velocityX > 0) {
                        // Swipe right
                        if (isRtl) activity.loadNextPage() else activity.loadPreviousPage()
                    } else {
                        // Swipe left
                        if (isRtl) activity.loadPreviousPage() else activity.loadNextPage()
                    }
                    return true
                }
                return false
            }
        })
    }

    private fun handleSecondaryScreenTap() {
        // Get current state
        val currentLocalMenu = localMenuVisible
        val primaryMenuVisible = activity.viewModel.state.value.menuVisible

        if (currentLocalMenu) {
            // Hide local menu
            localMenuVisible = false
        } else {
            // Show local menu and hide primary menu (shared menu behavior)
            localMenuVisible = true
            if (primaryMenuVisible) {
                activity.hideMenu()
            }
        }
    }

    /**
     * Calculates which page should be shown on the secondary screen based on reading direction.
     *
     * For R2L (manga): Secondary shows N-1 (page to the right)
     * For L2R (comics): Secondary shows N+1 (page to the left)
     *
     * @param currentPage The current page on the primary screen (1-based)
     * @return The page number that should be shown on the secondary screen (1-based)
     */
    private fun getSecondaryPageNumber(currentPage: Int): Int {
        val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode(resolveDefault = true))
        val isRtl = readingMode == ReadingMode.RIGHT_TO_LEFT

        return if (isRtl) {
            // R2L: secondary shows previous page (to the right)
            currentPage - 1
        } else {
            // L2R: secondary shows next page (to the left)
            currentPage + 1
        }
    }

    /**
     * Sets up rotation of the presentation display to match the activity display.
     */
    fun setupRotation() {
        val activityRotation = activity.windowManager.defaultDisplay.rotation
        val presentationRotation = display.rotation

        var actDeg = when (activityRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val presDeg = when (presentationRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        if (activity.preferences.swapPresentationRotation().get()) {
            actDeg = (actDeg + 180) % 360
        }

        val diff = (actDeg - presDeg + 360) % 360
        val rotation = diff.toFloat()

        container?.let { dashboard ->
            if (diff == 90 || diff == 270) {
                val metrics = context.resources.displayMetrics
                val w = metrics.heightPixels
                val h = metrics.widthPixels

                dashboard.layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    gravity = Gravity.CENTER
                }
                dashboard.rotation = rotation
            } else {
                dashboard.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                dashboard.rotation = rotation
            }
        }
    }

    /**
     * Toggles the menu visibility on the secondary screen.
     */
    fun toggleMenu() {
        activity.toggleMenu()
    }

    /**
     * Shows the menu on the secondary screen.
     */
    fun showMenu() {
        activity.showMenu()
    }

    /**
     * Hides the menu on the secondary screen.
     */
    fun hideMenu() {
        activity.hideMenu()
    }

    /**
     * Hides the local menu on this presentation.
     */
    fun hideLocalMenu() {
        localMenuVisible = false
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStop() {
        super.onStop()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        composeView?.disposeComposition()
        composeView = null
        container = null
        viewModelStore.clear()
    }
}
