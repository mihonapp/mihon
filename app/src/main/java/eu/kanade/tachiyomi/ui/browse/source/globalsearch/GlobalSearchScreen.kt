package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.replace
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceRoute
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.serialization.Serializable
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data class GlobalSearchRoute(
    val searchQuery: String = "",
    val extensionFilter: String? = null,
) : NavKey

@Composable
fun GlobalSearchScreen(
    searchQuery: String,
    extensionFilter: String?,
) {
    val backStack = LocalBackStack.current

    val viewModel =
        assistedMetroViewModel<GlobalSearchViewModel, GlobalSearchViewModel.Factory> {
            create(initialQuery = searchQuery, initialExtensionFilter = extensionFilter)
        }
    val state by viewModel.state.collectAsState()
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
                        backStack.replace(MangaRoute(manga.id, true))
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
            navigateUp = backStack::removeLastOrNull,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            getManga = { viewModel.getManga(it) },
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            onClickSource = {
                backStack.add(BrowseSourceRoute(it.id, state.searchQuery))
            },
            onClickItem = { backStack.add(MangaRoute(it.id, true)) },
            onLongClickItem = { backStack.add(MangaRoute(it.id, true)) },
        )
    }
}
