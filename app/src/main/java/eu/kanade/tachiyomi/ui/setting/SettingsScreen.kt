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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.TwoPanelBox

class SettingsScreen(
    private val destination: Int? = null,
) : Screen() {

    constructor(destination: Destination) : this(destination.id)

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        if (!isTabletUi()) {
            Navigator(
                screen = when (destination) {
                    Destination.About.id -> AboutScreen
                    Destination.DataAndStorage.id -> SettingsDataScreen
                    Destination.Tracking.id -> SettingsTrackingScreen
                    else -> SettingsMainScreen
                },
                content = {
                    val pop: () -> Unit = {
                        if (it.canPop) {
                            it.pop()
                        } else {
                            parentNavigator.pop()
                        }
                    }
                    CompositionLocalProvider(LocalBackPress provides pop) {
                        DefaultNavigatorScreenTransition(navigator = it)
                    }
                },
            )
        } else {
            Navigator(
                screen = when (destination) {
                    Destination.About.id -> AboutScreen
                    Destination.DataAndStorage.id -> SettingsDataScreen
                    Destination.Tracking.id -> SettingsTrackingScreen
                    else -> SettingsAppearanceScreen
                },
            ) {
                val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                TwoPanelBox(
                    modifier = Modifier
                        .windowInsetsPadding(insets)
                        .consumeWindowInsets(insets),
                    startContent = {
                        CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                            SettingsMainScreen.Content(twoPane = true)
                        }
                    },
                    endContent = { DefaultNavigatorScreenTransition(navigator = it) },
                )
            }
        }
    }

    sealed class Destination(val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Tracking : Destination(2)
    }
}
