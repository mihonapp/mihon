package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.download.service.NovelDownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsNovelDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_novel_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val novelDownloadPreferences = remember { Injekt.get<NovelDownloadPreferences>() }

        return listOf(
            getDownloadThrottlingGroup(novelDownloadPreferences),
            getUpdateThrottlingGroup(novelDownloadPreferences),
            getMassImportThrottlingGroup(novelDownloadPreferences),
        )
    }

    @Composable
    private fun getDownloadThrottlingGroup(
        prefs: NovelDownloadPreferences,
    ): Preference.PreferenceGroup {
        val enabled = prefs.enableThrottling().collectAsState().value
        val downloadDelay = prefs.downloadDelay().collectAsState().value
        val randomDelay = prefs.randomDelayRange().collectAsState().value
        val parallelDownloads = prefs.parallelNovelDownloads().collectAsState().value

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_downloads),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.enableThrottling(),
                    title = stringResource(MR.strings.pref_novel_download_throttling),
                    subtitle = stringResource(MR.strings.pref_novel_download_throttling_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = downloadDelay,
                    valueRange = 0..30000,
                    title = stringResource(MR.strings.pref_novel_download_delay),
                    subtitle = stringResource(MR.strings.pref_novel_download_delay_summary),
                    valueString = "${downloadDelay}ms",
                    onValueChanged = { prefs.downloadDelay().set(it) },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = randomDelay,
                    valueRange = 0..5000,
                    title = stringResource(MR.strings.pref_novel_random_delay),
                    subtitle = stringResource(MR.strings.pref_novel_random_delay_summary),
                    valueString = "0-${randomDelay}ms",
                    onValueChanged = { prefs.randomDelayRange().set(it) },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = parallelDownloads,
                    valueRange = 1..50,
                    title = stringResource(MR.strings.pref_novel_parallel_downloads),
                    onValueChanged = { prefs.parallelNovelDownloads().set(it) },
                ),
            ),
        )
    }

    @Composable
    private fun getUpdateThrottlingGroup(
        prefs: NovelDownloadPreferences,
    ): Preference.PreferenceGroup {
        val enabled = prefs.enableUpdateThrottling().collectAsState().value
        val updateDelay = prefs.updateDelay().collectAsState().value

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.enableUpdateThrottling(),
                    title = stringResource(MR.strings.pref_novel_update_throttling),
                    subtitle = stringResource(MR.strings.pref_novel_update_throttling_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = updateDelay,
                    valueRange = 0..10000,
                    title = stringResource(MR.strings.pref_novel_update_delay),
                    subtitle = stringResource(MR.strings.pref_novel_update_delay_summary),
                    valueString = "${updateDelay}ms",
                    onValueChanged = { prefs.updateDelay().set(it) },
                    enabled = enabled,
                ),
            ),
        )
    }

    @Composable
    private fun getMassImportThrottlingGroup(
        prefs: NovelDownloadPreferences,
    ): Preference.PreferenceGroup {
        val enabled = prefs.enableMassImportThrottling().collectAsState().value
        val massImportDelay = prefs.massImportDelay().collectAsState().value

        return Preference.PreferenceGroup(
            title = "Mass Import",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.enableMassImportThrottling(),
                    title = stringResource(MR.strings.pref_novel_mass_import_throttling),
                    subtitle = stringResource(MR.strings.pref_novel_mass_import_throttling_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = massImportDelay,
                    valueRange = 0..10000,
                    title = stringResource(MR.strings.pref_novel_mass_import_delay),
                    subtitle = stringResource(MR.strings.pref_novel_mass_import_delay_summary),
                    valueString = "${massImportDelay}ms",
                    onValueChanged = { prefs.massImportDelay().set(it) },
                    enabled = enabled,
                ),
            ),
        )
    }
}
