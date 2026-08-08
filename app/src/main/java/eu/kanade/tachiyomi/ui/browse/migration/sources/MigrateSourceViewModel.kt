package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MigrateSourceViewModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
) : ViewModel() {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    val state: StateFlow<State> = combine(
        getSourcesWithFavoriteCount.subscribe()
            .catch {
                logcat(LogPriority.ERROR, it)
                _channel.send(Event.FailedFetchingSourcesWithCount)
            },
        preferences.migrationSortingMode.changes(),
        preferences.migrationSortingDirection.changes(),
    ) { sources, sortingMode, sortingDirection ->
        State(
            isLoading = false,
            items = sources,
            sortingMode = sortingMode,
            sortingDirection = sortingDirection,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    fun toggleSortingMode() {
        preferences.migrationSortingMode.getAndSet { mode ->
            when (mode) {
                SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
            }
        }
    }

    fun toggleSortingDirection() {
        preferences.migrationSortingDirection.getAndSet { direction ->
            when (direction) {
                SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
            }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<Pair<Source, Long>> = listOf(),
        val sortingMode: SetMigrateSorting.Mode = SetMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetMigrateSorting.Direction = SetMigrateSorting.Direction.ASCENDING,
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object FailedFetchingSourcesWithCount : Event
    }
}
