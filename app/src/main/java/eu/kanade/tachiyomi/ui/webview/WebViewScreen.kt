package eu.kanade.tachiyomi.ui.webview

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.webview.WebViewScreenContent
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.navigation.LocalAssistUrl
import mihon.core.navigation.WebViewRoute
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun WebViewScreen(
    route: WebViewRoute,
    onNavigateUp: () -> Unit,
) {
    val setAssistUrl = LocalAssistUrl.current
    val context = LocalContext.current

    if (!remember { WebViewUtil.supportsWebView(context) }) {
        LaunchedEffect(Unit) {
            context.toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            onNavigateUp()
        }
        return
    }

    val viewModel =
        assistedMetroViewModel<WebViewViewModel, WebViewViewModel.Factory> { create(sourceId = route.sourceId) }

    val headers by viewModel.headers.collectAsState()
    if (headers == null) {
        LoadingScreen()
        return
    }

    WebViewScreenContent(
        onNavigateUp = onNavigateUp,
        initialTitle = route.initialTitle,
        url = route.url,
        headers = headers.orEmpty(),
        defaultUserAgentProvider = viewModel::defaultUserAgentProvider,
        onUrlChange = setAssistUrl,
        onShare = { viewModel.shareWebpage(context, it) },
        onOpenInBrowser = { viewModel.openInBrowser(context, it) },
        onClearCookies = viewModel::clearCookies,
    )
}
