package eu.kanade.tachiyomi.ui.duplicates

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
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
import tachiyomi.domain.hiddenDuplicates.interactor.AddHiddenDuplicate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PossibleDuplicatesScreenModel(
    private val context: Context,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val addHiddenDuplicate: AddHiddenDuplicate = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<PossibleDuplicatesScreenModel.State>(State(libraryPreferences.duplicateMatchLevel().get())) {

    private var _duplicatesMapState: MutableStateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        MutableStateFlow(mapOf())
    val duplicatesMapState: StateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        _duplicatesMapState.asStateFlow()

    init {
        refreshList()
    }

    private fun refreshList() {
        _duplicatesMapState.value = mapOf()
        screenModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val allLibraryManga = getLibraryManga.await()
            allLibraryManga.forEach { libraryManga ->
                val keyManga = MangaWithChapterCount(libraryManga.manga, libraryManga.totalChapters)
                val duplicates = getDuplicateLibraryManga(libraryManga.manga, state.value.matchLevel).sortedBy {
                    it.manga.title
                }
                updateDuplicatesMap(keyManga, duplicates)
            }
            mutableState.update { it.copy(loading = false) }
        }
    }

    private fun updateDuplicatesMap(key: MangaWithChapterCount, value: List<MangaWithChapterCount>?) {
        val updatedMap = duplicatesMapState.value.toMutableMap()
        if (value.isNullOrEmpty()) {
            updatedMap.remove(key)
        } else {
            if (value.count() == 1 && value[0] in _duplicatesMapState.value) return
            updatedMap[key] = value
        }
        _duplicatesMapState.value = updatedMap
    }

    private fun removeSingleMangaFromList(key: MangaWithChapterCount, mangaId: Long) {
        val newList = duplicatesMapState.value[key]?.mapNotNull { it.takeIf { it.manga.id != mangaId } }
        updateDuplicatesMap(key, newList)
    }

    private fun removeList(key: MangaWithChapterCount) {
        updateDuplicatesMap(key, null)
    }

    private fun removeMangaFromMap(manga: MangaWithChapterCount) {
        removeList(manga)
        duplicatesMapState.value.forEach { (key, _) ->
            removeSingleMangaFromList(key, manga.manga.id)
        }
    }

    fun removeFavorite(mangaItem: MangaWithChapterCount) {
        val manga = mangaItem.manga
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
                    removeMangaFromMap(mangaItem)
                }
            }
        }
    }

    fun hideSingleDuplicate(keyManga: MangaWithChapterCount, duplicateManga: MangaWithChapterCount) {
        screenModelScope.launch {
            addHiddenDuplicate(keyManga.manga.id, duplicateManga.manga.id)
            withUIContext {
                removeSingleMangaFromList(keyManga, duplicateManga.manga.id)
                removeSingleMangaFromList(duplicateManga, keyManga.manga.id)
            }
        }
    }

    fun hideGroupDuplicate(keyManga: MangaWithChapterCount, duplicateManga: List<MangaWithChapterCount>) {
        screenModelScope.launch {
            duplicateManga.forEach {
                addHiddenDuplicate(keyManga.manga.id, it.manga.id)
            }
            withUIContext {
                removeList(keyManga)
                duplicateManga.forEach {
                    removeSingleMangaFromList(it, keyManga.manga.id)
                }
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun showFilterDialog() {
        setDialog(Dialog.FilterSheet)
    }

    fun setMatchLevel(level: LibraryPreferences.DuplicateMatchLevel) {
        mutableState.update { it.copy(matchLevel = level) }
        refreshList()
    }

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }

    @Immutable
    data class State(
        val matchLevel: LibraryPreferences.DuplicateMatchLevel,
        val loading: Boolean = true,
        val dialog: Dialog? = null,
    )
}
