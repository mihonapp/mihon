package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import android.os.Handler
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.f2prateek.rx.preferences.Preference
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.kizitonwose.time.Interval
import com.kizitonwose.time.days
import com.kizitonwose.time.hours
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.toast
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.eh.EHentaiUpdateWorker
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.EHentaiUpdaterStats
import exh.favorites.FavoritesIntroDialog
import exh.favorites.LocalFavoritesStorage
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.nullIfBlank
import exh.uconfig.WarnConfigureDialogController
import exh.ui.login.LoginController
import exh.util.await
import exh.util.trans
import humanize.Humanize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.util.*

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
    private val gson: Gson by injectLazy()
    private val db: DatabaseHelper by injectLazy()

    private fun Preference<*>.reconfigure(): Boolean {
        //Listen for change commit
        asObservable()
                .skip(1) //Skip first as it is emitted immediately
                .take(1) //Only listen for first commit
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeUntilDestroy {
                    //Only listen for first change commit
                    WarnConfigureDialogController.uploadSettings(router)
                }

        //Always return true to save changes
        return true
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "E-Hentai"

        switchPreference {
            title = "Enable ExHentai"
            summaryOff = "Requires login"
            key = PreferenceKeys.eh_enableExHentai
            isPersistent = false
            defaultValue = false
            preferences.enableExhentai()
                    .asObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeUntilDestroy {
                        isChecked = it
                    }

            onChange { newVal ->
                newVal as Boolean
                if(!newVal) {
                    preferences.enableExhentai().set(false)
                    true
                } else {
                    router.pushController(RouterTransaction.with(LoginController())
                            .pushChangeHandler(FadeChangeHandler())
                            .popChangeHandler(FadeChangeHandler()))
                    false
                }
            }
        }

        switchPreference {
            title = "Use Hentai@Home Network"
            summary = "Do you wish to load images through the Hentai@Home Network? Disabling this option will reduce the amount of pages you are able to view"
            key = "enable_hah"
            defaultValue = true

            onChange { preferences.useHentaiAtHome().reconfigure() }
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            title = "Show Japanese titles in search results"
            summaryOn = "Currently showing Japanese titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
            summaryOff = "Currently showing English/Romanized titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
            key = "use_jp_title"
            defaultValue = false

            onChange { preferences.useJapaneseTitle().reconfigure() }
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            title = "Use original images"
            summaryOn = "Currently using original images"
            summaryOff = "Currently using resampled images"
            key = PreferenceKeys.eh_useOrigImages
            defaultValue = false

            onChange { preferences.eh_useOriginalImages().reconfigure() }
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            defaultValue = true
            key = "secure_exh"
            title = "Secure ExHentai/E-Hentai"
            summary = "Use the HTTPS version of ExHentai/E-Hentai."
        }

        listPreference {
            defaultValue = "auto"
            key = "ehentai_quality"
            summary = "The quality of the downloaded images"
            title = "Image quality"
            entries = arrayOf(
                    "Auto",
                    "2400x",
                    "1600x",
                    "1280x",
                    "980x",
                    "780x"
            )
            entryValues = arrayOf(
                    "auto",
                    "ovrs_2400",
                    "ovrs_1600",
                    "high",
                    "med",
                    "low"
            )

            onChange { preferences.imageQuality().reconfigure() }
        }.dependency = PreferenceKeys.eh_enableExHentai

        preferenceCategory {
            title = "Favorites sync"

            switchPreference {
                title = "Disable favorites uploading"
                summary = "Favorites are only downloaded from ExHentai. Any changes to favorites in the app will not be uploaded. Prevents accidental loss of favorites on ExHentai. Note that removals will still be downloaded (if you remove a favorites on ExHentai, it will be removed in the app as well)."
                key = PreferenceKeys.eh_readOnlySync
                defaultValue = false
            }

            preference {
                title = "Show favorites sync notes"
                summary = "Show some information regarding the favorites sync feature"

                onClick {
                    activity?.let {
                        FavoritesIntroDialog().show(it)
                    }
                }
            }

            switchPreference {
                title = "Ignore sync errors when possible"
                summary = "Do not abort immediately when encountering errors during the sync process. Errors will still be displayed when the sync is complete. Can cause loss of favorites in some cases. Useful when syncing large libraries."
                key = PreferenceKeys.eh_lenientSync
                defaultValue = false
            }

            preference {
                title = "Force sync state reset"
                summary = "Performs a full resynchronization on the next sync. Removals will not be synced. All favorites in the app will be re-uploaded to ExHentai and all favorites on ExHentai will be re-downloaded into the app. Useful for repairing sync after sync has been interrupted."

                onClick {
                    activity?.let {
                        MaterialDialog.Builder(it)
                                .title("Are you sure?")
                                .content("Resetting the sync state can cause your next sync to be extremely slow.")
                                .positiveText("Yes")
                                .onPositive { _, _ ->
                                    LocalFavoritesStorage().apply {
                                        getRealm().use {
                                            it.trans {
                                                clearSnapshots(it)
                                            }
                                        }
                                    }
                                    it.toast("Sync state reset", Toast.LENGTH_LONG)
                                }
                                .negativeText("No")
                                .cancelable(false)
                                .show()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            preferenceCategory {
                title = "Gallery update checker"

                intListPreference {
                    key = PreferenceKeys.eh_autoUpdateFrequency
                    title = "Time between update batches"
                    entries = arrayOf("Never update galleries", "1 hour", "2 hours", "3 hours", "6 hours", "12 hours", "24 hours", "48 hours")
                    entryValues = arrayOf("0", "1", "2", "3", "6", "12", "24", "48")
                    defaultValue = "0"

                    preferences.eh_autoUpdateFrequency().asObservable().subscribeUntilDestroy { newVal ->
                        summary = if(newVal == 0) {
                            "${context.getString(R.string.app_name)} will currently never check galleries in your library for updates."
                        } else {
                            "${context.getString(R.string.app_name)} checks/updates galleries in batches. " +
                                    "This means it will wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} galleries," +
                                    " wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} and so on..."
                        }
                    }

                    onChange { newValue ->
                        val interval = (newValue as String).toInt()
                        EHentaiUpdateWorker.scheduleBackground(context, interval)
                        true
                    }
                }

                multiSelectListPreference {
                    key = PreferenceKeys.eh_autoUpdateRestrictions
                    title = "Auto update restrictions"
                    entriesRes = arrayOf(R.string.wifi, R.string.charging)
                    entryValues = arrayOf("wifi", "ac")
                    summaryRes = R.string.pref_library_update_restriction_summary

                    preferences.eh_autoUpdateFrequency().asObservable()
                            .subscribeUntilDestroy { isVisible = it > 0 }

                    onChange {
                        // Post to event looper to allow the preference to be updated.
                        Handler().post { EHentaiUpdateWorker.scheduleBackground(context) }
                        true
                    }
                }

                preference {
                    title = "Show updater statistics"

                    onClick {
                        val progress = MaterialDialog.Builder(context)
                                .progress(true, 0)
                                .content("Collecting statistics...")
                                .cancelable(false)
                                .show()

                        GlobalScope.launch(Dispatchers.IO) {
                            val updateInfo = try {
                                val stats = preferences.eh_autoUpdateStats().getOrDefault().nullIfBlank()?.let {
                                    gson.fromJson<EHentaiUpdaterStats>(it)
                                }

                                val statsText = if (stats != null) {
                                    "The updater last ran ${Humanize.naturalTime(Date(stats.startTime))}, and checked ${stats.updateCount} out of the ${stats.possibleUpdates} galleries that were ready for checking."
                                } else "The updater has not ran yet."

                                val allMeta = db.getFavoriteMangaWithMetadata().await().filter {
                                    it.source == EH_SOURCE_ID || it.source == EXH_SOURCE_ID
                                }.mapNotNull {
                                    db.getFlatMetadataForManga(it.id!!).await()?.raise<EHentaiSearchMetadata>()
                                }.toList()

                                fun metaInRelativeDuration(duration: Interval<*>): Int {
                                    val durationMs = duration.inMilliseconds.longValue
                                    return allMeta.asSequence().filter {
                                        System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                                    }.count()
                                }

                                """
                                    $statsText

                                    Galleries that were checked in the last:
                                    - hour: ${metaInRelativeDuration(1.hours)}
                                    - 6 hours: ${metaInRelativeDuration(6.hours)}
                                    - 12 hours: ${metaInRelativeDuration(12.hours)}
                                    - day: ${metaInRelativeDuration(1.days)}
                                    - 2 days: ${metaInRelativeDuration(2.days)}
                                    - week: ${metaInRelativeDuration(7.days)}
                                    - month: ${metaInRelativeDuration(30.days)}
                                    - year: ${metaInRelativeDuration(365.days)}
                                """.trimIndent()
                            } finally {
                                progress.dismiss()
                            }

                            withContext(Dispatchers.Main) {
                                MaterialDialog.Builder(context)
                                        .title("Gallery updater statistics")
                                        .content(updateInfo)
                                        .positiveText("Ok")
                                        .show()
                            }
                        }
                    }
                }
            }
        }
    }
}
