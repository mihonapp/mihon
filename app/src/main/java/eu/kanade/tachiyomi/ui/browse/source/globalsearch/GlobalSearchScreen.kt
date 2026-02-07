package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.domain.base.BasePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import mihon.core.dualscreen.DualScreenState

class GlobalSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
        }

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    SearchItemResult.Loading -> return@LaunchedEffect
                    is SearchItemResult.Success -> {
                        val manga = result.result.singleOrNull()
                        if (manga != null) {
                            navigator.replace(MangaScreen(manga.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            GlobalSearchScreen(
                state = state,
                navigateUp = DualScreenState.navigateUpOr { navigator.pop() },
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getManga = { screenModel.getManga(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = {
                    val preferences = Injekt.get<BasePreferences>()
                    if (preferences.enableDualScreenMode().get()) {
                        DualScreenState.openScreen(MangaScreen(it.id, true))
                    } else {
                        navigator.push(MangaScreen(it.id, true))
                    }
                },
                onLongClickItem = {
                    val preferences = Injekt.get<BasePreferences>()
                    if (preferences.enableDualScreenMode().get()) {
                        DualScreenState.openScreen(MangaScreen(it.id, true))
                    } else {
                        navigator.push(MangaScreen(it.id, true))
                    }
                },
            )
        }
    }
}
