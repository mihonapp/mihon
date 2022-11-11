package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

class ExtensionFilterScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val router = LocalRouter.currentOrThrow
        val screenModel = rememberScreenModel { ExtensionFilterScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is ExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as ExtensionFilterState.Success

        ExtensionFilterScreen(
            navigateUp = router::popCurrentController,
            state = successState,
            onClickToggle = { screenModel.toggle(it) },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest {
                when (it) {
                    ExtensionFilterEvent.FailedFetchingLanguages -> {
                        context.toast(R.string.internal_error)
                    }
                }
            }
        }
    }
}
