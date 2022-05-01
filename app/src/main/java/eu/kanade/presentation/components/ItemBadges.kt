package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ItemBadges(
    modifier: Modifier = Modifier,
    primaryText: String,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = primaryText,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Medium,
            ),
        )

        // TODO: support more badges (e.g., for library items)
    }
}
