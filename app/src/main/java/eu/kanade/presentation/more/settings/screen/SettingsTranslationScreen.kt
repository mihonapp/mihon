package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val translationPreferences = remember { context.appGraph.translationPreferences }

        val engine by translationPreferences.translatorEngine.collectAsState()

        val languages = mapOf(
            "id" to "Indonesian (Indonesian)",
            "en" to "English (English)",
            "ja" to "Japanese (日本語)",
            "ko" to "Korean (한국어)",
            "zh" to "Chinese (中文)",
            "es" to "Spanish (Español)",
            "fr" to "French (Français)",
            "de" to "German (Deutsch)",
            "ru" to "Russian (Русский)",
            "pt" to "Portuguese (Português)",
        )

        val fonts = mapOf(
            "Comic Neue" to "Comic Neue",
            "Bangers" to "Bangers",
            "Mansalva" to "Mansalva",
        )

        val engines = mapOf(
            "mlkit" to "ML-Kit (On-device)",
            "google_web" to "Google Translate (Web)",
            "deepl_web" to "DeepL Translate (Web)",
            "gemini" to "Gemini AI",
            "openrouter" to "OpenRouter API",
        )

        val preferences = mutableListOf<Preference>()

        preferences.add(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_general),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = translationPreferences.autoTranslateOnDownload,
                        title = stringResource(MR.strings.pref_auto_translate_on_download),
                        subtitle = stringResource(MR.strings.pref_auto_translate_on_download_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.targetLanguage,
                        entries = languages,
                        title = stringResource(MR.strings.pref_translation_target_language),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.fontSelection,
                        entries = fonts,
                        title = stringResource(MR.strings.pref_translation_font),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.translatorEngine,
                        entries = engines,
                        title = stringResource(MR.strings.pref_translation_engine),
                    ),
                ),
            ),
        )

        if (engine == "gemini") {
            preferences.add(
                Preference.PreferenceGroup(
                    title = "Gemini AI Settings",
                    preferenceItems = listOf(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.geminiApiKey,
                            title = "Gemini API Key",
                            subtitle = "Enter your Gemini API key",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.geminiModel,
                            title = "Gemini Model",
                            subtitle = "Default: gemini-1.5-flash",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.translationPrompt,
                            title = "Translation Prompt",
                            subtitle = "Custom prompt template for translation",
                        ),
                    ),
                ),
            )
        } else if (engine == "openrouter") {
            preferences.add(
                Preference.PreferenceGroup(
                    title = "OpenRouter Settings",
                    preferenceItems = listOf(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.openRouterApiKey,
                            title = "OpenRouter API Key",
                            subtitle = "Enter your OpenRouter API key",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.openRouterModel,
                            title = "OpenRouter Model",
                            subtitle = "Default: google/gemini-2.0-flash-001",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.translationPrompt,
                            title = "Translation Prompt",
                            subtitle = "Custom prompt template for translation",
                        ),
                    ),
                ),
            )
        }

        return preferences
    }
}
