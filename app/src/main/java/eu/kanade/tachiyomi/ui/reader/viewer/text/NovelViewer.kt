package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.speech.tts.TextToSpeech
import android.text.Html
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
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.injectLazy
import java.util.Locale

/**
 * NovelViewer handles the display of novel/text content with features like:
 * - Infinite scroll (load next/previous chapters automatically)
 * - Reading progress tracking
 * - Text-to-speech
 * - Auto-scroll
 * - Customizable appearance
 */
class NovelViewer(val activity: ReaderActivity) : Viewer, TextToSpeech.OnInitListener {

    private val container = FrameLayout(activity)
    private lateinit var scrollView: NestedScrollView
    private lateinit var contentContainer: LinearLayout
    private var loadingIndicator: ReaderProgressIndicator? = null
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
    private var currentChapterIndex = 0
    
    // Flag to track if next chapter load is from infinite scroll (vs manual navigation)
    private var isInfiniteScrollNavigation = false
    
    // For tracking scroll position and progress
    private var lastSavedProgress = 0f
    private var progressSaveJob: Job? = null
    
    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Return true so we continue receiving gesture events
            return true
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
    })

    init {
        initViews()
        container.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        tts = TextToSpeech(activity, this)
        observePreferences()
        setupScrollListener()
    }
    
    private fun initViews() {
        scrollView = object : NestedScrollView(activity) {
            private var isTextSelectionMode = false
            
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // Always pass touch events to gesture detector first
                gestureDetector.onTouchEvent(ev)
                return super.dispatchTouchEvent(ev)
            }
            
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                // If text selection is enabled and user is doing a long press or selection,
                // don't intercept the touch event - let the TextView handle it
                if (preferences.novelTextSelectable().get()) {
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isTextSelectionMode = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // If there's an active text selection handle, don't intercept
                            if (isTextSelectionMode) {
                                return false
                            }
                        }
                    }
                    
                    // Check if any child TextView has an active text selection
                    loadedChapters.forEach { loaded ->
                        if (loaded.textView.hasSelection()) {
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
            
            // Update current chapter based on scroll position first
            updateCurrentChapterFromScroll(scrollY)
            
            // Calculate progress within current chapter (not overall)
            val chapterProgress = calculateCurrentChapterProgress(scrollY)
            
            // Save progress periodically (debounced)
            scheduleProgressSave(chapterProgress)
            
            // Notify activity of progress change for slider update
            activity.onNovelProgressChanged(chapterProgress)
            
            // Check for infinite scroll - load next chapter when near bottom
            if (preferences.novelInfiniteScroll().get()) {
                if (overallProgress > 0.95f && !isLoadingNext) {
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
        
        // For multiple chapters, calculate progress within current chapter
        var accumulatedHeight = 0
        for ((index, loadedChapter) in loadedChapters.withIndex()) {
            val chapterHeight = loadedChapter.headerView.height + loadedChapter.textView.height
            if (index == currentChapterIndex) {
                val chapterScrollY = (scrollY - accumulatedHeight).coerceAtLeast(0)
                // Account for visible area - progress of 1.0 when bottom of chapter is at top of visible area
                val visibleHeight = scrollView.height
                val effectiveChapterHeight = (chapterHeight - visibleHeight).coerceAtLeast(1)
                return (chapterScrollY.toFloat() / effectiveChapterHeight).coerceIn(0f, 1f)
            }
            accumulatedHeight += chapterHeight
        }
        
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
        
        // Find which chapter is currently in view
        var accumulatedHeight = 0
        for ((index, loadedChapter) in loadedChapters.withIndex()) {
            val chapterHeight = loadedChapter.headerView.height + loadedChapter.textView.height
            if (scrollY < accumulatedHeight + chapterHeight) {
                if (currentChapterIndex != index) {
                    currentChapterIndex = index
                    // Reset progress for new chapter
                    lastSavedProgress = 0f
                    
                    // Calculate progress within the new current chapter
                    val chapterScrollY = scrollY - accumulatedHeight
                    if (chapterHeight > 0) {
                        lastSavedProgress = (chapterScrollY.toFloat() / chapterHeight).coerceIn(0f, 1f)
                    }
                    
                    // Update current page reference and notify activity
                    loadedChapter.chapter.pages?.firstOrNull()?.let { page ->
                        if (page is ReaderPage) {
                            currentPage = page
                            // Notify activity to update the toolbar title and chapter state
                            activity.onPageSelected(page)
                            logcat(LogPriority.DEBUG) { "NovelViewer: Chapter changed to ${loadedChapter.chapter.chapter.name}" }
                        }
                    }
                }
                break
            }
            accumulatedHeight += chapterHeight
        }
    }
    
    private fun loadNextChapterIfAvailable() {
        val chapters = currentChapters ?: return
        if (chapters.nextChapter == null) return
        
        if (isLoadingNext) return
        isLoadingNext = true
        
        // Mark this as an infinite scroll navigation (not manual)
        isInfiniteScrollNavigation = true
        
        showBottomLoadingIndicator()
        
        scope.launch {
            try {
                activity.loadNextChapter()
            } finally {
                isLoadingNext = false
                hideBottomLoadingIndicator()
            }
        }
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
        
        // Observe text selection preference separately
        scope.launch {
            preferences.novelTextSelectable().changes()
                .collectLatest { isSelectable ->
                    loadedChapters.forEach { loaded ->
                        loaded.textView.setTextIsSelectable(isSelectable)
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stopTts() {
        tts?.stop()
    }
    
    fun isTtsSpeaking(): Boolean = tts?.isSpeaking == true

    override fun destroy() {
        // Save progress before destroying
        progressSaveJob?.cancel()
        getScrollProgress { progress ->
            saveProgress(progress)
        }
        
        tts?.shutdown()
        scope.cancel()
        loadJob?.cancel()
        loadedChapters.clear()
    }

    override fun getView(): View {
        return container
    }

    override fun setChapters(chapters: ViewerChapters) {
        val page = chapters.currChapter.pages?.firstOrNull() as? ReaderPage ?: return
        
        // Cancel previous load job
        loadJob?.cancel()
        currentPage = page
        currentChapters = chapters
        
        // Determine if this is an infinite scroll append or manual navigation
        // isInfiniteScrollNavigation is set by loadNextChapterIfAvailable() when auto-loading
        // If chapters are loaded but this is NOT infinite scroll navigation, it's manual navigation
        val isInfiniteScrollAppend = preferences.novelInfiniteScroll().get() && 
                                     loadedChapters.isNotEmpty() && 
                                     isInfiniteScrollNavigation
        
        // Reset the flag after checking
        isInfiniteScrollNavigation = false
        
        // Clear previous chapters if infinite scroll is disabled, this is first load, or manual navigation
        if (!preferences.novelInfiniteScroll().get() || loadedChapters.isEmpty() || !isInfiniteScrollAppend) {
            contentContainer.removeAllViews()
            loadedChapters.clear()
            currentChapterIndex = 0
        }
        
        // If page is already ready with text (downloaded chapters), display immediately
        if (page.status == Page.State.Ready && !page.text.isNullOrEmpty()) {
            hideLoadingIndicator()
            displayChapter(chapters.currChapter, page)
            // Only restore progress if not appending in infinite scroll mode
            if (!isInfiniteScrollAppend) {
                restoreProgress(page)
            }
            return
        }
        
        // Show loading indicator
        showLoadingIndicator()
        
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
                        showLoadingIndicator()
                    }
                    Page.State.Ready -> {
                        hideLoadingIndicator()
                        displayChapter(chapters.currChapter, page)
                        // Only restore progress if not appending in infinite scroll mode
                        if (!isInfiniteScrollAppend) {
                            restoreProgress(page)
                        }
                    }
                    is Page.State.Error -> {
                        hideLoadingIndicator()
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
        
        // Check if chapter is already loaded
        val existingIndex = loadedChapters.indexOfFirst { it.chapter.chapter.id == chapter.chapter.id }
        if (existingIndex >= 0) {
            currentChapterIndex = existingIndex
            return
        }
        
        // Create header for chapter (for infinite scroll mode)
        val headerView = TextView(activity).apply {
            text = chapter.chapter.name
            textSize = 18f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 16)
            // Hide header if not infinite scroll
            isVisible = preferences.novelInfiniteScroll().get() && loadedChapters.isNotEmpty()
        }
        
        // Create text view for content
        val textView = TextView(activity).apply {
            setTextIsSelectable(preferences.novelTextSelectable().get())
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
        
        applyTextViewStyles(textView)
        
        val loadedChapter = LoadedChapter(
            chapter = chapter,
            textView = textView,
            headerView = headerView,
            isLoaded = true,
        )
        
        // Add to end for infinite scroll
        loadedChapters.add(loadedChapter)
        currentChapterIndex = loadedChapters.size - 1
        
        // Add views to container
        contentContainer.addView(headerView)
        contentContainer.addView(textView)
        
        // Set content
        setTextViewContent(textView, content)
        
        applyBackgroundColor()
        
        // Clean up old chapters if too many loaded
        cleanupDistantChapters()
    }
    
    private fun cleanupDistantChapters() {
        // Keep at most 3 chapters loaded (current + 2 previous)
        val maxChapters = 3
        while (loadedChapters.size > maxChapters && currentChapterIndex > 0) {
            val toRemove = loadedChapters.first()
            contentContainer.removeView(toRemove.headerView)
            contentContainer.removeView(toRemove.textView)
            loadedChapters.removeAt(0)
            currentChapterIndex--
        }
    }
    
    private fun restoreProgress(page: ReaderPage) {
        // Restore scroll position from lastPageRead (stored as percentage)
        val savedProgress = page.chapter.chapter.last_page_read
        logcat(LogPriority.DEBUG) { "NovelViewer: Restoring progress, savedProgress=$savedProgress for ${page.chapter.chapter.name}" }
        
        if (savedProgress > 0 && savedProgress <= 100) {
            val progress = savedProgress / 100f
            // Set lastSavedProgress BEFORE posting to prevent race condition with scroll listener
            lastSavedProgress = progress
            scrollView.post {
                setScrollProgress(progress.coerceIn(0f, 1f))
                logcat(LogPriority.DEBUG) { "NovelViewer: Scroll restored to ${(progress * 100).toInt()}%" }
            }
        } else {
            // Scroll to top for new chapters
            lastSavedProgress = 0f
            scrollView.post {
                scrollView.scrollTo(0, 0)
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
        textView.typeface = when (fontFamily.lowercase()) {
            "serif" -> android.graphics.Typeface.SERIF
            "monospace" -> android.graphics.Typeface.MONOSPACE
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
        
        return when(theme) {
            "dark" -> 0xFF121212.toInt() to 0xFFE0E0E0.toInt()
            "sepia" -> 0xFFF4ECD8.toInt() to 0xFF5B4636.toInt()
            "black" -> 0xFF000000.toInt() to 0xFFCCCCCC.toInt()
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
        // Process content to ensure paragraph tags exist for styling
        var processedContent = content
        
        // If content doesn't have <p> tags, wrap paragraphs (double newlines or single <br> followed by text)
        if (!processedContent.contains("<p>", ignoreCase = true)) {
            // Replace double line breaks with paragraph markers
            processedContent = processedContent
                .replace("\n\n", "</p><p>")
                .replace("\r\n\r\n", "</p><p>")
            // Wrap in paragraph tags
            processedContent = "<p>$processedContent</p>"
        }
        
        // Note: CSS text-indent doesn't work with Html.fromHtml(), so paragraph indent
        // setting is only available in WebView mode
        
        scope.launch {
            val spanned = withContext(Dispatchers.Default) {
                Html.fromHtml(processedContent, Html.FROM_HTML_MODE_COMPACT)
            }
            textView.text = spanned
        }
    }
    
    /**
     * Strips the chapter title from the beginning of the content.
     * Removes the first H1-H6 heading element or the first paragraph if it matches the chapter name.
     */
    private fun stripChapterTitle(content: String, chapterName: String): String {
        // Try to remove first heading (H1-H6) that matches or is at the start
        val headingRegex = """^\s*<h[1-6][^>]*>.*?</h[1-6]>\s*""".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = headingRegex.find(content)
        if (matchResult != null) {
            return content.substring(matchResult.range.last + 1).trimStart()
        }
        
        // Try to remove first paragraph if it closely matches chapter name (for sites that use <p> for title)
        val paragraphRegex = """^\s*<p[^>]*>(.*?)</p>\s*""".toRegex(RegexOption.IGNORE_CASE)
        val pMatch = paragraphRegex.find(content)
        if (pMatch != null) {
            val pText = pMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            // Check if paragraph text is similar to chapter name (fuzzy match)
            if (pText.equals(chapterName, ignoreCase = true) ||
                chapterName.contains(pText, ignoreCase = true) ||
                pText.contains(chapterName, ignoreCase = true)) {
                return content.substring(pMatch.range.last + 1).trimStart()
            }
        }
        
        // No title found to strip
        return content
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
    
    private fun showLoadingIndicator() {
        if (loadingIndicator == null) {
            loadingIndicator = ReaderProgressIndicator(activity).apply {
                container.addView(this, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                })
            }
        }
        loadingIndicator?.show()
    }
    
    private fun hideLoadingIndicator() {
        loadingIndicator?.hide()
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
        val speed = preferences.novelAutoScrollSpeed().get().coerceAtLeast(1)
        isAutoScrolling = true
        
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive && isAutoScrolling) {
                scrollView.smoothScrollBy(0, 2)
                // Adjust delay based on speed. 
                // Assuming speed is a multiplier or duration. 
                // Let's try a base delay.
                val delayMs = (20 / speed).toLong().coerceAtLeast(1)
                delay(delayMs)
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
        val progress = if (totalHeight > 0) scrollY.toFloat() / totalHeight else 0f
        return (progress * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Sets the scroll position by progress (0.0 to 1.0)
     */
    fun setScrollProgress(progress: Float) {
        val child = scrollView.getChildAt(0) ?: return
        val totalHeight = child.height - scrollView.height
        val scrollY = (totalHeight * progress).toInt()
        scrollView.scrollTo(0, scrollY)
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
