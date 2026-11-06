package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import cafe.adriel.voyager.core.screen.Screen
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.LocalBackPress

/**
 * Identifies a preference to highlight after navigating from the settings search.
 *
 * A title isn't unique by itself: a group heading and a preference item can share one,
 * and so can multiple preferences on the same screen.
 *
 * @param title title of the group heading or preference item.
 * @param isGroupHeader whether the target is a group heading instead of a preference item.
 * @param occurrence index among the visible entries with the same title and type.
 */
data class PreferenceHighlightKey(
    val title: String,
    val isGroupHeader: Boolean = false,
    val occurrence: Int = 0,
)

interface SearchableSettings : Screen {

    @Composable
    @ReadOnlyComposable
    fun getTitleRes(): StringResource

    @Composable
    fun getPreferences(): List<Preference>

    @Composable
    fun RowScope.AppBarAction() {
    }

    @Composable
    override fun Content() {
        val handleBack = LocalBackPress.current
        PreferenceScaffold(
            titleRes = getTitleRes(),
            onBackPressed = if (handleBack != null) handleBack::invoke else null,
            actions = { AppBarAction() },
            itemsProvider = { getPreferences() },
        )
    }

    companion object {
        // HACK: for the background blipping thingy.
        // The target PreferenceItem
        // Set before showing the destination screen and reset after
        // See BasePreferenceWidget.highlightBackground
        var highlightKey: PreferenceHighlightKey? = null
    }
}
