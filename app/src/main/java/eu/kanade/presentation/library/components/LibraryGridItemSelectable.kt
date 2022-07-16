package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

fun Modifier.selectedOutline(isSelected: Boolean) = composed {
    val secondary = MaterialTheme.colorScheme.secondary
    if (isSelected) {
        drawBehind {
            val additional = 24.dp.value
            val offset = additional / 2
            val height = size.height + additional
            val width = size.width + additional
            drawRoundRect(
                color = secondary,
                topLeft = Offset(-offset, -offset),
                size = Size(width, height),
                cornerRadius = CornerRadius(offset),
            )
        }
    } else {
        this
    }
}

@Composable
fun LibraryGridItemSelectable(
    isSelected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(Modifier.selectedOutline(isSelected)) {
        CompositionLocalProvider(LocalContentColor provides if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
