package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<Model>(
            factory = Model.Factory,
            extras = CreationExtras {
                set(Model.MANGA_KEY, manga)
            },
        )
        val state by viewModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = viewModel::updateNotes,
        )
    }

    class Model(
        private val manga: Manga,
        private val updateMangaNotes: UpdateMangaNotes = Injekt.get(),
    ) : StateViewModel<State>(State(manga, manga.notes)) {

        companion object {
            val MANGA_KEY = CreationExtras.Key<Manga>()

            val Factory = viewModelFactory {
                initializer {
                    Model(
                        manga = get(MANGA_KEY)!!,
                    )
                }
            }
        }

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
                it.copy(notes = content)
            }

            viewModelScope.launchNonCancellable {
                updateMangaNotes(manga.id, content)
            }
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
