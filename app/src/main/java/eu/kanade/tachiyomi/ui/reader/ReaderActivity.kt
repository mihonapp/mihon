package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Bundle
import android.view.*
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import com.afollestad.materialdialogs.MaterialDialog
import com.hippo.unifile.UniFile
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.checkedChanges
import com.jakewharton.rxbinding.widget.textChanges
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.LeftToRightReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader
import eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical.VerticalReader
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonReader
import eu.kanade.tachiyomi.util.*
import eu.kanade.tachiyomi.widget.SimpleAnimationListener
import eu.kanade.tachiyomi.widget.SimpleSeekBarListener
import kotlinx.android.synthetic.main.reader_activity.*
import me.zhanghai.android.systemuihelper.SystemUiHelper
import me.zhanghai.android.systemuihelper.SystemUiHelper.*
import nucleus.factory.RequiresPresenter
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

@RequiresPresenter(ReaderPresenter::class)
class ReaderActivity : BaseRxActivity<ReaderPresenter>() {

    companion object {
        @Suppress("unused")
        const val LEFT_TO_RIGHT = 1
        const val RIGHT_TO_LEFT = 2
        const val VERTICAL = 3
        const val WEBTOON = 4

        const val WHITE_THEME = 0
        const val BLACK_THEME = 1

        const val MENU_VISIBLE = "menu_visible"
        // --> EH
        const val EH_UTILS_VISIBLE = "eh_utils_visible"
        // <-- EH

        fun newIntent(context: Context, manga: Manga, chapter: Chapter): Intent {
            SharedData.put(ReaderEvent(manga, chapter))
            return Intent(context, ReaderActivity::class.java)
        }
    }

    private var viewer: BaseReader? = null

    val subscriptions by lazy { CompositeSubscription() }

    // --> EH
    private var autoscrollSubscription: Subscription? = null
    // <-- EH

    private var customBrightnessSubscription: Subscription? = null

    private var customFilterColorSubscription: Subscription? = null

    var readerTheme: Int = 0
        private set

    var maxBitmapSize: Int = 0
        private set

    private val decimalFormat = DecimalFormat("#.###")

    private val volumeKeysEnabled by lazy { preferences.readWithVolumeKeys().getOrDefault() }

    private val volumeKeysInverted by lazy { preferences.readWithVolumeKeysInverted().getOrDefault() }

    val preferences by injectLazy<PreferencesHelper>()

    private var systemUi: SystemUiHelper? = null

    private var menuVisible = false

