package eu.kanade.tachiyomi.ui.updates.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun CalendarDay(
    date: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate = date,
) {
    val selected = selectedDate == date

    val today = LocalDate.now()

    val inThePast = date.isBefore(today)

    val currentDay = today == date

    Column(
        modifier = modifier
            .border(
                border = getBorder(currentDay, MaterialTheme.colorScheme.onBackground, selected),
                shape = CircleShape,
            )
            .clip(shape = CircleShape)
            .dayBackgroundColor(
                selected,
                MaterialTheme.colorScheme.primary,
                date,
            )
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
                selected -> MaterialTheme.colorScheme.onPrimary
                inThePast -> MaterialTheme.colorScheme.onBackground.copy(ContentAlpha.disabled)
                else -> MaterialTheme.colorScheme.onBackground
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/**
 * Returns the border stroke based on the current day, color, and selected state.
 *
 * @param currentDay Whether the day is the current day.
 * @param color The color of the border.
 * @param selected Whether the day is selected.
 *
 * @return The border stroke to be applied.
 */
private fun getBorder(currentDay: Boolean, color: Color, selected: Boolean): BorderStroke {
    val emptyBorder = BorderStroke(0.dp, Color.Transparent)
    return if (currentDay && selected.not()) {
        BorderStroke(1.dp, color)
    } else {
        emptyBorder
    }
}

private fun Modifier.circleLayout() =
    layout { measurable, constraints ->
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

private const val FULL_ALPHA = 1f
private const val TOWNED_DOWN_ALPHA = 0.4F

private fun Modifier.dayBackgroundColor(
    selected: Boolean,
    color: Color,
    date: LocalDate,
//    selectedRange: KalendarSelectedDayRange?
): Modifier {
//    val inRange = date == selectedRange?.start || date == selectedRange?.end

    val backgroundColor = when {
        selected -> color
//        selectedRange != null && date in selectedRange.start..selectedRange.end -> {
//            val alpha = if (inRange) FULL_ALPHA else TOWNED_DOWN_ALPHA
//            color.copy(alpha = alpha)
//        }

        else -> Color.Transparent
    }

    return this.then(
        background(backgroundColor),
    )
}
