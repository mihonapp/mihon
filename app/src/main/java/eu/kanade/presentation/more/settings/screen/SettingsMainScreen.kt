package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.R

object SettingsMainScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    @StringRes
    override fun getTitleRes() = R.string.label_settings

    @Composable
    @NonRestartableComposable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_general),
                icon = Icons.Outlined.Tune,
                onClick = { navigator.push(SettingsGeneralScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_appearance),
                icon = Icons.Outlined.Palette,
                onClick = { navigator.push(SettingsAppearanceScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_library),
                icon = Icons.Outlined.CollectionsBookmark,
                onClick = { navigator.push(SettingsLibraryScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_reader),
                icon = Icons.Outlined.ChromeReaderMode,
                onClick = { navigator.push(SettingsReaderScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_downloads),
                icon = Icons.Outlined.GetApp,
                onClick = { navigator.push(SettingsDownloadScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_tracking),
                icon = Icons.Outlined.Sync,
                onClick = { navigator.push(SettingsTrackingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.browse),
                icon = Icons.Outlined.Explore,
                onClick = { navigator.push(SettingsBrowseScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.label_backup),
                icon = Icons.Outlined.SettingsBackupRestore,
                onClick = { navigator.push(SettingsBackupScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_security),
                icon = Icons.Outlined.Security,
                onClick = { navigator.push(SettingsSecurityScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_advanced),
                icon = Icons.Outlined.Code,
                onClick = { navigator.push(SettingsAdvancedScreen()) },
            ),
        )
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        PreferenceScaffold(
            titleRes = getTitleRes(),
            actions = {
                AppBarActions(
                    listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_search),
                            icon = Icons.Outlined.Search,
                            onClick = { navigator.push(SettingsSearchScreen()) },
                        ),
                    ),
                )
            },
            onBackPressed = backPress::invoke,
            itemsProvider = { getPreferences() },
        )
    }
}
