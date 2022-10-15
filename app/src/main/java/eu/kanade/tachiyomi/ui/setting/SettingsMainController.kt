package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.os.bundleOf
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import eu.kanade.presentation.more.settings.screen.SettingsBackupScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import soup.compose.material.motion.animation.materialSharedAxisZ

class SettingsMainController : BasicFullComposeController {

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getBoolean(TO_BACKUP_SCREEN))

    constructor(toBackupScreen: Boolean = false) : super(bundleOf(TO_BACKUP_SCREEN to toBackupScreen))

    private val toBackupScreen = args.getBoolean(TO_BACKUP_SCREEN)

    @Composable
    override fun ComposeContent() {
        Navigator(
            screen = if (toBackupScreen) SettingsBackupScreen() else SettingsMainScreen,
            content = {
                CompositionLocalProvider(
                    LocalRouter provides router,
                    LocalBackPress provides this::back,
                ) {
                    ScreenTransition(
                        navigator = it,
                        transition = { materialSharedAxisZ(forward = it.lastEvent != StackEvent.Pop) },
                    )
                }
            },
        )
    }

    private fun back() {
        activity?.onBackPressed()
    }
}

private const val TO_BACKUP_SCREEN = "to_backup_screen"
