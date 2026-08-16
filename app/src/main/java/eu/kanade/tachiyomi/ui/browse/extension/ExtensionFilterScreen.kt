package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class ExtensionFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<ExtensionFilterViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is ExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as ExtensionFilterState.Success

        ExtensionFilterScreen(
            navigateUp = navigator::pop,
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
}
