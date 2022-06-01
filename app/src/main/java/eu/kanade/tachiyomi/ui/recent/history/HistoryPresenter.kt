package eu.kanade.tachiyomi.ui.recent.history

import android.os.Bundle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import eu.kanade.domain.history.interactor.DeleteHistoryTable
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextChapter
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.RemoveHistoryByMangaId
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Presenter of HistoryFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
class HistoryPresenter(
    private val getHistory: GetHistory = Injekt.get(),
    private val getNextChapter: GetNextChapter = Injekt.get(),
    private val deleteHistoryTable: DeleteHistoryTable = Injekt.get(),
    private val removeHistoryById: RemoveHistoryById = Injekt.get(),
    private val removeHistoryByMangaId: RemoveHistoryByMangaId = Injekt.get(),
) : BasePresenter<HistoryController>() {

    private val _query: MutableStateFlow<String> = MutableStateFlow("")
    private val _state: MutableStateFlow<HistoryState> = MutableStateFlow(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            _query.collectLatest { query ->
                getHistory.subscribe(query)
                    .catch { exception ->
                        _state.value = HistoryState.Error(exception)
                    }
                    .map { pagingData ->
                        pagingData.toHistoryUiModels()
                    }
                    .cachedIn(presenterScope)
                    .let { uiModelsPagingDataFlow ->
                        _state.value = HistoryState.Success(uiModelsPagingDataFlow)
                    }
            }
        }
    }

    private fun PagingData<HistoryWithRelations>.toHistoryUiModels(): PagingData<HistoryUiModel> {
        return this.map {
            HistoryUiModel.Item(it)
        }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toDateKey() ?: Date(0)
                val afterDate = after?.item?.readAt?.time?.toDateKey() ?: Date(0)
                when {
                    beforeDate.time != afterDate.time && afterDate.time != 0L -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    fun search(query: String) {
        presenterScope.launchIO {
            _query.emit(query)
        }
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        presenterScope.launchIO {
            removeHistoryById.await(history)
        }
    }

    fun removeAllFromHistory(mangaId: Long) {
        presenterScope.launchIO {
            removeHistoryByMangaId.await(mangaId)
        }
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        presenterScope.launchIO {
            val chapter = getNextChapter.await(mangaId, chapterId)
            launchUI {
                view?.openChapter(chapter)
            }
        }
    }

    fun deleteAllHistory() {
        presenterScope.launchIO {
            val result = deleteHistoryTable.await()
            if (!result) return@launchIO
            launchUI {
                view?.activity?.toast(R.string.clear_history_completed)
            }
        }
    }

    fun resumeLastChapterRead() {
        presenterScope.launchIO {
            val chapter = getNextChapter.await()
            launchUI {
                view?.openChapter(chapter)
            }
        }
    }
}

sealed class HistoryState {
    object Loading : HistoryState()
    data class Error(val error: Throwable) : HistoryState()
    data class Success(val uiModels: Flow<PagingData<HistoryUiModel>>) : HistoryState()
}
