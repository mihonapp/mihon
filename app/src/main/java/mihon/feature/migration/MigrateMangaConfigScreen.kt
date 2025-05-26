package mihon.feature.migration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.update
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaConfigScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ScreenModel() }
        val state by screenModel.state.collectAsState()

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            // The list is preceded by an info header hence -1
            screenModel.orderSource(from.index - 1, to.index - 1)
        }
        Scaffold(
            topBar = {
                AppBar(
                    title = "",
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectAllLabel),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.toggleSource(ScreenModel.EnableSourceConfig.All) },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectNoneLabel),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = { screenModel.toggleSource(ScreenModel.EnableSourceConfig.None) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectEnabledLabel),
                                    onClick = { screenModel.toggleSource(ScreenModel.EnableSourceConfig.Enabled) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectPinnedLabel),
                                    onClick = { screenModel.toggleSource(ScreenModel.EnableSourceConfig.Pinned) },
                                ),
                            ),
                        )
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                    icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    onClick = { navigator.replace(MigrateSearchScreen(mangaId)) },
                    expanded = lazyListState.shouldExpandFAB(),
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                item {
                    Text(
                        text = stringResource(MR.strings.migrationConfigScreen_infoHeader),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    )
                }
                items(
                    items = state.sources,
                    key = { it.id },
                ) { source ->
                    ReorderableItem(reorderableState, source.id) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier.draggableHandle(),
                                )
                            },
                            headlineContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SourceIcon(source = source.source)
                                    Text(
                                        text = source.visualName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = source.isEnabled,
                                    onCheckedChange = null,
                                )
                            },
                            modifier = Modifier.clickable(onClick = { screenModel.toggleSource(source.id) }),
                        )
                    }
                }
            }
        }
    }

    private class ScreenModel(
        private val sourceManager: SourceManager = Injekt.get(),
        private val sourcePreferences: SourcePreferences = Injekt.get(),
    ) : StateScreenModel<ScreenModel.State>(State()) {

        init {
            screenModelScope.launchIO {
                updateSources()
            }
        }

        private fun updateSources() {
            val languages = sourcePreferences.enabledLanguages().get()
            val includedSources = sourcePreferences.migrationSources().get()
            val disabledSources = sourcePreferences.disabledSources().get()
                .mapNotNull { it.toLongOrNull() }
            val sources = sourceManager.getCatalogueSources()
                .asSequence()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages }
                .sortedBy { "(${it.lang}) ${it.name}" }
                .map {
                    val source = Source(
                        id = it.id,
                        lang = it.lang,
                        name = it.name,
                        supportsLatest = false,
                        isStub = false,
                    )
                    MigrationSource(
                        source = source,
                        isEnabled = source.isEnabled(
                            includedSources,
                            disabledSources,
                        ),
                    )
                }
                .toList()

            val sorted = sources
                .filter { it.isEnabled }
                .sortedBy { includedSources.indexOf(it.source.id) }
                .plus(
                    sources.filterNot { it.isEnabled },
                )

            mutableState.update { it.copy(sources = sorted) }
        }

        fun toggleSource(id: Long) {
            mutableState.update {
                val updatedSources = it.sources.map { source ->
                    source.copy(isEnabled = if (source.source.id == id) !source.isEnabled else source.isEnabled)
                }

                it.copy(sources = updatedSources)
            }

            saveSourceSelection()
        }

        fun toggleSource(config: EnableSourceConfig) {
            val pinnedSources = sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }
            val disabledSources = sourcePreferences.disabledSources().get().mapNotNull { it.toLongOrNull() }
            val isEnabled: (Long) -> Boolean = {
                when (config) {
                    EnableSourceConfig.All -> true
                    EnableSourceConfig.None -> false
                    EnableSourceConfig.Pinned -> it in pinnedSources
                    EnableSourceConfig.Enabled -> it !in disabledSources
                }
            }
            mutableState.update {
                val updatedSources = it.sources.map { source ->
                    source.copy(isEnabled = isEnabled(source.source.id))
                }

                it.copy(sources = updatedSources)
            }

            saveSourceSelection()
        }

        fun orderSource(from: Int, to: Int) {
            mutableState.update {
                val reorderedSources = it.sources
                    .toMutableList()
                    .apply {
                        add(to, removeAt(from))
                    }
                    .toList()

                it.copy(sources = reorderedSources)
            }

            saveSourceSelection()
        }

        private fun saveSourceSelection() {
            state.value.sources
                .filter { source -> source.isEnabled }
                .map { source -> source.source.id }
                .let { sources -> sourcePreferences.migrationSources().set(sources) }
        }

        private fun Source.isEnabled(included: List<Long>, disabled: List<Long>): Boolean {
            return if (included.isEmpty()) {
                id !in disabled
            } else {
                id in included
            }
        }

        data class State(
            val sources: List<MigrationSource> = emptyList(),
        )

        enum class EnableSourceConfig {
            All,
            None,
            Pinned,
            Enabled,
        }
    }

    data class MigrationSource(
        val source: Source,
        val isEnabled: Boolean,
    ) {
        val id = source.id
        val visualName = source.visualName
    }
}
