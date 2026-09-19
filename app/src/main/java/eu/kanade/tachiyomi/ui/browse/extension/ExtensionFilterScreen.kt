package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.ExtensionFilterScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import mihon.navigation.util.LocalBackStack
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data object ExtensionFilterRoute : NavKey

@Composable
fun ExtensionFilterScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val viewModel = metroViewModel<ExtensionFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is ExtensionFilterState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as ExtensionFilterState.Success

    ExtensionFilterScreen(
        navigateUp = backStack::removeLastOrNull,
        state = successState,
        onClickToggle = viewModel::toggle,
    )

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest {
            when (it) {
                ExtensionFilterEvent.FailedFetchingLanguages -> {
                    context.stringResource(MR.strings.internal_error)
                }
            }
        }
    }
}
