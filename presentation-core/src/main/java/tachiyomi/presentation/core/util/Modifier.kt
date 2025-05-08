package tachiyomi.presentation.core.util

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

@Composable
fun Modifier.selectedBackground(isSelected: Boolean): Modifier {
    if (!isSelected) return this
    val alpha = if (isSystemInDarkTheme()) 0.16f else 0.22f
    val color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha)
    return this.drawBehind { drawRect(color) }
}

fun Modifier.secondaryItemAlpha(): Modifier = this.alpha(SECONDARY_ALPHA)

fun Modifier.clickableNoIndication(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) = this.combinedClickable(
    interactionSource = null,
    indication = null,
    onLongClick = onLongClick,
    onClick = onClick,
)

/**
 * For TextField, the provided [action] will be invoked when
 * physical enter key is pressed.
 *
 * Naturally, the TextField should be set to single line only.
 */
fun Modifier.runOnEnterKeyPressed(action: () -> Unit): Modifier = this.onPreviewKeyEvent {
    when (it.key) {
        Key.Enter, Key.NumPadEnter -> {
            // Physical keyboards generate two event types:
            // - KeyDown when the key is pressed
            // - KeyUp when the key is released
            if (it.type == KeyEventType.KeyDown) {
                action()
                true
            } else {
                false
            }
        }

        else -> false
    }
}

/**
 * For TextField on AppBar, this modifier will request focus
 * to the element the first time it's composed.
 */
@Composable
fun Modifier.showSoftKeyboard(show: Boolean): Modifier {
    if (!show) return this
    val focusRequester = remember { FocusRequester() }
    var openKeyboard by rememberSaveable { mutableStateOf(show) }
    LaunchedEffect(focusRequester) {
        if (openKeyboard) {
            focusRequester.requestFocus()
            openKeyboard = false
        }
    }
    return this.focusRequester(focusRequester)
}

/**
 * For TextField, this modifier will clear focus when soft
 * keyboard is hidden.
 */
@Composable
fun Modifier.clearFocusOnSoftKeyboardHide(
    onFocusCleared: (() -> Unit)? = null,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    var keyboardShowedSinceFocused by remember { mutableStateOf(false) }
    if (isFocused) {
        val imeVisible = WindowInsets.isImeVisible
        val focusManager = LocalFocusManager.current
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                keyboardShowedSinceFocused = true
            } else if (keyboardShowedSinceFocused) {
                focusManager.clearFocus()
                onFocusCleared?.invoke()
            }
        }
    }

    return this.onFocusChanged {
        if (isFocused != it.isFocused) {
            if (isFocused) {
                keyboardShowedSinceFocused = false
            }
            isFocused = it.isFocused
        }
    }
}
