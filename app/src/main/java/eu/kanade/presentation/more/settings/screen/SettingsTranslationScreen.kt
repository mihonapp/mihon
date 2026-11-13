package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.displayName
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.model.TranslationLetterCase
import tachiyomi.domain.translation.model.TranslationProvider
import tachiyomi.domain.translation.model.TranslationTextColor
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsTranslationScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { context.appGraph.translationPreferences }
        val provider by preferences.provider.collectAsState()

        val autoLabel = stringResource(MR.strings.translation_language_auto)
        val languageNames = TranslationLanguage.entries.associateWith { it.displayName() }
        val sourceLanguageNames = mapOf(TranslationLanguage.AUTO to autoLabel) +
            languageNames.minus(TranslationLanguage.AUTO)

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.translation_languages),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.sourceLanguage,
                        entries = sourceLanguageNames,
                        title = stringResource(MR.strings.pref_translation_source_language),
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(MR.strings.translation_source_language_hint),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.targetLanguage,
                        entries = languageNames.minus(TranslationLanguage.AUTO),
                        title = stringResource(MR.strings.pref_translation_target_language),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_translation_appearance),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.textColor,
                        entries = mapOf(
                            TranslationTextColor.AUTO to stringResource(MR.strings.translation_color_auto),
                            TranslationTextColor.WHITE to stringResource(MR.strings.translation_color_white),
                            TranslationTextColor.BLACK to stringResource(MR.strings.translation_color_black),
                            TranslationTextColor.RED to stringResource(MR.strings.translation_color_red),
                            TranslationTextColor.YELLOW to stringResource(MR.strings.translation_color_yellow),
                        ),
                        title = stringResource(MR.strings.pref_translation_text_color),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.boldText,
                        title = stringResource(MR.strings.pref_translation_bold),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.letterCase,
                        entries = mapOf(
                            TranslationLetterCase.ORIGINAL to stringResource(MR.strings.translation_case_original),
                            TranslationLetterCase.UPPER to stringResource(MR.strings.translation_case_upper),
                            TranslationLetterCase.LOWER to stringResource(MR.strings.translation_case_lower),
                        ),
                        title = stringResource(MR.strings.pref_translation_letter_case),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_translation_ocr),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SliderPreference(
                        preference = preferences.ocrQuality,
                        valueRange = 1..3,
                        valueText = { quality ->
                            stringResource(
                                when (quality) {
                                    1 -> MR.strings.translation_ocr_fast
                                    3 -> MR.strings.translation_ocr_accurate
                                    else -> MR.strings.translation_ocr_balanced
                                },
                            )
                        },
                        title = stringResource(MR.strings.pref_translation_ocr_quality),
                        subtitle = stringResource(MR.strings.pref_translation_ocr_quality_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.translation_service),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.provider,
                        entries = mapOf(
                            TranslationProvider.GOOGLE to stringResource(MR.strings.translation_provider_google),
                            TranslationProvider.DEEPL to stringResource(MR.strings.translation_provider_deepl),
                            TranslationProvider.LIBRE_TRANSLATE to
                                stringResource(MR.strings.translation_provider_libre),
                            TranslationProvider.OPENAI to stringResource(MR.strings.translation_provider_openai),
                            TranslationProvider.GEMINI to stringResource(MR.strings.translation_provider_gemini),
                            TranslationProvider.CLAUDE to stringResource(MR.strings.translation_provider_claude),
                            TranslationProvider.ON_DEVICE to
                                stringResource(MR.strings.translation_provider_on_device),
                        ),
                        title = stringResource(MR.strings.pref_translation_provider),
                        subtitle = if (provider == TranslationProvider.ON_DEVICE) {
                            stringResource(MR.strings.translation_on_device_hint)
                        } else {
                            null
                        },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.deeplApiKey,
                        title = stringResource(MR.strings.pref_translation_deepl_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_deepl_api_key_summary),
                        visible = provider == TranslationProvider.DEEPL,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.libreTranslateUrl,
                        title = stringResource(MR.strings.pref_translation_libre_url),
                        visible = provider == TranslationProvider.LIBRE_TRANSLATE,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.libreTranslateApiKey,
                        title = stringResource(MR.strings.pref_translation_libre_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_libre_api_key_summary),
                        visible = provider == TranslationProvider.LIBRE_TRANSLATE,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.openAiApiKey,
                        title = stringResource(MR.strings.pref_translation_ai_key),
                        subtitle = stringResource(MR.strings.pref_translation_ai_key_summary),
                        visible = provider == TranslationProvider.OPENAI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.openAiModel,
                        title = stringResource(MR.strings.pref_translation_ai_model),
                        subtitle = stringResource(MR.strings.pref_translation_ai_model_summary),
                        visible = provider == TranslationProvider.OPENAI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.geminiApiKey,
                        title = stringResource(MR.strings.pref_translation_ai_key),
                        subtitle = stringResource(MR.strings.pref_translation_ai_key_summary),
                        visible = provider == TranslationProvider.GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.geminiModel,
                        title = stringResource(MR.strings.pref_translation_ai_model),
                        subtitle = stringResource(MR.strings.pref_translation_ai_model_summary),
                        visible = provider == TranslationProvider.GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.claudeApiKey,
                        title = stringResource(MR.strings.pref_translation_ai_key),
                        subtitle = stringResource(MR.strings.pref_translation_ai_key_summary),
                        visible = provider == TranslationProvider.CLAUDE,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.claudeModel,
                        title = stringResource(MR.strings.pref_translation_ai_model),
                        subtitle = stringResource(MR.strings.pref_translation_ai_model_summary),
                        visible = provider == TranslationProvider.CLAUDE,
                    ),
                ),
            ),
        )
    }
}
