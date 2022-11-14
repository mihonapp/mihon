package eu.kanade.tachiyomi.ui.browse.migration.sources

import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.MigrateSourceState
import eu.kanade.presentation.browse.MigrateSourceStateImpl
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesPresenter(
    private val presenterScope: CoroutineScope,
    private val state: MigrateSourceStateImpl = MigrateSourceState() as MigrateSourceStateImpl,
    private val preferences: SourcePreferences = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val setMigrateSorting: SetMigrateSorting = Injekt.get(),
) : MigrateSourceState by state {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    fun onCreate() {
        presenterScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    state.items = sources
                    state.isLoading = false
                }
        }

        preferences.migrationSortingDirection().changes()
            .onEach { state.sortingDirection = it }
            .launchIn(presenterScope)

        preferences.migrationSortingMode().changes()
            .onEach { state.sortingMode = it }
            .launchIn(presenterScope)
    }

    fun toggleSortingMode() {
        val newMode = when (state.sortingMode) {
            SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
            SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
        }

        setMigrateSorting.await(newMode, state.sortingDirection)
    }

    fun toggleSortingDirection() {
        val newDirection = when (state.sortingDirection) {
            SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
            SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
        }

        setMigrateSorting.await(state.sortingMode, newDirection)
    }

    sealed class Event {
        object FailedFetchingSourcesWithCount : Event()
    }
}
