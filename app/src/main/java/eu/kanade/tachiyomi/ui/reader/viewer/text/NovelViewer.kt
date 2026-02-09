package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.i18n.stringResource
import uy.kohesive.injekt.injectLazy
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Custom span for paragraph spacing - adds vertical space after paragraphs
 */
private class ParagraphSpacingSpan(private val spacingPx: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        // Only add spacing after the last line of a paragraph (ends with newline)
        if (end > 0 && end <= text.length && text[end - 1] == '\n') {
            fm.descent += spacingPx
            fm.bottom += spacingPx
        }
    }
}

/**
 * Custom span for paragraph indent - adds leading margin to first line
 */
private class ParagraphIndentSpan(private val indentPx: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int {
        return if (first) indentPx else 0
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        // No custom drawing needed
    }
}

/**
 * Drawable wrapper that delegates drawing to an inner drawable.
 * Used as a placeholder that can be updated asynchronously when images load.
 */
private class DrawableWrapper : Drawable() {
    var innerDrawable: Drawable? = null

    override fun draw(canvas: Canvas) {
        innerDrawable?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        innerDrawable?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        innerDrawable?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = innerDrawable?.opacity ?: PixelFormat.TRANSPARENT
}

/**
 * Html.ImageGetter implementation that loads images asynchronously using Coil 3.
 * Images are scaled to fit within the TextView width while maintaining aspect ratio.
 */
private class CoilImageGetter(
    private val textView: TextView,
    private val activity: ReaderActivity,
) : Html.ImageGetter {

    override fun getDrawable(source: String?): Drawable {
        val wrapper = DrawableWrapper()
        if (source.isNullOrBlank()) return wrapper

        // Handle base64 data URIs inline (EPUB downloads encode media as base64)
        if (source.startsWith("data:")) {
            try {
                val commaIndex = source.indexOf(',')
                if (commaIndex > 0) {
                    val base64Data = source.substring(commaIndex + 1)
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val drawable = android.graphics.drawable.BitmapDrawable(activity.resources, bitmap)
                        val maxWidth = textView.width - textView.paddingLeft - textView.paddingRight
                        val imgWidth = drawable.intrinsicWidth
                        val imgHeight = drawable.intrinsicHeight
                        if (imgWidth > 0 && imgHeight > 0) {
                            val width = if (maxWidth > 0 && imgWidth > maxWidth) maxWidth else imgWidth
                            val ratio = width.toFloat() / imgWidth.toFloat()
                            val height = (imgHeight * ratio).toInt()
                            drawable.setBounds(0, 0, width, height)
                            wrapper.innerDrawable = drawable
                            wrapper.setBounds(0, 0, width, height)
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "Failed to decode base64 image: ${e.message}" }
            }
            return wrapper
        }

        // Skip EPUB-internal relative paths (they reference files inside the archive)
        if (!source.startsWith("http://") && !source.startsWith("https://") && !source.startsWith("//")) {
            logcat(LogPriority.DEBUG) { "Skipping non-URL image source: $source" }
            return wrapper
        }

        // Resolve protocol-relative URLs
        val imageUrl = if (source.startsWith("//")) "https:$source" else source

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val request = ImageRequest.Builder(activity)
                    .data(imageUrl)
                    .build()
                val result = activity.imageLoader.execute(request)
                val drawable = result.image?.asDrawable(activity.resources) ?: return@launch

                // Scale to fit TextView width
                val maxWidth = textView.width - textView.paddingLeft - textView.paddingRight
                val imgWidth = drawable.intrinsicWidth
                val imgHeight = drawable.intrinsicHeight

                if (imgWidth <= 0 || imgHeight <= 0) return@launch

                val width = if (maxWidth > 0 && imgWidth > maxWidth) maxWidth else imgWidth
                val ratio = width.toFloat() / imgWidth.toFloat()
                val height = (imgHeight * ratio).toInt()

                drawable.setBounds(0, 0, width, height)
                wrapper.innerDrawable = drawable
                wrapper.setBounds(0, 0, width, height)

                // Force TextView to re-layout with the loaded image
                // Use invalidate() + requestLayout() instead of re-assigning text
                // to avoid breaking text selection state
                textView.invalidate()
                textView.requestLayout()
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "Failed to load image in novel reader: $imageUrl - ${e.message}" }
            }
        }

        return wrapper
    }
}

/**
 * NovelViewer renders novel content using a native TextView.
 * It supports custom parsing, styling, and pagination.
 */
class NovelViewer(val activity: ReaderActivity) : Viewer, TextToSpeech.OnInitListener {

    private val container = FrameLayout(activity)
    private lateinit var scrollView: NestedScrollView
    private lateinit var contentContainer: LinearLayout
    private var bottomLoadingIndicator: ProgressBar? = null
    private val preferences: ReaderPreferences by injectLazy()
    private var tts: TextToSpeech? = null
    private var isAutoScrolling = false
    private var autoScrollJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var currentPage: ReaderPage? = null
    private var currentChapters: ViewerChapters? = null

    // Track loaded chapters for infinite scroll
    private data class LoadedChapter(
        val chapter: ReaderChapter,
        val textView: TextView,
        val headerView: TextView,
        var isLoaded: Boolean = false,
    )

    private val loadedChapters = mutableListOf<LoadedChapter>()
    private var isLoadingNext = false
    private var isRestoringScroll = false
    private var currentChapterIndex = 0

    // Flag to track if next chapter load is from infinite scroll (vs manual navigation)
    private var isInfiniteScrollNavigation = false

    // For tracking scroll position and progress
    private var lastSavedProgress = 0f
    private var progressSaveJob: Job? = null

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            // Increased thresholds for less sensitive swipe detection
            private val SWIPE_THRESHOLD = 150
            private val SWIPE_VELOCITY_THRESHOLD = 200

            // Require horizontal swipe to be significantly more horizontal than vertical
            private val DIRECTION_RATIO = 1.5f

