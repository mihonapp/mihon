package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class GlobalSearchController(
    val searchQuery: String = "",
    val extensionFilter: String = "",
) : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        CompositionLocalProvider(LocalRouter provides router) {
            Navigator(
                screen = GlobalSearchScreen(
                    searchQuery = searchQuery,
                    extensionFilter = extensionFilter,
                ),
            )
        }
    }
}
