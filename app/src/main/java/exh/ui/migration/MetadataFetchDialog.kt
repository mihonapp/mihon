package exh.ui.migration

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.text.Html
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import exh.EXH_SOURCE_ID
import exh.isLewdSource
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class MetadataFetchDialog {

    val db: DatabaseHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    val preferenceHelper: PreferencesHelper by injectLazy()

    fun show(context: Activity) {
        //Too lazy to actually deal with orientation changes
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

        var running = true

        val progressDialog = MaterialDialog.Builder(context)
                .title("Fetching library metadata")
                .content("Preparing library")
                .progress(false, 0, true)
                .negativeText("Stop")
                .onNegative { dialog, which ->
                    running = false
                    dialog.dismiss()
                    notifyMigrationStopped(context)
                }
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .show()

        thread {
            val libraryMangas = db.getLibraryMangas().executeAsBlocking()
                    .filter { isLewdSource(it.source) }
                    .distinctBy { it.id }

            context.runOnUiThread {
                progressDialog.maxProgress = libraryMangas.size
            }

            val mangaWithMissingMetadata = libraryMangas
                    .filterIndexed { index, libraryManga ->
                        if(index % 100 == 0) {
                            context.runOnUiThread {
                                progressDialog.setContent("[Stage 1/2] Scanning for missing metadata...")
                                progressDialog.setProgress(index + 1)
                            }
                        }
                        db.getSearchMetadataForManga(libraryManga.id!!).executeAsBlocking() == null
                    }
                    .toList()

            context.runOnUiThread {
                progressDialog.maxProgress = mangaWithMissingMetadata.size
            }

            //Actual metadata fetch code
            for((i, manga) in mangaWithMissingMetadata.withIndex()) {
                if(!running) break
                context.runOnUiThread {
                    progressDialog.setContent("[Stage 2/2] Processing: ${manga.title}")
                    progressDialog.setProgress(i + 1)
                }
                try {
                    val source = sourceManager.get(manga.source)
                    source?.let {
                        manga.copyFrom(it.fetchMangaDetails(manga).toBlocking().first())
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Could not migrate manga!")
                }
            }

            context.runOnUiThread {
                // Ensure activity still exists before we do anything to the activity
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !context.isDestroyed) {
                    progressDialog.dismiss()

                    //Enable orientation changes again
                    context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

                    if (running) displayMigrationComplete(context)
                }
            }
        }
    }

    fun askMigration(activity: Activity, explicit: Boolean) {
        var extra = ""
        db.getLibraryMangas().asRxSingle().subscribe {
            if(!explicit && it.none { isLewdSource(it.source) }) {
                // Do not open dialog on startup if no manga
                // Also do not check again
                preferenceHelper.migrateLibraryAsked().set(true)
            } else {
                //Not logged in but have ExHentai galleries
                if (!preferenceHelper.enableExhentai().getOrDefault()) {
                    it.find { it.source == EXH_SOURCE_ID }?.let {
                        extra = "<b><font color='red'>If you use ExHentai, please log in first before fetching your library metadata!</font></b><br><br>"
                    }
                }
                activity.runOnUiThread {
                    MaterialDialog.Builder(activity)
                            .title("Fetch library metadata")
                            .content(Html.fromHtml("You need to fetch your library's metadata before tag searching in the library will function.<br><br>" +
                                    "This process may take a long time depending on your library size and will also use up a significant amount of internet bandwidth but can be stopped and started whenever you wish.<br><br>" +
                                    extra +
                                    "This process can be done later if required."))
                            .positiveText("Migrate")
                            .negativeText("Later")
                            .onPositive { _, _ -> show(activity) }
                            .onNegative { _, _ -> adviseMigrationLater(activity) }
                            .onAny { _, _ -> preferenceHelper.migrateLibraryAsked().set(true) }
                            .cancelable(false)
                            .canceledOnTouchOutside(false)
                            .show()
                }
            }
        }

    }

    fun adviseMigrationLater(activity: Activity) {
        MaterialDialog.Builder(activity)
                .title("Metadata fetch canceled")
                .content("Library metadata fetch has been canceled.\n\n" +
                        "You can run this operation later by going to: Settings > Advanced > Migrate library metadata")
                .positiveText("Ok")
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .show()
    }

    fun notifyMigrationStopped(activity: Activity) {
        MaterialDialog.Builder(activity)
                .title("Metadata fetch stopped")
                .content("Library metadata fetch has been stopped.\n\n" +
                        "You can continue this operation later by going to: Settings > Advanced > Migrate library metadata")
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
