package eu.kanade.presentation.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A page that failed to load or decode: the message, Retry, and Open in WebView when [onOpenInWebView] is given.
 *
 * [onButtonPressChange] tells a viewer that reads every touch for its own taps when a button is held.
 */
@Composable
fun ReaderError(
    message: String?,
    onRetry: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onButtonPressChange: ((pressed: Boolean) -> Unit)? = null,
) {
    val buttonModifier = if (onButtonPressChange != null) Modifier.reportPress(onButtonPressChange) else Modifier
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = message ?: stringResource(MR.strings.decode_image_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = buttonModifier) {
            Text(stringResource(MR.strings.action_retry))
        }
        if (onOpenInWebView != null) {
            Button(onClick = onOpenInWebView, modifier = buttonModifier) {
                Text(stringResource(MR.strings.action_open_in_web_view))
            }
        }
    }
}

internal fun Modifier.reportPress(onPressChange: (Boolean) -> Unit) = pointerInput(onPressChange) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPressChange(true)
        waitForUpOrCancellation(pass = PointerEventPass.Initial)
        onPressChange(false)
    }
}
