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

@Composable
fun MangaNotesDisplay(
    content: String,
    modifier: Modifier,
) {
    val alpha = remember { Animatable(1f) }
    var isFirstUpdate by remember { mutableStateOf(true) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val richTextState = rememberRichTextState()
    LaunchedEffect(content) {
        richTextState.setMarkdown(markdown = content)

        if (!isFirstUpdate) {
            alpha.snapTo(targetValue = 0f)
            alpha.animateTo(targetValue = 1f, animationSpec = tween(500))
            return@LaunchedEffect
        }

        isFirstUpdate = false
    }
    LaunchedEffect(Unit) {
        richTextState.config.listIndent = 10
    }
    LaunchedEffect(primaryColor) {
        richTextState.config.linkColor = primaryColor
    }

    // to prevent the content size from animating from first render
    val richTextModifier = remember(isFirstUpdate) {
        if (isFirstUpdate) modifier else modifier.animateContentSize()
    }

    SelectionContainer {
        RichText(
            modifier = richTextModifier
                .alpha(alpha.value),
            style = MaterialTheme.typography.bodyMedium,
            state = richTextState,
        )
    }
}
