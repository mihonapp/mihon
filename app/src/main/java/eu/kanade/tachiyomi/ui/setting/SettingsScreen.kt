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
import eu.kanade.presentation.more.settings.screen.AboutScreen
import eu.kanade.presentation.more.settings.screen.SettingsBackupScreen
import eu.kanade.presentation.more.settings.screen.SettingsGeneralScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.TwoPanelBox

class SettingsScreen private constructor(
    val toBackup: Boolean,
    val toAbout: Boolean,
) : Screen() {

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
                        DefaultNavigatorScreenTransition(navigator = it)
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

    companion object {
        fun toMainScreen() = SettingsScreen(toBackup = false, toAbout = false)

        fun toBackupScreen() = SettingsScreen(toBackup = true, toAbout = false)

        fun toAboutScreen() = SettingsScreen(toBackup = false, toAbout = true)
    }
}
