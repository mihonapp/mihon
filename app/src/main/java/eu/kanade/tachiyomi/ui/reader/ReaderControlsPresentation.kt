package eu.kanade.tachiyomi.ui.reader

import android.app.Presentation
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ImageView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import okio.Buffer
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Dashboard mode enum for the dual-screen reader controls.
 */
sealed class DashboardMode {
    /** Normal mode with page arrows or touchpad indicator */
    object NORMAL : DashboardMode()

    /** Scrubber mode with horizontal page thumbnail strip */
    object SCRUBBER : DashboardMode()

    /** Get the next mode in the cycle (book mode is toggled separately) */
    fun next(): DashboardMode = when (this) {
        NORMAL -> SCRUBBER
        SCRUBBER -> NORMAL
    }
}

interface ControlsListener {
    fun onPagePrev()
    fun onPageNext()
    fun onChapterPrev()
    fun onChapterNext()
    fun onOpenSettings()
    fun onOpenChapterList()
    fun onSeekTo(page: Int)
}

class ReaderControlsPresentation(
    outerContext: Context,
    display: Display,
    private val activity: ReaderActivity,
) : Presentation(outerContext, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    private val readerPreferences = Injekt.get<ReaderPreferences>()

    private var normalDashboardRoot: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backgroundColor = ContextCompat.getColor(context, R.color.reader_background_dark)
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))

        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        window!!.decorView.setViewTreeLifecycleOwner(this)
        window!!.decorView.setViewTreeSavedStateRegistryOwner(this)
        window!!.decorView.setViewTreeViewModelStoreOwner(this)

        showNormalDashboard()
    }

    private fun showNormalDashboard() {
        val container = FrameLayout(context)
        val backgroundColor = ContextCompat.getColor(context, R.color.reader_background_dark)
        container.setBackgroundColor(backgroundColor)
        setContentView(container)

        val dashboard = layoutInflater.inflate(R.layout.presentation_reader_dashboard, container, false)
        container.addView(dashboard)
        normalDashboardRoot = dashboard

        setupRotation()

        val pageSeeker = dashboard.findViewById<SeekBar>(R.id.page_seeker)
        val textCurrentPage = dashboard.findViewById<TextView>(R.id.text_current_page)
        val textCurrentChapter = dashboard.findViewById<TextView>(R.id.text_current_chapter)
        val btnPrevPage = dashboard.findViewById<ImageButton>(R.id.btn_prev_page)
        val btnNextPage = dashboard.findViewById<ImageButton>(R.id.btn_next_page)
        val btnPrevChapter = dashboard.findViewById<ImageButton>(R.id.btn_prev_chapter)
        val btnNextChapter = dashboard.findViewById<ImageButton>(R.id.btn_next_chapter)
        val btnSettings = dashboard.findViewById<ImageButton>(R.id.btn_reader_settings)
        val btnChapters = dashboard.findViewById<ImageButton>(R.id.btn_chapters)
        val btnDisplayMode = dashboard.findViewById<ImageButton>(R.id.btn_display_mode)
        val btnBookMode = dashboard.findViewById<ImageButton>(R.id.btn_book_mode)
        val touchpadArea = dashboard.findViewById<View>(R.id.touchpad_area)
        val touchpadIndicator = dashboard.findViewById<android.widget.ImageView>(R.id.iv_touchpad_indicator)
        val recyclerPreviews = dashboard.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_page_previews)
        val middleContainer = dashboard.findViewById<ViewGroup>(R.id.middle_container)
        val btnViewMode = dashboard.findViewById<ImageButton>(R.id.btn_view_mode)

        var currentDashboardMode: DashboardMode = DashboardMode.NORMAL

        val accentBlue = 0xFF2196F3.toInt()
        val accentBlueList = ColorStateList.valueOf(accentBlue)

        pageSeeker.progressTintList = accentBlueList
        pageSeeker.thumbTintList = accentBlueList
        touchpadIndicator.imageTintList = ColorStateList.valueOf(Color.WHITE)

        recyclerPreviews.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val adapter = ReaderPagePreviewAdapter { page ->
            activity.moveToPageIndex(page.index)
        }
        recyclerPreviews.adapter = adapter

        fun moveNext() {
            val current = activity.viewModel.state.value.currentPage
            val total = activity.viewModel.state.value.totalPages
            if (current < total) activity.moveToPageIndex(current)
            else activity.loadNextChapter()
        }
        fun movePrev() {
            val current = activity.viewModel.state.value.currentPage
            if (current > 1) activity.moveToPageIndex(current - 2)
            else activity.loadPreviousChapter()
        }

        fun updateMiddleAreaVisibility(isWebtoon: Boolean) {
            when (currentDashboardMode) {
                DashboardMode.SCRUBBER -> {
                    recyclerPreviews.visibility = View.VISIBLE
                    middleContainer.visibility = View.GONE
                    btnViewMode.setImageResource(R.drawable.ic_touchpad_scroll_24dp)
                }
                DashboardMode.NORMAL -> {
                    recyclerPreviews.visibility = View.GONE
                    middleContainer.visibility = View.VISIBLE
                    btnViewMode.setImageResource(R.drawable.ic_view_module_24dp)
                    touchpadIndicator.setImageResource(R.drawable.ic_touchpad_scroll_24dp)
                    val visibility = if (isWebtoon) View.GONE else View.VISIBLE
                    btnPrevPage.visibility = visibility
                    btnNextPage.visibility = visibility
                    touchpadIndicator.visibility = if (isWebtoon) View.VISIBLE else View.GONE
                }
            }
            btnViewMode.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }

        var isPanningMode = false

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isPanningMode) {
                    activity.handleExternalPan(-distanceX, -distanceY)
                } else {
                    activity.handleExternalScroll(distanceY)
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!isPanningMode) {
                    activity.handleExternalFling(velocityY)
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (activity.isZoomed()) {
                    isPanningMode = !isPanningMode
                    touchpadIndicator.setImageResource(
                        if (isPanningMode) R.drawable.ic_drag_handle_24dp else R.drawable.ic_touchpad_scroll_24dp
                    )
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                activity.handleExternalZoomReset()
                isPanningMode = false
                touchpadIndicator.setImageResource(R.drawable.ic_touchpad_scroll_24dp)
                return true
            }
        })

        val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                activity.handleExternalScale(detector.scaleFactor)
                return true
            }
        })

        touchpadArea.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        btnPrevPage.setOnClickListener {
            val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode())
            if (readingMode == ReadingMode.RIGHT_TO_LEFT) moveNext() else movePrev()
        }
        btnNextPage.setOnClickListener {
            val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode())
            if (readingMode == ReadingMode.RIGHT_TO_LEFT) movePrev() else moveNext()
        }
        btnPrevChapter.setOnClickListener { activity.loadPreviousChapter() }
        btnNextChapter.setOnClickListener { activity.loadNextChapter() }
        btnSettings.setOnClickListener { activity.viewModel.openSettingsDialog() }
        btnChapters.setOnClickListener { activity.openMangaScreen() }
        btnDisplayMode.setOnClickListener { activity.viewModel.openReadingModeSelectDialog() }

        btnBookMode.setOnClickListener {
            activity.setBookMode(true)
        }

        btnViewMode.setOnClickListener {
            currentDashboardMode = currentDashboardMode.next()
            val readingMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode())
            val isWebtoon = readingMode == ReadingMode.WEBTOON || readingMode == ReadingMode.CONTINUOUS_VERTICAL
            updateMiddleAreaVisibility(isWebtoon)
        }

        pageSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) activity.moveToPageIndex(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        lifecycleScope.launch {
            activity.viewModel.state.collect { state ->
                val currentPage = state.currentPage
                val totalPages = state.totalPages

                pageSeeker.max = (totalPages - 1).coerceAtLeast(0)
                pageSeeker.progress = (currentPage - 1).coerceAtLeast(0)

                textCurrentPage.text = "$currentPage / $totalPages"
                textCurrentChapter.text = state.currentChapter?.chapter?.name ?: ""

                btnPrevChapter.isEnabled = state.viewerChapters?.prevChapter != null
                btnNextChapter.isEnabled = state.viewerChapters?.nextChapter != null

                val resolvedMode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode())
                btnDisplayMode.setImageResource(resolvedMode.iconRes)
                btnDisplayMode.imageTintList = ColorStateList.valueOf(Color.WHITE)

                val isRtl = resolvedMode == ReadingMode.RIGHT_TO_LEFT
                val direction = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                pageSeeker.layoutDirection = direction
                recyclerPreviews.layoutDirection = direction

                val pages = state.currentChapter?.pages
                if (pages != null) {
                    adapter.submitList(pages)
                    val currentReaderPage = pages.getOrNull(currentPage - 1)
                    if (currentReaderPage != null) {
                        adapter.setCurrentPage(currentReaderPage)
                        recyclerPreviews.scrollToPosition(currentPage - 1)
                    }
                }

                val isWebtoon = resolvedMode == ReadingMode.WEBTOON || resolvedMode == ReadingMode.CONTINUOUS_VERTICAL
                updateMiddleAreaVisibility(isWebtoon)
                updateNavigationButtons(middleContainer as androidx.constraintlayout.widget.ConstraintLayout, btnPrevPage, btnNextPage, resolvedMode)
            }
        }
    }

    private fun updateNavigationButtons(
        middleContainer: androidx.constraintlayout.widget.ConstraintLayout,
        btnPrev: ImageButton,
        btnNext: ImageButton,
        readingMode: ReadingMode,
    ) {
        val isVertical = readingMode.direction == ReadingMode.Direction.Vertical
        val isWebtoon = readingMode == ReadingMode.WEBTOON || readingMode == ReadingMode.CONTINUOUS_VERTICAL

        // Hide navigation buttons in webtoon modes (scroll-based, not page-based)
        val buttonVisibility = if (isWebtoon) View.GONE else View.VISIBLE
        btnPrev.visibility = buttonVisibility
        btnNext.visibility = buttonVisibility

        // Skip constraint updates for hidden buttons
        if (isWebtoon) return

        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(middleContainer)
        
        // Clear all existing constraints
        constraintSet.clear(btnPrev.id)
        constraintSet.clear(btnNext.id)
        constraintSet.setRotation(btnPrev.id, 0f)
        constraintSet.setRotation(btnNext.id, 0f)

        // Ensure arrows are solid white
        val whiteList = ColorStateList.valueOf(Color.WHITE)
        btnPrev.imageTintList = whiteList
        btnNext.imageTintList = whiteList

        if (isVertical) {
            btnPrev.setImageResource(R.drawable.ic_chevron_up_24dp)
            btnNext.setImageResource(R.drawable.ic_chevron_down_24dp)
            
            // Vertical Chain Manual Setup (50/50 split)
            // btnPrev TOP -> PARENT TOP
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
            // btnPrev BOTTOM -> btnNext TOP
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, btnNext.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
            // btnNext TOP -> btnPrev BOTTOM
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.TOP, btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
            // btnNext BOTTOM -> PARENT BOTTOM
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

            // Horizontal Centering (Explicit START/END for RTL safety)
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

            // Weights and Sizes
            constraintSet.constrainWidth(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainWidth(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            
            constraintSet.setVerticalWeight(btnPrev.id, 1f)
            constraintSet.setVerticalWeight(btnNext.id, 1f)
        } else {
            btnPrev.setImageResource(R.drawable.ic_chevron_left_24dp)
            btnNext.setImageResource(R.drawable.ic_chevron_right_24dp)

            // Horizontal Chain Manual Setup (50/50 split)
            // btnPrev START -> PARENT START
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            // btnPrev END -> btnNext START
            constraintSet.connect(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.END, btnNext.id, androidx.constraintlayout.widget.ConstraintSet.START)
            // btnNext START -> btnPrev END
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.START, btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.END)
            // btnNext END -> PARENT END
            constraintSet.connect(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

            // Vertical Centering
            constraintSet.centerVertically(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID)
            constraintSet.centerVertically(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID)

            // Weights and Sizes
            constraintSet.constrainWidth(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(btnPrev.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainWidth(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(btnNext.id, androidx.constraintlayout.widget.ConstraintSet.MATCH_CONSTRAINT)

            constraintSet.setHorizontalWeight(btnPrev.id, 1f)
            constraintSet.setHorizontalWeight(btnNext.id, 1f)
        }
        constraintSet.applyTo(middleContainer)
    }

    fun setupRotation() {
        val dashboard = normalDashboardRoot ?: return
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
        viewModelStore.clear()
    }

    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = activity.onBackPressedDispatcher
}
