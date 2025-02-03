package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
            getPaddingGroup(uiPreferences = uiPreferences),
            getCornerGroup(uiPreferences = uiPreferences)
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = { appThemePref.set(it) },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    pref = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.tabletUiMode(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.dateFormat(),
                    title = stringResource(MR.strings.pref_date_format),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun getPaddingGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val bookPaddingPref = uiPreferences.bookPadding()
        val bookPadding by bookPaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = "边距",//i18n
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = bookPadding,
                    min = 0,
                    max = 8,
                    title = "书间的边距",
//                    subtitle = stringResource(MR.strings.pref_flash_duration_summary, flashMillis),
                    subtitle = "边距 $bookPadding.dp",
                    onValueChanged = {
                        bookPaddingPref.set(it)
                        true
                    },
                    enabled = true,
                )
            ),
        )
    }

    @Composable
    private fun getCornerGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val shapeELPref = uiPreferences.cornerEL()
        val shapeLPref = uiPreferences.cornerL()
        val shapeMPref = uiPreferences.cornerM()
        val shapeSPref = uiPreferences.cornerS()
        val shapeESPref = uiPreferences.cornerES()

        val shapeEL by shapeELPref.collectAsState()
        val shapeL by shapeLPref.collectAsState()
        val shapeM by shapeMPref.collectAsState()
        val shapeS by shapeSPref.collectAsState()
        val shapeES by shapeESPref.collectAsState()

        return Preference.PreferenceGroup(
            title = "形状",//i18n
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = shapeEL,
                    min = 20,
                    max = 40,
                    title = "超大",
                    subtitle = "圆角 $shapeEL.dp",
                    onValueChanged = {
                        shapeELPref.set(it)
                        true
                    },
                    enabled = true,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = shapeL,
                    min = 0,
                    max = 30,
                    title = "大",
                    subtitle = "圆角 $shapeL.dp",
                    onValueChanged = {
                        shapeLPref.set(it)
                        true
                    },
                    enabled = true,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = shapeM,
                    min = 0,
                    max = 30,
                    title = "中",
                    subtitle = "圆角 $shapeM.dp",
                    onValueChanged = {
                        shapeMPref.set(it)
                        true
                    },
                    enabled = true,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = shapeS,
                    min = 0,
                    max = 20,
                    title = "小",
                    subtitle = "圆角 $shapeS.dp",
                    onValueChanged = {
                        shapeSPref.set(it)
                        true
                    },
                    enabled = true,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = shapeES,
                    min = 0,
                    max = 10,
                    title = "超小",
                    subtitle = "圆角 $shapeES.dp",
                    onValueChanged = {
                        shapeESPref.set(it)
                        true
                    },
                    enabled = true,
                ),
            ),
        )
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
