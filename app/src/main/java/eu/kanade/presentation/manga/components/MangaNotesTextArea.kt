package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreenState

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
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
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
