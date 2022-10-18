package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.os.bundleOf
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import eu.kanade.presentation.components.TwoPanelBox
import eu.kanade.presentation.more.settings.screen.AboutScreen
import eu.kanade.presentation.more.settings.screen.SettingsBackupScreen
import eu.kanade.presentation.more.settings.screen.SettingsGeneralScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.presentation.util.calculateWindowWidthSizeClass
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

class SettingsMainController(bundle: Bundle = bundleOf()) : BasicFullComposeController(bundle) {

    private val toBackupScreen = args.getBoolean(TO_BACKUP_SCREEN)
    private val toAboutScreen = args.getBoolean(TO_ABOUT_SCREEN)

    @Composable
    override fun ComposeContent() {
        CompositionLocalProvider(LocalRouter provides router) {
            val widthSizeClass = calculateWindowWidthSizeClass()
            if (widthSizeClass == WindowWidthSizeClass.Compact) {
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
                            val slideDistance = rememberSlideDistance()
                            ScreenTransition(
                                navigator = it,
                                transition = {
                                    materialSharedAxisX(
                                        forward = it.lastEvent != StackEvent.Pop,
                                        slideDistance = slideDistance,
                                    )
                                },
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
                            val slideDistance = rememberSlideDistance()
                            ScreenTransition(
                                navigator = it,
                                transition = {
                                    materialSharedAxisX(
                                        forward = it.lastEvent != StackEvent.Pop,
                                        slideDistance = slideDistance,
                                    )
                                },
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
