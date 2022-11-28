package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class SettingsMainController(bundle: Bundle = bundleOf()) : BasicFullComposeController(bundle) {

    private val toBackupScreen = args.getBoolean(TO_BACKUP_SCREEN)
    private val toAboutScreen = args.getBoolean(TO_ABOUT_SCREEN)

    @Composable
    override fun ComposeContent() {
        Navigator(
            screen = when {
                toBackupScreen -> SettingsScreen.toBackupScreen()
                toAboutScreen -> SettingsScreen.toAboutScreen()
                else -> SettingsScreen.toMainScreen()
            },
        )
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
