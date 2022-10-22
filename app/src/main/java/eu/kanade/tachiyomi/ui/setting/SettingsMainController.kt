package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.with
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import eu.kanade.presentation.components.TwoPanelBox
import eu.kanade.presentation.more.settings.screen.AboutScreen
import eu.kanade.presentation.more.settings.screen.SettingsBackupScreen
import eu.kanade.presentation.more.settings.screen.SettingsGeneralScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.util.system.isTabletUi

class SettingsMainController(bundle: Bundle = bundleOf()) : BasicFullComposeController(bundle) {

    private val toBackupScreen = args.getBoolean(TO_BACKUP_SCREEN)
    private val toAboutScreen = args.getBoolean(TO_ABOUT_SCREEN)

    /**
     * Mimics [eu.kanade.tachiyomi.ui.base.controller.OneWayFadeChangeHandler]
     */
    private val transition = fadeIn(
        animationSpec = tween(
            easing = LinearEasing,
        ),
    ) with ExitTransition.None

    @Composable
    override fun ComposeContent() {
        CompositionLocalProvider(LocalRouter provides router) {
            val configuration = LocalConfiguration.current
            val isTabletUi = remember { configuration.isTabletUi() } // won't survive config change
            if (!isTabletUi) {
                Navigator(
                    screen = if (toBackupScreen) {
                        SettingsBackupScreen()
                    } else if (toAboutScreen) {
                        AboutScreen()
                    } else {
                        SettingsMainScreen
                    },
                    content = {
                        CompositionLocalProvider(LocalBackPress provides this::back) {
                            ScreenTransition(
                                navigator = it,
                                transition = { transition },
                            )
                        }
                    },
                )
            } else {
                Navigator(
                    screen = if (toBackupScreen) {
                        SettingsBackupScreen()
                    } else if (toAboutScreen) {
                        AboutScreen()
                    } else {
                        SettingsGeneralScreen()
                    },
                ) {
                    TwoPanelBox(
                        startContent = {
                            CompositionLocalProvider(LocalBackPress provides this@SettingsMainController::back) {
                                SettingsMainScreen.Content(twoPane = true)
                            }
                        },
                        endContent = {
                            ScreenTransition(
                                navigator = it,
                                transition = { transition },
                            )
                        },
                    )
                }
            }
        }
    }

    private fun back() {
        activity?.onBackPressed()
    }

    companion object {
        fun toBackupScreen(): SettingsMainController {
            return SettingsMainController(bundleOf(TO_BACKUP_SCREEN to true))
        }

        fun toAboutScreen(): SettingsMainController {
            return SettingsMainController(bundleOf(TO_ABOUT_SCREEN to true))
        }
    }
}

private const val TO_BACKUP_SCREEN = "to_backup_screen"
private const val TO_ABOUT_SCREEN = "to_about_screen"
