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
import tachiyomi.domain.hiddenDuplicates.interactor.AddHiddenDuplicate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PossibleDuplicatesScreenModel(
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
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
            state.distinctUntilChangedBy { it.matchLevel }.collectLatest { state ->
                mutableState.update { it.copy(loading = true) }
                getDuplicateLibraryManga.subscribe(state.matchLevel).collectLatest { duplicatesMap ->
                    mutableState.update { it.copy(loading = true) }
                    _duplicatesMapState.value = duplicatesMap
                    mutableState.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun removeFavorite(manga: Manga, deleteDownloads: Boolean) {
        screenModelScope.launchNonCancellable {
            if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                if (manga.removeCovers() != manga) {
                    updateManga.awaitUpdateCoverLastModified(manga.id)
                }
            }
            if (deleteDownloads) {
                val source = sourceManager.get(manga.source) as? HttpSource
                if (source != null) {
                    downloadManager.deleteManga(manga, source)
                }
            }
        }
    }

    fun hideSingleDuplicate(keyManga: MangaWithChapterCount, duplicateManga: MangaWithChapterCount) {
        screenModelScope.launch {
            addHiddenDuplicate(keyManga.manga.id, duplicateManga.manga.id)
        }
    }

    fun hideGroupDuplicate(keyManga: MangaWithChapterCount, duplicateManga: List<MangaWithChapterCount>) {
        screenModelScope.launch {
            duplicateManga.forEach {
                addHiddenDuplicate(keyManga.manga.id, it.manga.id)
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
        mutableState.update { it.copy(dialog = Dialog.ConfirmRemove(mangaItem.manga)) }
    }

    fun setMatchLevel(level: LibraryPreferences.DuplicateMatchLevel) {
        mutableState.update { it.copy(matchLevel = level) }
    }

    sealed interface Dialog {
        data object FilterSheet : Dialog
        data class ConfirmRemove(val manga: Manga) : Dialog
    }

    @Immutable
    data class State(
        val matchLevel: LibraryPreferences.DuplicateMatchLevel,
        val loading: Boolean = true,
        val dialog: Dialog? = null,
    )
}
