package eu.kanade.presentation.more.settings.screen

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {

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
                entries = ReadingModeType.entries.drop(1)
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
            getReadingGroup(readerPreferences = readerPref),
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
                    entries = OrientationType.entries.drop(1)
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
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_reading),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipRead(),
                    title = stringResource(R.string.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipFiltered(),
                    title = stringResource(R.string.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipDupe(),
                    title = stringResource(R.string.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.alwaysShowChapterTransition(),
                    title = stringResource(R.string.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager()
        val imageScaleTypePref = readerPreferences.imageScaleType()
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged()
        val rotateToFitPref = readerPreferences.dualPageRotateToFit()

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pager_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(R.string.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.pagerNavInverted(),
                    title = stringResource(R.string.pref_read_with_tapping_inverted),
                    entries = mapOf(
                        ReaderPreferences.TappingInvertMode.NONE to stringResource(R.string.none),
                        ReaderPreferences.TappingInvertMode.HORIZONTAL to stringResource(R.string.tapping_inverted_horizontal),
                        ReaderPreferences.TappingInvertMode.VERTICAL to stringResource(R.string.tapping_inverted_vertical),
                        ReaderPreferences.TappingInvertMode.BOTH to stringResource(R.string.tapping_inverted_both),
                    ),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = imageScaleTypePref,
                    title = stringResource(R.string.pref_image_scale_type),
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.zoomStart(),
                    title = stringResource(R.string.pref_zoom_start),
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBorders(),
                    title = stringResource(R.string.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.landscapeZoom(),
                    title = stringResource(R.string.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.navigateToPan(),
                    title = stringResource(R.string.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(R.string.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(R.string.pref_dual_page_invert),
                    subtitle = stringResource(R.string.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rotateToFitPref,
                    title = stringResource(R.string.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageRotateToFitInvert(),
                    title = stringResource(R.string.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon()
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon()
        val webtoonSidePaddingPref = readerPreferences.webtoonSidePadding()

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val webtoonSidePadding by webtoonSidePaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(R.string.webtoon_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(R.string.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.webtoonNavInverted(),
                    title = stringResource(R.string.pref_read_with_tapping_inverted),
                    entries = mapOf(
                        ReaderPreferences.TappingInvertMode.NONE to stringResource(R.string.none),
                        ReaderPreferences.TappingInvertMode.HORIZONTAL to stringResource(R.string.tapping_inverted_horizontal),
                        ReaderPreferences.TappingInvertMode.VERTICAL to stringResource(R.string.tapping_inverted_vertical),
                        ReaderPreferences.TappingInvertMode.BOTH to stringResource(R.string.tapping_inverted_both),
                    ),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = webtoonSidePadding,
                    title = stringResource(R.string.pref_webtoon_side_padding),
                    subtitle = numberFormat.format(webtoonSidePadding / 100f),
                    min = ReaderPreferences.WEBTOON_PADDING_MIN,
                    max = ReaderPreferences.WEBTOON_PADDING_MAX,
                    onValueChanged = {
                        webtoonSidePaddingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerHideThreshold(),
                    title = stringResource(R.string.pref_hide_threshold),
                    entries = mapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(R.string.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(R.string.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(R.string.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(R.string.pref_lowest),
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
                    enabled = !isReleaseBuildType, // TODO: Show in release build when the feature is stable
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.webtoonDoubleTapZoomEnabled(),
                    title = stringResource(R.string.pref_double_tap_zoom),
                    enabled = true,
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
                    subtitle = stringResource(R.string.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }
}
