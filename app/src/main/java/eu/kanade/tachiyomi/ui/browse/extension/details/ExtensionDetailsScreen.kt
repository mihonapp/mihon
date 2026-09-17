package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.Screen
import kotlinx.serialization.Serializable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data class ExtensionDetailsRoute(val pkgName: String) : NavKey

@Composable
fun ExtensionDetailsScreen(pkgName: String) {
    val viewModel =
        assistedMetroViewModel<ExtensionDetailsViewModel, ExtensionDetailsViewModel.Factory> {
            create(pkgName = pkgName)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val backStack = LocalBackStack.current

    when (val state = state) {
        ExtensionDetailsViewModel.State.Loading -> LoadingScreen()
        ExtensionDetailsViewModel.State.Uninstalled -> {
            LaunchedEffect(Unit) { backStack.removeLastOrNull() }
            EmptyScreen(MR.strings.empty_screen)
        }
        is ExtensionDetailsViewModel.State.Success -> {
            ExtensionDetailsScreen(
                navigateUp = backStack::removeLastOrNull,
                state = state,
                onClickSourcePreferences = { backStack.add(SourcePreferencesRoute(it)) },
                onClickEnableAll = { viewModel.toggleSources(true) },
                onClickDisableAll = { viewModel.toggleSources(false) },
                onClickClearCookies = viewModel::clearCookies,
                onClickUninstall = viewModel::uninstallExtension,
                onClickSource = viewModel::toggleSource,
                onClickIncognito = viewModel::toggleIncognito,
            )
        }
    }

}