            override fun onDown(e: MotionEvent): Boolean {
                // Return true so we continue receiving gesture events
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (!preferences.novelSwipeNavigation().get()) return false
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // Require horizontal swipe to be significantly more horizontal than vertical
                val absDiffX = kotlin.math.abs(diffX)
                val absDiffY = kotlin.math.abs(diffY)

                if (absDiffX > absDiffY * DIRECTION_RATIO) {
                    if (absDiffX > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right - go to previous chapter
                            activity.loadPreviousChapter()
                        } else {
                            // Swipe left - go to next chapter
                            activity.loadNextChapter()
                        }
                        return true
                    }
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val viewWidth = container.width.toFloat()
                val viewHeight = container.height.toFloat()
                val x = e.x
                val y = e.y

                // Define center region (middle third of the screen)
                val centerXStart = viewWidth / 3
                val centerXEnd = viewWidth * 2 / 3
                val centerYStart = viewHeight / 3
                val centerYEnd = viewHeight * 2 / 3

                if (x in centerXStart..centerXEnd && y in centerYStart..centerYEnd) {
                    // Center tap - toggle menu
                    activity.toggleMenu()
                    return true
                }

                // Handle tap-to-scroll if enabled
                if (preferences.novelTapToScroll().get()) {
                    // Top zone - scroll up
                    if (y < centerYStart) {
                        scrollView.smoothScrollBy(0, -250)
                        return true
                    }
                    // Bottom zone - scroll down
                    if (y > centerYEnd) {
                        scrollView.smoothScrollBy(0, 250)
                        return true
                    }
                }

                return false
            }
        },
    ).apply {
        // Disable long press handling so TextView can handle text selection
        setIsLongpressEnabled(false)
    }

    init {
        initViews()
        container.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        // Defer TTS initialization until actually needed to avoid "not bound" errors
        // TTS will be initialized lazily when startTts() is called
        observePreferences()
        setupScrollListener()
    }

    private fun initViews() {
        scrollView = object : NestedScrollView(activity) {
            private var isTextSelectionMode = false
            private var selectionStartX = 0f
            private var selectionStartY = 0f
            private var touchedTextView: TextView? = null

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // Always pass touch events to gesture detector first
                gestureDetector.onTouchEvent(ev)
                return super.dispatchTouchEvent(ev)
            }

            private fun findTextViewAt(x: Float, y: Float): TextView? {
                loadedChapters.forEach { loaded ->
                    val textView = loaded.textView
                    val location = IntArray(2)
                    textView.getLocationOnScreen(location)
                    val scrollViewLocation = IntArray(2)
                    this.getLocationOnScreen(scrollViewLocation)
                    
                    val relativeTop = location[1] - scrollViewLocation[1]
                    val relativeBottom = relativeTop + textView.height
                    
                    if (y >= relativeTop && y <= relativeBottom) {
                        return textView
                    }
                }
                return null
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                // If text selection is enabled, we need to be careful about intercepting
                if (preferences.novelTextSelectable().get()) {
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isTextSelectionMode = false
                            selectionStartX = ev.x
                            selectionStartY = ev.y
                            touchedTextView = findTextViewAt(ev.x, ev.y)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // If text selection mode is active, don't intercept
                            if (isTextSelectionMode) {
                                return false
                            }
                            
                            val deltaX = kotlin.math.abs(ev.x - selectionStartX)
                            val deltaY = kotlin.math.abs(ev.y - selectionStartY)
                            
                            // If horizontal movement is greater than vertical, likely text selection
                            if (deltaX > deltaY && deltaX > 10) {
                                isTextSelectionMode = true
                                return false
                            }
                            
                            // Check for small movements (likely text selection vs scroll)
                            if (deltaY < 20 && deltaX < 20) {
                                // Very small movement - might be selecting text, don't intercept
                                return false
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            touchedTextView = null
                        }
                    }

                    // Check if any child TextView has an active text selection or focus
                    loadedChapters.forEach { loaded ->
                        if (loaded.textView.hasSelection() || loaded.textView.isFocused) {
                            isTextSelectionMode = true
                            return false
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
            isFillViewport = true
        }

        contentContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        scrollView.addView(contentContainer)
    }

    private fun setupScrollListener() {
        scrollView.setOnScrollChangeListener { _: NestedScrollView, _: Int, scrollY: Int, _: Int, _: Int ->
            val child = scrollView.getChildAt(0) ?: return@setOnScrollChangeListener
            val totalHeight = child.height - scrollView.height

            if (totalHeight <= 0) return@setOnScrollChangeListener

            val overallProgress = scrollY.toFloat() / totalHeight

            // Track chapter index before update to detect chapter change
            val previousChapterIndex = currentChapterIndex

            // Update current chapter based on scroll position first
            updateCurrentChapterFromScroll(scrollY)

            // If chapter changed, skip progress update (updateCurrentChapterFromScroll already reset it to 0)
            val chapterChanged = previousChapterIndex != currentChapterIndex
            if (chapterChanged) return@setOnScrollChangeListener

            // Calculate progress within current chapter (not overall)
            val chapterProgress = calculateCurrentChapterProgress(scrollY)

            // Save progress periodically (debounced)
            scheduleProgressSave(chapterProgress)

            // Notify activity of progress change for slider update
            activity.onNovelProgressChanged(chapterProgress)

            // Check for infinite scroll - load next/prev chapters seamlessly
            if (preferences.novelInfiniteScroll().get()) {
                // Historically this preference could be stored as 0; treat it as a sensible default.
                val autoLoadAt = preferences.novelAutoLoadNextChapterAt().get()
                val effectiveThreshold = if (autoLoadAt > 0) autoLoadAt / 100f else 0.95f

                // Load next chapter when reaching threshold within the current chapter.
                if (!isRestoringScroll && chapterProgress >= effectiveThreshold && !isLoadingNext) {
                    logcat(LogPriority.DEBUG) {
                        "NovelViewer: scroll threshold hit (progress=$chapterProgress >= $effectiveThreshold, currentIdx=$currentChapterIndex, loadedCount=${loadedChapters.size})"
                    }
                    loadNextChapterIfAvailable()
                }
            }
        }
    }

    /**
     * Calculate progress within the current chapter only (not overall scroll progress)
     */
    private fun calculateCurrentChapterProgress(scrollY: Int): Float {
        if (loadedChapters.isEmpty()) return 0f

        // For single chapter, calculate simple progress
        if (loadedChapters.size == 1) {
            val child = scrollView.getChildAt(0) ?: return 0f
            val totalHeight = child.height - scrollView.height
            if (totalHeight <= 0) return 0f
            return (scrollY.toFloat() / totalHeight).coerceIn(0f, 1f)
        }

        // For multiple chapters, calculate progress within current chapter using actual view positions.
        val loadedChapter = loadedChapters.getOrNull(currentChapterIndex) ?: return 0f
        val chapterTop = loadedChapter.headerView.top
        val chapterBottom = loadedChapter.textView.bottom
        val chapterHeight = (chapterBottom - chapterTop).coerceAtLeast(1)
        val visibleHeight = scrollView.height
        val effectiveChapterHeight = (chapterHeight - visibleHeight).coerceAtLeast(1)
        val chapterScrollY = (scrollY - chapterTop).coerceIn(0, effectiveChapterHeight)
        return (chapterScrollY.toFloat() / effectiveChapterHeight).coerceIn(0f, 1f)

        return 0f
    }

    private fun scheduleProgressSave(progress: Float) {
        // Only save if progress changed significantly
        if (kotlin.math.abs(progress - lastSavedProgress) < 0.01f) return

        progressSaveJob?.cancel()
        progressSaveJob = scope.launch {
            delay(500) // Debounce
            saveProgress(progress)
            lastSavedProgress = progress
        }
    }

    private fun saveProgress(progress: Float) {
        currentPage?.let { page ->
            // Store progress as percentage (0-100) for novel chapters
            val progressValue = (progress * 100).toInt().coerceIn(0, 100)
            activity.saveNovelProgress(page, progressValue)
            logcat(LogPriority.DEBUG) { "NovelViewer: Saving progress $progressValue% for chapter" }
        }
    }

    private fun updateCurrentChapterFromScroll(scrollY: Int) {
        if (loadedChapters.size <= 1) return

        // Find which chapter is currently in view based on visible area center, using actual view positions.
        val visibleCenter = scrollY + (scrollView.height / 2)

        for ((index, loadedChapter) in loadedChapters.withIndex()) {
            val chapterStart = loadedChapter.headerView.top
            val chapterEnd = loadedChapter.textView.bottom
            if (chapterEnd <= chapterStart) continue

            // Check if visible center is within this chapter
            if (visibleCenter >= chapterStart && visibleCenter < chapterEnd) {
                if (currentChapterIndex != index) {
                    val oldIndex = currentChapterIndex
                    currentChapterIndex = index

                    // Determine initial progress based on direction
                    // If moving forward (index > oldIndex), we are at start (0%)
                    // If moving backward (index < oldIndex), we are at end (100%)
                    val initialProgress = if (index > oldIndex) 0f else 1f

                    // Reset progress for new chapter
                    lastSavedProgress = initialProgress

                    // Update current page reference and notify activity
                    loadedChapter.chapter.pages?.firstOrNull()?.let { page ->
                        currentPage = page
                        // Update novel app bar title based on what's visible (no chapter reload).
                        activity.viewModel.setNovelVisibleChapter(loadedChapter.chapter.chapter)
                        // Also update the active chapter pointers so infinite scroll can continue past one append.
                        activity.onPageSelected(page)
                        logcat(LogPriority.DEBUG) {
                            "NovelViewer: Chapter changed from index $oldIndex to $index (${loadedChapter.chapter.chapter.name})"
                        }

                        // Force immediate progress update to UI
                        activity.onNovelProgressChanged(initialProgress)
                    }
                }
                break
            }
        }
    }

    private fun loadNextChapterIfAvailable() {
        val anchor = loadedChapters.lastOrNull()?.chapter ?: currentChapters?.currChapter ?: run {
            logcat(LogPriority.ERROR) { "NovelViewer: loadNext failed, no anchor (loadedCount=${loadedChapters.size})" }
            showInlineError("No anchor chapter for infinite scroll", isPrepend = false)
            return
        }

        if (isLoadingNext) {
            logcat(LogPriority.DEBUG) { "NovelViewer: loadNext ignored, already loading" }
            return
        }
        isLoadingNext = true
        logcat(LogPriority.DEBUG) {
            "NovelViewer: loadNext starting from anchor=${anchor.chapter.id}/${anchor.chapter.name}"
        }

        scope.launch {
            try {
                val preparedChapter = activity.viewModel.prepareNextChapterForInfiniteScroll(anchor) ?: run {
                    logcat(LogPriority.WARN) { "NovelViewer: No next chapter after ${anchor.chapter.name}" }
                    showInlineError("No next chapter available", isPrepend = false)
                    return@launch
                }
                logcat(LogPriority.DEBUG) {
                    "NovelViewer: prepared next=${preparedChapter.chapter.id}/${preparedChapter.chapter.name}"
                }

                // Prevent duplicates (e.g., if anchor gets out of sync)
                if (loadedChapters.any { it.chapter.chapter.id == preparedChapter.chapter.id }) {
                    logcat(LogPriority.DEBUG) {
                        "NovelViewer: next chapter ${preparedChapter.chapter.id} already loaded"
                    }
                    return@launch
                }
                val page = preparedChapter.pages?.firstOrNull() as? ReaderPage ?: run {
                    logcat(LogPriority.ERROR) { "NovelViewer: No page in prepared next chapter" }
                    showInlineError("No page in next chapter", isPrepend = false)
                    return@launch
                }
                val loader = page.chapter.pageLoader ?: run {
                    logcat(LogPriority.ERROR) { "NovelViewer: No loader for next chapter" }
                    showInlineError("No loader for next chapter", isPrepend = false)
                    return@launch
                }

                showInlineLoading(isPrepend = false)
                logcat(LogPriority.DEBUG) {
                    "NovelViewer: loading page for next ${preparedChapter.chapter.id}, state=${page.status}"
                }

                val loaded = try {
                    awaitPageText(page = page, loader = loader, timeoutMs = 30_000)
                } catch (e: TimeoutCancellationException) {
                    logcat(LogPriority.ERROR) { "NovelViewer: Timed out loading next chapter page after 30s" }
                    showInlineError("Timeout loading next chapter", isPrepend = false)
                    false
                } catch (e: CancellationException) {
                    // Reader was closed/navigated away; don't surface as an error.
                    logcat(LogPriority.DEBUG) { "NovelViewer: loadNext cancelled" }
                    false
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "NovelViewer: Error loading next chapter page: ${e.message}" }
                    showInlineError("Error: ${e.message ?: "Unknown error"}", isPrepend = false)
                    false
                }

                if (!loaded) return@launch

                // Append seamlessly with no UI interruption.
                logcat(LogPriority.DEBUG) { "NovelViewer: appending chapter ${preparedChapter.chapter.id}" }
                displayChapter(preparedChapter, page)
                logcat(LogPriority.INFO) {
                    "NovelViewer: Successfully appended next chapter ${preparedChapter.chapter.name}"
                }
            } finally {
                hideInlineLoading()
                isLoadingNext = false
            }
        }
    }

    /**
     * Silently preload next chapter without UI interruption.
     * This is triggered when reaching a certain scroll percentage.
     */
    private fun preloadNextChapterIfAvailable() {
        // Keep existing public behavior, but for novel infinite scroll we just append the next chapter.
        loadNextChapterIfAvailable()
    }

    // Backward auto-prepend is intentionally disabled (forward-only infinite scroll).

    private suspend fun awaitPageText(
        page: ReaderPage,
        loader: eu.kanade.tachiyomi.ui.reader.loader.PageLoader,
        timeoutMs: Long,
    ): Boolean {
        // If already loaded and has content, don't trigger a second request.
        if (!page.text.isNullOrBlank() && page.status is Page.State.Ready) {
            logcat(LogPriority.DEBUG) { "NovelViewer: page already ready, text.length=${page.text?.length ?: 0}" }
            return true
        }

        // Trigger loading if still queued.
        // IMPORTANT: loadPage() never returns (suspends forever), so fire-and-forget with launch.
        if (page.status is Page.State.Queue) {
            scope.launch(Dispatchers.IO) {
                loader.loadPage(page)
            }
        }

        // Wait for statusFlow to emit Ready or Error
        val finalState = withTimeout(timeoutMs) {
            page.statusFlow.first { state ->
                state is Page.State.Ready || state is Page.State.Error
            }
        }

        return when (finalState) {
            is Page.State.Ready -> {
                logcat(LogPriority.DEBUG) { "NovelViewer: page ready, text.length=${page.text?.length ?: 0}" }
                !page.text.isNullOrBlank()
            }
            is Page.State.Error -> {
                logcat(LogPriority.ERROR) { "NovelViewer: page error: ${finalState.error.message}" }
                false
            }
            else -> false
        }
    }

    private var inlineLoadingView: TextView? = null

    private fun showInlineLoading(isPrepend: Boolean) {
        if (inlineLoadingView != null) return
        inlineLoadingView = TextView(activity).apply {
            text = activity.stringResource(tachiyomi.i18n.MR.strings.loading)
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
        }
        val view = inlineLoadingView ?: return
        if (isPrepend) {
            contentContainer.addView(view, 0)
        } else {
            contentContainer.addView(view)
        }
    }

    private fun hideInlineLoading() {
        inlineLoadingView?.let { view ->
            contentContainer.removeView(view)
        }
        inlineLoadingView = null
    }

    private var inlineErrorView: android.widget.TextView? = null

    private fun showInlineError(message: String, isPrepend: Boolean) {
        // Remove any existing error
        inlineErrorView?.let { view ->
            contentContainer.removeView(view)
        }

        inlineErrorView = android.widget.TextView(activity).apply {
            text = "$message (tap to dismiss)"
            textSize = 14f
            setTextColor(0xFFFF5252.toInt())
            setBackgroundColor(0x1AFF5252.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
            setOnClickListener {
                contentContainer.removeView(this)
                inlineErrorView = null
            }
        }

        val view = inlineErrorView ?: return
        if (isPrepend) {
            contentContainer.addView(view, 0)
        } else {
            contentContainer.addView(view)
        }

        // Auto-dismiss after 8 seconds
        scope.launch {
            delay(8000)
            if (inlineErrorView == view) {
                contentContainer.removeView(view)
                inlineErrorView = null
            }
        }
    }

    private fun prependChapter(chapter: ReaderChapter, page: ReaderPage) {
        var content = page.text
        if (content.isNullOrBlank()) {
            logcat(LogPriority.ERROR) { "NovelViewer: Page text is null or blank (prepend)" }
            return
        }

        if (preferences.novelHideChapterTitle().get()) {
            content = stripChapterTitle(content, chapter.chapter.name)
        }

        // Optionally force lowercase
        if (preferences.novelForceTextLowercase().get()) {
            content = content.lowercase()
        }

        // Prevent duplicates
        val existingIndex = loadedChapters.indexOfFirst { it.chapter.chapter.id == chapter.chapter.id }
        if (existingIndex >= 0) return

        val oldScrollY = scrollView.scrollY

        val headerView = TextView(activity).apply {
            text = chapter.chapter.name
            textSize = 18f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 16)
            isVisible = false
        }

        val textView = createSelectableTextView()
        applyTextViewStyles(textView)

        val loadedChapter = LoadedChapter(
            chapter = chapter,
            textView = textView,
            headerView = headerView,
            isLoaded = true,
        )

        loadedChapters.add(0, loadedChapter)
        currentChapterIndex += 1

        contentContainer.addView(headerView, 0)
        contentContainer.addView(textView, 1)

        setTextViewContent(textView, content)
        if (activity.isTranslationEnabled() && !preferences.novelShowRawHtml().get()) {
            val finalContent = content
            scope.launch {
                val translatedContent = activity.translateContentIfEnabled(finalContent)
                withContext(Dispatchers.Main) {
                    setTextViewContent(textView, translatedContent)
                }
            }
        }

        applyBackgroundColor()

        // Keep scroll position stable after the prepend
        scrollView.post {
            val addedHeight = headerView.height + textView.height
            if (addedHeight > 0) {
                scrollView.scrollTo(0, oldScrollY + addedHeight)
            }
        }

        cleanupDistantChapters()
    }

    private fun showTopLoadingIndicator() {
        if (bottomLoadingIndicator == null) {
            bottomLoadingIndicator = ProgressBar(activity).apply {
                isIndeterminate = true
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 16, 0, 16)
        }

        if (bottomLoadingIndicator?.parent == null) {
            contentContainer.addView(bottomLoadingIndicator, 0, params) // Add at top (index 0)
        }
        bottomLoadingIndicator?.isVisible = true
    }

    private fun hideTopLoadingIndicator() {
        bottomLoadingIndicator?.isVisible = false
        (bottomLoadingIndicator?.parent as? ViewGroup)?.removeView(bottomLoadingIndicator)
    }

    private fun showBottomLoadingIndicator() {
        if (bottomLoadingIndicator == null) {
            bottomLoadingIndicator = ProgressBar(activity).apply {
                isIndeterminate = true
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 16, 0, 16)
        }

        if (bottomLoadingIndicator?.parent == null) {
            contentContainer.addView(bottomLoadingIndicator, params)
        }
        bottomLoadingIndicator?.isVisible = true
    }

    private fun hideBottomLoadingIndicator() {
        bottomLoadingIndicator?.isVisible = false
        (bottomLoadingIndicator?.parent as? ViewGroup)?.removeView(bottomLoadingIndicator)
    }

    private fun observePreferences() {
        // Observe preference changes and refresh content
        scope.launch {
            merge(
                preferences.novelFontSize().changes(),
                preferences.novelFontFamily().changes(),
                preferences.novelTheme().changes(),
                preferences.novelLineHeight().changes(),
                preferences.novelTextAlign().changes(),
                preferences.novelMarginLeft().changes(),
                preferences.novelMarginRight().changes(),
                preferences.novelMarginTop().changes(),
                preferences.novelMarginBottom().changes(),
                preferences.novelFontColor().changes(),
                preferences.novelBackgroundColor().changes(),
            ).drop(11) // Drop initial emissions from all 11 preferences
                .collect {
                    // Re-display text when preferences change
                    refreshAllChapterStyles()
                }
        }

        // Observe paragraph formatting preferences separately (require content reload)
        scope.launch {
            merge(
                preferences.novelParagraphIndent().changes(),
                preferences.novelParagraphSpacing().changes(),
                preferences.novelShowRawHtml().changes(),
            ).drop(3) // Drop initial emissions
                .collect {
                    // Reload content to apply new formatting
                    currentChapters?.let { setChapters(it) }
                }
        }

        // Observe text selection preference separately
        // Note: setTextIsSelectable() requires recreating the textview to work properly
        // when toggling at runtime, so we fully reload chapters
        scope.launch {
            preferences.novelTextSelectable().changes()
                .drop(1) // Drop initial value
                .collectLatest { selectable ->
                    // Force reload all chapters by clearing loaded chapters first
                    // This ensures the text selection property is properly applied
                    activity.runOnUiThread {
                        // Update text selection on existing loaded chapters immediately
                        loadedChapters.forEach { loaded ->
                            loaded.textView.setTextIsSelectable(selectable)
                            loaded.textView.isFocusable = selectable
                            loaded.textView.isFocusableInTouchMode = selectable
                            loaded.textView.isLongClickable = selectable
                            // Only set LinkMovementMethod when NOT selectable
                            // When selectable, setTextIsSelectable sets up the correct movement method
                            if (!selectable) {
                                loaded.textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                            }
                        }
                        // Also reload to ensure consistent state
                        currentChapters?.let { 
                            contentContainer.removeAllViews()
                            loadedChapters.clear()
                            currentChapterIndex = 0
                            setChapters(it) 
                        }
                    }
                }
        }

        // Observe force lowercase preference - reload content to reapply transformation
        scope.launch {
            preferences.novelForceTextLowercase().changes()
                .drop(1)
                .collectLatest {
                    activity.runOnUiThread {
                        currentChapters?.let {
                            contentContainer.removeAllViews()
                            loadedChapters.clear()
                            currentChapterIndex = 0
                            setChapters(it)
                        }
                    }
                }
        }

        // Observe hide chapter title preference - reload content
        scope.launch {
            preferences.novelHideChapterTitle().changes()
                .drop(1)
                .collectLatest {
                    activity.runOnUiThread {
                        currentChapters?.let {
                            contentContainer.removeAllViews()
                            loadedChapters.clear()
                            currentChapterIndex = 0
                            setChapters(it)
                        }
                    }
                }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSelectableTextView(): TextView {
        return TextView(activity).apply {
            val isSelectable = preferences.novelTextSelectable().get()
            
            // Set explicit layout params with MATCH_PARENT width for text selection to work
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            
            // setTextIsSelectable MUST be called and we should NOT override movementMethod
            // Android internally sets up the correct movement method for selection when this is true
            setTextIsSelectable(isSelectable)
            
            // For text selection to work, we need to ensure the textview can receive focus
            if (isSelectable) {
                isFocusable = true
                isFocusableInTouchMode = true
                // DO NOT set movementMethod here - setTextIsSelectable already sets up the correct one
                // Setting ArrowKeyMovementMethod was breaking touch-based selection
                // Enable long click for selection
                isLongClickable = true
            } else {
                // When not selectable, use LinkMovementMethod for clicking links
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
            }
            
            // Request parent not intercept when trying to select text
            setOnTouchListener { v, event ->
                val textView = v as TextView
                if (preferences.novelTextSelectable().get()) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // DON'T disallow intercept on down - let scroll view decide
                            // Only request focus if not already focused
                            if (!textView.isFocused) {
                                textView.requestFocus()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Re-enable parent intercept after touch ends
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Only disallow intercept if text is actively selected
                            // This allows vertical scrolling to work normally
                            if (textView.hasSelection()) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    }
                }
                false // Let the TextView handle the event
            }
            
            // Set up long click listener for text selection
            if (isSelectable) {
                setOnLongClickListener {
                    // Request focus and start selection mode
                    requestFocus()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    false // Let default behavior handle selection
                }
            }
        }
    }

    private fun refreshAllChapterStyles() {
        loadedChapters.forEach { loaded ->
            if (loaded.isLoaded) {
                applyTextViewStyles(loaded.textView)
            }
        }
        applyBackgroundColor()
    }

    private var ttsInitialized = false
    private var isTtsAutoPlay = false // Track if TTS should auto-continue to next chapter
    private var ttsPaused = false
    private var ttsChunks: List<String> = emptyList()
    private var ttsCurrentChunkIndex = 0

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            // Apply TTS settings from preferences
            applyTtsSettings()
            // Set up utterance progress listener for auto-continue
            setupTtsListener()
        } else {
            logcat(LogPriority.ERROR) { "TTS initialization failed with status: $status" }
            ttsInitialized = false
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Track which chunk is currently speaking
                utteranceId?.removePrefix("tts_utterance_")?.toIntOrNull()?.let {
                    ttsCurrentChunkIndex = it
                }
            }

            override fun onDone(utteranceId: String?) {
                // Advance chunk index
                val finishedIndex = utteranceId?.removePrefix("tts_utterance_")?.toIntOrNull() ?: -1
                val isLastChunk = finishedIndex >= ttsChunks.size - 1

                // Check if this was the last chunk and auto-play is enabled
                if (isLastChunk && isTtsAutoPlay && preferences.novelTtsAutoNextChapter().get()) {
                    // Check if we've finished all chunks for current chapter
                    activity.runOnUiThread {
                        // Small delay to ensure speech is fully done
                        scope.launch {
                            delay(500)
                            if (!isTtsSpeaking()) {
                                // Load and play next chapter
                                loadNextChapterForTts()
                            }
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                logcat(LogPriority.ERROR) { "TTS error on utterance: $utteranceId" }
            }
        })
    }

    private fun loadNextChapterForTts() {
        val chapters = currentChapters ?: return
        val nextChapter = chapters.nextChapter ?: return

        logcat(LogPriority.DEBUG) { "TTS: Auto-loading next chapter for playback" }

        // Load next chapter and start TTS when ready
        scope.launch {
            activity.loadNextChapter()
            // Wait for the chapter to load
            delay(1000)
            // Start TTS on new chapter
            startTts()
        }
    }

    private fun applyTtsSettings() {
        tts?.let { textToSpeech ->
            // Set voice/locale
            val voicePref = preferences.novelTtsVoice().get()
            if (voicePref.isNotEmpty()) {
                // Try to find matching voice by name
                val voices = textToSpeech.voices
                val selectedVoice = voices?.find { it.name == voicePref }
                if (selectedVoice != null) {
                    textToSpeech.voice = selectedVoice
                } else {
                    // Fallback to locale matching
                    try {
                        val locale = Locale.forLanguageTag(voicePref)
                        textToSpeech.language = locale
                    } catch (e: Exception) {
                        textToSpeech.language = Locale.getDefault()
                    }
                }
            } else {
                textToSpeech.language = Locale.getDefault()
            }

            // Set speech rate (speed)
            val speed = preferences.novelTtsSpeed().get()
            textToSpeech.setSpeechRate(speed)

            // Set pitch
            val pitch = preferences.novelTtsPitch().get()
            textToSpeech.setPitch(pitch)
        }
    }

    fun speak(text: String) {
        if (!ttsInitialized || tts == null) {
            logcat(LogPriority.WARN) { "TTS not initialized, cannot speak" }
            return
        }

        // Re-apply settings before speaking in case they changed
        applyTtsSettings()

        ttsPaused = false

        // Android TTS has a max length limit (~4000 chars), chunk long text
        val maxLength = TextToSpeech.getMaxSpeechInputLength()

        ttsChunks = if (text.length <= maxLength) {
            listOf(text)
        } else {
            splitTextForTts(text, maxLength)
        }
        ttsCurrentChunkIndex = 0

        speakChunksFrom(0)
    }

    private fun splitTextForTts(text: String, maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                chunks.add(remaining)
                break
            }

            // Find a good break point (sentence end, paragraph, or space)
            var breakPoint = maxLength

            // Try to find sentence end (. ! ?) before maxLength
            val sentenceEnd = remaining.substring(0, maxLength).lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
            if (sentenceEnd > maxLength / 2) {
                breakPoint = sentenceEnd + 1
            } else {
                // Fall back to last space
                val lastSpace = remaining.substring(0, maxLength).lastIndexOf(' ')
                if (lastSpace > maxLength / 2) {
                    breakPoint = lastSpace + 1
                }
            }

            chunks.add(remaining.substring(0, breakPoint).trim())
            remaining = remaining.substring(breakPoint).trim()
        }

        return chunks
    }

    private fun ensureTtsInitialized() {
        if (tts == null) {
            // Check if TTS data is available first
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            try {
                tts = TextToSpeech(activity, this)
                logcat(LogPriority.DEBUG) { "TTS: Initialization started" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "TTS: Failed to create TextToSpeech instance: ${e.message}" }
                activity.runOnUiThread {
                    logcat(LogPriority.DEBUG) { "TTS engine not available. Please install a TTS engine from Google Play." }
                }
            }
        }
    }

    fun startTts() {
        ensureTtsInitialized()
        
        if (!ttsInitialized) {
            logcat(LogPriority.WARN) { "TTS: Not initialized yet, waiting..." }
            // Queue the speech to start when TTS becomes available
            scope.launch {
                // Wait up to 2 seconds for initialization
                var waited = 0
                while (!ttsInitialized && waited < 2000) {
                    delay(100)
                    waited += 100
                }
                if (ttsInitialized) {
                    startTts() // Retry now that it's initialized
                } else {
                    activity.runOnUiThread {
                       logcat(LogPriority.DEBUG) {"TTS not available. Please check your TTS settings."}
                    }
                }
            }
            return
        }
        
        isTtsAutoPlay = true // Enable auto-continue
        val text = loadedChapters.getOrNull(currentChapterIndex)?.textView?.text?.toString()
            ?: loadedChapters.firstOrNull()?.textView?.text?.toString()

        if (text.isNullOrEmpty()) {
            logcat(LogPriority.WARN) {
                "TTS: No text to speak. loadedChapters=${loadedChapters.size}, currentIndex=$currentChapterIndex"
            }
            return
        }

        logcat(LogPriority.DEBUG) { "TTS: Starting to speak ${text.length} characters" }
        speak(text)
    }

    fun stopTts() {
        isTtsAutoPlay = false // Disable auto-continue when manually stopped
        ttsPaused = false
        ttsChunks = emptyList()
        ttsCurrentChunkIndex = 0
        if (ttsInitialized) {
            tts?.stop()
        }
    }

    fun pauseTts() {
        if (ttsInitialized && tts?.isSpeaking == true) {
            ttsPaused = true
            tts?.stop() // TTS doesn't have native pause, so stop and track position
        }
    }

    fun resumeTts() {
        if (ttsPaused && ttsChunks.isNotEmpty()) {
            ttsPaused = false
            // Resume from the chunk that was interrupted
            speakChunksFrom(ttsCurrentChunkIndex)
        }
    }

    fun isTtsPaused(): Boolean = ttsPaused

    fun isTtsSpeaking(): Boolean = ttsInitialized && tts?.isSpeaking == true

    private fun speakChunksFrom(startIndex: Int) {
        if (ttsChunks.isEmpty() || startIndex >= ttsChunks.size) return
        ttsChunks.drop(startIndex).forEachIndexed { i, chunk ->
            val actualIndex = startIndex + i
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(chunk, queueMode, null, "tts_utterance_$actualIndex")
        }
    }

    /**
     * Get list of available TTS voices for the settings UI
     */
    fun getAvailableVoices(): List<Pair<String, String>> {
        val voices = tts?.voices ?: return emptyList()
        return voices.map { voice ->
            val displayName = "${voice.locale.displayLanguage} (${voice.name})"
            Pair(voice.name, displayName)
        }.sortedBy { it.second }
    }

    /**
     * Get the current TTS voice name
     */
    fun getCurrentVoiceName(): String {
        return tts?.voice?.name ?: preferences.novelTtsVoice().get()
    }

    override fun destroy() {
        // Save progress before destroying
        progressSaveJob?.cancel()
        getScrollProgress { progress ->
            saveProgress(progress)
        }

        // Cleanup TTS - only if initialized
        if (ttsInitialized) {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null

        scope.cancel()
        loadJob?.cancel()
        loadedChapters.clear()
    }

    override fun getView(): View {
        return container
    }

    /**
     * Reload content with current translation state.
     * Re-renders all loaded chapters with or without translation.
     */
    fun reloadWithTranslation() {
        // Re-render all loaded chapters with new translation state
        loadedChapters.forEach { loadedChapter ->
            val page = loadedChapter.chapter.pages?.firstOrNull() as? ReaderPage ?: return@forEach
            val content = page.text ?: return@forEach
            val textView = loadedChapter.textView

            // Apply translation if enabled (async)
            if (activity.isTranslationEnabled()) {
                textView.text = "Translating..."
                scope.launch {
                    val translatedContent = activity.translateContentIfEnabled(content)
                    withContext(Dispatchers.Main) {
                        setTextViewContent(textView, translatedContent)
                    }
                }
            } else {
                // Show original content
                setTextViewContent(textView, content)
            }
        }
    }

    override fun setChapters(chapters: ViewerChapters) {
        val page = chapters.currChapter.pages?.firstOrNull() as? ReaderPage ?: return

        // Cancel previous load job
        loadJob?.cancel()
        currentPage = page
        currentChapters = chapters

        // setChapters() is for loading a single chapter (manual navigation or initial load).
        // Infinite scroll appends are handled explicitly via loadNext/PrevChapterIfAvailable().
        val isInfiniteScrollAppend = false

        // Check if chapter is already loaded to prevent clearing
        val isAlreadyLoaded = loadedChapters.any { it.chapter.chapter.id == chapters.currChapter.chapter.id }

        // If this chapter is already appended (infinite scroll), never redraw or restore progress.
        if (preferences.novelInfiniteScroll().get() && loadedChapters.isNotEmpty() && isAlreadyLoaded) {
            return
        }

        // Clear previous chapters if infinite scroll is disabled, this is first load, or manual navigation
        // BUT do NOT clear if the chapter is already loaded (prevents unloading on state updates)
        if (!preferences.novelInfiniteScroll().get() || (loadedChapters.isEmpty() && !isAlreadyLoaded) ||
            (!isInfiniteScrollAppend && !isAlreadyLoaded)
        ) {
            contentContainer.removeAllViews()
            loadedChapters.clear()
            currentChapterIndex = 0
        }

        // If page is already ready with text (downloaded chapters), display immediately
        if (page.status == Page.State.Ready && !page.text.isNullOrEmpty()) {
            // NEVER show loading during infinite scroll - should be seamless
            if (!isInfiniteScrollAppend) {
                hideLoadingIndicator()
            }
            displayChapter(chapters.currChapter, page)
            // Only restore progress if not appending in infinite scroll mode
            if (!isInfiniteScrollAppend) {
                restoreProgress(page)
            }
            return
        }

        // Show loading indicator ONLY for non-infinite-scroll loads
        // For infinite scroll, content should append seamlessly without any visual interruption
        if (!isInfiniteScrollAppend) {
            showLoadingIndicator()
        }

        // Start loading the page and observe status
        loadJob = scope.launch {
            val loader = page.chapter.pageLoader
            if (loader == null) {
                logcat(LogPriority.ERROR) { "NovelViewer: No page loader available" }
                hideLoadingIndicator()
                return@launch
            }

            // Start loading in background
            launch(Dispatchers.IO) {
                loader.loadPage(page)
            }

            // Observe page status
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue, Page.State.LoadPage -> {
                        // NEVER show loading during infinite scroll - completely seamless
                        if (!isInfiniteScrollAppend) {
                        }
                    }
                    Page.State.Ready -> {
                        // NEVER show/hide loading during infinite scroll
                        if (!isInfiniteScrollAppend) {
                            hideLoadingIndicator()
                        }
                        displayChapter(chapters.currChapter, page)
                        // Only restore progress if not appending in infinite scroll mode
                        if (!isInfiniteScrollAppend) {
                            restoreProgress(page)
                        }
                    }
                    is Page.State.Error -> {
                        if (!isInfiniteScrollAppend) {
                            hideLoadingIndicator()
                        }
                        displayError(state.error)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun displayChapter(chapter: ReaderChapter, page: ReaderPage) {
        var content = page.text
        if (content.isNullOrBlank()) {
            logcat(LogPriority.ERROR) { "NovelViewer: Page text is null or blank" }
            displayError(Exception("No text content available"))
            return
        }

        // Optionally strip chapter title from content
        if (preferences.novelHideChapterTitle().get()) {
            content = stripChapterTitle(content, chapter.chapter.name)
        }

        // Optionally force lowercase
        if (preferences.novelForceTextLowercase().get()) {
            content = content.lowercase()
        }

        // Check if chapter is already loaded - return early to prevent duplicate adds
        val existingIndex = loadedChapters.indexOfFirst { it.chapter.chapter.id == chapter.chapter.id }
        if (existingIndex >= 0) {
            logcat(LogPriority.DEBUG) {
                "NovelViewer: Chapter ${chapter.chapter.id} already in loadedChapters at index $existingIndex, skipping display"
            }
            currentChapterIndex = existingIndex
            return
        }

        logcat(LogPriority.DEBUG) {
            "NovelViewer: Displaying chapter ${chapter.chapter.id}, infinite scroll enabled: ${preferences.novelInfiniteScroll().get()}, loaded count: ${loadedChapters.size}"
        }

        // Create header for chapter (for infinite scroll mode)
        val headerView = TextView(activity).apply {
            text = chapter.chapter.name
            textSize = 18f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 16)
            // Never show a chapter boundary indicator during infinite scroll.
            isVisible = false
        }

        // Create text view for content
        val textView = createSelectableTextView()

        applyTextViewStyles(textView)

        val loadedChapter = LoadedChapter(
            chapter = chapter,
            textView = textView,
            headerView = headerView,
            isLoaded = true,
        )

        // Check if this is an append (infinite scroll) or new chapter (manual nav)
        val isAppend = loadedChapters.isNotEmpty() && preferences.novelInfiniteScroll().get()
        val previousIndex = currentChapterIndex

        // Add to end for infinite scroll
        loadedChapters.add(loadedChapter)

        // Only update currentChapterIndex if this is not an append (manual navigation)
        // For infinite scroll appends, keep reading the current chapter
        if (!isAppend) {
            currentChapterIndex = loadedChapters.size - 1
        }

        // Add views to container
        contentContainer.addView(headerView)
        contentContainer.addView(textView)

        // Apply translation if enabled (async)
        val finalContent = content
        // Always show content immediately; translation (if enabled) replaces it asynchronously.
        setTextViewContent(textView, finalContent)
        if (activity.isTranslationEnabled() && !preferences.novelShowRawHtml().get()) {
            scope.launch {
                val translatedContent = activity.translateContentIfEnabled(finalContent)
                withContext(Dispatchers.Main) {
                    setTextViewContent(textView, translatedContent)
                }
            }
        }

        applyBackgroundColor()

        // Clean up old chapters if too many loaded
        cleanupDistantChapters()
    }

    private fun cleanupDistantChapters() {
        // Keep at most 3 chapters loaded (current + 1 next+1prev)
        // When at chapter x, unload chapter x-2
        val maxChapters = 3
        while (loadedChapters.size > maxChapters && currentChapterIndex > 0) {
            val toRemove = loadedChapters.first()
            val removedHeight = toRemove.headerView.height + toRemove.textView.height

            contentContainer.removeView(toRemove.headerView)
            contentContainer.removeView(toRemove.textView)
            loadedChapters.removeAt(0)
            currentChapterIndex--

            // Adjust scroll position to prevent visual jump
            // Content shifted UP by removedHeight, so we must scroll UP by the same amount
            // to keep the viewport on the same content.
            scrollView.scrollBy(0, -removedHeight)

            logcat(LogPriority.DEBUG) { "NovelViewer: Removed distant chapter, adjusted scroll by -$removedHeight" }
        }
    }

    private fun restoreProgress(page: ReaderPage) {
        // Restore scroll position from lastPageRead (stored as percentage)
        val savedProgress = page.chapter.chapter.last_page_read
        val isRead = page.chapter.chapter.read
        logcat(LogPriority.DEBUG) {
            "NovelViewer: Restoring progress, savedProgress=$savedProgress, isRead=$isRead for ${page.chapter.chapter.name}"
        }

        // If chapter is marked as read, start from top (0%) to avoid infinite scroll issues
        if (!isRead && savedProgress > 0 && savedProgress <= 100) {
            val progress = savedProgress / 100f
            // Set lastSavedProgress BEFORE posting to prevent race condition with scroll listener
            lastSavedProgress = progress
            
            // Wait for layout to complete before scrolling
            scrollView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    // Double-check the content is loaded
                    val child = scrollView.getChildAt(0) ?: return
                    val totalHeight = child.height - scrollView.height
                    if (totalHeight <= 0) {
                        // Content not ready yet, schedule a retry
                        scrollView.postDelayed({
                            isRestoringScroll = true
                            setScrollProgress(progress.coerceIn(0f, 1f))
                            logcat(LogPriority.DEBUG) { "NovelViewer: Scroll restored (delayed) to ${(progress * 100).toInt()}%" }
                            isRestoringScroll = false
                        }, 200)
                        return
                    }
                    
                    isRestoringScroll = true
                    setScrollProgress(progress.coerceIn(0f, 1f))
                    logcat(LogPriority.DEBUG) { "NovelViewer: Scroll restored to ${(progress * 100).toInt()}%" }
                    isRestoringScroll = false
                }
            })
        } else {
            // Scroll to top for new chapters or already read chapters
            lastSavedProgress = 0f
            scrollView.post {
                isRestoringScroll = true
                scrollView.scrollTo(0, 0)
                isRestoringScroll = false
            }
        }
    }

    private fun applyTextViewStyles(textView: TextView) {
        val fontSize = preferences.novelFontSize().get()
        val lineHeight = preferences.novelLineHeight().get()
        val marginLeft = preferences.novelMarginLeft().get()
        val marginRight = preferences.novelMarginRight().get()
        val marginTop = preferences.novelMarginTop().get()
        val marginBottom = preferences.novelMarginBottom().get()
        val fontColor = preferences.novelFontColor().get()
        val theme = preferences.novelTheme().get()
        val textAlign = preferences.novelTextAlign().get()
        val fontFamily = preferences.novelFontFamily().get()

        val density = activity.resources.displayMetrics.density
        val leftPx = (marginLeft * density).toInt()
        val rightPx = (marginRight * density).toInt()
        val topPx = (marginTop * density).toInt()
        val bottomPx = (marginBottom * density).toInt()
        textView.setPadding(leftPx, topPx, rightPx, bottomPx)

        textView.textSize = fontSize.toFloat()
        textView.setLineSpacing(0f, lineHeight)

        // Apply font family
        // For custom fonts (file:// or content:// URIs), load the Typeface from file
        textView.typeface = when {
            fontFamily.startsWith("file://") || fontFamily.startsWith("content://") -> {
                try {
                    val fontUri = android.net.Uri.parse(fontFamily)
                    // For content:// URIs, copy to cache first
                    val fontFile = if (fontFamily.startsWith("content://")) {
                        val tempFile = java.io.File(activity.cacheDir, "custom_font.ttf")
                        activity.contentResolver.openInputStream(fontUri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    } else {
                        // file:// URI - extract path
                        java.io.File(fontUri.path ?: fontFamily.removePrefix("file://"))
                    }
                    android.graphics.Typeface.createFromFile(fontFile)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to load custom font: ${e.message}" }
                    android.graphics.Typeface.SANS_SERIF
                }
            }
            fontFamily.contains("serif", ignoreCase = true) && !fontFamily.contains("sans", ignoreCase = true) ->
                android.graphics.Typeface.SERIF
            fontFamily.contains("monospace", ignoreCase = true) ->
                android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }

        // Apply text alignment
        textView.gravity = when (textAlign) {
            "center" -> Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.END
            "justify" -> Gravity.START // Android doesn't have justify, fallback to start
            else -> Gravity.START
        }
        // For justify on API 26+, use justification mode
        if (textAlign == "justify" && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }

        val (_, themeTextColor) = getThemeColors(theme)
        // 0 = default (use theme color), other values are actual colors
        // Note: 0xFFFFFFFF (white) = -1 as signed int, so we check for 0 as default
        val finalTextColor = if (fontColor != 0) fontColor else themeTextColor
        textView.setTextColor(finalTextColor)
    }

    private fun applyBackgroundColor() {
        val theme = preferences.novelTheme().get()
        val backgroundColor = preferences.novelBackgroundColor().get()

        val (themeBgColor, _) = getThemeColors(theme)
        // 0 = default (use theme color)
        val finalBgColor = if (theme == "custom" && backgroundColor != 0) backgroundColor else themeBgColor
        scrollView.setBackgroundColor(finalBgColor)
    }

    private fun getThemeColors(theme: String): Pair<Int, Int> {
        val backgroundColor = preferences.novelBackgroundColor().get()
        val fontColor = preferences.novelFontColor().get()

        return when (theme) {
            "app" -> {
                // Follow app theme - use actual Material theme colors
                val typedValue = android.util.TypedValue()
                val theme = activity.theme
                val bgColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    typedValue.data
                } else {
                    // Fallback: detect dark/light mode
                    val nightMode = activity.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
                }
                val textColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    typedValue.data
                } else {
                    val nightMode = activity.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xFFE0E0E0.toInt() else 0xFF000000.toInt()
                }
                bgColor to textColor
            }
            "dark" -> 0xFF121212.toInt() to 0xFFE0E0E0.toInt()
            "sepia" -> 0xFFF4ECD8.toInt() to 0xFF5B4636.toInt()
            "black" -> 0xFF000000.toInt() to 0xFFCCCCCC.toInt()
            "grey" -> 0xFF292832.toInt() to 0xFFCCCCCC.toInt()
            "custom" -> {
                // 0 = default, use fallback colors
                val bg = if (backgroundColor != 0) backgroundColor else 0xFFFFFFFF.toInt()
                val text = if (fontColor != 0) fontColor else 0xFF000000.toInt()
                bg to text
            }
            else -> 0xFFFFFFFF.toInt() to 0xFF000000.toInt()
        }
    }

    private fun setTextViewContent(textView: TextView, content: String) {
        // Check if raw HTML mode is enabled (for debugging)
        val showRawHtml = preferences.novelShowRawHtml().get()
        if (showRawHtml) {
            // Display raw HTML tags without parsing
            textView.text = content
            return
        }

        // Process content to ensure paragraph tags exist for styling
        var processedContent = content
        
        // Strip script tags and their content — they would render as visible text
        processedContent = processedContent.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        processedContent = processedContent.replace(Regex("<script[^>]*/>", RegexOption.IGNORE_CASE), "")
        // Strip style tags too — their CSS rules show up as text in TextView
        processedContent = processedContent.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        processedContent = processedContent.replace(Regex("<style[^>]*/>", RegexOption.IGNORE_CASE), "")
        // Strip noscript tags
        processedContent = processedContent.replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")

        // Optionally strip media tags entirely when blocking media
        if (preferences.novelBlockMedia().get()) {
            processedContent = processedContent
                .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<image[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</image>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<video[^>]*>[\\s\\S]*?</video>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<audio[^>]*>[\\s\\S]*?</audio>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<source[^>]*>", RegexOption.IGNORE_CASE), "")
        }

        // First, strip any existing leading non-breaking spaces from paragraphs
        // This prevents double-spacing when indent is applied
        processedContent = processedContent.replace(Regex("<p>(\u00A0|&#160;|&nbsp;)+"), "<p>")

        // If content doesn't have <p> tags, wrap paragraphs (double newlines or single <br> followed by text)
        if (!processedContent.contains("<p>", ignoreCase = true)) {
            // Replace double line breaks with paragraph markers
            processedContent = processedContent
                .replace("\n\n", "</p><p>")
                .replace("\r\n\r\n", "</p><p>")
            // Wrap in paragraph tags
            processedContent = "<p>$processedContent</p>"
        }

        // Get paragraph spacing preference (em units, default 0.5)
        val paragraphSpacing = preferences.novelParagraphSpacing().get()
        // Get paragraph indent preference (em units, default 0)
        val paragraphIndent = preferences.novelParagraphIndent().get()
        val fontSize = preferences.novelFontSize().get()
        val density = activity.resources.displayMetrics.density

        scope.launch {
            // Create image getter if media is not blocked
            val blockMedia = preferences.novelBlockMedia().get()
            val imageGetter = if (!blockMedia) {
                CoilImageGetter(textView, activity)
            } else {
                null
            }

            val spanned = withContext(Dispatchers.Default) {
                Html.fromHtml(processedContent, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
            }

            // Apply custom paragraph spacing and indent using spans
            val spannable = android.text.SpannableStringBuilder(spanned)

            // Calculate pixel values from em units
            val spacingPx = (paragraphSpacing * fontSize * density).toInt()
            val indentPx = (paragraphIndent * fontSize * density).toInt()

            if (spacingPx > 0 || indentPx > 0) {
                // Find paragraph boundaries (newline characters)
                var i = 0
                var paragraphStart = 0
                while (i < spannable.length) {
                    if (spannable[i] == '\n' || i == spannable.length - 1) {
                        val paragraphEnd = if (spannable[i] == '\n') i + 1 else i + 1

                        // Apply spacing span to this paragraph
                        if (spacingPx > 0 && paragraphEnd <= spannable.length) {
                            spannable.setSpan(
                                ParagraphSpacingSpan(spacingPx),
                                paragraphStart,
                                paragraphEnd,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }

                        // Apply indent span to this paragraph
                        if (indentPx > 0 && paragraphEnd <= spannable.length) {
                            spannable.setSpan(
                                ParagraphIndentSpan(indentPx),
                                paragraphStart,
                                paragraphEnd,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }

                        paragraphStart = paragraphEnd
                    }
                    i++
                }
            }

            withContext(Dispatchers.Main) {
                textView.setText(spannable, TextView.BufferType.SPANNABLE)
                // Re-apply text selection after setting text — Android can lose
                // the internal Editor state when text changes.
                // Toggle false→true to force full re-initialization (calling with
                // the same value is a no-op in the Android framework).
                if (preferences.novelTextSelectable().get()) {
                    textView.setTextIsSelectable(false)
                    textView.setTextIsSelectable(true)
                    textView.isFocusable = true
                    textView.isFocusableInTouchMode = true
                    textView.isLongClickable = true
                    // Post requestFocus to ensure layout is complete
                    textView.post {
                        textView.requestFocus()
                    }
                }
            }
        }
    }

    /**
     * Strips the chapter title from the beginning of the content.
     * Searches within the first ~500 characters for chapter title or name matches.
     */
    private fun stripChapterTitle(content: String, chapterName: String): String {
        val normalizedChapterName = chapterName.trim().lowercase()
        // Search within first 500 chars for title (to handle leading whitespace/tags)
        val searchArea = content.take(500)

        // Try to remove first heading (H1-H6) anywhere in search area
        val headingRegex = """<h[1-6][^>]*>(.*?)</h[1-6]>""".toRegex(RegexOption.IGNORE_CASE)
        val headingMatch = headingRegex.find(searchArea)
        if (headingMatch != null) {
            val headingText = headingMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim().lowercase()
            if (isTitleMatch(headingText, normalizedChapterName)) {
                return content.substring(0, headingMatch.range.first) +
                    content.substring(headingMatch.range.last + 1)
            }
        }

        // Try to remove first strong/b/em tag if it looks like a title
        val strongRegex = """<(strong|b|em)[^>]*>(.*?)</\1>""".toRegex(RegexOption.IGNORE_CASE)
        val strongMatch = strongRegex.find(searchArea)
        if (strongMatch != null) {
            val strongText = strongMatch.groupValues[2].replace(Regex("<[^>]+>"), "").trim().lowercase()
            if (isTitleMatch(strongText, normalizedChapterName)) {
                return content.substring(0, strongMatch.range.first) +
                    content.substring(strongMatch.range.last + 1)
            }
        }

        // Try to remove first paragraph if it matches chapter name
        val paragraphRegex = """<p[^>]*>(.*?)</p>""".toRegex(RegexOption.IGNORE_CASE)
        val pMatch = paragraphRegex.find(searchArea)
        if (pMatch != null) {
            val pText = pMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim().lowercase()
            if (isTitleMatch(pText, normalizedChapterName)) {
                return content.substring(0, pMatch.range.first) +
                    content.substring(pMatch.range.last + 1)
            }
        }

        // Try to remove first div or span if it matches chapter name
        val divSpanRegex = """<(div|span)[^>]*>(.*?)</\1>""".toRegex(RegexOption.IGNORE_CASE)
        val divMatch = divSpanRegex.find(searchArea)
        if (divMatch != null) {
            val divText = divMatch.groupValues[2].replace(Regex("<[^>]+>"), "").trim().lowercase()
            if (isTitleMatch(divText, normalizedChapterName)) {
                return content.substring(0, divMatch.range.first) +
                    content.substring(divMatch.range.last + 1)
            }
        }

        // Try to find and remove plain text that matches chapter name at the very start
        val plainTextContent = content.replace(Regex("<[^>]+>"), " ").trim()
        val firstLine = plainTextContent.lines().firstOrNull()?.trim()?.lowercase() ?: ""
        if (firstLine.isNotEmpty() && isTitleMatch(firstLine, normalizedChapterName)) {
            // Find where this text ends in the original HTML and remove it
            val escapedFirstLine = Regex.escape(content.lines().firstOrNull()?.trim() ?: "")
            if (escapedFirstLine.isNotEmpty()) {
                val lineRegex = """^\s*$escapedFirstLine\s*""".toRegex(RegexOption.IGNORE_CASE)
                return content.replace(lineRegex, "").trimStart()
            }
        }

        // No title found to strip
        return content
    }

    private fun isTitleMatch(text: String, chapterName: String): Boolean {
        if (text.isEmpty() || chapterName.isEmpty()) return false
        // Exact match
        if (text == chapterName) return true
        // Chapter name contains the text (e.g., "Chapter 1" contains "1")
        if (chapterName.contains(text) && text.length > 2) return true
        // Text contains chapter name
        if (text.contains(chapterName)) return true
        // Check for common chapter patterns
        val chapterPatterns = listOf(
            """chapter\s*\d+""".toRegex(RegexOption.IGNORE_CASE),
            """ch\.?\s*\d+""".toRegex(RegexOption.IGNORE_CASE),
            """episode\s*\d+""".toRegex(RegexOption.IGNORE_CASE),
            """part\s*\d+""".toRegex(RegexOption.IGNORE_CASE),
        )
        return chapterPatterns.any { it.matches(text) && it.containsMatchIn(chapterName) }
    }

    /**
     * Reloads the current chapter content.
     */
    fun reloadChapter() {
        // Clear loaded chapters and reload
        contentContainer.removeAllViews()
        loadedChapters.clear()
        currentChapterIndex = 0
        currentChapters?.let { setChapters(it) }
    }

    private var initialLoadingView: TextView? = null
    
    private fun showLoadingIndicator() {
        // Use inline loading text instead of progress bar
        contentContainer.removeAllViews()

        initialLoadingView = TextView(activity).apply {
            text = "Loading..."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 64)
            setTextColor(0xFF888888.toInt())
        }

        contentContainer.addView(initialLoadingView)
        applyBackgroundColor()
    }

    private fun hideLoadingIndicator() {
        // Remove the initial loading view if still present
        initialLoadingView?.let { view ->
            contentContainer.removeView(view)
        }
        initialLoadingView = null
    }

    private fun displayError(error: Throwable) {
        val errorView = TextView(activity).apply {
            text = "Error loading chapter: ${error.message}"
            setTextColor(0xFFFF5555.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        contentContainer.removeAllViews()
        contentContainer.addView(errorView)
    }

    fun startAutoScroll() {
        val speed = preferences.novelAutoScrollSpeed().get().coerceIn(1, 10)
        isAutoScrolling = true

        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive && isAutoScrolling) {
                // Scroll amount per tick - higher speed = more scroll
                val scrollAmount = speed // 1-10 pixels per tick
                scrollView.smoothScrollBy(0, scrollAmount)

                // Fixed delay between scroll ticks
                // At speed 1: scroll 1px every 50ms = ~20px/sec
                // At speed 10: scroll 10px every 50ms = ~200px/sec
                delay(50L)
            }
        }
    }

    fun stopAutoScroll() {
        isAutoScrolling = false
        autoScrollJob?.cancel()
    }

    fun toggleAutoScroll() {
        if (isAutoScrolling) {
            stopAutoScroll()
        } else {
            startAutoScroll()
        }
    }

    fun isAutoScrollActive(): Boolean = isAutoScrolling

    /**
     * Scrolls to the top of the page
     */
    fun scrollToTop() {
        scrollView.scrollTo(0, 0)
    }

    /**
     * Scrolls to the bottom of the page
     */
    fun scrollToBottom() {
        scrollView.fullScroll(View.FOCUS_DOWN)
    }

    /**
     * Gets the current scroll progress (0.0 to 1.0)
     */
    fun getScrollProgress(callback: (Float) -> Unit) {
        val scrollY = scrollView.scrollY
        val child = scrollView.getChildAt(0)
        val totalHeight = if (child != null) child.height - scrollView.height else 0
        val progress = if (totalHeight > 0) scrollY.toFloat() / totalHeight else 0f
        callback(progress)
    }

    /**
     * Gets the current scroll progress as percentage (0 to 100)
     */
    fun getProgressPercent(): Int {
        val scrollY = scrollView.scrollY
        val child = scrollView.getChildAt(0)
        val totalHeight = if (child != null) child.height - scrollView.height else 0
        if (totalHeight <= 0) return 100 // If no scrollable content, we're at 100%
        val progress = scrollY.toFloat() / totalHeight
        // Round up if very close to 100% (within 2%) to allow reaching 100%
        val percent = if (progress >= 0.98f) 100 else (progress * 100).toInt()
        return percent.coerceIn(0, 100)
    }

    /**
     * Sets the scroll position within the current chapter by progress (0.0 to 1.0)
     */
    fun setScrollProgress(progress: Float) {
        // For single chapter or no infinite scroll, use overall scroll
        if (loadedChapters.size <= 1 || !preferences.novelInfiniteScroll().get()) {
            val child = scrollView.getChildAt(0) ?: return
            val totalHeight = child.height - scrollView.height
            val scrollY = (totalHeight * progress).toInt()
            scrollView.scrollTo(0, scrollY)
            return
        }

        // For infinite scroll with multiple chapters, scroll within current chapter
        var accumulatedHeight = 0
        for ((index, loadedChapter) in loadedChapters.withIndex()) {
            if (index == currentChapterIndex) {
                val chapterHeight = loadedChapter.headerView.height + loadedChapter.textView.height
                val visibleHeight = scrollView.height
                val effectiveChapterHeight = (chapterHeight - visibleHeight).coerceAtLeast(1)
                val chapterScrollY = accumulatedHeight + (effectiveChapterHeight * progress).toInt()
                scrollView.scrollTo(0, chapterScrollY)
                return
            }
            accumulatedHeight += loadedChapter.headerView.height + loadedChapter.textView.height
        }
    }

    /**
     * Sets the scroll position by progress percentage (0 to 100)
     */
    fun setProgressPercent(percent: Int) {
        val progress = percent.coerceIn(0, 100) / 100f
        setScrollProgress(progress)
    }

    override fun moveToPage(page: ReaderPage) {
        // For novels, each chapter is one "page"
        // If we need to support multi-page novels, implement navigation here
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (isUp) activity.toggleMenu()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (preferences.novelVolumeKeysScroll().get()) {
                    if (!isUp) scrollView.pageScroll(View.FOCUS_DOWN)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (preferences.novelVolumeKeysScroll().get()) {
                    if (!isUp) scrollView.pageScroll(View.FOCUS_UP)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (!isUp) {
                    if (event.isShiftPressed) {
                        scrollView.pageScroll(View.FOCUS_UP)
                    } else {
                        scrollView.pageScroll(View.FOCUS_DOWN)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (!isUp) scrollView.pageScroll(View.FOCUS_UP)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (!isUp) scrollView.pageScroll(View.FOCUS_DOWN)
                return true
            }
        }
        return false
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }
}
