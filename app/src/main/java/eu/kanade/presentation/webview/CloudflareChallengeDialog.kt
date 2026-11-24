package eu.kanade.presentation.webview

import android.graphics.Rect
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CloudflareChallengeDialog() {
    val queue = LocalContext.current.appGraph.cloudflareChallengeQueue
    // The queue takes its subscribers as the app being in the foreground
    val challenges by queue.challenges.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val challenge = challenges.firstOrNull() ?: return
    val waiting = challenges.drop(1).map { it.host }
    val widget by challenge.widget.collectAsState()
    val windowSize = LocalWindowInfo.current.containerSize

    AlertDialog(
        onDismissRequest = challenge.cancel,
        dismissButton = {
            TextButton(onClick = challenge.cancel) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {},
        title = { Text(text = stringResource(MR.strings.cloudflare_challenge_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = stringResource(MR.strings.cloudflare_challenge_message, challenge.host))
                key(challenge) {
                    CroppedWebView(
                        webView = challenge.webView,
                        pageSize = windowSize,
                        crop = widget,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                if (waiting.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            MR.plurals.cloudflare_challenge_waiting,
                            waiting.size,
                            waiting.size,
                            waiting.joinToString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

/**
 * Lays the page out at [pageSize], as in a browser, but shows only its [crop]: all that fits until there is one.
 */
@Composable
private fun CroppedWebView(webView: WebView, pageSize: IntSize, crop: Rect?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            // Still in the previous activity's dialog when the app moved to another
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(pageSize.width, pageSize.height))
                val width = crop?.width() ?: constraints.maxWidth
                val height = crop?.height() ?: 240.dp.roundToPx()
                layout(width, height) {
                    placeable.place(-(crop?.left ?: 0), -(crop?.top ?: 0))
                }
            },
    )
}
