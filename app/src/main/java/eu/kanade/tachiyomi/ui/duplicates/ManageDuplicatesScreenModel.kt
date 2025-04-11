package eu.kanade.tachiyomi.ui.duplicates

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastDistinctBy
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.AddHiddenDuplicate
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ManageDuplicatesScreenModel(
    private val context: Context,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val addHiddenDuplicate: AddHiddenDuplicate = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<ManageDuplicatesScreenModel.State>(State()) {

    private var _duplicatesListState: MutableStateFlow<List<List<MangaWithChapterCount>>> = MutableStateFlow(listOf())
    val duplicatesListState: StateFlow<List<List<MangaWithChapterCount>>> = _duplicatesListState.asStateFlow()

    init {
        screenModelScope.launch {
            val allLibraryManga = getLibraryManga.await()
            allLibraryManga.forEach { libraryManga ->
                val duplicates = getDuplicateLibraryManga(libraryManga.manga)
                if (duplicates.isNotEmpty()) {
                    val duplicateList = (
                        duplicates +
                            MangaWithChapterCount(libraryManga.manga, libraryManga.totalChapters)
                        ).sortedBy { it.manga.id }
                    val updatedList = duplicatesListState.value.toMutableList() + listOf(duplicateList)
                    _duplicatesListState.value = updatedList.fastDistinctBy { list -> list.map { it.manga.id } }
                }
            }
            mutableState.update { it.copy(loading = false) }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun removeFavorite(manga: Manga) {
        screenModelScope.launch {
            if (downloadManager.getDownloadCount(manga) == 0) return@launch
            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.delete_downloads_for_manga),
                actionLabel = context.stringResource(MR.strings.action_delete),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val source = sourceManager.getOrStub(manga.source)
                downloadManager.deleteManga(manga, source)
            }
        }
        screenModelScope.launchIO {
            if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                if (manga.removeCovers() != manga) {
                    updateManga.awaitUpdateCoverLastModified(manga.id)
                }
                withUIContext {
                    val updatedList = duplicatesListState.value.mapNotNull { lists ->
                        lists.mapNotNull { it.takeIf { it.manga.id != manga.id } }.takeIf { it.count() > 1 }
                    }.fastDistinctBy { list -> list.map { it.manga.id } }
                    _duplicatesListState.value = updatedList
                }
            }
        }
    }

    fun hideDuplicate(id: Long, othersIDs: List<Long>) {
        screenModelScope.launchIO {
            othersIDs.forEach { otherId ->
                addHiddenDuplicate(id, otherId)
                withUIContext {
                    val updatedList = duplicatesListState.value.mapNotNull { list ->
                        when (list.map { it.manga.id }.containsAll(othersIDs + id)) {
                            true -> {
                                list.mapNotNull { it.takeIf { it.manga.id != id } }.takeIf { it.count() > 1 }
                            }
                            false -> list
                        }
                    }
                    _duplicatesListState.value = updatedList
                }
            }
        }
    }

    sealed interface Dialog {
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val dialog: Dialog? = null,
    )
}
