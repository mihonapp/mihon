package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.getEnum
import tachiyomi.i18n.MR

class ReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // region General

    fun pageTransitions() = preferenceStore.getBoolean("pref_enable_transitions_key", true)

    fun flashOnPageChange() = preferenceStore.getBoolean("pref_reader_flash", false)

    fun doubleTapAnimSpeed() = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = preferenceStore.getBoolean("pref_show_page_number_key", true)

    fun showReadingMode() = preferenceStore.getBoolean("pref_show_reading_mode", true)

    // TODO: default this to true if reader long strip ever goes stable
    fun trueColor() = preferenceStore.getBoolean("pref_true_color_key", false)

    fun fullscreen() = preferenceStore.getBoolean("fullscreen", true)

    fun cutoutShort() = preferenceStore.getBoolean("cutout_short", true)

    fun keepScreenOn() = preferenceStore.getBoolean("pref_keep_screen_on_key", true)

    fun defaultReadingMode() = preferenceStore.getInt(
        "pref_default_reading_mode_key",
        ReadingMode.RIGHT_TO_LEFT.flagValue,
    )

    fun defaultOrientationType() = preferenceStore.getInt(
        "pref_default_orientation_type_key",
        ReaderOrientation.FREE.flagValue,
    )

    fun webtoonDoubleTapZoomEnabled() = preferenceStore.getBoolean("pref_enable_double_tap_zoom_webtoon", true)

    fun imageScaleType() = preferenceStore.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = preferenceStore.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = preferenceStore.getInt("pref_reader_theme_key", 1)

    fun alwaysShowChapterTransition() = preferenceStore.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = preferenceStore.getBoolean("crop_borders", false)

    fun navigateToPan() = preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = preferenceStore.getBoolean("landscape_zoom", true)

    fun cropBordersWebtoon() = preferenceStore.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = preferenceStore.getInt("webtoon_side_padding", WEBTOON_PADDING_MIN)

    fun readerHideThreshold() = preferenceStore.getEnum("reader_hide_threshold", ReaderHideThreshold.LOW)

    fun folderPerManga() = preferenceStore.getBoolean("create_folder_per_manga", false)

    fun skipRead() = preferenceStore.getBoolean("skip_read", false)

    fun skipFiltered() = preferenceStore.getBoolean("skip_filtered", true)

    fun skipDupe() = preferenceStore.getBoolean("skip_dupe", false)

    // endregion

    // region Split two page spread

    fun dualPageSplitPaged() = preferenceStore.getBoolean("pref_dual_page_split", false)

    fun dualPageInvertPaged() = preferenceStore.getBoolean("pref_dual_page_invert", false)

    fun dualPageSplitWebtoon() = preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertWebtoon() = preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    fun dualPageRotateToFit() = preferenceStore.getBoolean("pref_dual_page_rotate", false)

    fun dualPageRotateToFitInvert() = preferenceStore.getBoolean("pref_dual_page_rotate_invert", false)

    fun dualPageRotateToFitWebtoon() = preferenceStore.getBoolean("pref_dual_page_rotate_webtoon", false)

    fun dualPageRotateToFitInvertWebtoon() = preferenceStore.getBoolean("pref_dual_page_rotate_invert_webtoon", false)

    // endregion

    // region Color filter

    fun customBrightness() = preferenceStore.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = preferenceStore.getInt("custom_brightness_value", 0)

    fun colorFilter() = preferenceStore.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = preferenceStore.getInt("color_filter_value", 0)

    fun colorFilterMode() = preferenceStore.getInt("color_filter_mode", 0)

    fun grayscale() = preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = preferenceStore.getBoolean("pref_inverted_colors", false)

    // endregion

    // region Controls

    fun readWithLongTap() = preferenceStore.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = preferenceStore.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = preferenceStore.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = preferenceStore.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    fun pagerNavInverted() = preferenceStore.getEnum("reader_tapping_inverted", TappingInvertMode.NONE)

    fun webtoonNavInverted() = preferenceStore.getEnum("reader_tapping_inverted_webtoon", TappingInvertMode.NONE)

    fun showNavigationOverlayNewUser() = preferenceStore.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = preferenceStore.getBoolean("reader_navigation_overlay_on_start", false)

    // endregion

    enum class TappingInvertMode(
        val titleRes: StringResource,
        val shouldInvertHorizontal: Boolean = false,
        val shouldInvertVertical: Boolean = false,
    ) {
        NONE(MR.strings.tapping_inverted_none),
        HORIZONTAL(MR.strings.tapping_inverted_horizontal, shouldInvertHorizontal = true),
        VERTICAL(MR.strings.tapping_inverted_vertical, shouldInvertVertical = true),
        BOTH(MR.strings.tapping_inverted_both, shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47),
    }

    companion object {
        const val WEBTOON_PADDING_MIN = 0
        const val WEBTOON_PADDING_MAX = 25

        val TapZones = listOf(
            MR.strings.label_default,
            MR.strings.l_nav,
            MR.strings.kindlish_nav,
            MR.strings.edge_nav,
            MR.strings.right_and_left_nav,
            MR.strings.disabled_nav,
        )

        val ImageScaleType = listOf(
            MR.strings.scale_type_fit_screen,
            MR.strings.scale_type_stretch,
            MR.strings.scale_type_fit_width,
            MR.strings.scale_type_fit_height,
            MR.strings.scale_type_original_size,
            MR.strings.scale_type_smart_fit,
        )

        val ZoomStart = listOf(
            MR.strings.zoom_start_automatic,
            MR.strings.zoom_start_left,
            MR.strings.zoom_start_right,
            MR.strings.zoom_start_center,
        )

        val ColorFilterMode = buildList {
            addAll(
                listOf(
                    MR.strings.label_default to BlendMode.SrcOver,
                    MR.strings.filter_mode_multiply to BlendMode.Modulate,
                    MR.strings.filter_mode_screen to BlendMode.Screen,
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAll(
                    listOf(
                        MR.strings.filter_mode_overlay to BlendMode.Overlay,
                        MR.strings.filter_mode_lighten to BlendMode.Lighten,
                        MR.strings.filter_mode_darken to BlendMode.Darken,
                    ),
                )
            }
        }
    }
}
