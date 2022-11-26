package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class StatsController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        Navigator(screen = StatsScreen())
    }
}
