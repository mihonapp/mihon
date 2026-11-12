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
import tachiyomi.domain.translation.model.TranslationProvider
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
                title = stringResource(MR.strings.translation_service),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.provider,
                        entries = mapOf(
                            TranslationProvider.GOOGLE to stringResource(MR.strings.translation_provider_google),
                            TranslationProvider.DEEPL to stringResource(MR.strings.translation_provider_deepl),
                            TranslationProvider.LIBRE_TRANSLATE to
                                stringResource(MR.strings.translation_provider_libre),
                        ),
                        title = stringResource(MR.strings.pref_translation_provider),
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
                ),
            ),
        )
    }
}
