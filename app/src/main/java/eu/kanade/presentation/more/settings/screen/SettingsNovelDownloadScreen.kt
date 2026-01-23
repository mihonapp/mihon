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
            getImageEmbeddingGroup(novelDownloadPreferences),
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
    private fun getImageEmbeddingGroup(
        prefs: NovelDownloadPreferences,
    ): Preference.PreferenceGroup {
        val enabled = prefs.downloadChapterImages().collectAsState().value
        val maxSizeKb = prefs.maxImageSizeKb().collectAsState().value
        val compressionQuality = prefs.imageCompressionQuality().collectAsState().value

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_novel_images),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.downloadChapterImages(),
                    title = stringResource(MR.strings.pref_novel_download_images),
                    subtitle = stringResource(MR.strings.pref_novel_download_images_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = maxSizeKb,
                    valueRange = 0..2000,
                    title = stringResource(MR.strings.pref_novel_max_image_size),
                    subtitle = stringResource(MR.strings.pref_novel_max_image_size_summary),
                    valueString = if (maxSizeKb == 0) stringResource(MR.strings.no_limit) else "${maxSizeKb}KB",
                    onValueChanged = { prefs.maxImageSizeKb().set(it) },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = compressionQuality,
                    valueRange = 10..100,
                    title = stringResource(MR.strings.pref_novel_image_quality),
                    subtitle = stringResource(MR.strings.pref_novel_image_quality_summary),
                    valueString = "$compressionQuality%",
                    onValueChanged = { prefs.imageCompressionQuality().set(it) },
                    enabled = enabled,
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
        val parallelUpdates = prefs.parallelNovelUpdates().collectAsState().value

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
                    subtitle = "Delay between novels for the same extension",
                    valueString = "${updateDelay}ms",
                    onValueChanged = { prefs.updateDelay().set(it) },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = parallelUpdates,
                    valueRange = 1..10,
                    title = "Parallel novel updates",
                    subtitle = "Number of different extensions updating simultaneously (not within same extension)",
                    valueString = "$parallelUpdates",
                    onValueChanged = { prefs.parallelNovelUpdates().set(it) },
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
                    subtitle = "Apply delays between novels from the same source only",
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = massImportDelay,
                    valueRange = 0..10000,
                    title = stringResource(MR.strings.pref_novel_mass_import_delay),
                    subtitle = "Delay between imports from the same source (different sources run concurrently)",
                    valueString = "${massImportDelay}ms",
                    onValueChanged = { prefs.massImportDelay().set(it) },
                    enabled = enabled,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = prefs.parallelMassImport().collectAsState().value,
                    valueRange = 1..10,
                    title = "Concurrent Mass Imports",
                    subtitle = "Number of different sources importing simultaneously (throttle between same source is in delay setting)",
                    valueString = "${prefs.parallelMassImport().collectAsState().value}",
                    onValueChanged = { prefs.parallelMassImport().set(it) },
                ),
            ),
        )
    }
}
