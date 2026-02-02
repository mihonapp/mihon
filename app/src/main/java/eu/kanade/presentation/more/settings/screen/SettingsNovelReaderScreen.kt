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
            getNavigationGroup(readerPref),
            getAutoScrollGroup(readerPref),
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
                    ).toImmutableMap(),
                    title = stringResource(MR.strings.pref_novel_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.fullscreen(),
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber(),
                    title = stringResource(MR.strings.pref_show_page_number),
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
}
