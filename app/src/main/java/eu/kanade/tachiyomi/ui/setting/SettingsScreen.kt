package eu.kanade.tachiyomi.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.ScreenTransition
import eu.kanade.presentation.components.TwoPanelBox
import eu.kanade.presentation.more.settings.screen.AboutScreen
import eu.kanade.presentation.more.settings.screen.SettingsBackupScreen
import eu.kanade.presentation.more.settings.screen.SettingsGeneralScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Transition
import eu.kanade.presentation.util.isTabletUi

class SettingsScreen private constructor(
    val toBackup: Boolean,
    val toAbout: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        if (!isTabletUi()) {
            Navigator(
                screen = if (toBackup) {
                    SettingsBackupScreen
                } else if (toAbout) {
                    AboutScreen
                } else {
                    SettingsMainScreen
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
                        ScreenTransition(
                            navigator = it,
                            transition = { Transition.OneWayFade },
                        )
                    }
                },
            )
        } else {
            Navigator(
                screen = if (toBackup) {
                    SettingsBackupScreen
                } else if (toAbout) {
                    AboutScreen
                } else {
                    SettingsGeneralScreen
                },
            ) {
                TwoPanelBox(
                    startContent = {
                        CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                            SettingsMainScreen.Content(twoPane = true)
                        }
                    },
                    endContent = {
                        ScreenTransition(
                            navigator = it,
                            transition = { Transition.OneWayFade },
                        )
                    },
                )
            }
        }
    }

    companion object {
        fun toMainScreen() = SettingsScreen(toBackup = false, toAbout = false)

        fun toBackupScreen() = SettingsScreen(toBackup = true, toAbout = false)

        fun toAboutScreen() = SettingsScreen(toBackup = false, toAbout = true)
    }
}
