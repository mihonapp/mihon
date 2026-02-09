package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsNovelReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_novel

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerPref = remember { Injekt.get<ReaderPreferences>() }
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            getDisplayGroup(readerPref),
            getTextGroup(readerPref, navigator),
            getFormattingGroup(readerPref),
            getNavigationGroup(readerPref),
            getAutoScrollGroup(readerPref),
            getContentGroup(readerPref),
            getTtsGroup(readerPref),
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelTheme(),
                    entries = mapOf(
                        "light" to "Light",
                        "dark" to "Dark",
                        "sepia" to "Sepia",
                        "black" to "Black",
                        "grey" to "Grey",
                    ).toImmutableMap(),
                    title = stringResource(MR.strings.pref_novel_theme),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelRenderingMode(),
                    entries = mapOf(
                        "default" to "Native (TextView)",
                        "webview" to "WebView",
                    ).toImmutableMap(),
                    title = "Rendering mode",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.fullscreen(),
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber(),
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelShowProgressSlider(),
                    title = "Show progress slider",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelKeepScreenOn(),
                    title = "Keep screen on",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelCustomBrightness(),
                    title = "Custom brightness",
                ),
            ),
        )
    }

    @Composable
    private fun getTextGroup(readerPreferences: ReaderPreferences, navigator: cafe.adriel.voyager.navigator.Navigator): Preference.PreferenceGroup {
        val fontSize = readerPreferences.novelFontSize().collectAsState().value
        val lineHeight = readerPreferences.novelLineHeight().collectAsState().value

        return Preference.PreferenceGroup(
            title = "Text",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = fontSize,
                    valueRange = 10..40,
                    title = stringResource(MR.strings.pref_font_size),
                    valueString = "${fontSize}px",
                    onValueChanged = {
                        readerPreferences.novelFontSize().set(it)
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelFontFamily(),
                    entries = mapOf(
                        "sans-serif" to "Sans Serif",
                        "serif" to "Serif",
                        "monospace" to "Monospace",
                        "Georgia, serif" to "Georgia",
                        "Times New Roman, serif" to "Times New Roman",
                        "Arial, sans-serif" to "Arial",
                    ).toImmutableMap(),
                    title = stringResource(MR.strings.pref_font_family),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Font Manager",
                    subtitle = "Download or import custom fonts",
                    onClick = { navigator.push(FontManagerScreen()) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (lineHeight * 10).toInt(),
                    valueRange = 10..30,
                    title = stringResource(MR.strings.pref_novel_line_height),
                    valueString = "${lineHeight}x",
                    onValueChanged = {
                        readerPreferences.novelLineHeight().set(it / 10f)
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelTextAlign(),
                    entries = mapOf(
                        "left" to "Left",
                        "center" to "Center",
                        "right" to "Right",
                        "justify" to "Justify",
                    ).toImmutableMap(),
                    title = stringResource(MR.strings.pref_novel_text_align),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelForceTextLowercase(),
                    title = "Force lowercase",
                    subtitle = "Convert all text to lowercase",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelUseOriginalFonts(),
                    title = "Use original fonts (WebView only)",
                    subtitle = "Preserve fonts from the source website",
                ),
            ),
        )
    }

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelVolumeKeysScroll(),
                    title = stringResource(MR.strings.pref_novel_volume_keys_scroll),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelTapToScroll(),
                    title = stringResource(MR.strings.pref_novel_tap_to_scroll),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelSwipeNavigation(),
                    title = "Swipe navigation",
                    subtitle = "Swipe left/right to change chapters",
                ),
            ),
        )
    }

    @Composable
    private fun getAutoScrollGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val autoScrollSpeed = readerPreferences.novelAutoScrollSpeed().collectAsState().value

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_novel_auto_scroll),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = autoScrollSpeed,
                    valueRange = 5..120,
                    title = stringResource(MR.strings.pref_novel_auto_scroll_speed),
                    valueString = "${autoScrollSpeed}s per screen",
                    onValueChanged = {
                        readerPreferences.novelAutoScrollSpeed().set(it)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getFormattingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val paragraphIndent = readerPreferences.novelParagraphIndent().collectAsState().value
        val paragraphSpacing = readerPreferences.novelParagraphSpacing().collectAsState().value
        val marginLeft = readerPreferences.novelMarginLeft().collectAsState().value
        val marginRight = readerPreferences.novelMarginRight().collectAsState().value
        val marginTop = readerPreferences.novelMarginTop().collectAsState().value
        val marginBottom = readerPreferences.novelMarginBottom().collectAsState().value

        return Preference.PreferenceGroup(
            title = "Formatting",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = (paragraphIndent * 10).toInt(),
                    valueRange = 0..50,
                    title = "Paragraph indent",
                    valueString = "${paragraphIndent}em",
                    onValueChanged = { readerPreferences.novelParagraphIndent().set(it / 10f) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (paragraphSpacing * 10).toInt(),
                    valueRange = 0..30,
                    title = "Paragraph spacing",
                    valueString = "${paragraphSpacing}em",
                    onValueChanged = { readerPreferences.novelParagraphSpacing().set(it / 10f) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = marginLeft,
                    valueRange = 0..64,
                    title = "Margin left",
                    valueString = "${marginLeft}dp",
                    onValueChanged = { readerPreferences.novelMarginLeft().set(it) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = marginRight,
                    valueRange = 0..64,
                    title = "Margin right",
                    valueString = "${marginRight}dp",
                    onValueChanged = { readerPreferences.novelMarginRight().set(it) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = marginTop,
                    valueRange = 0..64,
                    title = "Margin top",
                    valueString = "${marginTop}dp",
                    onValueChanged = { readerPreferences.novelMarginTop().set(it) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = marginBottom,
                    valueRange = 0..64,
                    title = "Margin bottom",
                    valueString = "${marginBottom}dp",
                    onValueChanged = { readerPreferences.novelMarginBottom().set(it) },
                ),
            ),
        )
    }

    @Composable
    private fun getContentGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val autoLoadNextAt = readerPreferences.novelAutoLoadNextChapterAt().collectAsState().value

        return Preference.PreferenceGroup(
            title = "Content",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelInfiniteScroll(),
                    title = "Infinite scroll",
                    subtitle = "Load next chapter automatically while scrolling",
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = autoLoadNextAt,
                    valueRange = 50..100,
                    title = "Auto-load next chapter at",
                    valueString = "${autoLoadNextAt}%",
                    onValueChanged = { readerPreferences.novelAutoLoadNextChapterAt().set(it) },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelHideChapterTitle(),
                    title = "Hide chapter title",
                    subtitle = "Strip chapter title from content",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelBlockMedia(),
                    title = "Block media",
                    subtitle = "Block images and media loading in both readers",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelTextSelectable(),
                    title = "Text selectable",
                    subtitle = "Allow selecting and copying text",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelShowRawHtml(),
                    title = "Show raw HTML",
                    subtitle = "Display HTML source instead of rendered content",
                ),
            ),
        )
    }

    @Composable
    private fun getTtsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val ttsSpeed = readerPreferences.novelTtsSpeed().collectAsState().value
        val ttsPitch = readerPreferences.novelTtsPitch().collectAsState().value

        return Preference.PreferenceGroup(
            title = "Text-to-Speech",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = (ttsSpeed * 10).toInt(),
                    valueRange = 1..30,
                    title = "TTS speed",
                    valueString = "${ttsSpeed}x",
                    onValueChanged = { readerPreferences.novelTtsSpeed().set(it / 10f) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (ttsPitch * 10).toInt(),
                    valueRange = 1..30,
                    title = "TTS pitch",
                    valueString = "${ttsPitch}x",
                    onValueChanged = { readerPreferences.novelTtsPitch().set(it / 10f) },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.novelTtsAutoNextChapter(),
                    title = "TTS auto-next chapter",
                    subtitle = "Automatically continue to next chapter when TTS finishes",
                ),
            ),
        )
    }
}
