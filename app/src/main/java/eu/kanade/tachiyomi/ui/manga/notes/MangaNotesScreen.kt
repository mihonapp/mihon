package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.manga.MangaNotesScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.core.navigation.util.LocalNavigator
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.model.Manga

@Composable
fun MangaNotesScreen(manga: Manga) {
    val navigator = LocalNavigator.current

    val viewModel = assistedMetroViewModel<MangaNotesViewModel, MangaNotesViewModel.Factory> { create(manga = manga) }
    val state by viewModel.state.collectAsState()

    MangaNotesScreen(
        state = state,
        navigateUp = navigator::pop,
        onUpdate = viewModel::updateNotes,
    )
}

@AssistedInject
class MangaNotesViewModel(
    @Assisted private val manga: Manga,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val updateMangaNotes: UpdateMangaNotes,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State(manga, manga.notes))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(manga: Manga): MangaNotesViewModel
    }

    fun updateNotes(content: String) {
        if (content == state.value.notes) return

        state.update {
            it.copy(notes = content)
        }

        // On Main so the debounced saves and the one on leaving are written in order
        appScope.launch(Dispatchers.Main.immediate) {
            updateMangaNotes(manga.id, content)
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
