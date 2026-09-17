package eu.kanade.tachiyomi.ui.setting

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.isTabletUi
import kotlinx.serialization.Serializable
import tachiyomi.presentation.core.components.TwoPanelBox

@Serializable
data class SettingsRoute(val settingsDestination: SettingsDestination? = null) : NavKey

@Serializable
sealed class SettingsDestination(val id: Int) {
    @Serializable
    data object About : SettingsDestination(0)

    @Serializable
    data object DataAndStorage : SettingsDestination(1)

    @Serializable
    data object Tracking : SettingsDestination(2)
}

@Composable
fun SettingsScreen(settingsDestination: SettingsDestination?) {
    val backStack = LocalBackStack.current
    // TODO(nav): twopane
    // if (!isTabletUi()) {
    //     Navigator(
    //         screen = when (settingsDestination) {
    //             SettingsDestination.About -> AboutScreen
    //             SettingsDestination.DataAndStorage -> SettingsDataScreen
    //             SettingsDestination.Tracking -> SettingsTrackingScreen
    //             else -> SettingsMainScreen
    //         },
    //         onBackPressed = null,
    //     ) {
    //         val pop: () -> Unit = {
    //             if (it.canPop) {
    //                 it.pop()
    //             } else {
    //                 backStack.removeLastOrNull()
    //             }
    //         }
    //         CompositionLocalProvider(LocalBackPress provides pop) {
    //             DefaultNavigatorScreenTransition(navigator = it)
    //         }
    //     }
    // } else {
    //     Navigator(
    //         screen = when (settingsDestination) {
    //             SettingsDestination.About -> AboutScreen
    //             SettingsDestination.DataAndStorage -> SettingsDataScreen
    //             SettingsDestination.Tracking -> SettingsTrackingScreen
    //             else -> SettingsAppearanceScreen
    //         },
    //         onBackPressed = null,
    //     ) {
    //         val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    //         TwoPanelBox(
    //             modifier = Modifier
    //                 .windowInsetsPadding(insets)
    //                 .consumeWindowInsets(insets),
    //             startContent = {
    //                 CompositionLocalProvider(LocalBackPress provides backStack::removeLastOrNull) {
    //                     SettingsMainScreen.Content(twoPane = true)
    //                 }
    //             },
    //             endContent = { DefaultNavigatorScreenTransition(navigator = it) },
    //         )
    //     }
    // }
}
