package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.ui.category.CategoryEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetMangaNotes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreenModel(
    val mangaId: Long,
    getManga: GetManga = Injekt.get(),
    setMangaNotes: SetMangaNotes = Injekt.get(),
) : StateScreenModel<MangaNotesScreenState>(MangaNotesScreenState.Loading) {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            getManga.subscribe(mangaId).collectLatest { manga ->
                mutableState.update {
                    MangaNotesScreenState.Success(
                        content = manga.notes,
                        title = manga.title,
                    )
                }
            }
        }
    }

    fun beginEditing() {
        mutableState.update {
            when (it) {
                MangaNotesScreenState.Loading -> it
                is MangaNotesScreenState.Success -> it.copy(editing = true)
            }
        }
    }

    fun endEditing() {
        mutableState.update {
            when (it) {
                MangaNotesScreenState.Loading -> it
                is MangaNotesScreenState.Success -> it.copy(editing = false)
            }
        }
    }

    fun saveText(content: String) {
        mutableState.update {
            when (it) {
                MangaNotesScreenState.Loading -> it
                is MangaNotesScreenState.Success -> it.copy(content = content)
            }
        }

        // do the magic to set it backend
    }
}

sealed interface MangaNotesScreenState {

    @Immutable
    data object Loading : MangaNotesScreenState

    @Immutable
    data class Success(
        val content: String?,
        val title: String,

        val editing: Boolean = false,
    ) : MangaNotesScreenState
}
