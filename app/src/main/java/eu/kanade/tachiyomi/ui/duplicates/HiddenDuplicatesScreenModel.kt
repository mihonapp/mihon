package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.hiddenDuplicates.interactor.RemoveHiddenDuplicate
import tachiyomi.domain.manga.interactor.GetHiddenDuplicates
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiddenDuplicatesScreenModel(
    private val removeHiddenDuplicate: RemoveHiddenDuplicate = Injekt.get(),
    private val getHiddenDuplicates: GetHiddenDuplicates = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateScreenModel<HiddenDuplicatesScreenModel.State>(State()) {

    private var _hiddenDuplicatesMapState: MutableStateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        MutableStateFlow(mapOf())
    val hiddenDuplicatesMapState: StateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        _hiddenDuplicatesMapState.asStateFlow()

    init {
        screenModelScope.launch {
            val allLibraryManga = getLibraryManga.await()
            allLibraryManga.forEach { libraryManga ->
                val keyManga = MangaWithChapterCount(libraryManga.manga, libraryManga.totalChapters)
                val hiddenDuplicates = getHiddenDuplicates(libraryManga.manga).sortedBy { it.manga.title }
                updateHiddenDuplicatesMap(keyManga, hiddenDuplicates)
            }
            mutableState.update { it.copy(loading = false) }
        }
    }

    private fun updateHiddenDuplicatesMap(key: MangaWithChapterCount, value: List<MangaWithChapterCount>?) {
        val updatedMap = hiddenDuplicatesMapState.value.toMutableMap()
        if (value.isNullOrEmpty()) {
            updatedMap.remove(key)
        } else {
            if (value.count() == 1 && value[0] in _hiddenDuplicatesMapState.value) return
            updatedMap[key] = value
        }
        _hiddenDuplicatesMapState.value = updatedMap
    }

    private fun removeSingleMangaFromList(key: MangaWithChapterCount, mangaId: Long) {
        val newList = hiddenDuplicatesMapState.value[key]?.mapNotNull { it.takeIf { it.manga.id != mangaId } }
        updateHiddenDuplicatesMap(key, newList)
    }

    private fun removeList(key: MangaWithChapterCount) {
        updateHiddenDuplicatesMap(key, null)
    }

    fun unhideSingleDuplicate(keyManga: MangaWithChapterCount, duplicateManga: MangaWithChapterCount) {
        screenModelScope.launch {
            removeHiddenDuplicate(keyManga.manga.id, duplicateManga.manga.id)
            withUIContext {
                removeSingleMangaFromList(keyManga, duplicateManga.manga.id)
                removeSingleMangaFromList(duplicateManga, keyManga.manga.id)
            }
        }
    }

    fun unhideGroupDuplicate(keyManga: MangaWithChapterCount, duplicateManga: List<MangaWithChapterCount>) {
        screenModelScope.launch {
            duplicateManga.forEach {
                removeHiddenDuplicate(keyManga.manga.id, it.manga.id)
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

    sealed interface Dialog {
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val dialog: Dialog? = null,
    )
}
