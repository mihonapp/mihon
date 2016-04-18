package eu.kanade.tachiyomi.ui.reader

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.*
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.base.listener.SimpleAnimationListener
import eu.kanade.tachiyomi.ui.base.listener.SimpleSeekBarListener
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.LeftToRightReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical.VerticalReader
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonReader
import eu.kanade.tachiyomi.util.GLUtil
import eu.kanade.tachiyomi.util.toast
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.reader_menu.*
import nucleus.factory.RequiresPresenter
import rx.Subscription
import rx.subscriptions.CompositeSubscription
import timber.log.Timber
import java.text.DecimalFormat

@RequiresPresenter(ReaderPresenter::class)
class ReaderActivity : BaseRxActivity<ReaderPresenter>() {

    companion object {
        @Suppress("unused")
        const val LEFT_TO_RIGHT = 1
        const val RIGHT_TO_LEFT = 2
        const val VERTICAL = 3
        const val WEBTOON = 4

        const val BLACK_THEME = 1

        const val MENU_VISIBLE = "menu_visible"

        fun newIntent(context: Context): Intent {
            return Intent(context, ReaderActivity::class.java)
        }
    }

    private var viewer: BaseReader? = null

    private var uiFlags: Int = 0

    lateinit var subscriptions: CompositeSubscription
        private set

    private var customBrightnessSubscription: Subscription? = null

    var readerTheme: Int = 0
        private set

    var maxBitmapSize: Int = 0
        private set

    private val decimalFormat = DecimalFormat("#.###")

    private var popupMenu: ReaderPopupMenu? = null

    private var nextChapterBtn: MenuItem? = null

    private var prevChapterBtn: MenuItem? = null

    private val volumeKeysEnabled by lazy { preferences.readWithVolumeKeys().getOrDefault() }

