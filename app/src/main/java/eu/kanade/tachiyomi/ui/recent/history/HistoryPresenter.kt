package eu.kanade.tachiyomi.ui.recent.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.interactor.DeleteAllHistory
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextUnreadChapters
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.RemoveHistoryByMangaId
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class HistoryPresenter(
    private val state: HistoryStateImpl = HistoryState() as HistoryStateImpl,
    private val getHistory: GetHistory = Injekt.get(),
    private val getNextUnreadChapters: GetNextUnreadChapters = Injekt.get(),
    private val deleteAllHistory: DeleteAllHistory = Injekt.get(),
    private val removeHistoryById: RemoveHistoryById = Injekt.get(),
    private val removeHistoryByMangaId: RemoveHistoryByMangaId = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
) : BasePresenter<HistoryController>(), HistoryState by state {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()

    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    @Composable
    fun getHistory(): Flow<List<HistoryUiModel>> {
        val query = searchQuery ?: ""
        return remember(query) {
            getHistory.subscribe(query)
                .distinctUntilChanged()
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .map { pagingData ->
                    pagingData.toHistoryUiModels()
                }
        }
    }

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it) }
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
            val chapter = getNextUnreadChapters.await(mangaId, chapterId).firstOrNull()
            _events.send(if (chapter != null) Event.OpenChapter(chapter) else Event.NoNextChapterFound)
        }
    }

    fun deleteAllHistory() {
        presenterScope.launchIO {
            val result = deleteAllHistory.await()
            if (!result) return@launchIO
            withUIContext {
                view?.activity?.toast(R.string.clear_history_completed)
            }
        }
    }

    fun resumeLastChapterRead() {
        presenterScope.launchIO {
            val chapter = getNextUnreadChapters.await()
            _events.send(if (chapter != null) Event.OpenChapter(chapter) else Event.NoNextChapterFound)
        }
    }

    sealed class Dialog {
        object DeleteAll : Dialog()
        data class Delete(val history: HistoryWithRelations) : Dialog()
    }

    sealed class Event {
        object InternalError : Event()
        object NoNextChapterFound : Event()
        data class OpenChapter(val chapter: Chapter) : Event()
    }
}

@Stable
interface HistoryState {
    var searchQuery: String?
    var dialog: HistoryPresenter.Dialog?
}

fun HistoryState(): HistoryState {
    return HistoryStateImpl()
}

class HistoryStateImpl : HistoryState {
    override var searchQuery: String? by mutableStateOf(null)
    override var dialog: HistoryPresenter.Dialog? by mutableStateOf(null)
}
