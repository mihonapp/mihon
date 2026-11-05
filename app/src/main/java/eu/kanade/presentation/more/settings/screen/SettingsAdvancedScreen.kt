package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.presentation.core.components.material.padding
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
import eu.kanade.domain.translation.AiCapability
import eu.kanade.domain.translation.AiModelCatalog
import eu.kanade.domain.translation.AiProvider
import eu.kanade.domain.translation.TranslationOptions
import eu.kanade.domain.translation.TranslationPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.data.library.MetadataUpdateWorker
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
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.core.common.i18n.stringResource as getContextString
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
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

        val graph = remember { context.appGraph }
        val basePreferences = remember { graph.basePreferences }
        val networkPreferences = remember { graph.networkPreferences }
        val libraryPreferences = remember { graph.libraryPreferences }
        val translationPreferences = remember { graph.translationPreferences }
        val crashLogUtil = remember { graph.crashLogUtil }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        crashLogUtil.dumpLogs()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging,
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
            getTranslationGroup(
                translationPreferences = translationPreferences,
                modelDiscovery = graph.aiModelDiscovery,
            ),
            getExtensionsGroup(basePreferences = basePreferences),
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = listOf(
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
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        context.appGraph.downloadCache.invalidateCache()
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
        val networkHelper = remember { context.appGraph.networkHelper }
        val scope = rememberCoroutineScope()

        val userAgentPref = networkPreferences.defaultUserAgent
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = listOf(
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
                            scope.launch(Dispatchers.IO) {
                                context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                                withContext(Dispatchers.Main) { context.toast(MR.strings.webview_data_deleted) }
                            }
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider,
                    entries = mapOf(
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
                    visible = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
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
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { scope.launch { MetadataUpdateWorker.startNow(context.workManager) } },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launch {
                            val success = context.appGraph.resetViewerFlags.await()
                            val message = if (success) {
                                MR.strings.pref_reset_viewer_flags_success
                            } else {
                                MR.strings.pref_reset_viewer_flags_error
                            }
                            context.toast(message)
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles,
                    title = stringResource(MR.strings.pref_update_library_manga_titles),
                    subtitle = stringResource(MR.strings.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames,
                    title = stringResource(MR.strings.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(MR.strings.pref_disallow_non_ascii_filenames_details),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.enableChapterNameHash,
                    title = stringResource(MR.strings.pref_enable_chapter_name_hash),
                    subtitle = stringResource(MR.strings.pref_enable_chapter_name_hash_details),
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.highQualityRenderer,
                    title = stringResource(MR.strings.pref_high_quality_renderer),
                ),
            ),
        )
    }

    @Composable
    private fun getTranslationGroup(
        translationPreferences: TranslationPreferences,
        modelDiscovery: eu.kanade.tachiyomi.data.translation.AiModelDiscovery,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val enabledPref = remember { translationPreferences.enabled() }
        val providerPref = remember { translationPreferences.provider() }
        val targetLanguagePref = remember { translationPreferences.targetLanguage() }
        val targetLanguage by targetLanguagePref.collectAsState()
        val arabicStylePref = remember { translationPreferences.arabicStyle() }
        val isTargetArabic = TranslationOptions.isArabic(targetLanguage) ||
            java.util.Locale.getDefault().language == "ar"
        val modePref = remember { translationPreferences.mode() }
        val sfxPref = remember { translationPreferences.translateSfx() }
        val delayPref = remember { translationPreferences.requestDelaySeconds() }
        val fontPref = remember { translationPreferences.fontStyle() }
        val customFontPathPref = remember { translationPreferences.customFontPath() }
        val fontStyle by fontPref.collectAsState()
        val customFontPath by customFontPathPref.collectAsState()
        val textSizePref = remember { translationPreferences.textSizePercent() }
        val placementPref = remember { translationPreferences.placement() }
        val saveLogPref = remember { translationPreferences.saveLog() }

        val fontPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val fontsDir = java.io.File(context.filesDir, "custom_fonts").apply { mkdirs() }
                        val destFile = java.io.File(fontsDir, "custom_font.ttf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        customFontPathPref.set(destFile.absolutePath)
                        eu.kanade.tachiyomi.data.translation.overlay.PageOverlayFontManager.customFontPath = destFile.absolutePath
                        withContext(Dispatchers.Main) {
                            context.toast(context.getContextString(MR.strings.pref_translation_custom_font_selected, destFile.name))
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            context.toast("Failed to load font: ${e.message}")
                        }
                    }
                }
            }
        }

        val providerId by providerPref.collectAsState()
        val mode by modePref.collectAsState()
        val provider = AiProvider.fromId(providerId)
        val modelPref = remember(provider) { translationPreferences.model(provider) }
        val textModelPref = remember(provider) { translationPreferences.textModel(provider) }
        val apiKeyPref = remember(provider) { translationPreferences.apiKey(provider) }
        val customBaseUrlPref = remember { translationPreferences.customBaseUrl() }
        val catalogPref = remember(provider) { translationPreferences.modelCatalog(provider) }
        val apiKey by apiKeyPref.collectAsState()
        val catalogJson by catalogPref.collectAsState()
        val overlay = mode == TranslationOptions.MODE_OVERLAY
        val localOcr = mode == TranslationOptions.MODE_LOCAL_OCR
        val isOverlayOrLocalOcr = overlay || localOcr

        val discoveredModels = remember(catalogJson) { AiModelCatalog.decode(catalogJson) }
        val modelEntries = remember(discoveredModels, overlay, localOcr) {
            val required = when {
                localOcr -> setOf(AiCapability.TEXT_GENERATION)
                overlay -> setOf(AiCapability.TEXT_GENERATION, AiCapability.IMAGE_INPUT)
                else -> setOf(AiCapability.IMAGE_GENERATION)
            }
            discoveredModels.filter { required.all(it::supports) }
                .associate { it.id to "${it.name} (${it.id})" }
        }

        val modeEntries = buildMap {
            put(TranslationOptions.MODE_LOCAL_OCR, stringResource(MR.strings.pref_translation_mode_local_ocr))
            put(TranslationOptions.MODE_OVERLAY, stringResource(MR.strings.pref_translation_mode_overlay))
            if (provider.supportsImageGeneration) {
                put(TranslationOptions.MODE_REDRAW, stringResource(MR.strings.pref_translation_mode_redraw))
            }
        }
        val ocrScriptPref = remember { translationPreferences.ocrScript() }
        val ocrScriptEntries = mapOf(
            TranslationOptions.OCR_SCRIPT_JAPANESE to stringResource(MR.strings.pref_translation_ocr_script_japanese),
            TranslationOptions.OCR_SCRIPT_LATIN to stringResource(MR.strings.pref_translation_ocr_script_latin),
            TranslationOptions.OCR_SCRIPT_KOREAN to stringResource(MR.strings.pref_translation_ocr_script_korean),
            TranslationOptions.OCR_SCRIPT_CHINESE to stringResource(MR.strings.pref_translation_ocr_script_chinese),
            TranslationOptions.OCR_SCRIPT_AUTO to stringResource(MR.strings.pref_translation_ocr_script_auto),
        )
        val fontEntries = mapOf(
            "arabic_manga" to stringResource(MR.strings.translation_font_arabic_manga),
            "arabic_ruqaa" to stringResource(MR.strings.translation_font_arabic_ruqaa),
            "bold" to stringResource(MR.strings.translation_font_bold),
            "regular" to stringResource(MR.strings.translation_font_regular),
            "serif" to stringResource(MR.strings.translation_font_serif),
            "comic" to stringResource(MR.strings.translation_font_comic),
            "custom" to stringResource(MR.strings.translation_font_custom),
        )
        val placementEntries = mapOf(
            "replace" to stringResource(MR.strings.translation_placement_replace),
            "below" to stringResource(MR.strings.translation_placement_below),
        )
        val delayEntries = TranslationOptions.REQUEST_DELAYS.associateWith {
            if (it == 0) {
                stringResource(MR.strings.translation_delay_none)
            } else {
                stringResource(MR.strings.translation_delay_seconds, it)
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_translation),
            preferenceItems = buildList {
                add(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(MR.strings.translation_dashboard_title),
                        content = {
                            TranslationDashboardCard(
                                translationPreferences = translationPreferences,
                                onClearCache = {
                                    eu.kanade.tachiyomi.data.translation.TranslatedPageCache.clearAll()
                                    context.toast(context.getContextString(MR.strings.pref_translation_clear_cache_done))
                                },
                            )
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = enabledPref,
                        title = stringResource(MR.strings.pref_translation_enable),
                        subtitle = stringResource(MR.strings.pref_translation_enable_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = providerPref,
                        entries = AiProvider.entries.associate { it.id to it.displayName },
                        title = "AI provider",
                        onValueChanged = {
                            if (!AiProvider.fromId(it).supportsImageGeneration &&
                                modePref.get() == TranslationOptions.MODE_REDRAW
                            ) {
                                modePref.set(TranslationOptions.MODE_OVERLAY)
                            }
                            true
                        },
                    ),
                )
                if (provider == AiProvider.CUSTOM) {
                    add(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = customBaseUrlPref,
                            title = "Custom API base URL",
                            subtitle = "%s",
                        ),
                    )
                }
                add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = apiKeyPref,
                        title = "${provider.displayName} API key",
                        // Fixed subtitle without "%s" so the stored key is never shown in the list.
                        subtitle = if (apiKey.isBlank()) {
                            stringResource(MR.strings.pref_translation_api_key_not_set)
                        } else {
                            stringResource(MR.strings.pref_translation_api_key_set)
                        },
                        onValueChanged = { value ->
                            if (value.trim().isNotEmpty() && !enabledPref.get()) {
                                enabledPref.set(true)
                            }
                            try {
                                val models = modelDiscovery.listModels(
                                    provider,
                                    value,
                                    customBaseUrlPref.get().takeIf { provider == AiProvider.CUSTOM },
                                )
                                catalogPref.set(AiModelCatalog.encode(models))
                            } catch (_: Exception) {
                                // Keep the key saved; the explicit refresh action exposes retryable failures.
                            }
                            true
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = "Refresh available models",
                        subtitle = if (discoveredModels.isEmpty()) {
                            "Fetch the models supported by this API key"
                        } else {
                            "${discoveredModels.size} model(s) discovered; filtered by capability"
                        },
                        onClick = {
                            scope.launch {
                                try {
                                    val models = modelDiscovery.listModels(
                                        provider,
                                        apiKey,
                                        customBaseUrlPref.get().takeIf { provider == AiProvider.CUSTOM },
                                    )
                                    catalogPref.set(AiModelCatalog.encode(models))
                                } catch (_: Exception) {
                                    // The key remains usable; users can retry after fixing the endpoint/network.
                                }
                            }
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = "Test connection",
                        subtitle = "Check the API key and provider endpoint",
                        onClick = {
                            scope.launch {
                                try {
                                    val models = modelDiscovery.listModels(
                                        provider,
                                        apiKey,
                                        customBaseUrlPref.get().takeIf { provider == AiProvider.CUSTOM },
                                    )
                                    catalogPref.set(AiModelCatalog.encode(models))
                                    context.toast(
                                        "${provider.displayName} connection successful (${models.size} models found)",
                                    )
                                } catch (error: Exception) {
                                    context.toast("Connection failed: ${error.message ?: "unknown error"}")
                                }
                            }
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = modePref,
                        entries = modeEntries,
                        title = stringResource(MR.strings.pref_translation_mode),
                    ),
                )
                if (localOcr) {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = ocrScriptPref,
                            entries = ocrScriptEntries,
                            title = stringResource(MR.strings.pref_translation_ocr_script),
                        ),
                    )
                }
                if (isOverlayOrLocalOcr) {
                    if (modelEntries.isEmpty()) {
                        add(
                            Preference.PreferenceItem.InfoPreference(
                                "No compatible text models discovered. Add an API key and refresh available models.",
                            ),
                        )
                    } else {
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = textModelPref,
                                entries = modelEntries,
                                title = stringResource(MR.strings.pref_translation_text_model),
                            ),
                        )
                    }
                    if (overlay) {
                        add(
                            Preference.PreferenceItem.SwitchPreference(
                                preference = sfxPref,
                                title = stringResource(MR.strings.pref_translation_sfx),
                                subtitle = stringResource(MR.strings.pref_translation_sfx_summary),
                            ),
                        )
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = delayPref,
                                entries = delayEntries,
                                title = stringResource(MR.strings.pref_translation_delay),
                            ),
                        )
                    }
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = fontPref,
                            entries = fontEntries,
                            title = stringResource(MR.strings.pref_translation_font),
                        ),
                    )
                    if (fontStyle == "custom") {
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.pref_translation_custom_font),
                                subtitle = if (customFontPath.isNotBlank()) {
                                    stringResource(
                                        MR.strings.pref_translation_custom_font_selected,
                                        java.io.File(customFontPath).name,
                                    )
                                } else {
                                    stringResource(MR.strings.pref_translation_custom_font_none)
                                },
                                onClick = {
                                    try {
                                        fontPickerLauncher.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                                    } catch (_: Exception) {
                                        context.toast("File picker unavailable")
                                    }
                                },
                            ),
                        )
                    }
                    add(
                        Preference.PreferenceItem.SliderPreference(
                            preference = textSizePref,
                            valueRange = 60..100 step 10,
                            title = stringResource(MR.strings.pref_translation_text_size),
                            valueText = { stringResource(MR.strings.translation_text_size_value, it) },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = placementPref,
                            entries = placementEntries,
                            title = stringResource(MR.strings.pref_translation_placement),
                        ),
                    )
                } else {
                    if (modelEntries.isEmpty()) {
                        add(
                            Preference.PreferenceItem.InfoPreference(
                                "No compatible image models discovered for this provider.",
                            ),
                        )
                    } else {
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = modelPref,
                                entries = modelEntries,
                                title = stringResource(MR.strings.pref_translation_model),
                            ),
                        )
                    }
                }
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = targetLanguagePref,
                        entries = TranslationOptions.LANGUAGES.associateWith { it },
                        title = stringResource(MR.strings.pref_translation_target_language),
                    ),
                )
                if (isTargetArabic) {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = arabicStylePref,
                            entries = mapOf(
                                TranslationOptions.ARABIC_STYLE_BALADI to stringResource(MR.strings.translation_arabic_style_baladi),
                                TranslationOptions.ARABIC_STYLE_MODERN to stringResource(MR.strings.translation_arabic_style_modern),
                                TranslationOptions.ARABIC_STYLE_CLASSIC to stringResource(MR.strings.translation_arabic_style_classic),
                            ),
                            title = stringResource(MR.strings.pref_translation_arabic_style),
                        ),
                    )
                }
                if (overlay) {
                    add(
                        Preference.PreferenceItem.InfoPreference(
                            stringResource(MR.strings.pref_translation_free_privacy),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.InfoPreference(
                            stringResource(MR.strings.pref_translation_style_info),
                        ),
                    )
                }
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = saveLogPref,
                        title = stringResource(MR.strings.pref_translation_save_log),
                        subtitle = stringResource(MR.strings.pref_translation_save_log_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = remember { translationPreferences.autoPreTranslate() },
                        title = stringResource(MR.strings.pref_translation_auto_pre_translate),
                        subtitle = stringResource(MR.strings.pref_translation_auto_pre_translate_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = remember { translationPreferences.autoApplyTranslation() },
                        title = stringResource(MR.strings.pref_translation_auto_apply),
                        subtitle = stringResource(MR.strings.pref_translation_auto_apply_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_translation_clear_cache),
                        subtitle = stringResource(MR.strings.pref_translation_clear_cache_summary),
                        onClick = {
                            eu.kanade.tachiyomi.data.translation.TranslatedPageCache.clearAll()
                            context.toast(context.getContextString(MR.strings.pref_translation_clear_cache_done))
                        },
                    ),
                )
            },
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }
        val trustExtension = remember { context.appGraph.trustExtension }

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
            preferenceItems = listOf(
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
                        .associateWith { stringResource(it.titleRes) },
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
                    onClick = { trustExtension.revokeAll() },
                ),
            ),
        )
    }
}

