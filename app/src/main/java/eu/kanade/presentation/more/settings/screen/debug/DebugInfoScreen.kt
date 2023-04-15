package eu.kanade.presentation.more.settings.screen.debug

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.R

object DebugInfoScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_debug_info

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = WorkerInfoScreen.title,
                onClick = { navigator.push(WorkerInfoScreen) },
            ),
        )
    }
}
