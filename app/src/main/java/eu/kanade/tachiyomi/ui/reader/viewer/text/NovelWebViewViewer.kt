package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import eu.kanade.presentation.reader.settings.CodeSnippet
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.injectLazy

/**
 * NovelWebViewViewer renders novel content using a WebView for more flexibility.
 * Supports custom CSS and JavaScript injection.
 */
class NovelWebViewViewer(val activity: ReaderActivity) : Viewer {

    private val container = FrameLayout(activity)
    private lateinit var webView: WebView
    private var loadingIndicator: ReaderProgressIndicator? = null
    private val preferences: ReaderPreferences by injectLazy()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var currentPage: ReaderPage? = null
    private var currentChapters: ViewerChapters? = null
    
    // Track scroll progress
    private var lastSavedProgress = 0f
    
    // Auto-scroll state
    private var isAutoScrolling = false
    private var autoScrollJob: Job? = null
    
    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        
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
                activity.toggleMenu()
                return true
            }
            
            return false
        }
    })

    init {
        initWebView()
        observePreferences()
    }
    
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun initWebView() {
        webView = WebView(activity).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    hideLoadingIndicator()
                    injectCustomStyles()
                    injectCustomScript()
                    restoreScrollPosition()
                }
            }
            
            // Add JavaScript interface for progress saving
            addJavascriptInterface(WebViewInterface(), "Android")
            
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
        
        container.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }
    
    private fun observePreferences() {
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
                preferences.novelParagraphIndent().changes(),
                preferences.novelCustomCss().changes(),
                preferences.novelCustomCssSnippets().changes(),
                preferences.novelUseOriginalFonts().changes(),
            ).drop(17) // Drop initial emissions from all 17 preferences
            .collect {
                injectCustomStyles()
            }
        }
        
        // Observe JS changes separately to re-inject scripts
        scope.launch {
            merge(
                preferences.novelCustomJs().changes(),
                preferences.novelCustomJsSnippets().changes(),
            ).drop(2)
            .collect {
                injectCustomScript()
            }
        }
    }
    
    private fun injectCustomStyles() {
        val fontSize = preferences.novelFontSize().get()
        val fontFamily = preferences.novelFontFamily().get()
        val lineHeight = preferences.novelLineHeight().get()
        val marginLeft = preferences.novelMarginLeft().get()
        val marginRight = preferences.novelMarginRight().get()
        val marginTop = preferences.novelMarginTop().get()
        val marginBottom = preferences.novelMarginBottom().get()
        val fontColor = preferences.novelFontColor().get()
        val backgroundColor = preferences.novelBackgroundColor().get()
        val paragraphIndent = preferences.novelParagraphIndent().get()
        val textAlign = preferences.novelTextAlign().get()
        val theme = preferences.novelTheme().get()
        
        val (themeBgColor, themeTextColor) = getThemeColors(theme)
        val finalBgColor = if (theme == "custom" && backgroundColor != -1) backgroundColor else themeBgColor
        val finalTextColor = if (fontColor != -1) fontColor else themeTextColor
        
        val bgColorHex = String.format("#%06X", 0xFFFFFF and finalBgColor)
        val textColorHex = String.format("#%06X", 0xFFFFFF and finalTextColor)
        
        val customCss = preferences.novelCustomCss().get()
        val useOriginalFonts = preferences.novelUseOriginalFonts().get()
        
        // Collect enabled CSS snippets
        val cssSnippetsJson = preferences.novelCustomCssSnippets().get()
        val enabledSnippetsCss = try {
            val snippets = Json.decodeFromString<List<CodeSnippet>>(cssSnippetsJson)
            snippets.filter { it.enabled }.joinToString("\n") { it.code }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to parse CSS snippets: ${e.message}" }
            ""
        }
        
        // Only include font-family if not using original fonts
        val fontFamilyCss = if (useOriginalFonts) "" else "font-family: $fontFamily;"
        
        val css = """
            body {
                font-size: ${fontSize}px;
                $fontFamilyCss
                line-height: ${lineHeight};
                margin: ${marginTop}px ${marginRight}px ${marginBottom}px ${marginLeft}px;
                color: $textColorHex !important;
                background-color: $bgColorHex !important;
                text-align: $textAlign;
            }
            p {
                text-indent: ${paragraphIndent}em;
                margin-top: 0.5em;
                margin-bottom: 0.5em;
            }
            * {
                color: inherit !important;
            }
            $customCss
            $enabledSnippetsCss
        """.trimIndent().replace("\n", " ")
        
        val js = """
            (function() {
                var style = document.getElementById('mihon-custom-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'mihon-custom-style';
                    document.head.appendChild(style);
                }
                style.textContent = `$css`;
            })();
        """
        
        webView.evaluateJavascript(js, null)
    }
    
    private fun injectCustomScript() {
        val customJs = preferences.novelCustomJs().get()
        if (customJs.isNotBlank()) {
            webView.evaluateJavascript(customJs, null)
        }
        
        // Inject enabled JS snippets
        val jsSnippetsJson = preferences.novelCustomJsSnippets().get()
        try {
            val snippets = Json.decodeFromString<List<CodeSnippet>>(jsSnippetsJson)
            snippets.filter { it.enabled }.forEach { snippet ->
                webView.evaluateJavascript(snippet.code, null)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to parse JS snippets: ${e.message}" }
        }
        
        // Add scroll tracking script with infinite scroll support
        val infiniteScrollEnabled = preferences.novelInfiniteScroll().get()
        val scrollTrackingScript = """
            (function() {
                var lastProgress = 0;
                var saveTimeout = null;
                var loadingNext = false;
                var infiniteScrollEnabled = $infiniteScrollEnabled;
                
                window.addEventListener('scroll', function() {
                    var scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
                    var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                    var progress = scrollHeight > 0 ? scrollTop / scrollHeight : 0;
                    
                    if (Math.abs(progress - lastProgress) > 0.01) {
                        lastProgress = progress;
                        
                        clearTimeout(saveTimeout);
                        saveTimeout = setTimeout(function() {
                            Android.onScrollProgress(progress);
                        }, 500);
                    }
                    
                    // Infinite scroll: load next chapter when near bottom
                    if (infiniteScrollEnabled && progress > 0.95 && !loadingNext) {
                        loadingNext = true;
                        Android.loadNextChapter();
                        // Reset after a delay to prevent multiple calls
                        setTimeout(function() { loadingNext = false; }, 2000);
                    }
                });
            })();
        """
        webView.evaluateJavascript(scrollTrackingScript, null)
    }
    
    private fun restoreScrollPosition() {
        currentPage?.let { page ->
            val savedProgress = page.chapter.chapter.last_page_read
            if (savedProgress > 0 && savedProgress <= 100) {
                val progress = savedProgress / 100f
                val js = """
                    (function() {
                        var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                        window.scrollTo(0, scrollHeight * $progress);
                    })();
                """
                webView.evaluateJavascript(js, null)
                lastSavedProgress = progress
            }
        }
    }
    
    private fun getThemeColors(theme: String): Pair<Int, Int> {
        val backgroundColor = preferences.novelBackgroundColor().get()
        val fontColor = preferences.novelFontColor().get()
        
        return when(theme) {
            "dark" -> 0xFF121212.toInt() to 0xFFE0E0E0.toInt()
            "sepia" -> 0xFFF4ECD8.toInt() to 0xFF5B4636.toInt()
            "black" -> 0xFF000000.toInt() to 0xFFCCCCCC.toInt()
            "custom" -> {
                val bg = if (backgroundColor != -1) backgroundColor else 0xFFFFFFFF.toInt()
                val text = if (fontColor != -1) fontColor else 0xFF000000.toInt()
                bg to text
            }
            else -> 0xFFFFFFFF.toInt() to 0xFF000000.toInt()
        }
    }

    override fun destroy() {
        // Save progress before destroying
        saveProgress()
        
        scope.cancel()
        loadJob?.cancel()
        webView.destroy()
    }
    
    private fun saveProgress() {
        currentPage?.let { page ->
            val progressValue = (lastSavedProgress * 100).toInt().coerceIn(0, 100)
            activity.saveNovelProgress(page, progressValue)
            logcat(LogPriority.DEBUG) { "NovelWebViewViewer: Saving progress $progressValue%" }
        }
    }

    override fun getView(): View = container

    override fun setChapters(chapters: ViewerChapters) {
        val page = chapters.currChapter.pages?.firstOrNull() as? ReaderPage ?: return
        
        loadJob?.cancel()
        currentPage = page
        currentChapters = chapters
        
        if (page.status == Page.State.Ready && !page.text.isNullOrEmpty()) {
            hideLoadingIndicator()
            displayContent(chapters.currChapter, page)
            return
        }
        
        showLoadingIndicator()
        
        loadJob = scope.launch {
            val loader = page.chapter.pageLoader
            if (loader == null) {
                logcat(LogPriority.ERROR) { "NovelWebViewViewer: No page loader available" }
                hideLoadingIndicator()
                return@launch
            }
            
            launch(Dispatchers.IO) {
                loader.loadPage(page)
            }
            
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue, Page.State.LoadPage -> {
                        showLoadingIndicator()
                    }
                    Page.State.Ready -> {
                        hideLoadingIndicator()
                        displayContent(chapters.currChapter, page)
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
    
    private fun displayContent(chapter: ReaderChapter, page: ReaderPage) {
        var content = page.text
        if (content.isNullOrBlank()) {
            displayError(Exception("No text content available"))
            return
        }
        
        // Optionally strip chapter title from content
        if (preferences.novelHideChapterTitle().get()) {
            content = stripChapterTitle(content, chapter.chapter.name)
        }
        
        val theme = preferences.novelTheme().get()
        val (themeBgColor, themeTextColor) = getThemeColors(theme)
        val bgColorHex = String.format("#%06X", 0xFFFFFF and themeBgColor)
        val textColorHex = String.format("#%06X", 0xFFFFFF and themeTextColor)
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 16px;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                    }
                </style>
            </head>
            <body>
                $content
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
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
        val errorHtml = """
            <!DOCTYPE html>
            <html>
            <body style="display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0;">
                <div style="text-align: center; color: #ff5555;">
                    <h2>Error loading chapter</h2>
                    <p>${error.message}</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    override fun moveToPage(page: ReaderPage) {
        // For novels, navigation is by chapter not page
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
                    if (!isUp) webView.pageDown(false)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (preferences.novelVolumeKeysScroll().get()) {
                    if (!isUp) webView.pageUp(false)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (!isUp) {
                    if (event.isShiftPressed) {
                        webView.pageUp(false)
                    } else {
                        webView.pageDown(false)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (!isUp) webView.pageUp(false)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (!isUp) webView.pageDown(false)
                return true
            }
        }
        return false
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false
    
    /**
     * JavaScript interface for communication from WebView
     */
    inner class WebViewInterface {
        @JavascriptInterface
        fun onScrollProgress(progress: Float) {
            activity.runOnUiThread {
                lastSavedProgress = progress
                saveProgress()
                // Also notify activity for slider update
                activity.onNovelProgressChanged(progress)
            }
        }
        
        @JavascriptInterface
        fun loadNextChapter() {
            activity.runOnUiThread {
                if (preferences.novelInfiniteScroll().get()) {
                    activity.loadNextChapter()
                }
            }
        }
        
        @JavascriptInterface
        fun loadPrevChapter() {
            activity.runOnUiThread {
                if (preferences.novelInfiniteScroll().get()) {
                    activity.loadPreviousChapter()
                }
            }
        }
    }
    
    /**
     * Scroll to the top of the content
     */
    fun scrollToTop() {
        webView.scrollTo(0, 0)
    }
    
    /**
     * Toggle auto-scroll for WebView using JavaScript-based smooth scrolling
     */
    fun toggleAutoScroll() {
        isAutoScrolling = !isAutoScrolling
        
        if (isAutoScrolling) {
            startAutoScroll()
        } else {
            stopAutoScroll()
        }
    }
    
    private fun startAutoScroll() {
        val speed = preferences.novelAutoScrollSpeed().get().coerceAtLeast(1)
        isAutoScrolling = true
        
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive && isAutoScrolling) {
                // Use JavaScript to scroll smoothly
                webView.evaluateJavascript(
                    """
                    (function() {
                        window.scrollBy(0, 1);
                    })();
                    """.trimIndent(),
                    null
                )
                // Delay based on speed - lower speed value = faster scroll
                delay((50L - speed.coerceIn(1, 30) + 20).coerceAtLeast(20L))
            }
        }
    }
    
    private fun stopAutoScroll() {
        isAutoScrolling = false
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    
    /**
     * Check if auto-scroll is currently active
     */
    fun isAutoScrollActive(): Boolean = isAutoScrolling
    
    /**
     * Gets the current scroll progress as percentage (0 to 100)
     */
    fun getProgressPercent(): Int {
        // Return last saved progress since WebView scroll can't be accessed synchronously
        return (lastSavedProgress * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Sets the scroll position by progress percentage (0 to 100)
     */
    fun setProgressPercent(percent: Int) {
        val progress = percent.coerceIn(0, 100)
        webView.evaluateJavascript(
            """
            (function() {
                var scrollHeight = document.documentElement.scrollHeight - window.innerHeight;
                var targetScroll = scrollHeight * $progress / 100;
                window.scrollTo(0, targetScroll);
            })();
            """.trimIndent(),
            null
        )
    }
    
    /**
     * Reload the current chapter
     */
    fun reloadChapter() {
        currentChapters?.let { setChapters(it) }
    }
}
