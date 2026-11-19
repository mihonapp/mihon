package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.stack.mutableStateStackOf
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.webview.WebViewAdBlocker
import eu.kanade.tachiyomi.util.system.getHtml
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.TabClose
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.action_open_in_browser
import tachiyomi.i18n.action_share
import tachiyomi.i18n.action_webview_back
import tachiyomi.i18n.action_webview_close_tab
import tachiyomi.i18n.action_webview_forward
import tachiyomi.i18n.action_webview_open_in_app
import tachiyomi.i18n.action_webview_refresh
import tachiyomi.i18n.information_cloudflare_help
import tachiyomi.i18n.information_webview_crashed
import tachiyomi.i18n.no_results_found
import tachiyomi.i18n.pref_clear_cookies
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

private class WebViewWindow(
    webContent: WebContent,
    val navigator: WebViewNavigator,
    val popupMessage: Message? = null,
    val openerUrl: String? = null,
) {
    var state by mutableStateOf(WebViewState(webContent))
    var currentUrl by mutableStateOf((webContent as? WebContent.Url)?.url)
    var webView: WebView? = null
}

@Composable
fun WebViewScreenContent(
    onNavigateUp: () -> Unit,
    initialTitle: String?,
    url: String,
    defaultUserAgentProvider: () -> String,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    canOpenManga: suspend (String) -> Boolean,
    resolveManga: suspend (String) -> Long?,
    onOpenManga: (Long) -> Unit,
    onClearCookies: (String) -> Unit,
    adBlocker: WebViewAdBlocker,
    headers: Map<String, String> = emptyMap(),
    onUrlChange: (String) -> Unit = {},
) {
    if (adBlocker.settings.collectAsState().value == null) {
        LoadingScreen()
        return
    }

    val coroutineScope = rememberCoroutineScope()

    val windowStack = remember {
        mutableStateStackOf(
            WebViewWindow(
                WebContent.Url(url = url, additionalHttpHeaders = headers),
                WebViewNavigator(coroutineScope),
            ),
        )
    }

    val currentWindow = windowStack.lastItemOrNull!!
    val navigator = currentWindow.navigator
    val activeUrl = currentWindow.currentUrl
    val currentUrl = activeUrl ?: url

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navigateUp by rememberUpdatedState(onNavigateUp)
    val lifecycle = remember { WebViewLifecycle() }

    var showCloudflareHelp by remember { mutableStateOf(false) }
    var openingManga by remember { mutableStateOf(false) }

    fun closeScreen(onClose: () -> Unit = {}) {
        lifecycle.close {
            onClose()
            navigateUp()
        }
    }

    val mangaUrl by produceState<String?>(null, activeUrl) {
        value = null
        if (activeUrl != null && canOpenManga(activeUrl)) value = activeUrl
    }

    LaunchedEffect(currentUrl) {
        onUrlChange(currentUrl)
    }

    DisposableEffect(lifecycle) {
        onDispose {
            windowStack.items.forEach { it.webView = null }
            lifecycle.dispose()
        }
    }

    val headersOrigin = remember(url) { url.toHttpUrlOrNull() }

    val webClient = remember {
        object : AccompanistWebViewClient() {
            private fun canUse(view: WebView) = lifecycle.isActive && lifecycle.url(view) != null

            private fun updateUrl(view: WebView, url: String?) {
                if (url != null) {
                    lifecycle.updateUrl(view, url)
                    windowStack.items.find { it.webView === view }?.currentUrl = url
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!canUse(view)) return
                super.onPageStarted(view, url, favicon)
                updateUrl(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!canUse(view)) return
                super.onPageFinished(view, url)
                coroutineScope.launch {
                    if (!canUse(view)) return@launch
                    val html = view.getHtml()
                    showCloudflareHelp = "window._cf_chl_opt" in html || "Ray ID is" in html
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                if (!canUse(view)) return
                super.doUpdateVisitedHistory(view, url, isReload)
                updateUrl(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme.equals("intent", ignoreCase = true)) return true
                if (!request.isForMainFrame || view == null) return false
                if (!canUse(view)) return true

                val popup = windowStack.lastItemOrNull
                if ((uri.scheme == "http" || uri.scheme == "https") &&
                    popup?.popupMessage != null && popup.webView === view &&
                    adBlocker.shouldBlock(
                        url = uri.toString(),
                        host = uri.host,
                        sourceUrl = request.header("Referer") ?: popup.openerUrl ?: url,
                        resourceType = "document",
                        method = request.method,
                    )
                ) {
                    windowStack.pop()
                    return true
                }

                if (headers.isEmpty()) return false

                val url = uri.toString()
                val origin = headersOrigin ?: return false
                val target = url.toHttpUrlOrNull() ?: return false
                if (target.scheme != origin.scheme || target.host != origin.host || target.port != origin.port) {
                    return false
                }
                if (url == view.url) return false

                view.loadUrl(url, headers)
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (!request.isForMainFrame && (uri.scheme == "http" || uri.scheme == "https") &&
                    adBlocker.shouldBlock(
                        url = uri.toString(),
                        host = uri.host,
                        sourceUrl = request.header("Referer") ?: lifecycle.url(view) ?: url,
                        resourceType = request.resourceType(),
                        method = request.method,
                    )
                ) {
                    return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                windowStack.items.firstOrNull { it.webView === view }?.webView = null
                val failedUrl = lifecycle.release(view) ?: return true
                closeScreen {
                    logcat(LogPriority.ERROR) {
                        "WebView renderer gone: crashed=${detail.didCrash()}, " +
                            "priority=${detail.rendererPriorityAtExit()}, url=$failedUrl"
                    }
                    context.toast(MR.strings.information_webview_crashed, Toast.LENGTH_LONG)
                }
                return true
            }
        }
    }

    val webChromeClient = remember {
        object : AccompanistWebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                // if it wasn't initiated by a user gesture, we should ignore it like a normal browser would
                if (lifecycle.isActive && isUserGesture) {
                    windowStack.push(
                        WebViewWindow(
                            WebContent.NavigatorOnly,
                            WebViewNavigator(coroutineScope),
                            popupMessage = resultMsg,
                            openerUrl = view.url,
                        ),
                    )
                    return true
                }
                return false
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!lifecycle.isActive) {
                    result.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!lifecycle.isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                if (!lifecycle.isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    fun initializePopup(webView: WebView, message: Message): WebView {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()
        return webView
    }

    fun popWindow() {
        if (!lifecycle.isActive) return
        if (windowStack.size == 1) {
            closeScreen()
        } else {
            windowStack.pop()
        }
    }

    BackHandler(lifecycle.isActive, ::popWindow)

    fun openMangaInApp() {
        if (!lifecycle.isActive || openingManga) return
        openingManga = true
        coroutineScope.launch {
            try {
                val mangaId = resolveManga(currentUrl)
                if (!lifecycle.isActive) return@launch
                if (mangaId == null) {
                    context.toast(MR.strings.no_results_found)
                } else {
                    onOpenManga(mangaId)
                }
            } finally {
                openingManga = false
            }
        }
    }

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = currentWindow.state.pageTitle ?: initialTitle,
                        subtitle = currentUrl,
                        navigateUp = { closeScreen() },
                        navigationIcon = MaterialSymbols.Rounded.Close,
                        actions = {
                            AppBarActions(
                                listOfNotNull(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_back),
                                        icon = MaterialSymbols.AutoMirroredRounded.ArrowBack,
                                        onClick = {
                                            if (navigator.canGoBack) {
                                                navigator.navigateBack()
                                            }
                                        },
                                        enabled = navigator.canGoBack,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_forward),
                                        icon = MaterialSymbols.AutoMirroredRounded.ArrowForward,
                                        onClick = {
                                            if (navigator.canGoForward) {
                                                navigator.navigateForward()
                                            }
                                        },
                                        enabled = navigator.canGoForward,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_webview_refresh),
                                        onClick = { navigator.reload() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_share),
                                        onClick = { onShare(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        onClick = { onOpenInBrowser(currentUrl) },
                                    ),
                                    if (activeUrl != null && mangaUrl == activeUrl && !openingManga) {
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_webview_open_in_app),
                                            onClick = ::openMangaInApp,
                                        )
                                    } else {
                                        null
                                    },
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_clear_cookies),
                                        onClick = { onClearCookies(currentUrl) },
                                    ),
                                ).toMutableList().apply {
                                    if (windowStack.size > 1) {
                                        add(
                                            0,
                                            AppBar.Action(
                                                title = stringResource(MR.strings.action_webview_close_tab),
                                                icon = MaterialSymbols.Rounded.TabClose,
                                                onClick = ::popWindow,
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                    )

                    if (showCloudflareHelp) {
                        Surface(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            WarningBanner(
                                textRes = MR.strings.information_cloudflare_help,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        uriHandler.openUri(
                                            "https://mihon.app/docs/guides/troubleshooting/#cloudflare",
                                        )
                                    },
                            )
                        }
                    }
                }
                val loadingState = currentWindow.state.loadingState
                when {
                    openingManga || loadingState is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    loadingState is LoadingState.Loading -> LinearProgressIndicator(
                        progress = { loadingState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    else -> {}
                }
            }
        },
    ) { contentPadding ->
        if (!lifecycle.isActive) return@Scaffold

        // We need to key the WebView composable to the window object since simply updating the WebView composable will
        // not cause it to re-invoke the WebView factory and render the new current window's WebView. This lets us
        // completely reset the WebView composable when the current window switches.
        key(currentWindow) {
            WebView(
                state = currentWindow.state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                navigator = navigator,
                onCreated = { webView ->
                    webView.setDefaultSettings()

                    // Debug mode (chrome://inspect/#devices)
                    if (BuildConfig.DEBUG &&
                        0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                    ) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    webView.setUserAgent(headers["user-agent"] ?: defaultUserAgentProvider())
                },
                onDispose = { webView ->
                    val window = windowStack.items.firstOrNull { it.webView === webView }
                    if (window != null && lifecycle.isActive) {
                        // A parked tab must resume without reloading its starting page.
                        window.state.content = WebContent.NavigatorOnly
                    } else {
                        window?.webView = null
                        lifecycle.release(webView)
                    }
                },
                client = webClient,
                chromeClient = webChromeClient,
                factory = { context ->
                    currentWindow.webView
                        ?: WebView(context).also { webView ->
                            currentWindow.webView = webView
                            lifecycle.register(webView, currentWindow.currentUrl ?: currentWindow.openerUrl ?: url)
                            currentWindow.popupMessage?.let {
                                initializePopup(webView, it)
                            }
                        }
                },
            )
        }
    }
}

private fun WebResourceRequest.header(name: String) =
    requestHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun WebResourceRequest.resourceType(): String {
    return when (header("Sec-Fetch-Dest")) {
        "script", "worker", "sharedworker", "serviceworker", "audioworklet", "paintworklet" -> "script"
        "iframe", "frame" -> "subdocument"
        "style" -> "stylesheet"
        "audio", "video", "track" -> "media"
        "image" -> "image"
        "font" -> "font"
        "object", "embed" -> "object"
        "empty" -> "xmlhttprequest"
        else -> {
            // WebView does not expose Chromium's request type or always provide Sec-Fetch-Dest.
            when (url.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                "js", "mjs" -> "script"
                "css" -> "stylesheet"
                "avif", "gif", "ico", "jpeg", "jpg", "png", "svg", "webp" -> "image"
                "woff", "woff2", "ttf", "otf" -> "font"
                "mp3", "mp4", "webm", "m3u8" -> "media"
                else -> "other"
            }
        }
    }
}
