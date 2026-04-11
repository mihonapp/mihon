package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.reader.model.ReadingModeAutoRulesConfig
import tachiyomi.domain.reader.model.withPresetsMerged
import tachiyomi.i18n.MR

class ReaderPreferences(
    preferenceStore: PreferenceStore,
) {

    private val readingModeAutoRulesJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // region General

    val pageTransitions: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_transitions_key", true)

    val flashOnPageChange: Preference<Boolean> = preferenceStore.getBoolean("pref_reader_flash", false)

    val flashDurationMillis: Preference<Int> = preferenceStore.getInt("pref_reader_flash_duration", MILLI_CONVERSION)

    val flashPageInterval: Preference<Int> = preferenceStore.getInt("pref_reader_flash_interval", 1)

    val flashColor: Preference<FlashColor> = preferenceStore.getEnum("pref_reader_flash_mode", FlashColor.BLACK)

    val doubleTapAnimSpeed: Preference<Int> = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    val showPageNumber: Preference<Boolean> = preferenceStore.getBoolean("pref_show_page_number_key", true)

    val showReadingMode: Preference<Boolean> = preferenceStore.getBoolean("pref_show_reading_mode", true)

    val fullscreen: Preference<Boolean> = preferenceStore.getBoolean("fullscreen", true)

    val drawUnderCutout: Preference<Boolean> = preferenceStore.getBoolean("cutout_short", true)

    val keepScreenOn: Preference<Boolean> = preferenceStore.getBoolean("pref_keep_screen_on_key", false)

    val defaultReadingMode: Preference<Int> = preferenceStore.getInt(
        "pref_default_reading_mode_key",
        ReadingMode.RIGHT_TO_LEFT.flagValue,
    )

    val readingModeAutoRulesEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_reading_mode_auto_rules_enabled",
        false,
    )

    val readingModeAutoRules: Preference<ReadingModeAutoRulesConfig> = preferenceStore.getObjectFromString(
        key = "pref_reading_mode_auto_rules_v1",
        defaultValue = ReadingModeAutoRulesConfig().withPresetsMerged(),
        serializer = { readingModeAutoRulesJson.encodeToString(it) },
        deserializer = { str ->
            if (str.isBlank()) {
                ReadingModeAutoRulesConfig().withPresetsMerged()
            } else {
                try {
                    readingModeAutoRulesJson.decodeFromString<ReadingModeAutoRulesConfig>(str).withPresetsMerged()
                } catch (_: Exception) {
                    ReadingModeAutoRulesConfig().withPresetsMerged()
                }
            }
        },
    )

    val defaultOrientationType: Preference<Int> = preferenceStore.getInt(
        "pref_default_orientation_type_key",
        ReaderOrientation.FREE.flagValue,
    )

    val webtoonDoubleTapZoomEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_enable_double_tap_zoom_webtoon",
        true,
    )

    val imageScaleType: Preference<Int> = preferenceStore.getInt("pref_image_scale_type_key", 1)

    val zoomStart: Preference<Int> = preferenceStore.getInt("pref_zoom_start_key", 1)

    val readerTheme: Preference<Int> = preferenceStore.getInt("pref_reader_theme_key", 1)

    val alwaysShowChapterTransition: Preference<Boolean> = preferenceStore.getBoolean(
        "always_show_chapter_transition",
        true,
    )

    val cropBorders: Preference<Boolean> = preferenceStore.getBoolean("crop_borders", false)

    val navigateToPan: Preference<Boolean> = preferenceStore.getBoolean("navigate_pan", true)

    val landscapeZoom: Preference<Boolean> = preferenceStore.getBoolean("landscape_zoom", true)

    val cropBordersWebtoon: Preference<Boolean> = preferenceStore.getBoolean("crop_borders_webtoon", false)

    val webtoonSidePadding: Preference<Int> = preferenceStore.getInt("webtoon_side_padding", WEBTOON_PADDING_MIN)

    val readerHideThreshold: Preference<ReaderHideThreshold> = preferenceStore.getEnum(
        "reader_hide_threshold",
        ReaderHideThreshold.LOW,
    )

    val folderPerManga: Preference<Boolean> = preferenceStore.getBoolean("create_folder_per_manga", false)

    val skipRead: Preference<Boolean> = preferenceStore.getBoolean("skip_read", false)

    val skipFiltered: Preference<Boolean> = preferenceStore.getBoolean("skip_filtered", true)

    val skipDupe: Preference<Boolean> = preferenceStore.getBoolean("skip_dupe", false)

    val webtoonDisableZoomOut: Preference<Boolean> = preferenceStore.getBoolean("webtoon_disable_zoom_out", false)

    // endregion

    // region Split two-page spread

    val dualPageSplitPaged: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_split", false)

    val dualPageInvertPaged: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_invert", false)

    val dualPageSplitWebtoon: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    val dualPageInvertWebtoon: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    val dualPageRotateToFit: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_rotate", false)

    val dualPageRotateToFitInvert: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert",
        false,
    )

    val dualPageRotateToFitWebtoon: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_webtoon",
        false,
    )

    val dualPageRotateToFitInvertWebtoon: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert_webtoon",
        false,
    )

    // endregion

    // region Color filter

    val customBrightness: Preference<Boolean> = preferenceStore.getBoolean("pref_custom_brightness_key", false)

    val customBrightnessValue: Preference<Int> = preferenceStore.getInt("custom_brightness_value", 0)

    val colorFilter: Preference<Boolean> = preferenceStore.getBoolean("pref_color_filter_key", false)

    val colorFilterValue: Preference<Int> = preferenceStore.getInt("color_filter_value", 0)

    val colorFilterMode: Preference<Int> = preferenceStore.getInt("color_filter_mode", 0)

    val grayscale: Preference<Boolean> = preferenceStore.getBoolean("pref_grayscale", false)

    val invertedColors: Preference<Boolean> = preferenceStore.getBoolean("pref_inverted_colors", false)

    // endregion

    // region Controls

    val readWithLongTap: Preference<Boolean> = preferenceStore.getBoolean("reader_long_tap", true)

    val readWithVolumeKeys: Preference<Boolean> = preferenceStore.getBoolean("reader_volume_keys", false)

    val readWithVolumeKeysInverted: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_volume_keys_inverted",
        false,
    )

    val navigationModePager: Preference<Int> = preferenceStore.getInt("reader_navigation_mode_pager", 0)

    val navigationModeWebtoon: Preference<Int> = preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    val pagerNavInverted: Preference<TappingInvertMode> = preferenceStore.getEnum(
        "reader_tapping_inverted",
        TappingInvertMode.NONE,
    )

    val webtoonNavInverted: Preference<TappingInvertMode> = preferenceStore.getEnum(
        "reader_tapping_inverted_webtoon",
        TappingInvertMode.NONE,
    )

    val showNavigationOverlayNewUser: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_navigation_overlay_new_user",
        true,
    )

    val showNavigationOverlayOnStart: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_navigation_overlay_on_start",
        false,
    )

    // endregion

    enum class FlashColor {
        BLACK,
        WHITE,
        WHITE_BLACK,
    }

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

        const val MILLI_CONVERSION = 100

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
