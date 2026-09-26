package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.result.LocalResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MigrateSourceSearchRoute
import mihon.core.navigation.MigrationListRoute
import mihon.core.navigation.util.LocalBackStack
import mihon.core.navigation.util.popUntil
import mihon.core.navigation.util.replace
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.feature.migration.list.MatchOverrideEvent

@Composable
fun MigrateSearchScreen(mangaId: Long) {
    val backStack = LocalBackStack.current
    val resultEventBus = LocalResultEventBus.current

    val viewModel =
        assistedMetroViewModel<MigrateSearchViewModel, MigrateSearchViewModel.Factory> { create(mangaId = mangaId) }
    val state by viewModel.state.collectAsState()

    MigrateSearchScreen(
        state = state,
        fromSourceId = state.from?.source,
        navigateUp = backStack::removeLastOrNull,
        onChangeSearchQuery = viewModel::updateSearchQuery,
        onSearch = { viewModel.search() },
        getManga = { viewModel.getManga(it) },
        onChangeSearchFilter = viewModel::setSourceFilter,
        onToggleResults = viewModel::toggleFilterResults,
        onClickSource = { backStack.add(MigrateSourceSearchRoute(state.from!!, it.id, state.searchQuery)) },
        onClickItem = {
            val migrateListRoute = backStack
                .filterIsInstance<MigrationListRoute>()
                .lastOrNull()

            if (migrateListRoute == null) {
                viewModel.setMigrateDialog(mangaId, it)
            } else {
                resultEventBus.sendResult(
                    result = MatchOverrideEvent(current = mangaId, target = it.id),
                )
                backStack.popUntil { screen -> screen is MigrationListRoute }
            }
        },
        onLongClickItem = { backStack.add(MangaRoute(it.id, true)) },
    )

    when (val dialog = state.dialog) {
        is SearchViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                // Initiated from the context of [dialog.current] so we show [dialog.target].
                onClickTitle = { backStack.add(MangaRoute(dialog.target.id, true)) },
                onDismissRequest = { viewModel.clearDialog() },
                onComplete = {
                    if (backStack.last() is MangaRoute) {
                        val lastItem = backStack.last()
                        backStack.popUntil { backStack.contains(lastItem) }
                        backStack.add(MangaRoute(dialog.target.id))
                    } else {
                        backStack.replace(MangaRoute(dialog.target.id))
                    }
                },
            )
        }
        else -> {}
    }
}
