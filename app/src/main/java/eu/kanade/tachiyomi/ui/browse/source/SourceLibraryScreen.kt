package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.browse.SourceLibraryScreen as SourceLibraryScreenContent

class SourceLibraryScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<SourceLibraryViewModel, SourceLibraryViewModel.Factory> {
            create(sourceId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        SourceLibraryScreenContent(
            title = state.title,
            titles = state.titles,
            errorIds = state.errorIds,
            onClickManga = { navigator.push(MangaScreen(it)) },
            navigateUp = navigator::pop,
        )
    }
}
