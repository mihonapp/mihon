package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.core.screen.Screen
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.LocalBackButtonVisibility
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalBackStack

interface SearchableRoute : NavKey, SearchableSettings

interface SearchableSettings {

    @Composable
    @ReadOnlyComposable
    fun getTitleRes(): StringResource

    @Composable
    fun getPreferences(): List<Preference>

    @Composable
    fun RowScope.AppBarAction() {
    }

    @Composable
    fun Content() {
        val backStack = LocalBackStack.current
        val backVisibility = LocalBackButtonVisibility.current

        PreferenceScaffold(
            titleRes = getTitleRes(),
            onBackPressed = {
                backStack.removeLastOrNull()
                Unit
            }.takeIf { backVisibility },
            actions = { AppBarAction() },
            itemsProvider = { getPreferences() },
        )
    }

    companion object {
        // HACK: for the background blipping thingy.
        // The title of the target PreferenceItem
        // Set before showing the destination screen and reset after
        // See BasePreferenceWidget.highlightBackground
        var highlightKey: String? = null
    }
}
