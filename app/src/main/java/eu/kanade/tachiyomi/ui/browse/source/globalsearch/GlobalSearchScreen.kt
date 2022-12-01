package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController

class GlobalSearchScreen(
    val searchQuery: String = "",
    val extensionFilter: String = "",
) : Screen {

    @Composable
    override fun Content() {
        val router = LocalRouter.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()

        GlobalSearchScreen(
            state = state,
            navigateUp = router::popCurrentController,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = screenModel::search,
            getManga = { source, manga ->
                screenModel.getManga(
                    source = source,
                    initialManga = manga,
                )
            },
            onClickSource = {
                if (!screenModel.incognitoMode.get()) {
                    screenModel.lastUsedSourceId.set(it.id)
                }
                router.pushController(BrowseSourceController(it.id, state.searchQuery))
            },
            onClickItem = { router.pushController(MangaController(it.id, true)) },
            onLongClickItem = { router.pushController(MangaController(it.id, true)) },
        )
    }
}
