package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsAIScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsAIScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_ai

    @Composable
    override fun getPreferences(): List<Preference> {
        val basePreferences = remember { Injekt.get<BasePreferences>() }

        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = basePreferences.geminiAiApiKey(),
                title = stringResource(MR.strings.pref_gemini_api_key),
                subtitle = stringResource(MR.strings.pref_gemini_api_key_summary),
            ),
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(MR.strings.pref_gemini_model) + ": " + stringResource(MR.strings.pref_gemini_model_summary),
            ),
        )
    }
}
