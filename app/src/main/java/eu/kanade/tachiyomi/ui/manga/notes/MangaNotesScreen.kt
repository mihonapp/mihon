package eu.kanade.tachiyomi.ui.manga.notes

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen

class MangaNotesScreen(private val manga: Manga) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MangaNotesScreenModel(manga = manga) }
        val state by screenModel.state.collectAsState()

        if (state is MangaNotesScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaNotesScreenState.Success

        BackHandler(
            onBack = {
                if (!successState.editing) {
                    navigator.pop()
                    return@BackHandler
                }

                screenModel.endEditing()
            },
        )

        MangaNotesScreen(
            state = successState,
            navigateUp = navigator::pop,
            beginEditing = { screenModel.beginEditing() },
            endEditing = { screenModel.endEditing() },
            onSave = { screenModel.saveText(it) }
        )
    }
}
