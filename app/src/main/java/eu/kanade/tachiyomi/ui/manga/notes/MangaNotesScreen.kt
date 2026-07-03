package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.nativeClipboardManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val mangaNotes: String,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                mangaNotes = mangaNotes,
            )
        }
        val state by screenModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }

    private class Model(
        mangaTitle: String,
        mangaNotes: String,
        private val mangaId: Long,
        private val updateMangaNotes: UpdateMangaNotes = Injekt.get(),
    ) : StateScreenModel<State>(State(mangaId, mangaTitle, mangaNotes)) {

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
                it.copy(notes = content)
            }

            screenModelScope.launchNonCancellable {
                updateMangaNotes(mangaId, content)
            }
        }
    }

    @Immutable
    data class State(
        val mangaId: Long,
        val mangaTitle: String,
        val notes: String,
    )
}
