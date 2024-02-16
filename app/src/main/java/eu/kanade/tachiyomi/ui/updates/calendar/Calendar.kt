package eu.kanade.tachiyomi.ui.updates.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale


@Composable
fun Calendar(
    labelFormat: (DayOfWeek) -> String = {
        it.getDisplayName(
            TextStyle.SHORT,
            Locale.getDefault()
        )
    }
) {


    val today = LocalDate.now()
    val weekValue = remember { DayOfWeek.entries.toTypedArray() }
    val displayedMonth = remember { mutableStateOf(today.month) }
    val displayedYear = remember { mutableIntStateOf(today.year) }
    val currentMonth = displayedMonth.value
    val currentYear = displayedYear.value

    val daysInMonth = currentMonth.length(true);
    val startDayOfMonth = LocalDate.of(currentYear, currentMonth, 1)
    val firstDayOfMonth = startDayOfMonth.dayOfWeek;

    Column(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(all = 8.dp)
    ) {
        CalenderHeader(month = today.month, year = today.year)
        Spacer(modifier = Modifier.padding(vertical = 4.dp))
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Fixed(7),
        ) {
            items(weekValue) { item ->
                Text(
                    modifier = Modifier,
                    text = labelFormat(item),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            val selected = LocalDate.of(2024, 2, 15)

            items((getFirstDayOfMonth(firstDayOfMonth)..daysInMonth).toList()) {
                if (it > 0 ) {
                    CalendarDay(
                        date = LocalDate.of(currentYear, currentMonth, it),
                        onDayClick = {},
                        selectedDate = selected)
                }
            }

        }
    }
}

private fun getFirstDayOfMonth(firstDayOfMonth: DayOfWeek) = -(firstDayOfMonth.value).minus(2)
