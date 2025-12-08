package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.MetadataUpdateJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsAdvancedScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_onboarding_guide),
                onClick = { navigator.push(OnboardingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_manage_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            ),
            getBackgroundActivityGroup(),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(libraryPreferences = libraryPreferences),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(basePreferences = basePreferences),
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        Injekt.get<DownloadCache>().invalidateCache()
                        context.toast(MR.strings.download_cache_invalidated)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_database),
                    subtitle = stringResource(MR.strings.pref_clear_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { MetadataUpdateJob.startNow(context) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<ResetViewerFlags>().await()
                            withUIContext {
                                val message = if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles(),
                    title = stringResource(MR.strings.pref_update_library_manga_titles),
                    subtitle = stringResource(MR.strings.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames(),
                    title = stringResource(MR.strings.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(MR.strings.pref_disallow_non_ascii_filenames_details),
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = basePreferences.hardwareBitmapThreshold(),
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(MR.strings.pref_hardware_bitmap_threshold_default, option)
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(MR.strings.pref_hardware_bitmap_threshold_summary, options[value].orEmpty())
                    },
                    enabled = !ImageUtil.HARDWARE_BITMAP_UNSUPPORTED &&
                        GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.alwaysDecodeLongStripWithSSIV(),
                    title = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_2),
                    subtitle = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_display_profile),
                    subtitle = basePreferences.displayProfile().get(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }
        val trustExtension = remember { Injekt.get<TrustExtension>() }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_shizuku)) },
                text = { Text(text = stringResource(MR.strings.ext_installer_shizuku_unavailable_dialog)) },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = extensionInstallerPref,
                    entries = extensionInstallerPref.entries
                        .filter {
                            // TODO: allow private option in stable versions once URL handling is more fleshed out
                            if (isReleaseBuildType) {
                                it != BasePreferences.ExtensionInstaller.PRIVATE
                            } else {
                                true
                            }
                        }
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.ext_installer_pref),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = {
                        trustExtension.revokeAll()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }
}
