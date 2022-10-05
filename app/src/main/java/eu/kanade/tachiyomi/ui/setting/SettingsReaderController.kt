package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferenceValues.TappingInvertMode
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import uy.kohesive.injekt.injectLazy

class SettingsReaderController : SettingsController() {

    private val readerPreferences: ReaderPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_reader

        intListPreference {
            bindTo(readerPreferences.defaultReadingMode())
            titleRes = R.string.pref_viewer_type
            entriesRes = arrayOf(
                R.string.left_to_right_viewer,
                R.string.right_to_left_viewer,
                R.string.vertical_viewer,
                R.string.webtoon_viewer,
                R.string.vertical_plus_viewer,
            )
            entryValues = ReadingModeType.values().drop(1)
                .map { value -> "${value.flagValue}" }.toTypedArray()
            summary = "%s"
        }
        intListPreference {
            bindTo(readerPreferences.doubleTapAnimSpeed())
            titleRes = R.string.pref_double_tap_anim_speed
            entries = arrayOf(context.getString(R.string.double_tap_anim_speed_0), context.getString(R.string.double_tap_anim_speed_normal), context.getString(R.string.double_tap_anim_speed_fast))
            entryValues = arrayOf("1", "500", "250") // using a value of 0 breaks the image viewer, so min is 1
            summary = "%s"
        }
        switchPreference {
            bindTo(readerPreferences.showReadingMode())
            titleRes = R.string.pref_show_reading_mode
            summaryRes = R.string.pref_show_reading_mode_summary
        }
        switchPreference {
            bindTo(readerPreferences.showNavigationOverlayOnStart())
            titleRes = R.string.pref_show_navigation_mode
            summaryRes = R.string.pref_show_navigation_mode_summary
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switchPreference {
                bindTo(readerPreferences.trueColor())
                titleRes = R.string.pref_true_color
                summaryRes = R.string.pref_true_color_summary
            }
        }
        switchPreference {
            bindTo(readerPreferences.pageTransitions())
            titleRes = R.string.pref_page_transitions
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            intListPreference {
                bindTo(readerPreferences.defaultOrientationType())
                titleRes = R.string.pref_rotation_type
                entriesRes = arrayOf(
                    R.string.rotation_free,
                    R.string.rotation_portrait,
                    R.string.rotation_reverse_portrait,
                    R.string.rotation_landscape,
                    R.string.rotation_force_portrait,
                    R.string.rotation_force_landscape,
                )
                entryValues = OrientationType.values().drop(1)
                    .map { value -> "${value.flagValue}" }.toTypedArray()
                summary = "%s"
            }
            intListPreference {
                bindTo(readerPreferences.readerTheme())
                titleRes = R.string.pref_reader_theme
                entriesRes = arrayOf(R.string.black_background, R.string.gray_background, R.string.white_background, R.string.automatic_background)
                entryValues = arrayOf("1", "2", "0", "3")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.fullscreen())
                titleRes = R.string.pref_fullscreen
            }

            if (activity?.hasDisplayCutout() == true) {
                switchPreference {
                    bindTo(readerPreferences.cutoutShort())
                    titleRes = R.string.pref_cutout_short

                    visibleIf(readerPreferences.fullscreen()) { it }
                }
            }

            switchPreference {
                bindTo(readerPreferences.keepScreenOn())
                titleRes = R.string.pref_keep_screen_on
            }
            switchPreference {
                bindTo(readerPreferences.showPageNumber())
                titleRes = R.string.pref_show_page_number
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_reading

            switchPreference {
                bindTo(readerPreferences.skipRead())
                titleRes = R.string.pref_skip_read_chapters
            }
            switchPreference {
                bindTo(readerPreferences.skipFiltered())
                titleRes = R.string.pref_skip_filtered_chapters
            }
            switchPreference {
                bindTo(readerPreferences.alwaysShowChapterTransition())
                titleRes = R.string.pref_always_show_chapter_transition
            }
        }

