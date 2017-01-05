package exh.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.data.source.online.all.EHentai
import exh.metadata.MetadataHelper
import exh.metadata.copyTo
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class MetadataFetchDialog {

    val metadataHelper by lazy { MetadataHelper() }

    val db: DatabaseHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    val preferenceHelper: PreferencesHelper by injectLazy()

    fun show(context: Activity) {
        //Too lazy to actually deal with orientation changes
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

        val progressDialog = MaterialDialog.Builder(context)
                .title("Migrating library")
                .content("Preparing library")
                .progress(false, 0, true)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .show()

        thread {
            db.deleteMangasNotInLibrary().executeAsBlocking()

            val libraryMangas = db.getLibraryMangas()
                    .executeAsBlocking()
                    .filter {
                        it.source <= 2
                        && !metadataHelper.hasMetadata(it.url, it.source == 2)
                    }

            context.runOnUiThread {
                progressDialog.maxProgress = libraryMangas.size
            }

            //Actual metadata fetch code
            libraryMangas.forEachIndexed { i, manga ->
                context.runOnUiThread {
                    progressDialog.setContent("Processing: ${manga.title}")
                    progressDialog.setProgress(i + 1)
                }
                try {
                    val source = sourceManager.get(manga.source)
                    source?.let {
                        it as EHentai
                        manga.copyFrom(it.fetchMangaDetails(manga).toBlocking().first())
                        metadataHelper.fetchMetadata(manga.url, it.exh)?.copyTo(manga)
                    }
                } catch(t: Throwable) {
                    Timber.e(t, "Could not migrate manga!")
                }
            }

            context.runOnUiThread {
                progressDialog.dismiss()

                //Enable orientation changes again
                context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

                displayMigrationComplete(context)
            }
        }
    }

    fun tryAskMigration(activity: Activity) {
        if(preferenceHelper.migrateLibraryAsked().getOrDefault()) return

        askMigration(activity)
    }

    fun askMigration(activity: Activity) {
        MaterialDialog.Builder(activity)
                .title("Migrate library")
                .content("You need to migrate your library before tag searching in the library will function.\n\n" +
                        "This migration may take a long time depending on your library size and will also use up a significant amount of internet bandwidth.\n\n" +
                        "This process can be done later if required.")
                .positiveText("Migrate")
                .negativeText("Later")
                .onPositive { materialDialog, dialogAction -> show(activity) }
                .onNegative { materialDialog, dialogAction -> adviseMigrationLater(activity) }
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .dismissListener {
                    preferenceHelper.migrateLibraryAsked().set(true)
                }.show()
    }

    fun adviseMigrationLater(activity: Activity) {
        MaterialDialog.Builder(activity)
                .title("Migration canceled")
                .content("Library migration has been canceled.\n\n" +
                        "You can run this operation later by going to: Settings > EHentai > Migrate Library")
                .positiveText("Ok")
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .show()
    }

    fun displayMigrationComplete(activity: Activity) {
        MaterialDialog.Builder(activity)
                .title("Migration complete")
                .content("${activity.getString(R.string.app_name)} is now ready for use!")
                .positiveText("Ok")
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .show()
    }
}
