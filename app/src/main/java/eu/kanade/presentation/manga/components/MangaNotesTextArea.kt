package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreenState

private const val MAX_LENGTH = 10_000

@Composable
fun MangaNotesTextArea(
    state: MangaNotesScreenState.Success,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember {
        mutableStateOf(TextFieldValue(state.notes.orEmpty(), TextRange(Int.MAX_VALUE)))
    }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.text.length <= MAX_LENGTH) text = it },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            supportingText = {
                val displayWarning = text.text.length > MAX_LENGTH / 10 * 9
                if (!displayWarning) {
                    Text(
                        text = "0",
                        modifier = Modifier.alpha(0f),
                    )
                }
                AnimatedVisibility(
                    displayWarning,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "${text.text.length} / $MAX_LENGTH",
                    )
                }
            },
        )
    }

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            onSave(text.text)
        }
    }
}
