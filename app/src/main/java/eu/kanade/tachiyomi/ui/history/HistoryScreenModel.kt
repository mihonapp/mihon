package eu.kanade.tachiyomi.ui.history

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.prefs.asState
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextChapters
import eu.kanade.domain.history.interactor.RemoveHistory
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class HistoryScreenModel(
    private val getHistory: GetHistory = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val removeHistory: RemoveHistory = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
) : StateScreenModel<HistoryState>(HistoryState()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState(coroutineScope)
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState(coroutineScope)

    init {
        coroutineScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    getHistory.subscribe(query ?: "")
                        .distinctUntilChanged()
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .map { it.toHistoryUiModels() }
                        .flowOn(Dispatchers.IO)
                }
                .collect { newList -> mutableState.update { it.copy(list = newList) } }
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

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        coroutineScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        coroutineScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(mangaId: Long) {
        coroutineScope.launchIO {
            removeHistory.await(mangaId)
        }
    }

    fun removeAllHistory() {
        coroutineScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    sealed class Dialog {
        object DeleteAll : Dialog()
        data class Delete(val history: HistoryWithRelations) : Dialog()
    }

    sealed class Event {
        data class OpenChapter(val chapter: Chapter?) : Event()
        object InternalError : Event()
        object HistoryCleared : Event()
    }
}

@Immutable
data class HistoryState(
    val searchQuery: String? = null,
    val list: List<HistoryUiModel>? = null,
    val dialog: HistoryScreenModel.Dialog? = null,
)
