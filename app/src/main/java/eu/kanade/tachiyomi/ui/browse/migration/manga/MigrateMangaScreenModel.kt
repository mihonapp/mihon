package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaScreenModel(
    private val context: Context,
    private val sourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
) : StateScreenModel<MigrateMangaScreenModel.State>(State()) {

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()
    val maximumMigrationSelection = 50

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    mutableState.update { it.copy(titleList = list) }
                }
        }
    }

    fun toggleSelection(item: Manga) {
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                if (!list.remove(item.id)) {
                    if (state.selection.size == maximumMigrationSelection) {
                        screenModelScope.launchIO {
                            withUIContext {
                                context.toast(context.stringResource(MR.strings.migration_limit_reached))
                            }
                        }
                        return@mutate
                    }
                    list.add(item.id)
                }
            }
            state.copy(selection = selection)
        }
    }

    fun toggleRangeSelection(item: Manga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelectedId = list.lastOrNull()
                val lastSelectedManga = state.titles.find { it.id == lastSelectedId }
                val lastMangaIndex = state.titles.indexOf(lastSelectedManga)
                val curMangaIndex = state.titles.indexOf(item)

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    curMangaIndex < lastMangaIndex -> curMangaIndex..lastMangaIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }

                val selectionLimit = maximumMigrationSelection - list.size
                val limitedSelectionRange = selectionRange.take(selectionLimit + 1)
                if (limitedSelectionRange.last() != selectionRange.last) {
                    screenModelScope.launchIO {
                        withUIContext {
                            context.toast(context.stringResource(MR.strings.migration_limit_reached))
                        }
                    }
                }

                limitedSelectionRange.map { state.titles[it].id }.let(list::addAll)
            }
            state.copy(selection = newSelection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        private val titleList: ImmutableList<Manga>? = null,
    ) {

        val titles: ImmutableList<Manga>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
