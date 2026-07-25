package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourcesFilterScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class SourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<SourcesFilterViewModel>()
        val state by viewModel.state.collectAsState()

        if (state is SourcesFilterViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        if (state is SourcesFilterViewModel.State.Error) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.toast(MR.strings.internal_error)
                navigator.pop()
            }
            return
        }

        val successState = state as SourcesFilterViewModel.State.Success

        SourcesFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickLanguage = viewModel::toggleLanguage,
            onClickSource = viewModel::toggleSource,
        )
    }
}
