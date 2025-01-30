package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.interactor.SetMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            Model(
                manga = manga,
            )
        }
        val state by screenModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onSave = { screenModel.saveText(it) },
        )
    }

    private class Model(
        private val manga: Manga,
        private val setMangaNotes: SetMangaNotes = Injekt.get(),
    ) : StateScreenModel<State>(State(manga, manga.notes)) {

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
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