@Composable
private fun TranslationDashboardCard(
    translationPreferences: TranslationPreferences,
    onClearCache: () -> Unit,
) {
    val pagesCount by translationPreferences.statsPagesCount().collectAsState()
    val totalMs by translationPreferences.statsTotalTimeMs().collectAsState()
    var cacheVersion by remember { mutableIntStateOf(0) }

    val savedDataMb = remember(pagesCount) { translationPreferences.getEstimatedSavedDataMB() }
    val avgLatency = remember(pagesCount, totalMs) { translationPreferences.getAverageLatencySeconds() }
    val diskCacheMb = remember(pagesCount, cacheVersion) { eu.kanade.tachiyomi.data.translation.TranslatedPageCache.getDiskCacheSizeMB() }
    val cachedCount = remember(pagesCount, cacheVersion) { eu.kanade.tachiyomi.data.translation.TranslatedPageCache.getCachedPagesCount() }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.translation_dashboard_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Local OCR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                StatBox(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.translation_dashboard_pages),
                    value = "$pagesCount",
                )
                StatBox(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.translation_dashboard_saved_data),
                    value = String.format(java.util.Locale.US, "~%.1f MB", savedDataMb),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                StatBox(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.translation_dashboard_speed),
                    value = String.format(java.util.Locale.US, "%.2fs", avgLatency),
                )
                StatBox(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.translation_dashboard_cache),
                    value = "$cachedCount (" + String.format(java.util.Locale.US, "%.1f MB", diskCacheMb) + ")",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = {
                        onClearCache()
                        cacheVersion++
                    },
                ) {
                    Text(text = stringResource(MR.strings.pref_translation_clear_cache))
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
