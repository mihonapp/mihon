package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

class MangaNotesScreen(private val mangaId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MangaNotesScreenModel(mangaId = mangaId) }
        val state by screenModel.state.collectAsState()

        DisposableEffect(Unit) {
            onDispose {

            }
        }

        if (state is MangaNotesScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaNotesScreenState.Success

        MangaNotesScreen(
            state = successState,
            navigateUp = navigator::pop,
            beginEditing = { screenModel.beginEditing() },
            endEditing = { screenModel.endEditing() },
        )
    }
}
