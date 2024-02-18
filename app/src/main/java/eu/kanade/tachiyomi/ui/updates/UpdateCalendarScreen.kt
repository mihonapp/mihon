package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.updates.UpdateCalendarScreen
import eu.kanade.tachiyomi.ui.updates.calendar.UpdateCalendarScreenModel

class UpdateCalendarScreen : Screen() {

    @Composable
    override fun Content() {

        val screenModel = rememberScreenModel {
            UpdateCalendarScreenModel()
        }
        val state by screenModel.state.collectAsState()

        UpdateCalendarScreen(
            state = state,
        )
    }

}
