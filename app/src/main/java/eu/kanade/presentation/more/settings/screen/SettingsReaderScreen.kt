package eu.kanade.presentation.more.settings.screen

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerPref = remember { Injekt.get<ReaderPreferences>() }
        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.defaultReadingMode(),
                title = stringResource(MR.strings.pref_viewer_type),
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) }
                    .toImmutableMap(),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = readerPref.doubleTapAnimSpeed(),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
                entries = persistentMapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showReadingMode(),
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.showNavigationOverlayOnStart(),
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.trueColor(),
                title = stringResource(MR.strings.pref_true_color),
                subtitle = stringResource(MR.strings.pref_true_color_summary),
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.pageTransitions(),
                title = stringResource(MR.strings.pref_page_transitions),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = readerPref.flashOnPageChange(),
                title = stringResource(MR.strings.pref_flash_page),
                subtitle = stringResource(MR.strings.pref_flash_page_summ),
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
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.defaultOrientationType(),
                    title = stringResource(MR.strings.pref_rotation_type),
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.readerTheme(),
                    title = stringResource(MR.strings.pref_reader_theme),
                    entries = persistentMapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = fullscreenPref,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cutoutShort(),
                    title = stringResource(MR.strings.pref_cutout_short),
                    enabled = fullscreen &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        LocalView.current.rootWindowInsets?.displayCutout != null, // has cutout
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.keepScreenOn(),
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.showPageNumber(),
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reading),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipRead(),
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipFiltered(),
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.skipDupe(),
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.alwaysShowChapterTransition(),
                    title = stringResource(MR.strings.pref_always_show_chapter_transition),
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
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(MR.strings.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.pagerNavInverted(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = imageScaleTypePref,
                    title = stringResource(MR.strings.pref_image_scale_type),
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.zoomStart(),
                    title = stringResource(MR.strings.pref_zoom_start),
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBorders(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.landscapeZoom(),
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.navigateToPan(),
                    title = stringResource(MR.strings.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageRotateToFitInvert(),
                    title = stringResource(MR.strings.pref_page_rotate_invert),
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
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon()
        val webtoonSidePaddingPref = readerPreferences.webtoonSidePadding()

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()
        val webtoonSidePadding by webtoonSidePaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.webtoon_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = navModePref,
                    title = stringResource(MR.strings.pref_viewer_nav),
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = readerPreferences.webtoonNavInverted(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = webtoonSidePadding,
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
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
                    title = stringResource(MR.strings.pref_hide_threshold),
                    entries = persistentMapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.cropBordersWebtoon(),
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageInvertWebtoon(),
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.dualPageRotateToFitInvertWebtoon(),
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.webtoonDoubleTapZoomEnabled(),
                    title = stringResource(MR.strings.pref_double_tap_zoom),
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
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithVolumeKeysInverted(),
                    title = stringResource(MR.strings.pref_read_with_volume_keys_inverted),
                    enabled = readWithVolumeKeys,
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_actions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.readWithLongTap(),
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = readerPreferences.folderPerManga(),
                    title = stringResource(MR.strings.pref_create_folder_per_manga),
                    subtitle = stringResource(MR.strings.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }
}
