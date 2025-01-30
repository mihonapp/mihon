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
    private val manga: Manga,
    private val setMangaNotes: SetMangaNotes = Injekt.get(),
) : StateScreenModel<MangaNotesScreenState>(MangaNotesScreenState(manga, manga.notes)) {

    fun saveText(content: String) {
        // don't save what isn't modified
        if (content == state.value.notes) return

        mutableState.update {
            it.copy(notes = content)
        }

        screenModelScope.launchNonCancellable {
            setMangaNotes.awaitSetNotes(manga.id, content)
        }
    }
}

@Immutable
data class MangaNotesScreenState(
    val manga: Manga,
    val notes: String,
)
