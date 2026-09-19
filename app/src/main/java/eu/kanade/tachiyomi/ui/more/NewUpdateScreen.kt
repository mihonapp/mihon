package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.system.openInBrowser
import mihon.navigation.util.LocalBackStack

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    releaseLink: String,
    downloadLink: String,
) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val viewModel =
        assistedMetroViewModel<NewUpdateScreenModel, NewUpdateScreenModel.Factory> {
            create(changelogInfo = changelogInfo, downloadLink = downloadLink)
        }

    val state by viewModel.state.collectAsState()

    NewUpdateScreen(
        versionName = versionName,
        stage = state.stage,
        downloadProgress = { state.downloadProgress },
        changelogInfo = state.changelogInfo,
        onOpenInBrowser = { context.openInBrowser(releaseLink) },
        onAcceptUpdate = {
            when (state.stage) {
                NewUpdateScreenModel.Stage.Available, NewUpdateScreenModel.Stage.Failed -> viewModel.startDownload()
                NewUpdateScreenModel.Stage.Downloaded -> viewModel.installUpdate()
                else -> Unit
            }
        },
        onRejectUpdate = backStack::removeLastOrNull,
    )
}
