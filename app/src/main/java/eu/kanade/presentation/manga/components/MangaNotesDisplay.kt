package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

private val FADE_TIME = tween<Float>(500)

@Composable
fun MangaNotesDisplay(
    content: String,
    modifier: Modifier,
) {
    val alpha = remember { Animatable(1f) }
    var contentUpdatedOnce by remember { mutableStateOf(false) }

    val richTextState = rememberRichTextState()
    val primaryColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(content) {
        richTextState.setMarkdown(content)

        if (!contentUpdatedOnce) {
            contentUpdatedOnce = true
            return@LaunchedEffect
        }

        alpha.snapTo(targetValue = 0f)
        alpha.animateTo(targetValue = 1f, animationSpec = FADE_TIME)
    }
    LaunchedEffect(Unit) {
        richTextState.config.unorderedListIndent = 4
        richTextState.config.orderedListIndent = 20
    }
    LaunchedEffect(primaryColor) {
        richTextState.config.linkColor = primaryColor
    }

    SelectionContainer {
        RichText(
            modifier = modifier
                // Only animate size if the notes changes
                .then(if (contentUpdatedOnce) Modifier.animateContentSize() else Modifier)
                .alpha(alpha.value),
            style = MaterialTheme.typography.bodyMedium,
            state = richTextState,
        )
    }
}
