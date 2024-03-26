package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.updates.UpdateUpcomingScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class UpdateUpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { UpdateUpcomingScreenModel() }
        val state by screenModel.state.collectAsState()

        UpdateUpcomingScreen(
            state = state,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
        )
    }
}