        preferenceCategory {
            titleRes = R.string.pager_viewer

            intListPreference {
                bindTo(readerPreferences.navigationModePager())
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.pager_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.pagerNavInverted())
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both,
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name,
                )
                summary = "%s"
                visibleIf(readerPreferences.navigationModePager()) { it != 5 }
            }
            switchPreference {
                bindTo(readerPreferences.navigateToPan())
                titleRes = R.string.pref_navigate_pan
                visibleIf(readerPreferences.navigationModePager()) { it != 5 }
            }
            intListPreference {
                bindTo(readerPreferences.imageScaleType())
                titleRes = R.string.pref_image_scale_type
                entriesRes = arrayOf(
                    R.string.scale_type_fit_screen,
                    R.string.scale_type_stretch,
                    R.string.scale_type_fit_width,
                    R.string.scale_type_fit_height,
                    R.string.scale_type_original_size,
                    R.string.scale_type_smart_fit,
                )
                entryValues = arrayOf("1", "2", "3", "4", "5", "6")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.landscapeZoom())
                titleRes = R.string.pref_landscape_zoom
                visibleIf(readerPreferences.imageScaleType()) { it == 1 }
            }
            intListPreference {
                bindTo(readerPreferences.zoomStart())
                titleRes = R.string.pref_zoom_start
                entriesRes = arrayOf(
                    R.string.zoom_start_automatic,
                    R.string.zoom_start_left,
                    R.string.zoom_start_right,
                    R.string.zoom_start_center,
                )
                entryValues = arrayOf("1", "2", "3", "4")
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.cropBorders())
                titleRes = R.string.pref_crop_borders
            }
            switchPreference {
                bindTo(readerPreferences.dualPageSplitPaged())
                titleRes = R.string.pref_dual_page_split
            }
            switchPreference {
                bindTo(readerPreferences.dualPageInvertPaged())
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                visibleIf(readerPreferences.dualPageSplitPaged()) { it }
            }
        }

        preferenceCategory {
            titleRes = R.string.webtoon_viewer

            intListPreference {
                bindTo(readerPreferences.navigationModeWebtoon())
                titleRes = R.string.pref_viewer_nav
                entries = context.resources.getStringArray(R.array.webtoon_nav).also { values ->
                    entryValues = values.indices.map { index -> "$index" }.toTypedArray()
                }
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.webtoonNavInverted())
                titleRes = R.string.pref_read_with_tapping_inverted
                entriesRes = arrayOf(
                    R.string.tapping_inverted_none,
                    R.string.tapping_inverted_horizontal,
                    R.string.tapping_inverted_vertical,
                    R.string.tapping_inverted_both,
                )
                entryValues = arrayOf(
                    TappingInvertMode.NONE.name,
                    TappingInvertMode.HORIZONTAL.name,
                    TappingInvertMode.VERTICAL.name,
                    TappingInvertMode.BOTH.name,
                )
                summary = "%s"
                visibleIf(readerPreferences.navigationModeWebtoon()) { it != 5 }
            }
            intListPreference {
                bindTo(readerPreferences.webtoonSidePadding())
                titleRes = R.string.pref_webtoon_side_padding
                entriesRes = arrayOf(
                    R.string.webtoon_side_padding_0,
                    R.string.webtoon_side_padding_5,
                    R.string.webtoon_side_padding_10,
                    R.string.webtoon_side_padding_15,
                    R.string.webtoon_side_padding_20,
                    R.string.webtoon_side_padding_25,
                )
                entryValues = arrayOf("0", "5", "10", "15", "20", "25")
                summary = "%s"
            }
            listPreference {
                bindTo(readerPreferences.readerHideThreshold())
                titleRes = R.string.pref_hide_threshold
                entriesRes = arrayOf(
                    R.string.pref_highest,
                    R.string.pref_high,
                    R.string.pref_low,
                    R.string.pref_lowest,
                )
                entryValues = PreferenceValues.ReaderHideThreshold.values()
                    .map { it.name }
                    .toTypedArray()
                summary = "%s"
            }
            switchPreference {
                bindTo(readerPreferences.cropBordersWebtoon())
                titleRes = R.string.pref_crop_borders
            }
            switchPreference {
                bindTo(readerPreferences.dualPageSplitWebtoon())
                titleRes = R.string.pref_dual_page_split
            }
            switchPreference {
                bindTo(readerPreferences.dualPageInvertWebtoon())
                titleRes = R.string.pref_dual_page_invert
                summaryRes = R.string.pref_dual_page_invert_summary
                visibleIf(readerPreferences.dualPageSplitWebtoon()) { it }
            }
            switchPreference {
                bindTo(readerPreferences.longStripSplitWebtoon())
                titleRes = R.string.pref_long_strip_split
                summaryRes = R.string.split_tall_images_summary
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_reader_navigation

            switchPreference {
                bindTo(readerPreferences.readWithVolumeKeys())
                titleRes = R.string.pref_read_with_volume_keys
            }
            switchPreference {
                bindTo(readerPreferences.readWithVolumeKeysInverted())
                titleRes = R.string.pref_read_with_volume_keys_inverted
                visibleIf(readerPreferences.readWithVolumeKeys()) { it }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_reader_actions

            switchPreference {
                bindTo(readerPreferences.readWithLongTap())
                titleRes = R.string.pref_read_with_long_tap
            }
            switchPreference {
                bindTo(readerPreferences.folderPerManga())
                titleRes = R.string.pref_create_folder_per_manga
                summaryRes = R.string.pref_create_folder_per_manga_summary
            }
        }
    }
}
