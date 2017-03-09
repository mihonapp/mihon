package eu.kanade.tachiyomi.ui.main

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.TaskStackBuilder
import android.support.v4.view.GravityCompat
import android.view.MenuItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.backup.BackupFragment
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.catalogue.CatalogueFragment
import eu.kanade.tachiyomi.ui.download.DownloadActivity
import eu.kanade.tachiyomi.ui.latest_updates.LatestUpdatesFragment
import eu.kanade.tachiyomi.ui.library.LibraryFragment
import eu.kanade.tachiyomi.ui.recent_updates.RecentChaptersFragment
import eu.kanade.tachiyomi.ui.recently_read.RecentlyReadFragment
import eu.kanade.tachiyomi.ui.setting.SettingsActivity
import exh.ui.batchadd.BatchAddFragment
import exh.ui.lock.lockEnabled
import exh.ui.lock.notifyLockSecurity
import exh.ui.lock.showLockActivity
import exh.ui.migration.LibraryMigrationManager
import exh.ui.migration.SourceMigrator
import exh.ui.migration.UrlMigrator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar.*
import rx.Subscription
import uy.kohesive.injekt.injectLazy

class MainActivity : BaseActivity() {

    val preferences: PreferencesHelper by injectLazy()

    var finishSubscription: Subscription? = null

    var dismissQueue = mutableListOf<DialogInterface>()

    private val startScreenId by lazy {
        when (preferences.startScreen()) {
            1 -> R.id.nav_drawer_library
            2 -> R.id.nav_drawer_recently_read
            3 -> R.id.nav_drawer_recent_updates
            else -> R.id.nav_drawer_library
        }
    }

    override fun onCreate(savedState: Bundle?) {
        setAppTheme()
        super.onCreate(savedState)

        // Do not let the launcher create a new activity
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            finish()
            return
        }

        // Inflate activity_main.xml.
        setContentView(R.layout.activity_main)

        // Handle Toolbar
        setupToolbar(toolbar, backNavigation = false)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)

        // Set behavior of Navigation drawer
        nav_view.setNavigationItemSelectedListener { item ->
            // Make information view invisible
            empty_view.hide()

            val id = item.itemId

            val oldFragment = supportFragmentManager.findFragmentById(R.id.frame_container)
            if (oldFragment == null || oldFragment.tag.toInt() != id) {
                when (id) {
                    R.id.nav_drawer_library -> setFragment(LibraryFragment.newInstance(), id)
                    R.id.nav_drawer_recent_updates -> setFragment(RecentChaptersFragment.newInstance(), id)
                    R.id.nav_drawer_recently_read -> setFragment(RecentlyReadFragment.newInstance(), id)
                    R.id.nav_drawer_catalogues -> setFragment(CatalogueFragment.newInstance(), id)
                    R.id.nav_drawer_latest_updates -> setFragment(LatestUpdatesFragment.newInstance(), id)
                    R.id.nav_drawer_batch_add -> setFragment(BatchAddFragment.newInstance(), id)
                    R.id.nav_drawer_downloads -> startActivity(Intent(this, DownloadActivity::class.java))
                    R.id.nav_drawer_settings -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivityForResult(intent, REQUEST_OPEN_SETTINGS)
                    }
                    R.id.nav_drawer_backup -> setFragment(BackupFragment.newInstance(), id)
                }
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        if (savedState == null) {
            //Show lock
            val lockEnabled = lockEnabled(preferences)
            if(lockEnabled)
                showLockActivity(this)

            //Perform source migration
            SourceMigrator().tryMigrationWithDialog(this, {
                // Set start screen
                try {
                    setSelectedDrawerItem(startScreenId)
                } catch(e: Exception) {}

                // Show changelog if needed
                ChangelogDialogFragment.show(this, preferences, supportFragmentManager)

                // Migrate library if needed
                LibraryMigrationManager(this, dismissQueue).askMigrationIfNecessary()

                //Last part of migration requires finishing this activity
                finishSubscription?.unsubscribe()
                preferences.finishMainActivity().set(false)
                finishSubscription = preferences.finishMainActivity().asObservable().subscribe {
                    if (it)
                        finish()
                }

                //Migrate URLs if necessary
                UrlMigrator().tryMigration()

                //Check lock security
                if(lockEnabled)
                    notifyLockSecurity(this)
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        finishSubscription?.unsubscribe()
        preferences.finishMainActivity().set(false)
        dismissQueue.forEach { it.dismiss() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> drawer.openDrawer(GravityCompat.START)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_container)
        if (drawer.isDrawerOpen(GravityCompat.START) || drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawers()
        } else if (fragment != null && fragment.tag.toInt() != startScreenId) {
            if (resumed) {
                setSelectedDrawerItem(startScreenId)
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_OPEN_SETTINGS && resultCode != 0) {
            if (resultCode and SettingsActivity.FLAG_DATABASE_CLEARED != 0) {
                // If database is cleared avoid undefined behavior by recreating the stack.
                TaskStackBuilder.create(this)
                        .addNextIntent(Intent(this, MainActivity::class.java))
                        .startActivities()
            } else if (resultCode and SettingsActivity.FLAG_THEME_CHANGED != 0) {
                // Delay activity recreation to avoid fragment leaks.
                nav_view.post { recreate() }
            } else if (resultCode and SettingsActivity.FLAG_LANG_CHANGED != 0) {
                nav_view.post { recreate() }
            } else if (resultCode and SettingsActivity.FLAG_EH_RECREATE != 0) {
                TaskStackBuilder.create(this)
                        .addNextIntent(Intent(this, MainActivity::class.java))
                        .startActivities()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setSelectedDrawerItem(itemId: Int, triggerAction: Boolean = true) {
        nav_view.setCheckedItem(itemId)
        if (triggerAction) {
            nav_view.menu.performIdentifierAction(itemId, 0)
        }
    }

    private fun setFragment(fragment: Fragment, itemId: Int) {
        supportFragmentManager.beginTransaction()
                .replace(R.id.frame_container, fragment, "$itemId")
                .commit()
    }

    fun updateEmptyView(show: Boolean, textResource: Int, drawable: Int) {
        if (show) empty_view.show(drawable, textResource) else empty_view.hide()
    }

    companion object {
        private const val REQUEST_OPEN_SETTINGS = 200
        const val FINALIZE_MIGRATION = "finalize_migration"
    }
}
