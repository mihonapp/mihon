package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.interactor.SetMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreenModel(
    val manga: Manga,
    private val setMangaNotes: SetMangaNotes = Injekt.get(),
) : StateScreenModel<MangaNotesScreenState>(MangaNotesScreenState.Loading) {

    private val successState: MangaNotesScreenState.Success?
        get() = state.value as? MangaNotesScreenState.Success

    init {
        mutableState.update {
            MangaNotesScreenState.Success(
                manga = manga,
                notes = manga.notes,
            )
        }
    }

    fun saveText(content: String) {
        // don't save what isn't modified
        if (content == successState?.notes) return

        mutableState.update {
            when (it) {
                MangaNotesScreenState.Loading -> it
                is MangaNotesScreenState.Success -> {
                    it.copy(notes = content)
                }
            }
        }

        screenModelScope.launchNonCancellable {
            setMangaNotes.awaitSetNotes(manga, content)
        }
    }
}

sealed interface MangaNotesScreenState {

    @Immutable
    data object Loading : MangaNotesScreenState

    @Immutable
    data class Success(
        val manga: Manga,
        val notes: String?,
    ) : MangaNotesScreenState
}
