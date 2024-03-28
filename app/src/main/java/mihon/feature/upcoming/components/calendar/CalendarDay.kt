package mihon.feature.upcoming.components.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private const val MaxEvents = 3

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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .background(Color.Transparent)
            .clip(shape = CircleShape)
            .clickable(onClick = onDayClick)
            .circleLayout()
            .defaultMinSize(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = when {
                date.isBefore(today) -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onBackground
            },
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.offset(y = 12.dp),
        ) {
            val size = events.coerceAtMost(MaxEvents)
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
    // Measure the composable
    val placeable = measurable.measure(constraints)

    // get the current max dimension to assign width=height
    val currentHeight = placeable.height
    val currentWidth = placeable.width
    val newDiameter = maxOf(currentHeight, currentWidth)

    // assign the dimension and the center position
    layout(newDiameter, newDiameter) {
        // Where the composable gets placed
        placeable.placeRelative((newDiameter - currentWidth) / 2, (newDiameter - currentHeight) / 2)
    }
}
