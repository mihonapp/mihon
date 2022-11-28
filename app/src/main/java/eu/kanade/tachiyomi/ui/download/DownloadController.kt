package eu.kanade.tachiyomi.ui.download

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

/**
 * Controller that shows the currently active downloads.
 */
class DownloadController : BasicFullComposeController() {
    @Composable
    override fun ComposeContent() {
        Navigator(screen = DownloadQueueScreen)
    }
}
