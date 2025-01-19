package eu.kanade.presentation.manga.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

@Composable
fun MangaNotesDisplay(
    content: String,
    modifier: Modifier,
) {
    val richTextState = rememberRichTextState().setMarkdown(markdown = content)
    richTextState.config.linkColor = MaterialTheme.colorScheme.primary
    richTextState.config.listIndent = 10

    SelectionContainer {
        RichText(
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            state = richTextState,
        )
    }
}
