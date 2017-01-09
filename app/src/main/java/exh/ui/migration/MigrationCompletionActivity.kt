package exh.ui.migration

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ShareCompat
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.android.synthetic.main.toolbar.*
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

/**
 * Read backups directly from another Tachiyomi app
 */

class MigrationCompletionActivity : BaseActivity() {

    private val backupManager by lazy { BackupManager(Injekt.get()) }

    private val preferenceManager: PreferencesHelper by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        setAppTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.eh_activity_finish_migration)

        setup()

        setupToolbar(toolbar, backNavigation = false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    fun setup() {
        try {
            val sc = ShareCompat.IntentReader.from(this)
            if(sc.isShareIntent) {
                //Try to restore backup
                thread {
                    //Finish old MainActivity
                    preferenceManager.finishMainActivity().set(true)
                    try {
                        backupManager.restoreFromStream(contentResolver.openInputStream(sc.stream))
                    } catch(t: Throwable) {
                        Timber.e(t, "Failed to restore manga/galleries!")
                        migrationError("Failed to restore manga/galleries!")
                        return@thread
                    }

                    //Migrate urls
                    UrlMigrator().perform()

                    //Go back to MainActivity
                    //Set final steps
                    preferenceManager.migrationStatus().set(MigrationStatus.FINALIZE_MIGRATION)
                    //Wait for MainActivity to finish
                    Thread.sleep(1000)
                    //Start new MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra(MainActivity.Companion.FINALIZE_MIGRATION, true)
                    finish()
                    startActivity(intent)
                }
            }
        } catch(t: Throwable) {
            Timber.e(t, "Failed to migrate manga!")
            migrationError("An unknown error occurred during migration!")
        }
    }

    fun migrationError(message: String) {
        runOnUiThread {
            MaterialDialog.Builder(this)
                    .title("Migration error")
                    .content(message)
                    .positiveText("Ok")
                    .cancelable(false)
                    .canceledOnTouchOutside(false)
                    .dismissListener { finish() }
                    .show()
        }
    }

    override fun onBackPressed() {
        //Do not allow finishing this activity
    }
}