    val preferences: PreferencesHelper
        get() = presenter.prefs

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.activity_reader)

        setupToolbar(toolbar)
        subscriptions = CompositeSubscription()

        initializeMenu()
        initializeSettings()

        if (savedState != null) {
            setMenuVisibility(savedState.getBoolean(MENU_VISIBLE), animate = false)
        }

        maxBitmapSize = GLUtil.getMaxTextureSize()
    }

    override fun onResume() {
        super.onResume()
        setSystemUiVisibility()
    }

    override fun onPause() {
        viewer?.let {
            presenter.currentPage = it.getActivePage()
        }
        super.onPause()
    }

    override fun onDestroy() {
        subscriptions.unsubscribe()
        popupMenu?.dismiss()
        viewer = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)
        nextChapterBtn = menu.findItem(R.id.action_next_chapter)
        prevChapterBtn = menu.findItem(R.id.action_previous_chapter)
        setAdjacentChaptersVisibility()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_previous_chapter -> requestPreviousChapter()
            R.id.action_next_chapter -> requestNextChapter()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(MENU_VISIBLE, reader_menu.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        presenter.onChapterLeft()

        val chapterToUpdate = presenter.getMangaSyncChapterToUpdate()

        if (chapterToUpdate > 0) {
            if (presenter.prefs.askUpdateMangaSync()) {
                MaterialDialog.Builder(this)
                        .content(getString(R.string.confirm_update_manga_sync, chapterToUpdate))
                        .positiveText(android.R.string.yes)
                        .negativeText(android.R.string.no)
                        .onPositive { dialog, which -> presenter.updateMangaSyncLastChapterRead() }
                        .onAny { dialog1, which1 -> super.onBackPressed() }
                        .show()
            } else {
                presenter.updateMangaSyncLastChapterRead()
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setSystemUiVisibility()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isFinishing) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (volumeKeysEnabled) {
                        if (event.action == KeyEvent.ACTION_UP) {
                            viewer?.moveToNext()
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (volumeKeysEnabled) {
                        if (event.action == KeyEvent.ACTION_UP) {
                            viewer?.moveToPrevious()
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        viewer?.moveToNext()
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        viewer?.moveToPrevious()
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun onChapterError(error: Throwable) {
        Timber.e(error, error.message)
        finish()
        toast(R.string.page_list_error)
    }

    fun onChapterAppendError() {
        // Ignore
    }

    fun onChapterReady(manga: Manga, chapter: Chapter, currentPage: Page?) {
        val activePage = currentPage ?: chapter.pages.last()

        if (viewer == null) {
            viewer = getOrCreateViewer(manga)
        }
        viewer?.onPageListReady(chapter, activePage)

        if (viewer is RightToLeftReader && page_seekbar.rotation != 180f) {
            // Invert the seekbar for the right to left reader
            page_seekbar.rotation = 180f
        }
        setToolbarTitle(manga.title)
        setActiveChapter(chapter, activePage.pageNumber)
    }

    fun onEnterChapter(chapter: Chapter, currentPage: Int) {
        val activePage = if (currentPage == -1) chapter.pages.lastIndex else currentPage
        presenter.setActiveChapter(chapter)
        setActiveChapter(chapter, activePage)
    }

    fun setActiveChapter(chapter: Chapter, currentPage: Int) {
        val numPages = chapter.pages.size
        if (page_seekbar.rotation != 180f) {
            right_page_text.text = "$numPages"
            left_page_text.text = "${currentPage + 1}"
        } else {
            left_page_text.text = "$numPages"
            right_page_text.text = "${currentPage + 1}"
        }
        page_seekbar.max = numPages - 1
        page_seekbar.progress = currentPage

        setToolbarSubtitle(if (chapter.chapter_number != -1f)
            getString(R.string.chapter_subtitle, decimalFormat.format(chapter.chapter_number.toDouble()))
        else
            chapter.name)
    }

    fun onAppendChapter(chapter: Chapter) {
        viewer?.onPageListAppendReady(chapter)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAdjacentChapters(previous: Chapter?, next: Chapter?) {
        setAdjacentChaptersVisibility()
    }

    private fun setAdjacentChaptersVisibility() {
        prevChapterBtn?.isVisible = presenter.hasPreviousChapter()
        nextChapterBtn?.isVisible = presenter.hasNextChapter()
    }


    private fun getOrCreateViewer(manga: Manga): BaseReader {
        val mangaViewer = if (manga.viewer == 0) preferences.defaultViewer() else manga.viewer

        // Try to reuse the viewer using its tag
        var fragment: BaseReader? = supportFragmentManager.findFragmentByTag(manga.viewer.toString()) as? BaseReader
        if (fragment == null) {
            // Create a new viewer
            when (mangaViewer) {
                RIGHT_TO_LEFT -> fragment = RightToLeftReader()
                VERTICAL -> fragment = VerticalReader()
                WEBTOON -> fragment = WebtoonReader()
                else -> fragment = LeftToRightReader()
            }

            supportFragmentManager.beginTransaction().replace(R.id.reader, fragment, manga.viewer.toString()).commit()
        }
        return fragment
    }

    fun onPageChanged(currentPageIndex: Int, totalPages: Int) {
        val page = currentPageIndex + 1
        page_number.text = "$page/$totalPages"
        if (page_seekbar.rotation != 180f) {
            left_page_text.text = "$page"
        } else {
            right_page_text.text = "$page"
        }
        page_seekbar.progress = currentPageIndex
    }

    fun gotoPageInCurrentChapter(pageIndex: Int) {
        viewer?.let {
            val requestedPage = it.getActivePage().chapter.pages[pageIndex]
            it.setActivePage(requestedPage)
        }
    }

    fun onCenterSingleTap() {
        setMenuVisibility(reader_menu.visibility == View.GONE)
    }

    fun requestNextChapter() {
        if (!presenter.loadNextChapter()) {
            toast(R.string.no_next_chapter)
        }
    }

    fun requestPreviousChapter() {
        if (!presenter.loadPreviousChapter()) {
            toast(R.string.no_previous_chapter)
        }
    }

    private fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        if (visible) {
            reader_menu.visibility = View.VISIBLE

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbar.startAnimation(toolbarAnimation)

                val bottomMenuAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_bottom)
                reader_menu_bottom.startAnimation(bottomMenuAnimation)
            }
        } else {
            val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
            toolbarAnimation.setAnimationListener(object : SimpleAnimationListener() {
                override fun onAnimationEnd(animation: Animation) {
                    reader_menu.visibility = View.GONE
                }
            })
            toolbar.startAnimation(toolbarAnimation)

            val bottomMenuAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_bottom)
            reader_menu_bottom.startAnimation(bottomMenuAnimation)

            popupMenu?.dismiss()
        }
    }

    private fun initializeMenu() {
        // Intercept all events in this layout
        reader_menu_bottom.setOnTouchListener { v, event -> true }

        page_seekbar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    gotoPageInCurrentChapter(progress)
                }
            }
        })

        lock_orientation.setOnClickListener { v ->
            showImmersiveDialog(MaterialDialog.Builder(this)
                    .title(R.string.pref_rotation_type)
                    .items(R.array.rotation_type)
                    .itemsCallbackSingleChoice(preferences.rotation().getOrDefault() - 1,
                            { d, itemView, which, text ->
                                preferences.rotation().set(which + 1)
                                true
                            })
                    .build())
        }

        reader_zoom_selector.setOnClickListener { v ->
            showImmersiveDialog(MaterialDialog.Builder(this)
                    .title(R.string.pref_zoom_start)
                    .items(R.array.zoom_start)
                    .itemsCallbackSingleChoice(preferences.zoomStart().getOrDefault() - 1,
                            { d, itemView, which, text ->
                                preferences.zoomStart().set(which + 1)
                                true
                            })
                    .build())
        }

        reader_scale_type_selector.setOnClickListener { v ->
            showImmersiveDialog(MaterialDialog.Builder(this)
                    .title(R.string.pref_image_scale_type)
                    .items(R.array.image_scale_type)
                    .itemsCallbackSingleChoice(preferences.imageScaleType().getOrDefault() - 1,
                            { d, itemView, which, text ->
                                preferences.imageScaleType().set(which + 1)
                                true
                            })
                    .build())
        }

        reader_selector.setOnClickListener { v ->
            showImmersiveDialog(MaterialDialog.Builder(this)
                    .title(R.string.pref_viewer_type)
                    .items(R.array.viewers_selector)
                    .itemsCallbackSingleChoice(presenter.manga.viewer,
                            { d, itemView, which, text ->
                                presenter.updateMangaViewer(which)
                                recreate()
                                true
                            })
                    .build())
        }

        val popupView = layoutInflater.inflate(R.layout.reader_popup, null)
        popupMenu = ReaderPopupMenu(this, popupView)

        reader_extra_settings.setOnClickListener {
            popupMenu?.let {
                if (!it.isShowing)
                    it.showAtLocation(reader_extra_settings,
                            Gravity.BOTTOM or Gravity.RIGHT, 0, reader_menu_bottom.height)
                else
                    it.dismiss()
            }

        }

    }

    private fun initializeSettings() {
        subscriptions.add(preferences.showPageNumber().asObservable()
                .subscribe { setPageNumberVisibility(it) })

        subscriptions.add(preferences.rotation().asObservable()
                .subscribe {
                    setRotation(it)

                    val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

                    val resourceId = if (it == 1)
                        R.drawable.ic_screen_rotation_white_24dp
                    else if (isPortrait)
                        R.drawable.ic_screen_lock_portrait_white_24dp
                    else
                        R.drawable.ic_screen_lock_landscape_white_24dp

                    lock_orientation.setImageResource(resourceId)
                })

        subscriptions.add(preferences.hideStatusBar().asObservable()
                .subscribe { setStatusBarVisibility(it) })

        subscriptions.add(preferences.keepScreenOn().asObservable()
                .subscribe { setKeepScreenOn(it) })

        subscriptions.add(preferences.customBrightness().asObservable()
                .subscribe { setCustomBrightness(it) })

        subscriptions.add(preferences.readerTheme().asObservable()
                .distinctUntilChanged()
                .subscribe { applyTheme(it) })
    }

    private fun setRotation(rotation: Int) {
        when (rotation) {
            // Rotation free
            1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Lock in current rotation
            2 -> {
                val currentOrientation = resources.configuration.orientation
                setRotation(if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) 3 else 4)
            }
            // Lock in portrait
            3 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            // Lock in landscape
            4 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun setPageNumberVisibility(visible: Boolean) {
        page_number.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setCustomBrightness(enabled: Boolean) {
        if (enabled) {
            customBrightnessSubscription = preferences.customBrightnessValue().asObservable()
                    .map { Math.max(0.01f, it) }
                    .subscribe { setCustomBrightnessValue(it) }

            subscriptions.add(customBrightnessSubscription)
        } else {
            if (customBrightnessSubscription != null) {
                subscriptions.remove(customBrightnessSubscription)
            }
            setCustomBrightnessValue(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    private fun setCustomBrightnessValue(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private fun setStatusBarVisibility(hidden: Boolean) {
        createUiHideFlags(hidden)
        setSystemUiVisibility()
    }

    private fun createUiHideFlags(statusBarHidden: Boolean) {
        uiFlags = 0
        uiFlags = uiFlags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        if (statusBarHidden) {
            uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            uiFlags = uiFlags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    fun setSystemUiVisibility() {
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun applyTheme(theme: Int) {
        readerTheme = theme
        val rootView = window.decorView.rootView
        if (theme == BLACK_THEME) {
            rootView.setBackgroundColor(Color.BLACK)
            page_number.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimaryDark))
            page_number.setBackgroundColor(ContextCompat.getColor(this, R.color.pageNumberBackgroundDark))
        } else {
            rootView.setBackgroundColor(Color.WHITE)
            page_number.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimaryLight))
            page_number.setBackgroundColor(ContextCompat.getColor(this, R.color.pageNumberBackgroundLight))
        }
    }

    private fun showImmersiveDialog(dialog: Dialog) {
        // Hack to not leave immersive mode
        dialog.window.setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE)
        dialog.show()
        dialog.window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
        dialog.window.clearFlags(FLAG_NOT_FOCUSABLE)
    }

}
