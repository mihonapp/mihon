package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.isTablet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitle(): String = stringResource(id = R.string.pref_category_appearance)

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val themeModePref = uiPreferences.themeMode()
        val appThemePref = uiPreferences.appTheme()
        val amoledPref = uiPreferences.themeDarkAmoled()

        val themeMode by themeModePref.collectAsState()

        LaunchedEffect(Unit) {
            merge(appThemePref.changes(), amoledPref.changes())
                .drop(2)
                .collectLatest { (context as? Activity)?.let { ActivityCompat.recreate(it) } }
        }

        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = themeModePref,
                title = stringResource(id = R.string.pref_category_theme),
                subtitle = "%s",
                entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mapOf(
                        ThemeMode.SYSTEM to stringResource(id = R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(id = R.string.theme_light),
                        ThemeMode.DARK to stringResource(id = R.string.theme_dark),
                    )
                } else {
                    mapOf(
                        ThemeMode.LIGHT to stringResource(id = R.string.theme_light),
                        ThemeMode.DARK to stringResource(id = R.string.theme_dark),
                    )
                },
            ),
            Preference.PreferenceItem.AppThemePreference(
                title = stringResource(id = R.string.pref_app_theme),
                pref = appThemePref,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = amoledPref,
                title = stringResource(id = R.string.pref_dark_theme_pure_black),
                enabled = themeMode != ThemeMode.LIGHT,
            ),
            getNavigationGroup(context = context, uiPreferences = uiPreferences),
            getTimestampGroup(uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getNavigationGroup(
        context: Context,
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(id = R.string.pref_category_navigation),
            enabled = remember(context) { context.isTablet() },
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.sideNavIconAlignment(),
                    title = stringResource(id = R.string.pref_side_nav_icon_alignment),
                    subtitle = "%s",
                    entries = mapOf(
                        0 to stringResource(id = R.string.alignment_top),
                        1 to stringResource(id = R.string.alignment_center),
                        2 to stringResource(id = R.string.alignment_bottom),
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun getTimestampGroup(uiPreferences: UiPreferences): Preference.PreferenceGroup {
        val now = remember { Date().time }
        return Preference.PreferenceGroup(
            title = stringResource(id = R.string.pref_category_timestamps),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.relativeTime(),
                    title = stringResource(id = R.string.pref_relative_format),
                    subtitle = "%s",
                    entries = mapOf(
                        0 to stringResource(id = R.string.off),
                        2 to stringResource(id = R.string.pref_relative_time_short),
                        7 to stringResource(id = R.string.pref_relative_time_long),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.dateFormat(),
                    title = stringResource(id = R.string.pref_date_format),
                    subtitle = "%s",
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(id = R.string.label_default) }} ($formattedDate)"
                        },
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
