package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.hiddenDuplicates.interactor.AddHiddenDuplicate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PossibleDuplicatesScreenModel(
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val addHiddenDuplicate: AddHiddenDuplicate = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<PossibleDuplicatesScreenModel.State>(State(libraryPreferences.duplicateMatchLevel().get())) {

    private var _duplicatesMapState: MutableStateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        MutableStateFlow(mapOf())
    val duplicatesMapState: StateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        _duplicatesMapState.asStateFlow()

    init {
        screenModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val allLibraryManga = getLibraryManga.await().sortedBy { it.manga.title }
            state.distinctUntilChangedBy { it.matchLevel }.collectLatest { state ->
                mutableState.update { it.copy(loading = true) }
                allLibraryManga.forEach { libraryManga ->
                    val keyManga = MangaWithChapterCount(libraryManga.manga, libraryManga.totalChapters)
                    val duplicates = getDuplicateLibraryManga.invoke(
                        libraryManga.manga,
                        state.matchLevel,
                    ).sortedBy {
                        it.manga.title
                    }
                    updateDuplicatesMap(keyManga, duplicates)
                }
                mutableState.update { it.copy(loading = false) }
            }
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

    private fun removeSingleMangaFromDuplicateList(key: MangaWithChapterCount, mangaId: Long) {
        val newList = duplicatesMapState.value[key]?.mapNotNull { it.takeIf { it.manga.id != mangaId } }
        updateDuplicatesMap(key, newList)
    }

    private fun removeDuplicateListByKeyManga(key: MangaWithChapterCount) {
        updateDuplicatesMap(key, null)
    }

    private fun removeMangaFromMap(manga: MangaWithChapterCount) {
        removeDuplicateListByKeyManga(manga)
        duplicatesMapState.value.forEach { (key, _) ->
            removeSingleMangaFromDuplicateList(key, manga.manga.id)
        }
    }

    fun removeFavorite(mangaItem: MangaWithChapterCount, deleteDownloads: Boolean) {
        screenModelScope.launchNonCancellable {
            if (updateManga.awaitUpdateFavorite(mangaItem.manga.id, false)) {
                if (mangaItem.manga.removeCovers() != mangaItem.manga) {
                    updateManga.awaitUpdateCoverLastModified(mangaItem.manga.id)
                }
            }
            if (deleteDownloads) {
                val source = sourceManager.get(mangaItem.manga.source) as? HttpSource
                if (source != null) {
                    downloadManager.deleteManga(mangaItem.manga, source)
                }
            }
            withUIContext {
                removeMangaFromMap(mangaItem)
            }
        }
    }

    fun hideSingleDuplicate(keyManga: MangaWithChapterCount, duplicateManga: MangaWithChapterCount) {
        screenModelScope.launch {
            addHiddenDuplicate(keyManga.manga.id, duplicateManga.manga.id)
            withUIContext {
                removeSingleMangaFromDuplicateList(keyManga, duplicateManga.manga.id)
                removeSingleMangaFromDuplicateList(duplicateManga, keyManga.manga.id)
            }
        }
    }

    fun hideGroupDuplicate(keyManga: MangaWithChapterCount, duplicateManga: List<MangaWithChapterCount>) {
        screenModelScope.launch {
            duplicateManga.forEach {
                addHiddenDuplicate(keyManga.manga.id, it.manga.id)
            }
            withUIContext {
                removeDuplicateListByKeyManga(keyManga)
                duplicateManga.forEach {
                    removeSingleMangaFromDuplicateList(it, keyManga.manga.id)
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

    fun openDeleteMangaDialog(mangaItem: MangaWithChapterCount) {
        mutableState.update { it.copy(dialog = Dialog.ConfirmRemove(mangaItem)) }
    }

    fun setMatchLevel(level: LibraryPreferences.DuplicateMatchLevel) {
        mutableState.update { it.copy(matchLevel = level) }
    }

    sealed interface Dialog {
        data object FilterSheet : Dialog
        data class ConfirmRemove(val mangaItem: MangaWithChapterCount) : Dialog
    }

    @Immutable
    data class State(
        val matchLevel: LibraryPreferences.DuplicateMatchLevel,
        val loading: Boolean = true,
        val dialog: Dialog? = null,
    )
}
