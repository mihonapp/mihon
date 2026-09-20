package tachiyomi.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {
    val autoTranslateOnDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_on_download_key",
        false,
    )

    val targetLanguage: Preference<String> = preferenceStore.getString(
        "pref_translation_target_language_key",
        "id",
    )

    val fontSelection: Preference<String> = preferenceStore.getString(
        "pref_translation_font_selection_key",
        "Comic Neue",
    )

    val translatorEngine: Preference<String> = preferenceStore.getString(
        "pref_translation_engine_key",
        "mlkit",
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString(
        "pref_translation_gemini_api_key",
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "pref_translation_gemini_model",
        "gemini-1.5-flash",
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_api_key",
        "",
    )

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_model",
        "google/gemini-2.0-flash-001",
    )

    val translationPrompt: Preference<String> = preferenceStore.getString(
        "pref_translation_prompt_template",
        "Translate the following manga speech bubble text into target language accurately, retaining tone, nuance, and character expressions:\n{text}",
    )

    val showTranslationInReader: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_show_translation_in_reader",
        true,
    )
}
