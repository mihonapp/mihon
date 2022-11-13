package eu.kanade.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.presentation.util.padding
import eu.kanade.tachiyomi.util.lang.toRelativeString
import java.text.DateFormat
import java.util.Date

@Composable
fun RelativeDateHeader(
    modifier: Modifier = Modifier,
    date: Date,
    relativeTime: Int,
    dateFormat: DateFormat,
) {
    val context = LocalContext.current
    Text(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        text = remember {
            date.toRelativeString(
                context,
                relativeTime,
                dateFormat,
            )
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
