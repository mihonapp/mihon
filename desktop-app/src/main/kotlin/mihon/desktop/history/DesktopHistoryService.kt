package mihon.desktop.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

data class HistoryUiState(
    val query: String = "",
    val groups: List<DesktopHistoryGroup> = emptyList(),
    val rawCount: Int = 0,
    val isLoading: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopHistoryService(
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort,
    private val scope: CoroutineScope,
) {
    private val queryFlow = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = queryFlow
        .flatMapLatest { query ->
            repository.observeHistory(query)
        }
        .flowOn(Dispatchers.IO)
        .combine(queryFlow) { historyItems, query ->
            HistoryUiState(
                query = query,
                groups = HistoryGrouper.group(historyItems),
                rawCount = historyItems.size,
                isLoading = false,
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = HistoryUiState(isLoading = true),
        )

    fun setQuery(query: String) {
        queryFlow.update { query }
    }

    fun deleteItem(chapterId: Long) {
        scope.launch(Dispatchers.IO) {
            mutationPort.deleteHistory(chapterId)
        }
    }

    fun clearAll() {
        scope.launch(Dispatchers.IO) {
            mutationPort.clearAllHistory()
        }
    }
}
