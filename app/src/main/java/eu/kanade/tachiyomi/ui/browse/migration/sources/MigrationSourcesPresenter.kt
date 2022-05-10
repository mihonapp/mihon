package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesPresenter(
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val setMigrateSorting: SetMigrateSorting = Injekt.get(),
) : BasePresenter<MigrationSourcesController>() {

    private val _state: MutableStateFlow<MigrateSourceState> = MutableStateFlow(MigrateSourceState.Loading)
    val state: StateFlow<MigrateSourceState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch { exception ->
                    _state.value = MigrateSourceState.Error(exception)
                }
                .collectLatest { sources ->
                    _state.value = MigrateSourceState.Success(sources)
                }
        }
    }

    fun setAlphabeticalSorting(isAscending: Boolean) {
        setMigrateSorting.await(SetMigrateSorting.Mode.ALPHABETICAL, isAscending)
    }

    fun setTotalSorting(isAscending: Boolean) {
        setMigrateSorting.await(SetMigrateSorting.Mode.TOTAL, isAscending)
    }
}

sealed class MigrateSourceState {
    object Loading : MigrateSourceState()
    data class Error(val error: Throwable) : MigrateSourceState()
    data class Success(val sources: List<Pair<Source, Long>>) : MigrateSourceState()
}
