package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        onClick = {},
    ) {
        Row(
            modifier = modifier.clickable(onClick = onClick)
                .widthIn(min = 56.dp)
                .requiredHeight(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodySmall, content)
        }
    }
}

@Composable
fun ChoiceChip(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onClick: () -> Unit = {},
    selectedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable () -> Unit,
) {
    if (isSelected) {
        Chip(modifier, selectedBackgroundColor, selectedContentColor, onClick, content)
    } else {
        Chip(modifier, onClick = onClick, content = content)
    }
}
