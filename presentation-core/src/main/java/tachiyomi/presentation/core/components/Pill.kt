package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = LocalTextStyle.current,
) {
    Surface(
        modifier = modifier
            .padding(start = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp, 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                style = style,
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
) {
    val style = LocalTextStyle.current
    Pill(
        text = text,
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
    )
}
