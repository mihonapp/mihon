package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDevFlavor
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import rikka.sui.Sui
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAdvancedController(
    private val mangaRepository: MangaRepository = Injekt.get(),
) : SettingsController() {

    private val network: NetworkHelper by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()
    private val trackManager: TrackManager by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_advanced

        if (isDevFlavor.not()) {
            switchPreference {
                key = "acra.enable"
                titleRes = R.string.pref_enable_acra
                summaryRes = R.string.pref_acra_summary
                defaultValue = true
            }
        }

        preference {
            key = "dump_crash_logs"
            titleRes = R.string.pref_dump_crash_logs
            summaryRes = R.string.pref_dump_crash_logs_summary

            onClick {
                CrashLogUtil(context).dumpLogs()
            }
        }

        switchPreference {
            key = Keys.verboseLogging
            titleRes = R.string.pref_verbose_logging
            summaryRes = R.string.pref_verbose_logging_summary
            defaultValue = isDevFlavor

            onChange {
                activity?.toast(R.string.requires_app_restart)
                true
            }
        }

        preferenceCategory {
            titleRes = R.string.label_background_activity

            preference {
                key = "pref_disable_battery_optimization"
                titleRes = R.string.pref_disable_battery_optimization
                summaryRes = R.string.pref_disable_battery_optimization_summary

                onClick {
                    val packageName: String = context.packageName
                    if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(R.string.battery_optimization_setting_activity_not_found)
                        }
                    } else {
                        context.toast(R.string.battery_optimization_disabled)
                    }
                }
            }

            preference {
                key = "pref_dont_kill_my_app"
                title = "Don't kill my app!"
                summaryRes = R.string.about_dont_kill_my_app

                onClick {
                    openInBrowser("https://dontkillmyapp.com/")
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_data

            preference {
                key = CLEAR_CACHE_KEY
                titleRes = R.string.pref_clear_chapter_cache
                summary = context.getString(R.string.used_cache, chapterCache.readableSize)

                onClick { clearChapterCache() }
            }
            switchPreference {
                key = Keys.autoClearChapterCache
                titleRes = R.string.pref_auto_clear_chapter_cache
                defaultValue = false
            }
            preference {
                key = "pref_clear_database"
                titleRes = R.string.pref_clear_database
                summaryRes = R.string.pref_clear_database_summary

                onClick {
                    router.pushController(ClearDatabaseController())
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_network

            preference {
                key = "pref_clear_cookies"
                titleRes = R.string.pref_clear_cookies

                onClick {
                    network.cookieManager.removeAll()
                    activity?.toast(R.string.cookies_cleared)
                }
            }
            preference {
                key = "pref_clear_webview_data"
                titleRes = R.string.pref_clear_webview_data

                onClick { clearWebViewData() }
            }
            intListPreference {
                key = Keys.dohProvider
                titleRes = R.string.pref_dns_over_https
                entries = arrayOf(
                    context.getString(R.string.disabled),
                    "Cloudflare",
                    "Google",
                    "AdGuard",
                    "Quad9",
                )
                entryValues = arrayOf(
                    "-1",
                    PREF_DOH_CLOUDFLARE.toString(),
                    PREF_DOH_GOOGLE.toString(),
                    PREF_DOH_ADGUARD.toString(),
                    PREF_DOH_QUAD9.toString(),
                )
                defaultValue = "-1"
                summary = "%s"

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_library

            preference {
                key = "pref_refresh_library_covers"
                titleRes = R.string.pref_refresh_library_covers

                onClick { LibraryUpdateService.start(context, target = Target.COVERS) }
            }
            if (trackManager.hasLoggedServices()) {
                preference {
                    key = "pref_refresh_library_tracking"
                    titleRes = R.string.pref_refresh_library_tracking
                    summaryRes = R.string.pref_refresh_library_tracking_summary

                    onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
                }
            }
            preference {
                key = "pref_reset_viewer_flags"
                titleRes = R.string.pref_reset_viewer_flags
                summaryRes = R.string.pref_reset_viewer_flags_summary

                onClick { resetViewerFlags() }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_extensions

            listPreference {
                bindTo(preferences.extensionInstaller())
                titleRes = R.string.ext_installer_pref
                summary = "%s"

                // PackageInstaller doesn't work on MIUI properly for non-allowlisted apps
                val values = if (DeviceUtil.isMiui) {
                    PreferenceValues.ExtensionInstaller.values()
                        .filter { it != PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER }
                } else {
                    PreferenceValues.ExtensionInstaller.values().toList()
                }

                entriesRes = values.map { it.titleResId }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray()

                onChange {
                    if (it == PreferenceValues.ExtensionInstaller.SHIZUKU.name &&
                        !(context.isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui())
                    ) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.ext_installer_shizuku)
                            .setMessage(R.string.ext_installer_shizuku_unavailable_dialog)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                openInBrowser("https://shizuku.rikka.app/download")
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        false
                    } else {
                        true
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            listPreference {
                bindTo(preferences.tabletUiMode())
                titleRes = R.string.pref_tablet_ui_mode
                summary = "%s"
                entriesRes = PreferenceValues.TabletUiMode.values().map { it.titleResId }.toTypedArray()
                entryValues = PreferenceValues.TabletUiMode.values().map { it.name }.toTypedArray()

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }
    }

    private fun clearChapterCache() {
        val activity = activity ?: return
        launchIO {
            try {
                val deletedFiles = chapterCache.clear()
                withUIContext {
                    activity.toast(resources?.getString(R.string.cache_deleted, deletedFiles))
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_cache, chapterCache.readableSize)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                withUIContext { activity.toast(R.string.cache_delete_error) }
            }
        }
    }

    private fun clearWebViewData() {
        val activity = activity ?: return
        try {
            val webview = WebView(activity)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            activity.toast(R.string.webview_data_deleted)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            activity.toast(R.string.cache_delete_error)
        }
    }

    private fun resetViewerFlags() {
        val activity = activity ?: return
        launchIO {
            val success = mangaRepository.resetViewerFlags()
            withUIContext {
                val message = if (success) {
                    R.string.pref_reset_viewer_flags_success
                } else {
                    R.string.pref_reset_viewer_flags_error
                }
                activity.toast(message)
            }
        }
    }
}

private const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
