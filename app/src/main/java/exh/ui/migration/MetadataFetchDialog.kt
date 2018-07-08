package exh.ui.migration

import android.app.Activity
import android.content.pm.ActivityInfo
import android.text.Html
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import exh.isExSource
import exh.isLewdSource
import exh.metadata.queryMetadataFromManga
import exh.util.defRealm
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

        val progressDialog = MaterialDialog.Builder(context)
                .title("Fetching library metadata")
                .content("Preparing library")
                .progress(false, 0, true)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .show()

        thread {
            defRealm { realm ->
                db.deleteMangasNotInLibrary().executeAsBlocking()

                val libraryMangas = db.getLibraryMangas()
                        .executeAsBlocking()
                        .filter {
                            isLewdSource(it.source)
                                    && realm.queryMetadataFromManga(it).findFirst() == null
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
                            manga.copyFrom(it.fetchMangaDetails(manga).toBlocking().first())
                            realm.queryMetadataFromManga(manga).findFirst()?.copyTo(manga)
                        }
                    } catch (t: Throwable) {
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
    }

    fun askMigration(activity: Activity, explicit: Boolean) {
        var extra = ""
        db.getLibraryMangas().asRxSingle().subscribe {
            if(!explicit && it.none { isLewdSource(it.source) }) {
                //Do not open dialog on startup if no manga
            } else {
                //Not logged in but have ExHentai galleries
                if (!preferenceHelper.enableExhentai().getOrDefault()) {
                    it.find { isExSource(it.source) }?.let {
                        extra = "<b><font color='red'>If you use ExHentai, please log in first before fetching your library metadata!</font></b><br><br>"
                    }
                }
                activity.runOnUiThread {
                    MaterialDialog.Builder(activity)
                            .title("Fetch library metadata")
                            .content(Html.fromHtml("You need to fetch your library's metadata before tag searching in the library will function.<br><br>" +
                                    "This process may take a long time depending on your library size and will also use up a significant amount of internet bandwidth.<br><br>" +
                                    extra +
                                    "This process can be done later if required."))
                            .positiveText("Migrate")
                            .negativeText("Later")
                            .onPositive { _, _ -> show(activity) }
                            .onNegative({ _, _ -> adviseMigrationLater(activity) })
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
