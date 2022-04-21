package eu.kanade.tachiyomi.ui.recent.history

import android.os.Bundle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import eu.kanade.domain.history.interactor.DeleteHistoryTable
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextChapterForManga
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.RemoveHistoryByMangaId
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*

/**
 * Presenter of HistoryFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
class HistoryPresenter(
    private val getHistory: GetHistory = Injekt.get(),
    private val getNextChapterForManga: GetNextChapterForManga = Injekt.get(),
    private val deleteHistoryTable: DeleteHistoryTable = Injekt.get(),
    private val removeHistoryById: RemoveHistoryById = Injekt.get(),
    private val removeHistoryByMangaId: RemoveHistoryByMangaId = Injekt.get(),
) : BasePresenter<HistoryController>() {

    private var _query: MutableStateFlow<String> = MutableStateFlow("")
    private var _state: MutableStateFlow<HistoryState> = MutableStateFlow(HistoryState.EMPTY)
    val state: StateFlow<HistoryState> = _state

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            _state.update { state ->
                state.copy(
                    list = _query.flatMapLatest { query ->
                        getHistory.subscribe(query)
                            .map { pagingData ->
                                pagingData
                                    .map {
                                        UiModel.Item(it)
                                    }
                                    .insertSeparators { before, after ->
                                        val beforeDate = before?.item?.readAt?.time?.toDateKey() ?: Date(0)
                                        val afterDate = after?.item?.readAt?.time?.toDateKey() ?: Date(0)
                                        when {
                                            beforeDate.time != afterDate.time && afterDate.time != 0L -> UiModel.Header(afterDate)
                                            // Return null to avoid adding a separator between two items.
                                            else -> null
                                        }
                                    }
                            }
                    }
                        .cachedIn(presenterScope),
                )
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
            val chapter = getNextChapterForManga.await(mangaId, chapterId)
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
}

sealed class UiModel {
    data class Item(val item: HistoryWithRelations) : UiModel()
    data class Header(val date: Date) : UiModel()
}

data class HistoryState(
    val list: Flow<PagingData<UiModel>>? = null,
) {

    companion object {
        val EMPTY = HistoryState(null)
    }
}
