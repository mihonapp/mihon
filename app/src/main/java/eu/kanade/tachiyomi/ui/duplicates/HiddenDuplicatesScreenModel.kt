package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.hiddenDuplicates.interactor.RemoveHiddenDuplicate
import tachiyomi.domain.manga.interactor.GetHiddenDuplicateManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiddenDuplicatesScreenModel(
    private val removeHiddenDuplicate: RemoveHiddenDuplicate = Injekt.get(),
    private val getHiddenDuplicateManga: GetHiddenDuplicateManga = Injekt.get(),
) : StateScreenModel<HiddenDuplicatesScreenModel.State>(State()) {

    private var _hiddenDuplicatesMapState: MutableStateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        MutableStateFlow(mapOf())
    val hiddenDuplicatesMapState: StateFlow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> =
        _hiddenDuplicatesMapState.asStateFlow()

    init {
        mutableState.update { it.copy(loading = true) }
        screenModelScope.launch {
            getHiddenDuplicateManga.subscribe().collectLatest { newMap ->
                _hiddenDuplicatesMapState.value = newMap.filterNot {
                    it.value.count() == 1 &&
                        newMap[it.value[0]]?.count() == 1 &&
                        newMap[it.value[0]]?.get(0) == it.key &&
                        it.value[0].manga.id < it.key.manga.id
                }
            }
        }
        mutableState.update { it.copy(loading = false) }
    }

    fun unhideSingleDuplicate(keyManga: MangaWithChapterCount, duplicateManga: MangaWithChapterCount) {
        screenModelScope.launch {
            removeHiddenDuplicate(keyManga.manga.id, duplicateManga.manga.id)
        }
    }

    fun unhideGroupDuplicate(keyManga: MangaWithChapterCount, duplicateManga: List<MangaWithChapterCount>) {
        screenModelScope.launch {
            duplicateManga.forEach {
                removeHiddenDuplicate(keyManga.manga.id, it.manga.id)
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
