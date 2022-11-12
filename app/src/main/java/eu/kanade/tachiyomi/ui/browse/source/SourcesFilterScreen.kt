package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourcesFilterScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.toast

class SourcesFilterScreen : Screen {

    @Composable
    override fun Content() {
        val router = LocalRouter.currentOrThrow
        val screenModel = rememberScreenModel { SourcesFilterScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is SourcesFilterState.Loading) {
            LoadingScreen()
            return
        }

        if (state is SourcesFilterState.Error) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.toast(R.string.internal_error)
                router.popCurrentController()
            }
            return
        }

        val successState = state as SourcesFilterState.Success

        SourcesFilterScreen(
            navigateUp = router::popCurrentController,
            state = successState,
            onClickLanguage = screenModel::toggleLanguage,
            onClickSource = screenModel::toggleSource,
        )
    }
}
