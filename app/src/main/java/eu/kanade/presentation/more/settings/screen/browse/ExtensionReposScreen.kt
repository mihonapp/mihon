package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionReposScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

class ExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { ExtensionReposScreenModel() }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(url) {
            url?.let { screenModel.createRepo(it) }
        }

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        ExtensionReposScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(RepoDialog.Create) },
            onClickDelete = { screenModel.showDialog(RepoDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            RepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    repos = successState.repos,
                )
            }
            is RepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
