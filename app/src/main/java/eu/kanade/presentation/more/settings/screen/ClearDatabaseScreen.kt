package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetSourcesWithNonLibraryManga
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourceWithCount
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.tachiyomi.Database
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ClearDatabaseScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ClearDatabaseScreenModel() }
        val state by model.state.collectAsState()

        when (val s = state) {
            is ClearDatabaseScreenModel.State.Loading -> LoadingScreen()
            is ClearDatabaseScreenModel.State.Ready -> {
                if (s.showConfirmation) {
                    AlertDialog(
                        onDismissRequest = model::hideConfirmation,
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    model.removeMangaBySourceId()
                                    model.clearSelection()
                                    model.hideConfirmation()
                                    context.toast(R.string.clear_database_completed)
                                },
                            ) {
                                Text(text = stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = model::hideConfirmation) {
                                Text(text = stringResource(android.R.string.cancel))
                            }
                        },
                        text = {
                            Text(text = stringResource(R.string.clear_database_confirmation))
                        },
                    )
                }

                Scaffold(
                    topBar = { scrollBehavior ->
                        AppBar(
                            title = stringResource(R.string.pref_clear_database),
                            navigateUp = navigator::pop,
                            actions = {
                                if (s.items.isNotEmpty()) {
                                    AppBarActions(
                                        actions = listOf(
                                            AppBar.Action(
                                                title = stringResource(R.string.action_select_all),
                                                icon = Icons.Outlined.SelectAll,
                                                onClick = model::selectAll,
                                            ),
                                            AppBar.Action(
                                                title = stringResource(R.string.action_select_all),
                                                icon = Icons.Outlined.FlipToBack,
                                                onClick = model::invertSelection,
                                            ),
                                        ),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { contentPadding ->
                    if (s.items.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(R.string.database_clean),
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(contentPadding)
                                .fillMaxSize(),
                        ) {
                            FastScrollLazyColumn(
                                modifier = Modifier.weight(1f),
                            ) {
                                items(s.items) { sourceWithCount ->
                                    ClearDatabaseItem(
                                        source = sourceWithCount.source,
                                        count = sourceWithCount.count,
                                        isSelected = s.selection.contains(sourceWithCount.id),
                                        onClickSelect = { model.toggleSelection(sourceWithCount.source) },
                                    )
                                }
                            }

                            Divider()

                            Button(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                onClick = model::showConfirmation,
                                enabled = s.selection.isNotEmpty(),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ClearDatabaseItem(
        source: Source,
        count: Long,
        isSelected: Boolean,
        onClickSelect: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .selectedBackground(isSelected)
                .clickable(onClick = onClickSelect)
                .padding(horizontal = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(source = source)
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = source.visualName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(text = stringResource(R.string.clear_database_source_item_count, count))
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClickSelect() },
            )
        }
    }
}

private class ClearDatabaseScreenModel : StateScreenModel<ClearDatabaseScreenModel.State>(State.Loading) {
    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga = Injekt.get()
    private val database: Database = Injekt.get()

    init {
        coroutineScope.launchIO {
            getSourcesWithNonLibraryManga.subscribe()
                .collectLatest { list ->
                    mutableState.update { old ->
                        val items = list.sortedBy { it.name }
                        when (old) {
                            State.Loading -> State.Ready(items)
                            is State.Ready -> old.copy(items = items)
                        }
                    }
                }
        }
    }

    fun removeMangaBySourceId() {
        val state = state.value as? State.Ready ?: return
        database.mangasQueries.deleteMangasNotInLibraryBySourceIds(state.selection)
        database.historyQueries.removeResettedHistory()
    }

    fun toggleSelection(source: Source) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        val mutableList = state.selection.toMutableList()
        if (mutableList.contains(source.id)) {
            mutableList.remove(source.id)
        } else {
            mutableList.add(source.id)
        }
        state.copy(selection = mutableList)
    }

    fun clearSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = emptyList())
    }

    fun selectAll() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(selection = state.items.map { it.id })
    }

    fun invertSelection() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(
            selection = state.items
                .map { it.id }
                .filterNot { it in state.selection },
        )
    }

    fun showConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = true)
    }

    fun hideConfirmation() = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showConfirmation = false)
    }

    sealed class State {
        object Loading : State()
        data class Ready(
            val items: List<SourceWithCount>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
        ) : State()
    }
}
