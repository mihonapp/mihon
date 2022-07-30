package eu.kanade.tachiyomi.ui.setting.database

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetSourcesWithNonLibraryManga
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.more.settings.database.ClearDatabaseState
import eu.kanade.presentation.more.settings.database.ClearDatabaseStateImpl
import eu.kanade.tachiyomi.Database
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ClearDatabasePresenter(
    private val state: ClearDatabaseStateImpl = ClearDatabaseState() as ClearDatabaseStateImpl,
    private val database: Database = Injekt.get(),
    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga = Injekt.get(),
) : BasePresenter<ClearDatabaseController>(), ClearDatabaseState by state {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getSourcesWithNonLibraryManga.subscribe()
                .collectLatest { list ->
                    state.items = list.sortedBy { it.name }
                }
        }
    }

    fun removeMangaBySourceId(sourceIds: List<Long>) {
        database.mangasQueries.deleteMangasNotInLibraryBySourceIds(sourceIds)
        database.historyQueries.removeResettedHistory()
    }

    fun toggleSelection(source: Source) {
        val mutableList = state.selection.toMutableList()
        if (mutableList.contains(source.id)) {
            mutableList.remove(source.id)
        } else {
            mutableList.add(source.id)
        }
        state.selection = mutableList
    }

    fun clearSelection() {
        state.selection = emptyList()
    }

    fun selectAll() {
        state.selection = state.items.map { it.id }
    }

    fun invertSelection() {
        state.selection = state.items.map { it.id }.filterNot { it in state.selection }
    }

    sealed class Dialog {
        data class Delete(val sourceIds: List<Long>) : Dialog()
    }
}
