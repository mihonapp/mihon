package eu.kanade.presentation.updates.components.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableMap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val CalenderPadding = 8.dp
private val FontSize = 16.sp
private val CalculatedHeight = 302.dp
private const val DaysOfWeek = 7

@Composable
fun Calendar(
    events: ImmutableMap<LocalDate, Int>,
    modifier: Modifier = Modifier,
    labelFormat: (DayOfWeek) -> String = {
        it.getDisplayName(
            TextStyle.SHORT,
            Locale.getDefault(),
        )
    },
    onClickDay: (day: LocalDate) -> Unit = {},
) {
    val today = LocalDate.now()
    val weekValue = remember { DayOfWeek.entries.toTypedArray() }
    val displayedMonth = remember { mutableStateOf(today.month) }
    val displayedYear = remember { mutableIntStateOf(today.year) }
    val currentMonth = displayedMonth.value
    val currentYear = displayedYear.intValue

    val daysInMonth = currentMonth.length(true)
    val startDayOfMonth = LocalDate.of(currentYear, currentMonth, 1)
    val firstDayOfMonth = startDayOfMonth.dayOfWeek

    Column(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(all = CalenderPadding),
    ) {
        CalenderHeader(
            month = currentMonth,
            year = currentYear,
            onPreviousClick = {
                displayedYear.intValue -= if (currentMonth == Month.JANUARY) 1 else 0
                displayedMonth.value -= 1
            },
            onNextClick = {
                displayedYear.intValue += if (currentMonth == Month.DECEMBER) 1 else 0
                displayedMonth.value += 1
            },
        )
        Spacer(modifier = Modifier.padding(vertical = 4.dp))
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(CalculatedHeight),
            columns = GridCells.Fixed(DaysOfWeek),
        ) {
            items(weekValue) { item ->
                Text(
                    modifier = Modifier,
                    text = labelFormat(item),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FontSize,
                )
            }

            items((getFirstDayOfMonth(firstDayOfMonth)..daysInMonth).toList()) {
                if (it > 0) {
                    val localDate = LocalDate.of(currentYear, currentMonth, it)
                    CalendarDay(
                        date = localDate,
                        onDayClick = onClickDay,
                        events = events[localDate] ?: 0,
                    )
                }
            }
        }
    }
}

private fun getFirstDayOfMonth(firstDayOfMonth: DayOfWeek) = -(firstDayOfMonth.value).minus(2)
