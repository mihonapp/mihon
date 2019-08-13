package eu.kanade.tachiyomi.ui.main

import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.app.SearchManager
import android.app.usage.UsageStatsManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.graphics.drawable.DrawerArrowDrawable
import android.support.v7.widget.Toolbar
import android.view.ViewGroup
import com.bluelinelabs.conductor.*
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.controller.*
import eu.kanade.tachiyomi.ui.catalogue.CatalogueController
import eu.kanade.tachiyomi.ui.catalogue.global_search.CatalogueSearchController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.extension.ExtensionController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.recent_updates.RecentChaptersController
import eu.kanade.tachiyomi.ui.recently_read.RecentlyReadController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import exh.uconfig.WarnConfigureDialogController
import exh.ui.batchadd.BatchAddController
import exh.ui.lock.LockChangeHandler
import exh.ui.lock.LockController
import exh.ui.lock.lockEnabled
import exh.ui.lock.notifyLockSecurity
import kotlinx.android.synthetic.main.main_activity.*
import uy.kohesive.injekt.injectLazy
import android.text.TextUtils
import android.view.View
import eu.kanade.tachiyomi.util.vibrate
import exh.EXHMigrations
import exh.eh.EHentaiUpdateWorker
import exh.ui.migration.MetadataFetchDialog
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity() {

    private lateinit var router: Router

    val preferences: PreferencesHelper by injectLazy()

    private var drawerArrow: DrawerArrowDrawable? = null

    private var secondaryDrawer: ViewGroup? = null

    private val startScreenId by lazy {
        when (preferences.startScreen()) {
            2 -> R.id.nav_drawer_recently_read
            3 -> R.id.nav_drawer_recent_updates
            else -> R.id.nav_drawer_library
        }
    }

    lateinit var tabAnimator: TabsAnimator

    // Idle-until-urgent
    private var firstPaint = false
    private val iuuQueue = LinkedList<() -> Unit>()

    private fun initWhenIdle(task: () -> Unit) {
        // Avoid sync issues by enforcing main thread
        if(Looper.myLooper() != Looper.getMainLooper())
            throw IllegalStateException("Can only be called on main thread!")

        if(firstPaint) {
            task()
        } else {
            iuuQueue += task
        }
    }

    override fun onResume() {
        super.onResume()

        if(!firstPaint) {
            drawer.postDelayed({
                if(!firstPaint) {
                    firstPaint = true
                    iuuQueue.forEach { it() }
                }
            }, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(when (preferences.theme()) {
            2 -> R.style.Theme_Tachiyomi_Dark
            3 -> R.style.Theme_Tachiyomi_Amoled
            4 -> R.style.Theme_Tachiyomi_DarkBlue
            else -> R.style.Theme_Tachiyomi
        })
        super.onCreate(savedInstanceState)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setContentView(R.layout.main_activity)

        setSupportActionBar(toolbar)

        drawerArrow = DrawerArrowDrawable(this)
        drawerArrow?.color = Color.WHITE
        toolbar.navigationIcon = drawerArrow

        tabAnimator = TabsAnimator(tabs)

        // Set behavior of Navigation drawer
        nav_view.setNavigationItemSelectedListener { item ->
            val id = item.itemId

            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                when (id) {
                    R.id.nav_drawer_library -> setRoot(LibraryController(), id)
                    R.id.nav_drawer_recent_updates -> setRoot(RecentChaptersController(), id)
                    R.id.nav_drawer_recently_read -> setRoot(RecentlyReadController(), id)
                    R.id.nav_drawer_catalogues -> setRoot(CatalogueController(), id)
                    R.id.nav_drawer_extensions -> setRoot(ExtensionController(), id)
                    // --> EXH
                    R.id.nav_drawer_batch_add -> setRoot(BatchAddController(), id)
                    // <-- EHX
                    R.id.nav_drawer_downloads -> {
                        router.pushController(DownloadController().withFadeTransaction())
                    }
                    R.id.nav_drawer_settings -> {
                        router.pushController(SettingsMainController().withFadeTransaction())
                    }
                }
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        val container: ViewGroup = findViewById(R.id.controller_container)

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                setSelectedDrawerItem(startScreenId)
            }
        }

        toolbar.setNavigationOnClickListener {
            if (router.backstackSize == 1) {
                drawer.openDrawer(GravityCompat.START)
            } else {
                onBackPressed()
            }
        }

        router.addChangeListener(object : ControllerChangeHandler.ControllerChangeListener {
            override fun onChangeStarted(to: Controller?, from: Controller?, isPush: Boolean,
                                         container: ViewGroup, handler: ControllerChangeHandler) {

                syncActivityViewWithController(to, from)
            }

            override fun onChangeCompleted(to: Controller?, from: Controller?, isPush: Boolean,
                                           container: ViewGroup, handler: ControllerChangeHandler) {

            }

        })

        // --> EH
        initWhenIdle {
            //Hook long press hamburger menu to lock
            getToolbarNavigationIcon(toolbar)?.setOnLongClickListener {
                if(lockEnabled(preferences)) {
                    doLock(true)
                    vibrate(50) // Notify user of lock
                    true
                } else false
            }
        }

        //Show lock
        if (savedInstanceState == null) {
            if (lockEnabled(preferences)) {
                //Special case first lock
                doLock()

                //Check lock security
                initWhenIdle {
                    notifyLockSecurity(this)
                }
            }
        }
        // <-- EH

        syncActivityViewWithController(router.backstack.lastOrNull()?.controller())

        if (savedInstanceState == null) {
            // Show changelog if needed
            // TODO
//            if (Migrations.upgrade(preferences)) {
//                ChangelogDialogController().showDialog(router)
//            }

            // EXH -->
            // Perform EXH specific migrations
            if(EXHMigrations.upgrade(preferences)) {
                ChangelogDialogController().showDialog(router)
            }

            initWhenIdle {
                // Migrate metadata if empty (EH)
                if(!preferences.migrateLibraryAsked().getOrDefault()) {
                    MetadataFetchDialog().askMigration(this, false)
                }

                // Upload settings
                if(preferences.enableExhentai().getOrDefault()
                        && preferences.eh_showSettingsUploadWarning().getOrDefault())
                    WarnConfigureDialogController.uploadSettings(router)

                // Scheduler uploader job if required
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    EHentaiUpdateWorker.scheduleBackground(this)
                }
            }
            // EXH <--
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        when (intent.action) {
            SHORTCUT_LIBRARY -> setSelectedDrawerItem(R.id.nav_drawer_library)
            SHORTCUT_RECENTLY_UPDATED -> setSelectedDrawerItem(R.id.nav_drawer_recent_updates)
            SHORTCUT_RECENTLY_READ -> setSelectedDrawerItem(R.id.nav_drawer_recently_read)
            SHORTCUT_CATALOGUES -> setSelectedDrawerItem(R.id.nav_drawer_catalogues)
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                router.setRoot(RouterTransaction.with(MangaController(extras)))
            }
            SHORTCUT_DOWNLOADS -> {
                if (router.backstack.none { it.controller() is DownloadController }) {
                    setSelectedDrawerItem(R.id.nav_drawer_downloads)
                }
            }
            Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                //If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                //Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY)
                if (query != null && !query.isEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(CatalogueSearchController(query).withFadeTransaction())
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                if (query != null && !query.isEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(CatalogueSearchController(query, filter).withFadeTransaction())
                }
            }
            else -> return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        nav_view?.setNavigationItemSelectedListener(null)
        toolbar?.setNavigationOnClickListener(null)
    }

    override fun onBackPressed() {
        val backstackSize = router.backstackSize
        if (drawer.isDrawerOpen(GravityCompat.START) || drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawers()
        } else if (backstackSize == 1 && router.getControllerWithTag("$startScreenId") == null) {
            setSelectedDrawerItem(startScreenId)
        } else if (backstackSize == 1 || !router.handleBack()) {
            super.onBackPressed()
        }
    }

    private fun setSelectedDrawerItem(itemId: Int) {
        if (!isFinishing) {
            nav_view.setCheckedItem(itemId)
            nav_view.menu.performIdentifierAction(itemId, 0)
        }
    }

    private fun setRoot(controller: Controller, id: Int) {
        router.setRoot(controller.withFadeTransaction().tag(id.toString()))
    }

    fun getToolbarNavigationIcon(toolbar: Toolbar): View? {
        try {
            //check if contentDescription previously was set
            val hadContentDescription = !TextUtils.isEmpty(toolbar.navigationContentDescription)
            val contentDescription = if (!hadContentDescription) toolbar.navigationContentDescription else "navigationIcon"
            toolbar.navigationContentDescription = contentDescription

            val potentialViews = ArrayList<View>()

            //find the view based on it's content description, set programmatically or with android:contentDescription
            toolbar.findViewsWithText(potentialViews, contentDescription, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)

            //Nav icon is always instantiated at this point because calling setNavigationContentDescription ensures its existence
            val navIcon = potentialViews.firstOrNull()

            //Clear content description if not previously present
            if (!hadContentDescription)
                toolbar.navigationContentDescription = null
            return navIcon
        } catch(t: Throwable) {
            Timber.w(t, "Could not find toolbar nav icon!")
            return null
        }
    }

    private fun syncActivityViewWithController(to: Controller?, from: Controller? = null) {
        if (from is DialogController || to is DialogController) {
            return
        }

        val showHamburger = router.backstackSize == 1
        if (showHamburger) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

        // --> EH
        //Special case and hide drawer arrow for lock controller
        if(to is LockController) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            toolbar.navigationIcon = null
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.navigationIcon = drawerArrow
        }
        // <-- EH

        ObjectAnimator.ofFloat(drawerArrow, "progress", if (showHamburger) 0f else 1f).start()

        if (from is TabbedController) {
            from.cleanupTabs(tabs)
        }
        if (to is TabbedController) {
            tabAnimator.expand()
            to.configureTabs(tabs)
        } else {
            tabAnimator.collapse()
            tabs.setupWithViewPager(null)
        }

        if (from is SecondaryDrawerController) {
            if (secondaryDrawer != null) {
                from.cleanupSecondaryDrawer(drawer)
                drawer.removeView(secondaryDrawer)
                secondaryDrawer = null
            }
        }
        if (to is SecondaryDrawerController) {
            secondaryDrawer = to.createSecondaryDrawer(drawer)?.also { drawer.addView(it) }
        }

        if (to is NoToolbarElevationController) {
            appbar.disableElevation()
        } else {
            appbar.enableElevation()
        }
    }

    // --> EH
    //Lock code
    var willLock = false
    override fun onRestart() {
        super.onRestart()
        if(willLock && lockEnabled()) {
            doLock()
        }

        willLock = false
    }

    override fun onStop() {
        super.onStop()
        tryLock()
    }

    fun tryLock() {
        //Do not double-lock
        if(router.backstack.lastOrNull()?.controller() is LockController)
            return

        //Do not lock if manual lock enabled
        if(preferences.eh_lockManually().getOrDefault())
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mUsageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            // We get usage stats for the last 20 seconds
            val sortedStats =
                    mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                            time - 1000 * 20,
                            time)
                            ?.associateBy {
                                it.lastTimeUsed
                            }?.toSortedMap()
            if(sortedStats != null && sortedStats.isNotEmpty())
                if(sortedStats[sortedStats.lastKey()]?.packageName != packageName)
                    willLock = true
        } else {
            val am = getSystemService(Service.ACTIVITY_SERVICE) as ActivityManager
            val running = am.getRunningTasks(1)[0]
            if (running.topActivity.packageName != packageName) {
                willLock = true
            }
        }
    }

    fun doLock(animate: Boolean = false) {
        router.pushController(RouterTransaction.with(LockController())
                .popChangeHandler(LockChangeHandler(animate)))
    }
    // <-- EH

    companion object {
        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_CATALOGUES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }

}
