package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val preferences = remember { Injekt.get<BasePreferences>() }
        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.label_extensions),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = preferences.automaticExtUpdates(),
                        title = stringResource(R.string.pref_enable_automatic_extension_updates),
                        onValueChanged = {
                            ExtensionUpdateJob.setupTask(context, it)
                            true
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.action_global_search),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.searchPinnedSourcesOnly(),
                        title = stringResource(R.string.pref_search_pinned_sources_only),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_nsfw_content),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.showNsfwSource(),
                        title = stringResource(R.string.pref_show_nsfw_source),
                        subtitle = stringResource(R.string.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.getString(R.string.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(R.string.parental_controls_info)),
                ),
            ),
        )
    }
}