    // --> EH
    private var ehUtilsVisible = false
    // <-- EH

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.reader_activity)

        if (savedState == null && SharedData.get(ReaderEvent::class.java) == null) {
            finish()
            return
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        initializeSettings()
        initializeBottomMenu()

        if (savedState != null) {
            menuVisible = savedState.getBoolean(MENU_VISIBLE)

            // --> EH
            ehUtilsVisible = savedState.getBoolean(EH_UTILS_VISIBLE)
            // <-- EH
        }

        setMenuVisibility(menuVisible)

        // --> EH
        setEhUtilsVisibility(ehUtilsVisible)
        // <-- EH

        maxBitmapSize = GLUtil.getMaxTextureSize()

        left_chapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is RightToLeftReader)
                    requestNextChapter()
                else
                    requestPreviousChapter()
            }
        }
        right_chapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is RightToLeftReader)
                    requestPreviousChapter()
                else
                    requestNextChapter()
            }
        }

        // --> EH
        subscriptions += expand_eh_button.clicks().subscribe {
            ehUtilsVisible = !ehUtilsVisible
            setEhUtilsVisibility(ehUtilsVisible)
        }

        eh_autoscroll_freq.setText(preferences.eh_utilAutoscrollInterval().getOrDefault().let {
            if(it == -1f)
                ""
            else it.toString()
        })

        subscriptions += eh_autoscroll.checkedChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    setupAutoscroll(if(it)
                        preferences.eh_utilAutoscrollInterval().getOrDefault()
                    else -1f)
                }

        subscriptions += eh_autoscroll_freq.textChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val parsed = it?.toString()?.toFloatOrNull()

                    if (parsed == null || parsed <= 0 || parsed > 9999) {
                        eh_autoscroll_freq.error = "Invalid frequency"
                        preferences.eh_utilAutoscrollInterval().set(-1f)
                        eh_autoscroll.isEnabled = false
                        setupAutoscroll(-1f)
                    } else {
                        eh_autoscroll_freq.error = null
                        preferences.eh_utilAutoscrollInterval().set(parsed)
                        eh_autoscroll.isEnabled = true
                        setupAutoscroll(if(eh_autoscroll.isChecked) parsed else -1f)
                    }
                }

        subscriptions += eh_autoscroll_help.clicks().subscribe {
            MaterialDialog.Builder(this)
                    .title("Autoscroll help")
                    .content("Automatically scroll to the next page in the specified interval. Interval is specified in seconds.")
                    .positiveText("Ok")
                    .show()
        }

        subscriptions += eh_retry_all.clicks().subscribe {
            var retried = 0

            viewer?.chapters
                    ?.flatMap { it.pages ?: emptyList() }
                    ?.forEachIndexed { index, page ->
                        var shouldQueuePage = false
                        if(page.status == Page.ERROR) {
                            shouldQueuePage = true
                        } else if(page.status == Page.LOAD_PAGE
                                || page.status == Page.DOWNLOAD_IMAGE) {
                            // Do nothing
                        } else if (page.uri == null) {
                            shouldQueuePage = true
                        } else if (!UniFile.fromUri(this, page.uri).exists()) {
                            shouldQueuePage = true
                        }

                        if(shouldQueuePage) {
                            page.status = Page.QUEUE
                        } else {
                            return@forEachIndexed
                        }

                        //If we are using EHentai/ExHentai, get a new image URL
                        if(presenter.source is EHentai)
                            page.imageUrl = null

                        if(viewer?.currentPage == index)
                            presenter.loader.loadPriorizedPage(page)
                        else
                            presenter.loader.loadPage(page)

                        retried++
                    }

            toast("Retrying $retried failed pages...")
        }

        subscriptions += eh_retry_all_help.clicks().subscribe {
            MaterialDialog.Builder(this)
                    .title("Retry all help")
                    .content("Re-add all failed pages to the download queue.")
                    .positiveText("Ok")
                    .show()
        }

        subscriptions += eh_boost_page.clicks().subscribe {
            viewer?.let { viewer ->
                val curPage = viewer.pages.getOrNull(viewer.currentPage)
                        ?: run {
                            toast("Cannot find current page!")
                            return@let
                        }

                if(curPage.status == Page.ERROR) {
                    toast("Page failed to load, press the retry button instead!")
                } else if(curPage.status == Page.LOAD_PAGE || curPage.status == Page.DOWNLOAD_IMAGE) {
                    toast("This page is already downloading!")
                } else if(curPage.status == Page.READY) {
                    toast("This page has already been downloaded!")
                } else {
                    presenter.loader.boostPage(curPage)
                    toast("Boosted current page!")
                }
            }
        }

        subscriptions += eh_boost_page_help.clicks().subscribe {
            MaterialDialog.Builder(this)
                    .title("Boost page help")
                    .content("Normally the downloader can only download a specific amount of pages at the same time. This means you can be waiting for a page to download but the downloader will not start downloading the page until it has a free download slot. Pressing 'Boost page' will force the downloader to begin downloading the current page, regardless of whether or not there is an available slot.")
                    .positiveText("Ok")
                    .show()
        }
        // <-- EH
    }

    override fun onDestroy() {
        toolbar.setNavigationOnClickListener(null)
        subscriptions.unsubscribe()
        viewer = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> ReaderSettingsDialog().show(supportFragmentManager, "settings")
            R.id.action_custom_filter -> ReaderCustomFilterDialog().show(supportFragmentManager, "filter")
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(MENU_VISIBLE, menuVisible)

        // --> EH
        outState.putBoolean(EH_UTILS_VISIBLE, eh_utils.visibility == View.VISIBLE)
        // <-- EH

        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(menuVisible, animate = false)
        }
    }

    override fun onBackPressed() {
        val chapterToUpdate = presenter.getTrackChapterToUpdate()

        if (chapterToUpdate > 0) {
            if (preferences.askUpdateTrack()) {
                MaterialDialog.Builder(this)
                        .content(getString(R.string.confirm_update_manga_sync, chapterToUpdate))
                        .positiveText(android.R.string.yes)
                        .negativeText(android.R.string.no)
                        .onPositive { _, _ -> presenter.updateTrackLastChapterRead(chapterToUpdate) }
                        .onAny { _, _ -> super.onBackPressed() }
                        .show()
            } else {
                presenter.updateTrackLastChapterRead(chapterToUpdate)
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isFinishing) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (volumeKeysEnabled) {
                        if (event.action == KeyEvent.ACTION_UP) {
                            if (!volumeKeysInverted) viewer?.moveDown() else viewer?.moveUp()
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (volumeKeysEnabled) {
                        if (event.action == KeyEvent.ACTION_UP) {
                            if (!volumeKeysInverted) viewer?.moveUp() else viewer?.moveDown()
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isFinishing) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> viewer?.moveRight()
                KeyEvent.KEYCODE_DPAD_LEFT -> viewer?.moveLeft()
                KeyEvent.KEYCODE_DPAD_DOWN -> viewer?.moveDown()
                KeyEvent.KEYCODE_DPAD_UP -> viewer?.moveUp()
                KeyEvent.KEYCODE_PAGE_DOWN -> viewer?.moveDown()
                KeyEvent.KEYCODE_PAGE_UP -> viewer?.moveUp()
                KeyEvent.KEYCODE_MENU -> toggleMenu()
                else -> return super.onKeyUp(keyCode, event)
            }
        }
        return true
    }

    fun onChapterError(error: Throwable) {
        Timber.e(error)
        finish()
        toast(error.message)
    }

    fun onLongClick(page: Page) {
        MaterialDialog.Builder(this)
                .title(getString(R.string.options))
                .items(R.array.reader_image_options)
                .itemsIds(R.array.reader_image_options_values)
                .itemsCallback { _, _, i, _ ->
                    when (i) {
                        0 -> setImageAsCover(page)
                        1 -> shareImage(page)
                        2 -> presenter.savePage(page)
                    }
                }.show()
    }

    fun onChapterAppendError() {
        // Ignore
    }

    /**
     * Called from the presenter at startup, allowing to prepare the selected reader.
     */
    fun onMangaOpen(manga: Manga) {
        if (viewer == null) {
            viewer = getOrCreateViewer(manga)
        }
        if (viewer is RightToLeftReader && page_seekbar.rotation != 180f) {
            // Invert the seekbar for the right to left reader
            page_seekbar.rotation = 180f
        }
        supportActionBar?.title = manga.title
        please_wait.visibility = View.VISIBLE
        please_wait.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_long))
    }

    fun onChapterReady(chapter: ReaderChapter) {
        please_wait.visibility = View.GONE
        val pages = chapter.pages ?: run { onChapterError(Exception("Null pages")); return }
        if(pages.isEmpty()) {
            onChapterError(Exception("Page list empty!"))
            return
        }
        val activePage = pages.getOrElse(chapter.requestedPage) { pages.first() }

        viewer?.onPageListReady(chapter, activePage)
        setActiveChapter(chapter, activePage.index)
    }

    fun onEnterChapter(chapter: ReaderChapter, currentPage: Int) {
        val activePage = if (currentPage == -1) chapter.pages!!.lastIndex else currentPage
        presenter.setActiveChapter(chapter)
        setActiveChapter(chapter, activePage)
    }

    fun setActiveChapter(chapter: ReaderChapter, currentPage: Int) {
        val numPages = chapter.pages!!.size
        if (page_seekbar.rotation != 180f) {
            right_page_text.text = "$numPages"
            left_page_text.text = "${currentPage + 1}"
        } else {
            left_page_text.text = "$numPages"
            right_page_text.text = "${currentPage + 1}"
        }
        page_seekbar.max = numPages - 1
        page_seekbar.progress = currentPage

        supportActionBar?.subtitle = if (chapter.isRecognizedNumber)
            getString(R.string.chapter_subtitle, decimalFormat.format(chapter.chapter_number.toDouble()))
        else
            chapter.name
    }

    fun onAppendChapter(chapter: ReaderChapter) {
        viewer?.onPageListAppendReady(chapter)
    }

    fun onAdjacentChapters(previous: Chapter?, next: Chapter?) {
        val isInverted = viewer is RightToLeftReader

        // Chapters are inverted for the right to left reader
        val hasRightChapter = (if (isInverted) previous else next) != null
        val hasLeftChapter = (if (isInverted) next else previous) != null

        right_chapter.isEnabled = hasRightChapter
        right_chapter.alpha = if (hasRightChapter) 1f else 0.4f

        left_chapter.isEnabled = hasLeftChapter
        left_chapter.alpha = if (hasLeftChapter) 1f else 0.4f
    }

    private fun getOrCreateViewer(manga: Manga): BaseReader {
        val mangaViewer = if (manga.viewer == 0) preferences.defaultViewer() else manga.viewer

        // Try to reuse the viewer using its tag
        var fragment = supportFragmentManager.findFragmentByTag(manga.viewer.toString()) as? BaseReader
        if (fragment == null) {
            // Create a new viewer
            fragment = when (mangaViewer) {
                RIGHT_TO_LEFT -> RightToLeftReader()
                VERTICAL -> VerticalReader()
                WEBTOON -> WebtoonReader()
                else -> LeftToRightReader()
            }

            supportFragmentManager.beginTransaction().replace(R.id.reader, fragment, manga.viewer.toString()).commit()
        }
        return fragment
    }

    fun onPageChanged(page: Page) {
        presenter.onPageChanged(page)

        val pageNumber = page.number
        val pageCount = page.chapter.pages!!.size
        page_number.text = "$pageNumber/$pageCount"
        if (page_seekbar.rotation != 180f) {
            left_page_text.text = "$pageNumber"
        } else {
            right_page_text.text = "$pageNumber"
        }
        page_seekbar.progress = page.index
    }

    fun gotoPageInCurrentChapter(pageIndex: Int) {
        viewer?.let {
            val activePage = it.getActivePage()
            if (activePage != null) {
                val requestedPage = activePage.chapter.pages!![pageIndex]
                it.setActivePage(requestedPage)
            }
        }
    }

    fun toggleMenu() {
        setMenuVisibility(!menuVisible)
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

    private fun initializeBottomMenu() {
        // Intercept all events in this layout
        reader_menu_bottom.setOnTouchListener { _, _ -> true }

        page_seekbar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    gotoPageInCurrentChapter(value)
                }
            }
        })
    }

    private fun initializeSettings() {
        subscriptions += preferences.rotation().asObservable()
                .subscribe { setRotation(it) }

        subscriptions += preferences.showPageNumber().asObservable()
                .subscribe { setPageNumberVisibility(it) }

        subscriptions += preferences.fullscreen().asObservable()
                .subscribe { setFullscreen(it) }

        subscriptions += preferences.keepScreenOn().asObservable()
                .subscribe { setKeepScreenOn(it) }

        subscriptions += preferences.customBrightness().asObservable()
                .subscribe { setCustomBrightness(it) }

        subscriptions += preferences.colorFilter().asObservable()
                .subscribe { setColorFilter(it) }

        subscriptions += preferences.readerTheme().asObservable()
                .distinctUntilChanged()
                .subscribe { applyTheme(it) }
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

    private fun setFullscreen(enabled: Boolean) {
        systemUi = if (enabled) {
            val level = if (Build.VERSION.SDK_INT >= KITKAT) LEVEL_IMMERSIVE else LEVEL_HIDE_STATUS_BAR
            val flags = FLAG_IMMERSIVE_STICKY or FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES
            SystemUiHelper(this, level, flags)
        } else {
            null
        }
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
                    .sample(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .subscribe { setCustomBrightnessValue(it) }

            subscriptions.add(customBrightnessSubscription)
        } else {
            customBrightnessSubscription?.let { subscriptions.remove(it) }
            setCustomBrightnessValue(0)
        }
    }

    private fun setColorFilter(enabled: Boolean) {
        if (enabled) {
            customFilterColorSubscription = preferences.colorFilterValue().asObservable()
                    .sample(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .subscribe { setColorFilterValue(it) }

            subscriptions.add(customFilterColorSubscription)
        } else {
            customFilterColorSubscription?.let { subscriptions.remove(it) }
            color_overlay.visibility = View.GONE
        }
    }

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    private fun setCustomBrightnessValue(value: Int) {
        // Calculate and set reader brightness.
        val readerBrightness = if (value > 0) {
            value / 100f
        } else if (value < 0) {
            0.01f
        } else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

        // Set black overlay visibility.
        if (value < 0) {
            brightness_overlay.visibility = View.VISIBLE
            val alpha = (Math.abs(value) * 2.56).toInt()
            brightness_overlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        } else {
            brightness_overlay.visibility = View.GONE
        }
    }

    private fun setColorFilterValue(value: Int) {
        color_overlay.visibility = View.VISIBLE
        color_overlay.setBackgroundColor(value)
    }

    private fun applyTheme(theme: Int) {
        readerTheme = theme
        val rootView = window.decorView.rootView
        if (theme == BLACK_THEME) {
            rootView.setBackgroundColor(Color.BLACK)
        } else {
            rootView.setBackgroundColor(Color.WHITE)
        }
    }

    // --> EH
    private fun setEhUtilsVisibility(visible: Boolean) {
        if(visible) {
            eh_utils.visible()
            expand_eh_button.setImageResource(R.drawable.ic_keyboard_arrow_up_white_32dp)
        } else {
            eh_utils.gone()
            expand_eh_button.setImageResource(R.drawable.ic_keyboard_arrow_down_white_32dp)
        }
    }
    // <-- EH

    private fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        menuVisible = visible
        if (visible) {
            systemUi?.show()
            reader_menu.visibility = View.VISIBLE

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbarAnimation.setAnimationListener(object : SimpleAnimationListener() {
                    override fun onAnimationStart(animation: Animation) {
                        // Fix status bar being translucent the first time it's opened.
                        if (Build.VERSION.SDK_INT >= 21) {
                            window.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        }
                    }
                })
                header.startAnimation(toolbarAnimation)

                val bottomMenuAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_bottom)
                // --> EH
//                toolbar.startAnimation(bottomMenuAnimation)
                reader_menu_bottom.startAnimation(bottomMenuAnimation)
                // <-- EH
            }
        } else {
            systemUi?.hide()

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.setAnimationListener(object : SimpleAnimationListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        reader_menu.visibility = View.GONE
                    }
                })
                header.startAnimation(toolbarAnimation)

                val bottomMenuAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_bottom)
                // --> EH
