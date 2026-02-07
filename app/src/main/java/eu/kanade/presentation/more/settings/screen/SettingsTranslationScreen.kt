package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        val engineManager = remember { Injekt.get<TranslationEngineManager>() }

        return listOf(
            getGeneralGroup(translationPreferences, engineManager),
            getRateLimitGroup(translationPreferences),
            getApiKeysGroup(translationPreferences),
        )
    }

    @Composable
    private fun getGeneralGroup(
        prefs: TranslationPreferences,
        engineManager: TranslationEngineManager,
    ): Preference.PreferenceGroup {
        val enabled by prefs.translationEnabled().collectAsState()
        val selectedEngineId by prefs.selectedEngineId().collectAsState()
        val sourceLanguage by prefs.sourceLanguage().collectAsState()
        val targetLanguage by prefs.targetLanguage().collectAsState()

        val engines = engineManager.engines
        val engineEntries = engines.associate { it.id.toString() to it.name }.toImmutableMap()

        val selectedEngine = engines.find { it.id == selectedEngineId } ?: engines.first()
        val languageEntries = selectedEngine.supportedLanguages.associate { it.first to it.second }.toImmutableMap()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_translation_general),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.translationEnabled(),
                    title = stringResource(MR.strings.pref_translation_enabled),
                    subtitle = stringResource(MR.strings.pref_translation_enabled_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.selectedEngineId(),
                    title = stringResource(MR.strings.pref_translation_engine),
                    subtitle = selectedEngine.name + if (selectedEngine.isOffline) " (Offline)" else "",
                    entries = engineEntries.mapKeys { it.key.toLong() }.toImmutableMap(),
                    enabled = enabled,
                ),
                Preference.PreferenceItem.BasicListPreference(
                    value = sourceLanguage,
                    title = stringResource(MR.strings.pref_translation_source_language),
                    subtitle = languageEntries[sourceLanguage] ?: sourceLanguage,
                    entries = languageEntries,
                    onValueChanged = { newValue ->
                        prefs.sourceLanguage().set(newValue)
                        true
                    },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.BasicListPreference(
                    value = targetLanguage,
                    title = stringResource(MR.strings.pref_translation_target_language),
                    subtitle = languageEntries[targetLanguage] ?: targetLanguage,
                    entries = languageEntries.filterKeys { it != "auto" }.toImmutableMap(),
                    onValueChanged = { newValue ->
                        prefs.targetLanguage().set(newValue)
                        true
                    },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.autoDownloadBeforeTranslate(),
                    title = stringResource(MR.strings.pref_translation_auto_download),
                    subtitle = stringResource(MR.strings.pref_translation_auto_download_summary),
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.autoTranslateDownloads(),
                    title = stringResource(MR.strings.pref_translation_auto_translate),
                    subtitle = stringResource(MR.strings.pref_translation_auto_translate_summary),
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.replaceTitle(),
                    title = "Replace title with translation",
                    subtitle = "When translating, replace the manga/novel title with the translated version. Original title is saved to alternative titles.",
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.saveTranslatedTitleAsAlternative(),
                    title = "Save translated titles as alternative",
                    subtitle = "Store translated titles in alternative_titles for reference",
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.translateTags(),
                    title = "Translate tags",
                    subtitle = "Translate genre tags and merge with original tags",
                    enabled = enabled,
                ),
            ),
        )
    }

    @Composable
    private fun getRateLimitGroup(
        prefs: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val rateLimitDelay by prefs.rateLimitDelayMs().collectAsState()
        val warningThreshold by prefs.rateLimitWarningThreshold().collectAsState()
        val maxParallel by prefs.maxParallelTranslations().collectAsState()
        val timeoutMs by prefs.translationTimeoutMs().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_translation_rate_limit),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = rateLimitDelay,
                    valueRange = 500..10000,
                    title = stringResource(MR.strings.pref_translation_rate_limit_delay),
                    subtitle = stringResource(MR.strings.pref_translation_rate_limit_delay_summary),
                    valueString = "${rateLimitDelay}ms",
                    onValueChanged = { prefs.rateLimitDelayMs().set(it) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (timeoutMs / 1000).toInt(),
                    valueRange = 30..300,
                    title = stringResource(MR.strings.pref_translation_timeout),
                    subtitle = stringResource(MR.strings.pref_translation_timeout_summary),
                    valueString = "${timeoutMs / 1000}s",
                    onValueChanged = { prefs.translationTimeoutMs().set(it.toLong() * 1000) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = warningThreshold,
                    valueRange = 1..50,
                    title = stringResource(MR.strings.pref_translation_warning_threshold),
                    subtitle = stringResource(MR.strings.pref_translation_warning_threshold_summary),
                    valueString = "$warningThreshold chapters",
                    onValueChanged = { prefs.rateLimitWarningThreshold().set(it) },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.bypassRateLimitWarning(),
                    title = stringResource(MR.strings.pref_translation_bypass_warning),
                    subtitle = stringResource(MR.strings.pref_translation_bypass_warning_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = maxParallel,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_translation_max_parallel),
                    subtitle = stringResource(MR.strings.pref_translation_max_parallel_summary),
                    valueString = "$maxParallel",
                    onValueChanged = { prefs.maxParallelTranslations().set(it) },
                ),
            ),
        )
    }

    @Composable
    private fun getApiKeysGroup(
        prefs: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val openAiKey by prefs.openAiApiKey().collectAsState()
        val deepSeekKey by prefs.deepSeekApiKey().collectAsState()
        val libreTranslateUrl by prefs.libreTranslateUrl().collectAsState()
        val libreTranslateKey by prefs.libreTranslateApiKey().collectAsState()
        val ollamaUrl by prefs.ollamaUrl().collectAsState()
        val ollamaModel by prefs.ollamaModel().collectAsState()
        val systranKey by prefs.systranApiKey().collectAsState()
        val deepLKey by prefs.deepLApiKey().collectAsState()
        val googleKey by prefs.googleApiKey().collectAsState()
        val customHttpUrl by prefs.customHttpUrl().collectAsState()
        val customHttpApiKey by prefs.customHttpApiKey().collectAsState()
        val customHttpRequestTemplate by prefs.customHttpRequestTemplate().collectAsState()
        val customHttpResponsePath by prefs.customHttpResponsePath().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_translation_api_keys),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.openAiApiKey(),
                    title = stringResource(MR.strings.pref_translation_openai_key),
                    subtitle = if (openAiKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.deepSeekApiKey(),
                    title = stringResource(MR.strings.pref_translation_deepseek_key),
                    subtitle = if (deepSeekKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.systranApiKey(),
                    title = "SYSTRAN API Key",
                    subtitle = if (systranKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.deepLApiKey(),
                    title = "DeepL API Key",
                    subtitle = if (deepLKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.googleApiKey(),
                    title = "Google Translate API Key",
                    subtitle = if (googleKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.libreTranslateUrl(),
                    title = stringResource(MR.strings.pref_translation_libretranslate_url),
                    subtitle = libreTranslateUrl,
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.libreTranslateApiKey(),
                    title = "LibreTranslate API Key",
                    subtitle = if (libreTranslateKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.ollamaUrl(),
                    title = stringResource(MR.strings.pref_translation_ollama_url),
                    subtitle = ollamaUrl,
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.ollamaModel(),
                    title = stringResource(MR.strings.pref_translation_ollama_model),
                    subtitle = ollamaModel,
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.ollamaPrompt(),
                    title = "Ollama Custom Prompt",
                    subtitle = "Use {SOURCE_LANG}, {TARGET_LANG}, {TEXT} as placeholders. Leave empty for default.",
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.openAiSystemPrompt(),
                    title = "OpenAI System Prompt",
                    subtitle = "Custom system prompt for GPT models. Leave empty for default.",
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.openAiUserPrompt(),
                    title = "OpenAI User Prompt Template",
                    subtitle = "Use {SOURCE_LANG}, {TARGET_LANG}, {TEXT} as placeholders. Leave empty for default.",
                ),
                // Custom HTTP settings
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.customHttpUrl(),
                    title = "Custom HTTP API URL",
                    subtitle = if (customHttpUrl.isNotBlank()) customHttpUrl else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.customHttpApiKey(),
                    title = "Custom HTTP API Key",
                    subtitle = if (customHttpApiKey.isNotBlank()) "••••••••" else stringResource(MR.strings.not_set),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.customHttpRequestTemplate(),
                    title = "Custom HTTP Request Template",
                    subtitle = "Use {texts}, {text}, {source}, {target}",
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = prefs.customHttpResponsePath(),
                    title = "Custom HTTP Response Path",
                    subtitle = customHttpResponsePath.ifBlank { "translatedText" },
                ),
            ),
        )
    }
}
