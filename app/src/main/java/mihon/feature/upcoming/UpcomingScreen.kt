package mihon.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.domain.base.BasePreferences
import mihon.core.dualscreen.DualScreenState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { UpcomingScreenModel() }
        val state by screenModel.state.collectAsState()

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = screenModel::setSelectedYearMonth,
            onClickUpcoming = {
                val preferences = Injekt.get<BasePreferences>()
                if (preferences.enableDualScreenMode().get()) {
                    DualScreenState.openScreen(MangaScreen(it.id))
                } else {
                    navigator.push(MangaScreen(it.id))
                }
            },
        )
    }
}
