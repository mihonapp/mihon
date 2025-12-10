package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class ReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // region General

    fun pageTransitions() = preferenceStore.getBoolean("pref_enable_transitions_key", true)

    fun flashOnPageChange() = preferenceStore.getBoolean("pref_reader_flash", false)

    fun flashDurationMillis() = preferenceStore.getInt("pref_reader_flash_duration", MILLI_CONVERSION)

    fun flashPageInterval() = preferenceStore.getInt("pref_reader_flash_interval", 1)

    fun flashColor() = preferenceStore.getEnum("pref_reader_flash_mode", FlashColor.BLACK)

    fun doubleTapAnimSpeed() = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = preferenceStore.getBoolean("pref_show_page_number_key", true)

    fun showReadingMode() = preferenceStore.getBoolean("pref_show_reading_mode", true)

    fun fullscreen() = preferenceStore.getBoolean("fullscreen", true)

    fun drawUnderCutout() = preferenceStore.getBoolean("cutout_short", true)

    fun keepScreenOn() = preferenceStore.getBoolean("pref_keep_screen_on_key", false)

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

    fun webtoonDisableZoomOut() = preferenceStore.getBoolean("webtoon_disable_zoom_out", false)

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

    // region Novel
    fun novelFontSize() = preferenceStore.getInt("pref_novel_font_size", 16)
    fun novelFontFamily() = preferenceStore.getString("pref_novel_font_family", "sans-serif")
    fun novelTheme() = preferenceStore.getString("pref_novel_theme", "light")
    fun novelLineHeight() = preferenceStore.getFloat("pref_novel_line_height", 1.6f)
    fun novelTextAlign() = preferenceStore.getString("pref_novel_text_align", "left")
    fun novelAutoScrollSpeed() = preferenceStore.getInt("pref_novel_auto_scroll_speed", 30)
    fun novelVolumeKeysScroll() = preferenceStore.getBoolean("pref_novel_volume_keys_scroll", false)
    fun novelTapToScroll() = preferenceStore.getBoolean("pref_novel_tap_to_scroll", false)
    fun novelTextSelectable() = preferenceStore.getBoolean("pref_novel_text_selectable", true)

    // Font color (stored as ARGB int, 0 means use theme default)
    // Note: Using 0 instead of -1 because 0xFFFFFFFF (white) = -1 as signed int
    fun novelFontColor() = preferenceStore.getInt("pref_novel_font_color", 0)

    // Background color (stored as ARGB int, 0 means use theme default)
    // Note: Using 0 instead of -1 because 0xFFFFFFFF (white) = -1 as signed int
    fun novelBackgroundColor() = preferenceStore.getInt("pref_novel_background_color", 0)

    // Paragraph indentation in em units (0 = no indent, default 2em)
    fun novelParagraphIndent() = preferenceStore.getFloat("pref_novel_paragraph_indent", 0f)

    // Margin preferences (in dp)
    fun novelMarginLeft() = preferenceStore.getInt("pref_novel_margin_left", 16)
    fun novelMarginRight() = preferenceStore.getInt("pref_novel_margin_right", 16)
    fun novelMarginTop() = preferenceStore.getInt("pref_novel_margin_top", 16)
    fun novelMarginBottom() = preferenceStore.getInt("pref_novel_margin_bottom", 16)

    // Rendering mode: "default" = custom parser, "webview" = WebView rendering
    fun novelRenderingMode() = preferenceStore.getString("pref_novel_rendering_mode", "default")

    // Custom CSS/JS stored as JSON array of {title, code} objects
    fun novelCustomCss() = preferenceStore.getString("pref_novel_custom_css", "")
    fun novelCustomJs() = preferenceStore.getString("pref_novel_custom_js", "")
    fun novelCustomCssSnippets() = preferenceStore.getString("pref_novel_css_snippets", "[]")
    fun novelCustomJsSnippets() = preferenceStore.getString("pref_novel_js_snippets", "[]")

    // Global CSS/JS presets stored as JSON array of {name, css, js} objects
    fun novelGlobalPresets() = preferenceStore.getString("pref_novel_global_presets", "[]")

    // Currently active global preset name (empty = none)
    fun novelActivePreset() = preferenceStore.getString("pref_novel_active_preset", "")

    // Infinite scroll - automatically load next/previous chapters
    fun novelInfiniteScroll() = preferenceStore.getBoolean("pref_novel_infinite_scroll", false)

    // Keep chapters loaded in memory (0 = only current, 1 = current + prev, 2 = current + next, 3 = both)
    fun novelKeepChaptersLoaded() = preferenceStore.getInt("pref_novel_keep_chapters_loaded", 0)

    // Custom brightness for novel reader
    fun novelCustomBrightness() = preferenceStore.getBoolean("pref_novel_custom_brightness", false)

    // Brightness value for novel reader (-75 to 100, 0 = system)
    fun novelCustomBrightnessValue() = preferenceStore.getInt("pref_novel_custom_brightness_value", 0)

    // Show progress slider in novel reader (allows scrolling to position in current chapter)
    fun novelShowProgressSlider() = preferenceStore.getBoolean("pref_novel_show_progress_slider", true)

    // Hide chapter title in novel content
    fun novelHideChapterTitle() = preferenceStore.getBoolean("pref_novel_hide_chapter_title", false)

    // Use source's original fonts (don't force a specific font family)
    fun novelUseOriginalFonts() = preferenceStore.getBoolean("pref_novel_use_original_fonts", false)

    // Chapter sort order for novel reader: "source" = use source order, "chapter_number" = sort by chapter number
    // Default is "source" since many novel sources don't provide proper chapter numbers
    fun novelChapterSortOrder() = preferenceStore.getString("pref_novel_chapter_sort_order", "source")
    // endregion

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
