package eu.kanade.tachiyomi.ui.failedupdate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.interactor.DeleteMangaUpdateError
import tachiyomi.domain.updates.interactor.GetMangaUpdateErrors
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class FailedUpdatesViewModel(
    private val getMangaUpdateErrors: GetMangaUpdateErrors,
    private val deleteMangaUpdateError: DeleteMangaUpdateError,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    val snackbarHostState = SnackbarHostState()

    private val _events = Channel<Unit>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            try {
                deleteMangaUpdateError.awaitNonFavorites()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _events.send(Unit)
            }

            getMangaUpdateErrors.subscribeWithManga()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    state.update { it.copy(isLoading = false, hasError = true) }
                }
                .collectLatest { errorWithManga ->
                    val validMangaIds = errorWithManga.map { it.manga.id }.toSet()

                    state.update {
                        it.copy(
                            isLoading = false,
                            items = errorWithManga,
                            selectedIds = it.selectedIds.intersect(validMangaIds),
                        )
                    }
                }
        }
    }

    fun clearError(mangaId: Long) {
        clearErrors { deleteMangaUpdateError.await(mangaId) }
    }

    fun clearAllErrors() {
        clearErrors { deleteMangaUpdateError.awaitAll() }
    }

    fun clearSelectedErrors() {
        val selectedIds = state.value.selectedIds.toList()
        clearErrors {
            deleteMangaUpdateError.await(selectedIds)
            state.update { it.copy(selectedIds = it.selectedIds - selectedIds.toSet()) }
        }
    }

    private fun clearErrors(block: suspend () -> Unit) {
        viewModelScope.launchIO {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _events.send(Unit)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        state.update { it.copy(dialog = dialog) }
    }

    fun toggleSelection(item: MangaUpdateErrorWithManga, selected: Boolean) {
        state.update { state ->
            val selectedIds = if (selected && state.items.any { it.manga.id == item.manga.id }) {
                state.selectedIds + item.manga.id
            } else {
                state.selectedIds - item.manga.id
            }
            state.copy(selectedIds = selectedIds)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        state.update { state ->
            state.copy(selectedIds = if (selected) state.items.map { it.manga.id }.toSet() else emptySet())
        }
    }

    fun invertSelection() {
        state.update { state ->
            state.copy(selectedIds = state.items.map { it.manga.id }.toSet() - state.selectedIds)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val hasError: Boolean = false,
        val items: List<MangaUpdateErrorWithManga> = emptyList(),
        val selectedIds: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        val selectionMode = selectedIds.isNotEmpty()
    }

    sealed interface Dialog {
        data object ClearAllConfirmation : Dialog
        data object DeleteSelectedConfirmation : Dialog
    }
}
