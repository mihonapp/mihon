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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
    fontWeight: FontWeight = FontWeight.Medium,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    textColor: Color = MaterialTheme.colorScheme.onError,
    isCustomText: Boolean = false,
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
            if (isCustomText) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    style = style,
                    fontWeight = fontWeight,
                    color = textColor,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = text,
                    fontSize = fontSize,
                    maxLines = 1,
                )
            }
        }
    }
}
