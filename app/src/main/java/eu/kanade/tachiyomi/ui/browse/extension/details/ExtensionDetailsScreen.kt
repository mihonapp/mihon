package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val viewModel = viewModel<ExtensionDetailsViewModel>(
            factory = ExtensionDetailsViewModel.Factory,
            extras = CreationExtras {
                set(ExtensionDetailsViewModel.PKG_NAME_KEY, pkgName)
            },
        )
        val state by viewModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        ExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            onClickEnableAll = { viewModel.toggleSources(true) },
            onClickDisableAll = { viewModel.toggleSources(false) },
            onClickClearCookies = viewModel::clearCookies,
            onClickUninstall = viewModel::uninstallExtension,
            onClickSource = viewModel::toggleSource,
            onClickIncognito = viewModel::toggleIncognito,
        )

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
