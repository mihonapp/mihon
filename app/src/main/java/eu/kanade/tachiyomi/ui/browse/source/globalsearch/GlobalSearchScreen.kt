package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.GlobalSearchScreen
import mihon.core.navigation.BrowseSourceRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun GlobalSearchScreen(
    searchQuery: String,
    extensionFilter: String?,
) {
    val navigator = LocalNavigator.current

    val viewModel =
        assistedMetroViewModel<GlobalSearchViewModel, GlobalSearchViewModel.Factory> {
            create(initialQuery = searchQuery, initialExtensionFilter = extensionFilter)
        }
    val state by viewModel.state.collectAsState()
    var autoOpenPending by rememberSaveable(searchQuery, extensionFilter) {
        mutableStateOf(searchQuery.isNotBlank() && !extensionFilter.isNullOrBlank())
    }

    if (autoOpenPending) {
        LoadingScreen()

        val result = state.items.values.singleOrNull()
        LaunchedEffect(state.hasResolvedSources, result) {
            if (!state.hasResolvedSources || result == SearchItemResult.Loading) return@LaunchedEffect

            val manga = (result as? SearchItemResult.Success)?.result?.singleOrNull()
            if (manga != null) {
                navigator.replace(MangaRoute(manga.id, true))
            } else {
                autoOpenPending = false
            }
        }
    } else {
        GlobalSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            getManga = { viewModel.getManga(it) },
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            onClickSource = {
                navigator.push(BrowseSourceRoute(it.id, state.searchQuery))
            },
            onClickItem = { navigator.push(MangaRoute(it.id, true)) },
            onLongClickItem = { navigator.push(MangaRoute(it.id, true)) },
        )
    }
}
