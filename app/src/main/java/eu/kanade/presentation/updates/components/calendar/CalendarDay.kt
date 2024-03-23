package eu.kanade.presentation.updates.components.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
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
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    events: Int = 0,
) {
    val today = remember { LocalDate.now() }

    val inThePast = date.isBefore(today)

    val currentDay = today == date

    Column(
        modifier = modifier
            .border(
                border = getBorder(currentDay, MaterialTheme.colorScheme.onBackground),
                shape = CircleShape,
            )
            .clip(shape = CircleShape)
            .background(color = Color.Transparent)
            .clickable(onClick = { onDayClick(date) })
            .circleLayout()
            .wrapContentSize()
            .defaultMinSize(56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            modifier = Modifier.wrapContentSize(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = when {
                inThePast -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onBackground
            },
            fontWeight = FontWeight.SemiBold,
        )
        Row {
            val size = events.coerceAtMost(MaxEvents)
            for (index in 0 until size) {
                Row {
                    CalendarIndicator(
                        index = index,
                        size = 56.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Returns the border stroke based on the current day, color, and selected state.
 *
 * @param currentDay Whether the day is the current day.
 * @param color The color of the border.
 *
 * @return The border stroke to be applied.
 */
private fun getBorder(currentDay: Boolean, color: Color): BorderStroke {
    val emptyBorder = BorderStroke(0.dp, Color.Transparent)
    return if (currentDay) {
        BorderStroke(1.dp, color)
    } else {
        emptyBorder
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
