package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

const val DIVIDER_ALPHA = 0.2f

@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = DividerDefaults.color,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color = color)
            .alpha(DIVIDER_ALPHA),
    )
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = DividerDefaults.color,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(color = color)
            .alpha(DIVIDER_ALPHA),
    )
}
