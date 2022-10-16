package eu.kanade.presentation.more.settings.widget

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.LocalPreferenceHighlighted

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes logoRes: Int,
    @ColorInt logoColor: Int,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val highlighted = LocalPreferenceHighlighted.current
    Box(modifier = Modifier.highlightBackground(highlighted)) {
        Row(
            modifier = modifier
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .fillMaxWidth()
                .padding(horizontal = HorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = Color(logoColor), shape = RoundedCornerShape(8.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
            )
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    tint = Color(0xFF4CAF50),
                    contentDescription = null,
                )
            }
        }
    }
}
