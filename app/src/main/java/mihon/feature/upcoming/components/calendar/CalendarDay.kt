package mihon.feature.upcoming.components.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import java.time.LocalDate

private const val MAX_EVENTS = 3

@Composable
fun CalendarDay(
    date: LocalDate,
    events: Int,
    onDayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }

    Box(
        modifier = modifier
            .then(
                if (today == date) {
                    Modifier.border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape = CircleShape)
            .clickable(onClick = onDayClick)
            .circleLayout(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = if (date.isBefore(today)) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = DISABLED_ALPHA)
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.offset(y = 12.dp)) {
            val size = events.coerceAtMost(MAX_EVENTS)
            for (index in 0 until size) {
                CalendarIndicator(
                    index = index,
                    size = 56.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun Modifier.circleLayout() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)

    val currentHeight = placeable.height
    val currentWidth = placeable.width
    val newDiameter = maxOf(currentHeight, currentWidth)

    layout(newDiameter, newDiameter) {
        placeable.placeRelative(
            x = (newDiameter - currentWidth) / 2,
            y = (newDiameter - currentHeight) / 2,
        )
    }
}
