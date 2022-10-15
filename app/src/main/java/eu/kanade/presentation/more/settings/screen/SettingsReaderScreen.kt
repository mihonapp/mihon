package eu.kanade.presentation.more.settings.screen

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues.ReaderHideThreshold
import eu.kanade.tachiyomi.data.preference.PreferenceValues.TappingInvertMode
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerPref = remember { Injekt.get<ReaderPreferences>() }
        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.defaultReadingMode(),
                title = stringResource(R.string.pref_viewer_type),
                entries = ReadingModeType.values().drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.doubleTapAnimSpeed(),
                title = stringResource(R.string.pref_double_tap_anim_speed),
                entries = mapOf(
                    1 to stringResource(R.string.double_tap_anim_speed_0),
                    500 to stringResource(R.string.double_tap_anim_speed_normal),
                    250 to stringResource(R.string.double_tap_anim_speed_fast),
                ),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showReadingMode(),
                title = stringResource(R.string.pref_show_reading_mode),
                subtitle = stringResource(R.string.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showNavigationOverlayOnStart(),
                title = stringResource(R.string.pref_show_navigation_mode),
                subtitle = stringResource(R.string.pref_show_navigation_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.trueColor(),
                title = stringResource(R.string.pref_true_color),
                subtitle = stringResource(R.string.pref_true_color_summary),
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.pageTransitions(),
                title = stringResource(R.string.pref_page_transitions),
            ),
            getDisplayGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreenPref = readerPreferences.fullscreen()
        val fullscreen by fullscreenPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.defaultOrientationType(),
                    title = stringResource(R.string.pref_rotation_type),
                    entries = OrientationType.values().drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerTheme(),
                    title = stringResource(R.string.pref_reader_theme),
                    entries = mapOf(
                        1 to stringResource(R.string.black_background),
                        2 to stringResource(R.string.gray_background),
                        0 to stringResource(R.string.white_background),
                        3 to stringResource(R.string.automatic_background),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = fullscreenPref,
                    title = stringResource(R.string.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cutoutShort(),
                    title = stringResource(R.string.pref_cutout_short),
                    enabled = fullscreen &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        LocalView.current.rootWindowInsets?.displayCutout != null, // has cutout
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.keepScreenOn(),
                    title = stringResource(R.string.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.showPageNumber(),
                    title = stringResource(R.string.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager()
        val imageScaleTypePref = readerPreferences.imageScaleType()
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged()

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pager_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(R.string.pref_viewer_nav),
                    entries = stringArrayResource(id = R.array.pager_nav).let {
                        it.indices.zip(it).toMap()
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.pagerNavInverted(),
                    title = stringResource(R.string.pref_read_with_tapping_inverted),
                    entries = mapOf(
                        TappingInvertMode.NONE to stringResource(R.string.none),
                        TappingInvertMode.HORIZONTAL to stringResource(R.string.tapping_inverted_horizontal),
                        TappingInvertMode.VERTICAL to stringResource(R.string.tapping_inverted_vertical),
                        TappingInvertMode.BOTH to stringResource(R.string.tapping_inverted_both),
                    ),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.navigateToPan(),
                    title = stringResource(R.string.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = imageScaleTypePref,
                    title = stringResource(R.string.pref_image_scale_type),
                    entries = mapOf(
                        1 to stringResource(R.string.scale_type_fit_screen),
                        2 to stringResource(R.string.scale_type_stretch),
                        3 to stringResource(R.string.scale_type_fit_width),
                        4 to stringResource(R.string.scale_type_fit_height),
                        5 to stringResource(R.string.scale_type_original_size),
                        6 to stringResource(R.string.scale_type_smart_fit),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.landscapeZoom(),
                    title = stringResource(R.string.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.zoomStart(),
                    title = stringResource(R.string.pref_zoom_start),
                    entries = mapOf(
                        1 to stringResource(R.string.zoom_start_automatic),
                        2 to stringResource(R.string.zoom_start_left),
                        3 to stringResource(R.string.zoom_start_right),
                        4 to stringResource(R.string.zoom_start_center),
                    ),

                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBorders(),
                    title = stringResource(R.string.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(R.string.pref_dual_page_split),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(R.string.pref_dual_page_invert),
                    subtitle = stringResource(R.string.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModeWebtoon()
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon()

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(R.string.webtoon_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(R.string.pref_viewer_nav),
                    entries = stringArrayResource(id = R.array.webtoon_nav).let {
                        it.indices.zip(it).toMap()
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.webtoonNavInverted(),
                    title = stringResource(R.string.pref_read_with_tapping_inverted),
                    entries = mapOf(
                        TappingInvertMode.NONE to stringResource(R.string.none),
                        TappingInvertMode.HORIZONTAL to stringResource(R.string.tapping_inverted_horizontal),
                        TappingInvertMode.VERTICAL to stringResource(R.string.tapping_inverted_vertical),
                        TappingInvertMode.BOTH to stringResource(R.string.tapping_inverted_both),
                    ),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.webtoonSidePadding(),
                    title = stringResource(R.string.pref_webtoon_side_padding),
                    entries = mapOf(
                        0 to stringResource(R.string.webtoon_side_padding_0),
                        5 to stringResource(R.string.webtoon_side_padding_5),
                        10 to stringResource(R.string.webtoon_side_padding_10),
                        15 to stringResource(R.string.webtoon_side_padding_15),
                        20 to stringResource(R.string.webtoon_side_padding_20),
                        25 to stringResource(R.string.webtoon_side_padding_25),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerHideThreshold(),
                    title = stringResource(R.string.pref_hide_threshold),
                    entries = mapOf(
                        ReaderHideThreshold.HIGHEST to stringResource(R.string.pref_highest),
                        ReaderHideThreshold.HIGH to stringResource(R.string.pref_high),
                        ReaderHideThreshold.LOW to stringResource(R.string.pref_low),
                        ReaderHideThreshold.LOWEST to stringResource(R.string.pref_lowest),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBordersWebtoon(),
                    title = stringResource(R.string.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(R.string.pref_dual_page_split),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertWebtoon(),
                    title = stringResource(R.string.pref_dual_page_invert),
                    subtitle = stringResource(R.string.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.longStripSplitWebtoon(),
                    title = stringResource(R.string.pref_long_strip_split),
                    subtitle = stringResource(R.string.split_tall_images_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys()
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_reader_navigation),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readWithVolumeKeysPref,
                    title = stringResource(R.string.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithVolumeKeysInverted(),
                    title = stringResource(R.string.pref_read_with_volume_keys_inverted),
                    enabled = readWithVolumeKeys,
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_reader_actions),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithLongTap(),
                    title = stringResource(R.string.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.folderPerManga(),
                    title = stringResource(R.string.pref_create_folder_per_manga),
                ),
            ),
        )
    }
}