//                toolbar.startAnimation(bottomMenuAnimation)
                reader_menu_bottom.startAnimation(bottomMenuAnimation)
                // <-- EH
            }
        }
    }

    /**
     * Start a share intent that lets user share image
     *
     * @param page page object containing image information.
     */
    private fun shareImage(page: Page) {
        if (page.status != Page.READY)
            return

        var uri = page.uri ?: return
        if (uri.toString().startsWith("file://")) {
            uri = File(uri.toString().substringAfter("file://")).getUriCompat(this)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            type = "image/*"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /**
     * Sets the given page as the cover of the manga.
     *
     * @param page the page containing the image to set as cover.
     */
    private fun setImageAsCover(page: Page) {
        if (page.status != Page.READY)
            return

        MaterialDialog.Builder(this)
                .content(getString(R.string.confirm_set_image_as_cover))
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no)
                .onPositive { _, _ -> presenter.setImageAsCover(page) }
                .show()

    }

    // --> EH
    private fun setupAutoscroll(interval: Float) {
        subscriptions.remove(autoscrollSubscription)
        autoscrollSubscription = null

        if(interval == -1f) return

        val intervalMs = (interval * 1000).roundToLong()
        val sub = Observable.interval(intervalMs, intervalMs, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if(viewer is RightToLeftReader)
                        viewer?.moveLeft()
                    else
                        viewer?.moveRight()
                }

        autoscrollSubscription = sub
        subscriptions += sub
    }
    // <-- EH
}
