package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController

class MoreController : BasicFullComposeController(), RootController {

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MoreScreen)
    }

    companion object {
        const val URL_HELP = "https://tachiyomi.org/help/"
    }
}
