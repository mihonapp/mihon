package eu.kanade.tachiyomi.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.webview.WebViewScreenContent
import mihon.core.navigation.util.LocalAssistContentManager
import mihon.core.navigation.util.LocalBackStack
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun WebViewScreen(
    url: String,
    initialTitle: String?,
    sourceId: Long?,
) {
    val backStack = LocalBackStack.current
    val assistContentManager = LocalAssistContentManager.current
    val context = LocalContext.current
    val viewModel =
        assistedMetroViewModel<WebViewViewModel, WebViewViewModel.Factory> { create(sourceId = sourceId) }

    val headers by viewModel.headers.collectAsState()
    if (headers == null) {
        LoadingScreen()
        return
    }

    WebViewScreenContent(
        onNavigateUp = backStack::removeLastOrNull,
        initialTitle = initialTitle,
        url = url,
        headers = headers.orEmpty(),
        defaultUserAgentProvider = viewModel::defaultUserAgentProvider,
        onUrlChange = {
            assistContentManager.currentAssistUrl = it
        },
        onShare = { viewModel.shareWebpage(context, it) },
        onOpenInBrowser = { viewModel.openInBrowser(context, it) },
        onClearCookies = viewModel::clearCookies,
    )
}
