package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun TwoPanelBox(
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
) {
    val direction = LocalLayoutDirection.current
    val padding = contentWindowInsets.asPaddingValues()
    val startPadding = padding.calculateStartPadding(direction)
    val endPadding = padding.calculateEndPadding(direction)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth - startPadding - endPadding
        val firstWidth = (width / 2).coerceAtMost(450.dp)
        val secondWidth = width - firstWidth
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(firstWidth + startPadding),
            content = startContent,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(secondWidth + endPadding),
            content = endContent,
        )
    }
}
