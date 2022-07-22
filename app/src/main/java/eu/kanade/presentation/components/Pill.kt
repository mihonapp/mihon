package eu.kanade.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    elevation: Dp = 1.dp,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(100)),
        color = color,
        contentColor = contentColor,
        tonalElevation = elevation,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(6.dp, 1.dp),
            fontSize = fontSize,
        )
    }
}
