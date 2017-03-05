package exh.ui.migration

import android.app.Activity
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.data.backup.BackupManager
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import exh.LEWD_SOURCE_SERIES
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.concurrent.thread

class SourceMigrator {

    val db: DatabaseHelper by injectLazy()

    val prefs: PreferencesHelper by injectLazy()

    val backupManager by lazy {
        BackupManager(db)
    }

    fun perform() {
        db.insertMangas(db.getMangas().executeAsBlocking().map {
            if(it.source < 100) {
                if(it.url.trim('/').startsWith("g/")) {
                    //EH source, move ID
                    it.source += LEWD_SOURCE_SERIES
                }
            } else if(it.source < 200) {
                //Regular source, move ID down
                it.source -= 100
            }
            it
        }).executeAsBlocking()
    }

    fun tryMigrationWithDialog(context: Activity, callback: () -> Unit) {
        if(!prefs.hasPerformedSourceMigration().getOrDefault()) {
            val dialog = MaterialDialog.Builder(context)
                    .title("Migrating galleries")
                    .progress(true, 0)
                    .cancelable(false)
                    .show()

            thread {
                try {
                    context.runOnUiThread {
                        dialog.setContent("Backing up library...")
                    }
                    backupManager.backupToFile(File(context.filesDir, "teh-source-migration-bck.json"))
                    context.runOnUiThread {
                        dialog.setContent("Performing migration...")
                    }
                    perform()
                    context.runOnUiThread {
                        dialog.setContent("Completing migration...")
                    }
                    prefs.hasPerformedSourceMigration().set(true)
                    dialog.dismiss()
                } catch(e: Exception) {
                    Timber.e(e, "Error migrating source IDs!")
                }
                context.runOnUiThread {
                    callback()
                }
            }
        } else {
            callback()
        }
    }
}
