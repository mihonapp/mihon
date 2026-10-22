package eu.kanade.tachiyomi.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.webview.WebViewScreenContent
import mihon.core.navigation.util.LocalAssistUrl
import mihon.core.navigation.util.LocalNavigator
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun WebViewScreen(
    url: String,
    initialTitle: String?,
    sourceId: Long?,
) {
    val navigator = LocalNavigator.current
    val setAssistUrl = LocalAssistUrl.current
    val context = LocalContext.current
    val viewModel =
        assistedMetroViewModel<WebViewViewModel, WebViewViewModel.Factory> { create(sourceId = sourceId) }

    val headers by viewModel.headers.collectAsState()
    if (headers == null) {
        LoadingScreen()
        return
    }

    WebViewScreenContent(
        onNavigateUp = navigator::pop,
        initialTitle = initialTitle,
        url = url,
        headers = headers.orEmpty(),
        defaultUserAgentProvider = viewModel::defaultUserAgentProvider,
        onUrlChange = {
            setAssistUrl(it)
        },
        onShare = { viewModel.shareWebpage(context, it) },
        onOpenInBrowser = { viewModel.openInBrowser(context, it) },
        onClearCookies = viewModel::clearCookies,
    )
}
