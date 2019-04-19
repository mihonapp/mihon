package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.support.v7.preference.PreferenceScreen
import android.text.Html
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager.Companion.DELEGATED_SOURCES
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.util.toast
import exh.debug.SettingsDebugController
import exh.log.EHLogLevel
import exh.ui.migration.MetadataFetchDialog
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

class SettingsAdvancedController : SettingsController() {

    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_advanced

        preference {
            key = CLEAR_CACHE_KEY
            titleRes = R.string.pref_clear_chapter_cache
            summary = context.getString(R.string.used_cache, chapterCache.readableSize)

            onClick { clearChapterCache() }
        }
        preference {
            titleRes = R.string.pref_clear_cookies

            onClick {
                network.cookieManager.removeAll()
                activity?.toast(R.string.cookies_cleared)
            }
        }
        preference {
            titleRes = R.string.pref_clear_database
            summaryRes = R.string.pref_clear_database_summary

            onClick {
                val ctrl = ClearDatabaseDialogController()
                ctrl.targetController = this@SettingsAdvancedController
                ctrl.showDialog(router)
            }
        }
        preference {
            titleRes = R.string.pref_refresh_library_metadata
            summaryRes = R.string.pref_refresh_library_metadata_summary

            onClick { LibraryUpdateService.start(context, target = Target.DETAILS) }
        }
        preference {
            titleRes = R.string.pref_refresh_library_tracking
            summaryRes = R.string.pref_refresh_library_tracking_summary

            onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
        }

        // --> EXH
        preferenceCategory {
            title = "Gallery metadata"
            isPersistent = false

            preference {
                title = "Migrate library metadata"
                isPersistent = false
                key = "ex_migrate_library"
                summary = "Fetch the library metadata to enable tag searching in the library. This button will be visible even if you have already fetched the metadata"

                onClick {
                    activity?.let {
                        MetadataFetchDialog().askMigration(it, true)
                    }
                }
            }

            preference {
                title = "Clear library metadata"
                isPersistent = false
                key = "ex_clear_metadata"
                summary = "Clear all library metadata. Disables tag searching in the library"

                onClick {
                    db.inTransaction {
                        db.deleteAllSearchMetadata().executeAsBlocking()
                        db.deleteAllSearchTags().executeAsBlocking()
                        db.deleteAllSearchTitle().executeAsBlocking()
                    }

                    context.toast("Library metadata cleared!")
                }
            }
        }
        preferenceCategory {
            title = "Developer tools"
            isPersistent = false

            switchPreference {
                title = "Enable delegated sources"
                key = PreferenceKeys.eh_delegateSources
                defaultValue = true
                summary = "Apply ${context.getString(R.string.app_name)} enhancements to the following sources if they are installed: ${DELEGATED_SOURCES.values.map { it.sourceName }.distinct().joinToString()}"
            }

            intListPreference {
                key = PreferenceKeys.eh_logLevel
                title = "Log level"

                entries = EHLogLevel.values().map {
                    "${it.name.toLowerCase().capitalize()} (${it.description})"
                }.toTypedArray()
                entryValues = EHLogLevel.values().mapIndexed { index, _ -> "$index" }.toTypedArray()
                defaultValue = "0"

                summary = "Changing this can impact app performance. Force-restart app after changing. Current value: %s"
            }

            switchPreference {
                title = "Enable source blacklist"
                key = PreferenceKeys.eh_enableSourceBlacklist
                defaultValue = true
                summary = "Hide extensions/sources that are incompatible with ${context.getString(R.string.app_name)}. Force-restart app after changing."
            }

            preference {
                title = "Open debug menu"
                summary = Html.fromHtml("DO NOT TOUCH THIS MENU UNLESS YOU KNOW WHAT YOU ARE DOING! <font color='red'>IT CAN CORRUPT YOUR LIBRARY!</font>")
                onClick { router.pushController(SettingsDebugController().withFadeTransaction()) }
            }
        }
        // <-- EXH
    }

    private fun clearChapterCache() {
        if (activity == null) return
        val files = chapterCache.cacheDir.listFiles() ?: return

        var deletedFiles = 0

        val ctrl = DeletingFilesDialogController()
        ctrl.total = files.size
        ctrl.showDialog(router)

        Observable.defer { Observable.from(files) }
                .doOnNext { file ->
                    if (chapterCache.removeFileFromCache(file.name)) {
                        deletedFiles++
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ctrl.setProgress(deletedFiles)
                }, {
                    activity?.toast(R.string.cache_delete_error)
                }, {
                    ctrl.finish()
                    activity?.toast(resources?.getString(R.string.cache_deleted, deletedFiles))
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                            resources?.getString(R.string.used_cache, chapterCache.readableSize)
                })
    }

    class DeletingFilesDialogController : DialogController() {

        var total = 0

        private var materialDialog: MaterialDialog? = null

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog.Builder(activity!!)
                    .title(R.string.deleting)
                    .progress(false, total, true)
                    .cancelable(false)
                    .build()
                    .also { materialDialog = it }
        }

        override fun onDestroyView(view: View) {
            super.onDestroyView(view)
            materialDialog = null
        }

        override fun onRestoreInstanceState(savedInstanceState: Bundle) {
            super.onRestoreInstanceState(savedInstanceState)
            finish()
        }

        fun setProgress(deletedFiles: Int) {
            materialDialog?.setProgress(deletedFiles)
        }

        fun finish() {
            router.popController(this)
        }
    }

    class ClearDatabaseDialogController : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog.Builder(activity!!)
                    .content(R.string.clear_database_confirmation)
                    .positiveText(android.R.string.yes)
                    .negativeText(android.R.string.no)
                    .onPositive { _, _ ->
                        (targetController as? SettingsAdvancedController)?.clearDatabase()
                    }
                    .build()
        }
    }

    private fun clearDatabase() {
        // Avoid weird behavior by going back to the library.
        val newBackstack = listOf(RouterTransaction.with(LibraryController())) +
                router.backstack.drop(1)

        router.setBackstack(newBackstack, FadeChangeHandler())

        db.deleteMangasNotInLibrary().executeAsBlocking()
        db.deleteHistoryNoLastRead().executeAsBlocking()
        activity?.toast(R.string.clear_database_completed)
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
    }
}
