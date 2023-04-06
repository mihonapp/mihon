package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ProgressDialog
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.slider.Slider
import com.google.android.material.transition.platform.MaterialContainerTransform
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.orientationType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsSheet
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.preference.toggle
import eu.kanade.tachiyomi.util.system.applySystemAnimatorScale
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.util.system.getThemeColor
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.copy
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.setTooltip
import eu.kanade.tachiyomi.widget.listener.SimpleAnimationListener
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.Constants
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max

class ReaderActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        private const val ENABLED_BUTTON_IMAGE_ALPHA = 255
        private const val DISABLED_BUTTON_IMAGE_ALPHA = 64
    }

    private val readerPreferences: ReaderPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null

    val hasCutout by lazy { hasDisplayCutout() }

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    var viewer: BaseViewer? = null
        private set

    /**
     * Whether the menu is currently visible.
     */
    var menuVisible = false
        private set

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    /**
     * Progress dialog used when switching chapters from the menu buttons.
     */
    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog? = null

    private var menuToggleToast: Toast? = null

    private var readingModeToast: Toast? = null

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, binding.root) }

    private var loadingIndicator: ReaderProgressIndicator? = null

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.needsInit()) {
            val manga = intent.extras!!.getLong("manga", -1)
            val chapter = intent.extras!!.getLong("chapter", -1)
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, manga.hashCode(), Notifications.ID_NEW_CHAPTERS)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean(::menuVisible.name)
        }

        config = ReaderConfig()
        initializeMenu()

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setManga)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        viewer = null
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
        progressDialog?.dismiss()
        progressDialog = null
    }

    /**
     * Called when the activity is saving instance state. Current progress is persisted if this
     * activity isn't changing configurations.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(::menuVisible.name, menuVisible)
        viewModel.onSaveInstanceState()
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        viewModel.saveCurrentChapterReadingProgress()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.setReadStartTime()
        setMenuVisibility(menuVisible, animate = false)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(menuVisible, animate = false)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)

        val isChapterBookmarked = viewModel.getCurrentChapter()?.chapter?.bookmark ?: false
        menu.findItem(R.id.action_bookmark).isVisible = !isChapterBookmarked
        menu.findItem(R.id.action_remove_bookmark).isVisible = isChapterBookmarked
        menu.findItem(R.id.action_open_in_web_view).isVisible = viewModel.getSource() is HttpSource

        return true
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_in_web_view -> {
                openChapterInWebview()
            }
            R.id.action_bookmark -> {
                viewModel.bookmarkCurrentChapter(true)
                invalidateOptionsMenu()
            }
            R.id.action_remove_bookmark -> {
                viewModel.bookmarkCurrentChapter(false)
                invalidateOptionsMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.applyInsetter {
            type(navigationBars = true, statusBars = true) {
                margin(top = true, horizontal = true)
            }
        }
        binding.readerMenuBottom.applyInsetter {
            type(navigationBars = true) {
                margin(bottom = true, horizontal = true)
            }
        }

        binding.toolbar.setOnClickListener {
            viewModel.manga?.id?.let { id ->
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = Constants.SHORTCUT_MANGA
                        putExtra(Constants.MANGA_EXTRA, id)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                )
            }
        }

        // Init listeners on bottom menu
        binding.pageSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    isScrollingThroughPages = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    isScrollingThroughPages = false
                }
            },
        )
        binding.pageSlider.addOnChangeListener { slider, value, fromUser ->
            if (viewer != null && fromUser) {
                isScrollingThroughPages = true
                moveToPageIndex(value.toInt())
                slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        binding.leftChapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is R2LPagerViewer) {
                    loadNextChapter()
                } else {
                    loadPreviousChapter()
                }
            }
        }
        binding.rightChapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is R2LPagerViewer) {
                    loadPreviousChapter()
                } else {
                    loadNextChapter()
                }
            }
        }

        initBottomShortcuts()

        val toolbarBackground = (binding.toolbar.background as MaterialShapeDrawable).apply {
            elevation = resources.getDimension(R.dimen.m3_sys_elevation_level2)
            alpha = if (isNightMode()) 230 else 242 // 90% dark 95% light
        }
        binding.toolbarBottom.background = toolbarBackground.copy(this@ReaderActivity)

        binding.readerSeekbar.background = toolbarBackground.copy(this@ReaderActivity)?.apply {
            setCornerSize(999F)
        }
        listOf(binding.leftChapter, binding.rightChapter).forEach {
            it.background = binding.readerSeekbar.background.copy(this)
            it.foreground = RippleDrawable(
                ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
                null,
                it.background,
            )
        }

        val toolbarColor = ColorUtils.setAlphaComponent(
            toolbarBackground.resolvedTintColor,
            toolbarBackground.alpha,
        )
        window.statusBarColor = toolbarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = toolbarColor
        }

        // Set initial visibility
        setMenuVisibility(menuVisible)
    }

    private fun initBottomShortcuts() {
        // Reading mode
        with(binding.actionReadingMode) {
            setTooltip(R.string.viewer)

            setOnClickListener {
                popupMenu(
                    items = ReadingModeType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.getMangaReadingMode(resolveDefault = false),
                ) {
                    val newReadingMode = ReadingModeType.fromPreference(itemId)

                    viewModel.setMangaReadingMode(newReadingMode.flagValue)

                    menuToggleToast?.cancel()
                    if (!readerPreferences.showReadingMode().get()) {
                        menuToggleToast = toast(newReadingMode.stringRes)
                    }

                    updateCropBordersShortcut()
                }
            }
        }

        // Crop borders
        with(binding.actionCropBorders) {
            setTooltip(R.string.pref_crop_borders)

            setOnClickListener {
                val isPagerType = ReadingModeType.isPagerType(viewModel.getMangaReadingMode())
                val enabled = if (isPagerType) {
                    readerPreferences.cropBorders().toggle()
                } else {
                    readerPreferences.cropBordersWebtoon().toggle()
                }

                menuToggleToast?.cancel()
                menuToggleToast = toast(
                    if (enabled) {
                        R.string.on
                    } else {
                        R.string.off
                    },
                )
            }
        }
        updateCropBordersShortcut()
        listOf(readerPreferences.cropBorders(), readerPreferences.cropBordersWebtoon())
            .forEach { pref ->
                pref.changes()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(lifecycleScope)
            }

        // Rotation
        with(binding.actionRotation) {
            setTooltip(R.string.rotation_type)

            setOnClickListener {
                popupMenu(
                    items = OrientationType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.manga?.orientationType?.toInt()
                        ?: readerPreferences.defaultOrientationType().get(),
                ) {
                    val newOrientation = OrientationType.fromPreference(itemId)

                    viewModel.setMangaOrientationType(newOrientation.flagValue)

                    menuToggleToast?.cancel()
                    menuToggleToast = toast(newOrientation.stringRes)
                }
            }
        }

        // Settings sheet
        with(binding.actionSettings) {
            setTooltip(R.string.action_settings)

            var readerSettingSheet: ReaderSettingsSheet? = null

            setOnClickListener {
                if (readerSettingSheet?.isShowing == true) return@setOnClickListener
                readerSettingSheet = ReaderSettingsSheet(this@ReaderActivity).apply { show() }
            }

            setOnLongClickListener {
                if (readerSettingSheet?.isShowing == true) return@setOnLongClickListener false
                readerSettingSheet = ReaderSettingsSheet(this@ReaderActivity, showColorFilterSettings = true).apply { show() }
                true
            }
        }
    }

    private fun updateOrientationShortcut(preference: Int) {
        val orientation = OrientationType.fromPreference(preference)
        binding.actionRotation.setImageResource(orientation.iconRes)
    }

    private fun updateCropBordersShortcut() {
        val isPagerType = ReadingModeType.isPagerType(viewModel.getMangaReadingMode())
        val enabled = if (isPagerType) {
            readerPreferences.cropBorders().get()
        } else {
            readerPreferences.cropBordersWebtoon().get()
        }

        binding.actionCropBorders.setImageResource(
            if (enabled) {
                R.drawable.ic_crop_24dp
            } else {
                R.drawable.ic_crop_off_24dp
            },
        )
    }

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        menuVisible = visible
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            binding.readerMenu.isVisible = true

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbarAnimation.applySystemAnimatorScale(this)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationStart(animation: Animation) {
                            // Fix status bar being translucent the first time it's opened.
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        }
                    },
                )
                binding.toolbar.startAnimation(toolbarAnimation)

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_bottom)
                bottomAnimation.applySystemAnimatorScale(this)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (readerPreferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(false)
            }
        } else {
            if (readerPreferences.fullscreen().get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.applySystemAnimatorScale(this)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationEnd(animation: Animation) {
                            binding.readerMenu.isVisible = false
                        }
                    },
                )
                binding.toolbar.startAnimation(toolbarAnimation)

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_bottom)
                bottomAnimation.applySystemAnimatorScale(this)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (readerPreferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(true)
            }
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer
     * and the toolbar title.
     */
    private fun setManga(manga: Manga) {
        val prevViewer = viewer

        val viewerMode = ReadingModeType.fromPreference(viewModel.getMangaReadingMode(resolveDefault = false))
        binding.actionReadingMode.setImageResource(viewerMode.iconRes)

        val newViewer = ReadingModeType.toViewer(viewModel.getMangaReadingMode(), this)

        updateCropBordersShortcut()
        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientationType())
            }
        } else {
            setOrientation(viewModel.getMangaOrientationType())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        updateViewerInset(readerPreferences.fullscreen().get())
        binding.viewerContainer.addView(newViewer.getView())

        if (readerPreferences.showReadingMode().get()) {
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        supportActionBar?.title = manga.title

        binding.pageSlider.isRTL = newViewer is R2LPagerViewer
        if (newViewer is R2LPagerViewer) {
            binding.leftChapter.setTooltip(R.string.action_next_chapter)
            binding.rightChapter.setTooltip(R.string.action_previous_chapter)
        } else {
            binding.leftChapter.setTooltip(R.string.action_previous_chapter)
            binding.rightChapter.setTooltip(R.string.action_next_chapter)
        }

        val loadingIndicatorContext = createReaderThemeContext()
        loadingIndicator = ReaderProgressIndicator(loadingIndicatorContext).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = Gravity.CENTER
            }
        }
        binding.readerContainer.addView(loadingIndicator)

        startPostponedEnterTransition()
    }

    private fun openChapterInWebview() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                val intent = WebViewActivity.newIntent(this@ReaderActivity, url, source.id, manga.title)
                withUIContext { startActivity(intent) }
            }
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            val strings = resources.getStringArray(R.array.viewers_selector)
            readingModeToast?.cancel()
            readingModeToast = toast(strings[mode])
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    private fun setChapters(viewerChapters: ViewerChapters) {
        binding.readerContainer.removeView(loadingIndicator)
        viewer?.setChapters(viewerChapters)
        binding.toolbar.subtitle = viewerChapters.currChapter.chapter.name

        val currentChapterPageCount = viewerChapters.currChapter.pages?.size ?: 1
        binding.readerSeekbar.isInvisible = currentChapterPageCount == 1

        val leftChapterObject = if (viewer is R2LPagerViewer) viewerChapters.nextChapter else viewerChapters.prevChapter
        val rightChapterObject = if (viewer is R2LPagerViewer) viewerChapters.prevChapter else viewerChapters.nextChapter

        if (leftChapterObject == null && rightChapterObject == null) {
            binding.leftChapter.isVisible = false
            binding.rightChapter.isVisible = false
        } else {
            binding.leftChapter.isEnabled = leftChapterObject != null
            binding.leftChapter.imageAlpha = if (leftChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA

            binding.rightChapter.isEnabled = rightChapterObject != null
            binding.rightChapter.imageAlpha = if (rightChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA
        }

        // Invalidate menu to show proper chapter bookmark state
        invalidateOptionsMenu()

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    @Suppress("DEPRECATION")
    fun setProgressDialog(show: Boolean) {
        progressDialog?.dismiss()
        progressDialog = if (show) {
            ProgressDialog.show(this, null, getString(R.string.loading), true)
        } else {
            null
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = viewer ?: return
        val currentChapter = viewModel.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
        val pages = page.chapter.pages ?: return

        // Set bottom page number
        binding.pageNumber.text = "${page.number}/${pages.size}"

        // Set page numbers
        if (viewer !is R2LPagerViewer) {
            binding.leftPageText.text = "${page.number}"
            binding.rightPageText.text = "${pages.size}"
        } else {
            binding.rightPageText.text = "${page.number}"
            binding.leftPageText.text = "${pages.size}"
        }

        // Set slider progress
        binding.pageSlider.isEnabled = pages.size > 1
        binding.pageSlider.valueTo = max(pages.lastIndex.toFloat(), 1f)
        binding.pageSlider.value = page.index.toFloat()
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage) {
        ReaderPageSheet(this, page).show()
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preloadChapter(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the page sheet. It delegates the call to the presenter to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    fun shareImage(page: ReaderPage) {
        viewModel.shareImage(page)
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(uri: Uri, page: ReaderPage) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = getString(R.string.share_page_info, manga.title, chapter.name, page.number),
        )
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the presenter.
     */
    fun saveImage(page: ReaderPage) {
        viewModel.saveImage(page)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(R.string.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the presenter.
     */
    fun setAsCover(page: ReaderPage) {
        viewModel.setAsCover(page)
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> R.string.cover_updated
                AddToLibraryFirst -> R.string.notification_first_add_to_library
                Error -> R.string.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = OrientationType.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
        updateOrientationShortcut(viewModel.getMangaOrientationType(resolveDefault = false))
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    fun updateViewerInset(fullscreen: Boolean) {
        viewer?.getView()?.applyInsetter {
            if (!fullscreen) {
                type(navigationBars = true, statusBars = true) {
                    padding()
                }
            }
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        /**
         * Initializes the reader subscriptions.
         */
        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundResource(
                        when (theme) {
                            0 -> R.color.md_white_1000
                            2 -> R.color.reader_background_dark
                            3 -> automaticBackgroundColor()
                            else -> R.color.md_black_1000
                        },
                    )
                }
                .launchIn(lifecycleScope)

            readerPreferences.showPageNumber().changes()
                .onEach(::setPageNumberVisibility)
                .launchIn(lifecycleScope)

            readerPreferences.trueColor().changes()
                .onEach(::setTrueColor)
                .launchIn(lifecycleScope)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                readerPreferences.cutoutShort().changes()
                    .onEach(::setCutoutShort)
                    .launchIn(lifecycleScope)
            }

            readerPreferences.keepScreenOn().changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            readerPreferences.colorFilter().changes()
                .onEach(::setColorFilter)
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterMode().changes()
                .onEach { setColorFilter(readerPreferences.colorFilter().get()) }
                .launchIn(lifecycleScope)

            merge(readerPreferences.grayscale().changes(), readerPreferences.invertedColors().changes())
                .onEach { setLayerPaint(readerPreferences.grayscale().get(), readerPreferences.invertedColors().get()) }
                .launchIn(lifecycleScope)

            readerPreferences.fullscreen().changes()
                .onEach {
                    WindowCompat.setDecorFitsSystemWindows(window, !it)
                    updateViewerInset(it)
                }
                .launchIn(lifecycleScope)
        }

        /**
         * Picks background color for [ReaderActivity] based on light/dark theme preference
         */
        private fun automaticBackgroundColor(): Int {
            return if (baseContext.isNightMode()) {
                R.color.reader_background_dark
            } else {
                R.color.md_white_1000
            }
        }

        /**
         * Sets the visibility of the bottom page indicator according to [visible].
         */
        fun setPageNumberVisibility(visible: Boolean) {
            binding.pageNumber.isVisible = visible
        }

        /**
         * Sets the 32-bit color mode according to [enabled].
         */
        private fun setTrueColor(enabled: Boolean) {
            if (enabled) {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888)
            } else {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.RGB_565)
            }
        }

        @TargetApi(Build.VERSION_CODES.P)
        private fun setCutoutShort(enabled: Boolean) {
            window.attributes.layoutInDisplayCutoutMode = when (enabled) {
                true -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                false -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Trigger relayout
            setMenuVisibility(menuVisible)
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                readerPreferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach(::setCustomBrightnessValue)
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the color filter overlay according to [enabled].
         */
        private fun setColorFilter(enabled: Boolean) {
            if (enabled) {
                readerPreferences.colorFilterValue().changes()
                    .sample(100)
                    .onEach(::setColorFilterValue)
                    .launchIn(lifecycleScope)
            } else {
                binding.colorOverlay.isVisible = false
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }

            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            // Set black overlay visibility.
            if (value < 0) {
                binding.brightnessOverlay.isVisible = true
                val alpha = (abs(value) * 2.56).toInt()
                binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            } else {
                binding.brightnessOverlay.isVisible = false
            }
        }

        /**
         * Sets the color filter [value].
         */
        private fun setColorFilterValue(value: Int) {
            binding.colorOverlay.isVisible = true
            binding.colorOverlay.setFilterColor(value, readerPreferences.colorFilterMode().get())
        }

        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }
}
