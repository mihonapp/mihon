package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.LoadingState
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewNavigator
import com.google.accompanist.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.setDefaultSettings

@Composable
fun WebViewScreen(
    onUp: () -> Unit,
    initialTitle: String?,
    url: String,
    headers: Map<String, String> = emptyMap(),
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearCookies: (String) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberWebViewState(url = url, additionalHttpHeaders = headers)
    val navigator = rememberWebViewNavigator()

    Column {
        AppBar(
            title = state.pageTitle ?: initialTitle,
            subtitle = state.content.getCurrentUrl(),
            navigateUp = onUp,
            navigationIcon = Icons.Default.Close,
            actions = {
                AppBarActions(
                    listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_webview_back),
                            icon = Icons.Default.ArrowBack,
                            onClick = {
                                if (navigator.canGoBack) {
                                    navigator.navigateBack()
                                }
                            },
                            enabled = navigator.canGoBack,
                        ),
                        AppBar.Action(
                            title = stringResource(R.string.action_webview_forward),
                            icon = Icons.Default.ArrowForward,
                            onClick = {
                                if (navigator.canGoForward) {
                                    navigator.navigateForward()
                                }
                            },
                            enabled = navigator.canGoForward,
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(R.string.action_webview_refresh),
                            onClick = { navigator.reload() },
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(R.string.action_share),
                            onClick = { onShare(state.content.getCurrentUrl()!!) },
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(R.string.action_open_in_browser),
                            onClick = { onOpenInBrowser(state.content.getCurrentUrl()!!) },
                        ),
                        AppBar.OverflowAction(
                            title = stringResource(R.string.pref_clear_cookies),
                            onClick = { onClearCookies(state.content.getCurrentUrl()!!) },
                        ),
                    ),
                )
            },
        )

        Box {
            val loadingState = state.loadingState
            if (loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = loadingState.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f),
                )
            }

            val webClient = remember {
                object : AccompanistWebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        request?.let {
                            view?.loadUrl(it.url.toString(), headers)
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                }
            }

            WebView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                navigator = navigator,
                onCreated = { webView ->
                    webView.setDefaultSettings()

                    // Debug mode (chrome://inspect/#devices)
                    if (BuildConfig.DEBUG && 0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    headers["user-agent"]?.let {
                        webView.settings.userAgentString = it
                    }
                },
                client = webClient,
            )
        }
    }
}
