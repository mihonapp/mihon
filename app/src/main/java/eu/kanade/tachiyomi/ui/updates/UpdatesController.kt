package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController

class UpdatesController : BasicFullComposeController(), RootController {
    @Composable
    override fun ComposeContent() {
        Navigator(screen = UpdatesScreen)
    }
}
