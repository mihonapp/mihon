package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TwoPanelBox(
    modifier: Modifier = Modifier,
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val firstWidth = (maxWidth / 2).coerceAtMost(450.dp)
        val secondWidth = maxWidth - firstWidth
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(firstWidth),
            content = startContent,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(secondWidth),
            content = endContent,
        )
    }
}
