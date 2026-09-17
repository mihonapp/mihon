package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.SourcesFilterScreen
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.serialization.Serializable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data object SourcesFilterRoute : NavKey

@Composable
fun SourcesFilterScreen() {
    val backStack = LocalBackStack.current
    val viewModel = metroViewModel<SourcesFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is SourcesFilterViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    if (state is SourcesFilterViewModel.State.Error) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            context.toast(MR.strings.internal_error)
            backStack.removeLastOrNull()
        }
        return
    }

    val successState = state as SourcesFilterViewModel.State.Success

    SourcesFilterScreen(
        navigateUp = backStack::removeLastOrNull,
        state = successState,
        onClickLanguage = viewModel::toggleLanguage,
        onClickSource = viewModel::toggleSource,
    )
}
