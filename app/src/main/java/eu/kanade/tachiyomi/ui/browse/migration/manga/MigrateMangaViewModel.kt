package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class MigrateMangaViewModel(
    @Assisted private val sourceId: Long,
    private val sourceManager: SourceManager,
    private val getFavorites: GetFavorites,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): MigrateMangaViewModel
    }

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    private val source by lazy { sourceManager.getOrStub(sourceId) }

    private val selection = MutableStateFlow(emptySet<Long>())

    private val favorites = getFavorites.subscribe(sourceId)
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(MigrationMangaEvent.FailedFetchingFavorites)
            emit(listOf())
        }
        .map { manga ->
            manga.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }

    val state: StateFlow<State> = combine(
        favorites,
        selection,
    ) { titleList, selection ->
        State(source = source, selection = selection, titleList = titleList)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    fun toggleSelection(item: Manga) {
        selection.update { selection ->
            selection.mutate { list ->
                if (!list.remove(item.id)) list.add(item.id)
            }
        }
    }

    fun clearSelection() {
        selection.update { emptySet() }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        private val titleList: List<Manga>? = null,
    ) {

        val titles: List<Manga>
            get() = titleList ?: listOf()

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
