package eu.kanade.tachiyomi.ui.failedupdate

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.updates.interactor.DeleteMangaUpdateError
import tachiyomi.domain.updates.interactor.GetMangaUpdateErrors
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FailedUpdatesScreenModel(
    private val getMangaUpdateErrors: GetMangaUpdateErrors = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val deleteMangaUpdateError: DeleteMangaUpdateError = Injekt.get(),
) : StateScreenModel<FailedUpdatesScreenModel.State>(State()) {

    private val selectedMangaIds = mutableSetOf<Long>()

    init {
        // Clean up errors for non-favorite manga
        cleanupNonFavorites()

        screenModelScope.launchIO {
            getMangaUpdateErrors.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                }
                .collectLatest { errors ->
                    val errorWithManga = errors.mapNotNull { error ->
                        val manga = getManga.await(error.mangaId) ?: return@mapNotNull null
                        MangaUpdateErrorWithManga(error, manga)
                    }

                    // Get current valid manga IDs
                    val validMangaIds = errorWithManga.map { it.manga.id }.toSet()

                    // Remove invalid selections (manga that no longer have errors)
                    selectedMangaIds.retainAll(validMangaIds)

                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = errorWithManga.toImmutableList(),
                            selectedIds = selectedMangaIds.toSet(),
                        )
                    }
                }
        }
    }

    fun clearError(mangaId: Long) {
        screenModelScope.launchIO {
            deleteMangaUpdateError.await(mangaId)
        }
    }

    fun clearAllErrors() {
        screenModelScope.launchIO {
            deleteMangaUpdateError.awaitAll()
        }
    }

    fun clearSelectedErrors() {
        screenModelScope.launchIO {
            selectedMangaIds.forEach { mangaId ->
                deleteMangaUpdateError.await(mangaId)
            }
            selectedMangaIds.clear()
            mutableState.update { it.copy(selectedIds = emptySet()) }
        }
    }

    fun cleanupNonFavorites() {
        screenModelScope.launchIO {
            try {
                deleteMangaUpdateError.awaitNonFavorites()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clean up non-favorite errors" }
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun toggleSelection(item: MangaUpdateErrorWithManga, selected: Boolean) {
        if (selected) {
            selectedMangaIds.add(item.manga.id)
        } else {
            selectedMangaIds.remove(item.manga.id)
        }
        mutableState.update { it.copy(selectedIds = selectedMangaIds.toSet()) }
    }

    fun toggleAllSelection(selected: Boolean) {
        if (selected) {
            selectedMangaIds.clear()
            selectedMangaIds.addAll(state.value.items.map { it.manga.id })
        } else {
            selectedMangaIds.clear()
        }
        mutableState.update { it.copy(selectedIds = selectedMangaIds.toSet()) }
    }

    fun invertSelection() {
        val allIds = state.value.items.map { it.manga.id }.toSet()
        val currentSelection = selectedMangaIds.toSet()
        selectedMangaIds.clear()
        selectedMangaIds.addAll(allIds - currentSelection)
        mutableState.update { it.copy(selectedIds = selectedMangaIds.toSet()) }
    }

    fun getSelectedMangaIds(): List<Long> {
        return selectedMangaIds.toList()
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<MangaUpdateErrorWithManga> = kotlinx.collections.immutable.persistentListOf(),
        val selectedIds: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        val selectionMode = selectedIds.isNotEmpty()
    }

    sealed interface Dialog {
        data object ClearAllConfirmation : Dialog
        data object DeleteSelectedConfirmation : Dialog
        data class MigrateSelected(val mangaIds: List<Long>) : Dialog
    }
}
