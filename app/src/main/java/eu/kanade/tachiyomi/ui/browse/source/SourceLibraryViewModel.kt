package eu.kanade.tachiyomi.ui.browse.source

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
import eu.kanade.tachiyomi.data.library.LibraryUpdateErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class SourceLibraryViewModel(
    @Assisted private val sourceId: Long,
    sourceManager: SourceManager,
    getFavorites: GetFavorites,
    private val libraryUpdateErrors: LibraryUpdateErrors,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): SourceLibraryViewModel
    }

    private val title = flow {
        val source = sourceManager.getOrStub(sourceId)
        val lang = source.lang
        emit(if (lang.isEmpty()) source.name else "${source.name} (${lang.uppercase()})")
    }

    private val titles = getFavorites.subscribe(sourceId)
        .map { manga -> manga.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) }
        .catch { emit(emptyList()) }

    val state: StateFlow<State> = combine(
        title,
        titles,
        libraryUpdateErrors.errors.changes(),
    ) { name, manga, rawErrors ->
        State(
            isLoading = false,
            title = name,
            titles = manga,
            errorIds = libraryUpdateErrors.ids(rawErrors),
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val title: String = "",
        val titles: List<Manga> = emptyList(),
        val errorIds: Set<Long> = emptySet(),
    )
}
