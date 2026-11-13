package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.browse.components.LinkedSourceScanlatorDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.UUID

class LinkedSourceSearchScreen(
    private val mangaId: Long,
    private val title: String,
    private val sessionId: String = UUID.randomUUID().toString(),
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<GlobalSearchViewModel, GlobalSearchViewModel.Factory> {
            create(initialQuery = title, initialExtensionFilter = null, linkedSourceSearch = true)
        }
        val selection = assistedMetroViewModel<LinkedSourceSelectionViewModel, LinkedSourceSelectionViewModel.Factory> {
            create(mangaId = mangaId, sessionId = sessionId)
        }
        val state by viewModel.state.collectAsState()
        val manga by selection.manga.collectAsState()
        val draft by selection.draft.collectAsState()
        val pendingScanlators by selection.scanlatorSelection.collectAsState()
        val links = draft.links.orEmpty()
        LaunchedEffect(draft.applied) {
            if (draft.applied) navigator.pop()
        }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(selection) {
            selection.errors.collect { snackbar.showSnackbar(it) }
        }

        LinkedSourceScanlatorDialog(selection)
        GlobalSearchScreen(
            state = state.copy(items = state.items.filterKeys { it.id != manga?.source }),
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            getManga = { viewModel.getManga(it) },
            onClickSource = {
                navigator.push(
                    BrowseSourceScreen(it.id, state.searchQuery, linkedMangaId = mangaId, linkedSessionId = sessionId),
                )
            },
            onClickItem = selection::toggleSource,
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            isSelected = { candidate -> links.any { it.sourceId == candidate.source && it.url == candidate.url } },
            selectionHint = stringResource(MR.strings.linked_sources_search_hint, title),
            snackbarHostState = snackbar,
            onApply = selection::apply,
            onClear = selection::clear,
            selectionActionsEnabled = draft.links != null && !draft.saving && pendingScanlators == null,
        )
    }
}
