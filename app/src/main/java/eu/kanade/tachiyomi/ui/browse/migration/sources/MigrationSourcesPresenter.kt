package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.presentation.browse.MigrateSourceState
import eu.kanade.presentation.browse.MigrateSourceStateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesPresenter(
    private val state: MigrateSourceStateImpl = MigrateSourceState() as MigrateSourceStateImpl,
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val setMigrateSorting: SetMigrateSorting = Injekt.get(),
) : BasePresenter<MigrationSourcesController>(), MigrateSourceState by state {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch { exception ->
                    logcat(LogPriority.ERROR, exception)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    state.items = sources
                    state.isLoading = false
                }
        }
    }

    fun setAlphabeticalSorting(isAscending: Boolean) {
        setMigrateSorting.await(SetMigrateSorting.Mode.ALPHABETICAL, isAscending)
    }

    fun setTotalSorting(isAscending: Boolean) {
        setMigrateSorting.await(SetMigrateSorting.Mode.TOTAL, isAscending)
    }

    sealed class Event {
        object FailedFetchingSourcesWithCount : Event()
    }
}
