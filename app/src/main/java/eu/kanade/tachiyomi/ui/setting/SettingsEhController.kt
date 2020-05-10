package eu.kanade.tachiyomi.ui.setting

import android.os.Handler
import android.widget.Toast
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.kizitonwose.time.Interval
import com.kizitonwose.time.days
import com.kizitonwose.time.hours
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.system.toast
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
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
    private val gson: Gson by injectLazy()
    private val db: DatabaseHelper by injectLazy()

    private fun Preference<*>.reconfigure(): Boolean {
        // Listen for change commit
        asFlow()
            .take(1) // Only listen for first commit
            .onEach {
                // Only listen for first change commit
                WarnConfigureDialogController.uploadSettings(router)
            }
            .launchIn(scope)

        // Always return true to save changes
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
                .asFlow()
                .onEach {
                    isChecked = it
                }
                .launchIn(scope)

            onChange { newVal ->
                newVal as Boolean
                if (!newVal) {
                    preferences.enableExhentai().set(false)
                    true
                } else {
                    router.pushController(
                        RouterTransaction.with(LoginController())
                            .pushChangeHandler(FadeChangeHandler())
                            .popChangeHandler(FadeChangeHandler())
                    )
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
                    activity?.let { activity ->
                        MaterialDialog(activity)
                            .title(R.string.eh_force_sync_reset_title)
                            .message(R.string.eh_force_sync_reset_message)
                            .positiveButton(android.R.string.yes) {
                                LocalFavoritesStorage().apply {
                                    getRealm().use {
                                        it.trans {
                                            clearSnapshots(it)
                                        }
                                    }
                                }
                                activity.toast("Sync state reset", Toast.LENGTH_LONG)
                            }
                            .negativeButton(android.R.string.no)
                            .cancelable(false)
                            .show()
                    }
                }
            }
        }

        preferenceCategory {
            title = "Gallery update checker"

            intListPreference {
                key = PreferenceKeys.eh_autoUpdateFrequency
                title = "Time between update batches"
                entries = arrayOf(
                    "Never update galleries",
                    "1 hour",
                    "2 hours",
                    "3 hours",
                    "6 hours",
                    "12 hours",
                    "24 hours",
                    "48 hours"
                )
                entryValues = arrayOf("0", "1", "2", "3", "6", "12", "24", "48")
                defaultValue = "0"

                preferences.eh_autoUpdateFrequency().asFlow()
                    .onEach { newVal ->
                        summary = if (newVal == 0) {
                            "${context.getString(R.string.app_name)} will currently never check galleries in your library for updates."
                        } else {
                            "${context.getString(R.string.app_name)} checks/updates galleries in batches. " +
                                "This means it will wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} galleries," +
                                " wait $newVal hour(s), check ${EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION} and so on..."
                        }
                    }
                    .launchIn(scope)

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

                preferences.eh_autoUpdateFrequency().asFlow()
                    .onEach { isVisible = it > 0 }
                    .launchIn(scope)

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    Handler().post { EHentaiUpdateWorker.scheduleBackground(context) }
                    true
                }
            }

            preference {
                title = "Show updater statistics"

                onClick {
                    val progress = MaterialDialog(context)
                        .message(R.string.eh_show_update_statistics_dialog)
                        .cancelable(false)
                    progress.show()

                    GlobalScope.launch(Dispatchers.IO) {
                        val updateInfo = try {
                            val stats =
                                preferences.eh_autoUpdateStats().get().nullIfBlank()?.let {
                                    gson.fromJson<EHentaiUpdaterStats>(it)
                                }

                            val statsText = if (stats != null) {
                                "The updater last ran ${Humanize.naturalTime(Date(stats.startTime))}, and checked ${stats.updateCount} out of the ${stats.possibleUpdates} galleries that were ready for checking."
                            } else "The updater has not ran yet."

                            val allMeta = db.getFavoriteMangaWithMetadata().await().filter {
                                it.source == EH_SOURCE_ID || it.source == EXH_SOURCE_ID
                            }.mapNotNull {
                                db.getFlatMetadataForManga(it.id!!).await()
                                    ?.raise<EHentaiSearchMetadata>()
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
                            MaterialDialog(context)
                                .title(text = "Gallery updater statistics")
                                .message(text = updateInfo)
                                .positiveButton(android.R.string.ok)
                                .show()
                        }
                    }
                }
            }
        }
    }
}
