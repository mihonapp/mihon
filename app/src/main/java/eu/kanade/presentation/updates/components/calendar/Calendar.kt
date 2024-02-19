package eu.kanade.presentation.updates.components.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private val CalenderPadding = 8.dp
private val FontSize = 16.sp
private const val ExtendedScale = 0.31f
private const val MediumScale = 0.60f
private const val HeightMultiplier = 68
private const val DaysOfWeek = 7

@Composable
fun Calendar(
    events: ImmutableMap<LocalDate, Int>,
    screenWidth: Dp,
    modifier: Modifier = Modifier,
    isTabletUi: Boolean = false,
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

    val widthModifier = when {
        isTabletUi -> 1.0f
        screenWidth > 840.dp -> ExtendedScale
        screenWidth > 600.dp -> MediumScale
        else -> 1.0f
    }

    Column(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(all = CalenderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            CalendarGrid(
                weekValue = weekValue,
                labelFormat = labelFormat,
                currentMonth = currentMonth,
                currentYear = currentYear,
                isTabletUi = isTabletUi,
                events = events,
                widthModifier = widthModifier,
                onClickDay = onClickDay,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    weekValue: Array<DayOfWeek>,
    labelFormat: (DayOfWeek) -> String,
    currentMonth: Month,
    currentYear: Int,
    isTabletUi: Boolean,
    events: ImmutableMap<LocalDate, Int>,
    modifier: Modifier = Modifier,
    onClickDay: (day: LocalDate) -> Unit = {},
    widthModifier: Float = 1.0F,
) {
    val daysInMonth = currentMonth.length(true)
    val startDayOfMonth = LocalDate.of(currentYear, currentMonth, 1)
    val firstDayOfMonth = startDayOfMonth.dayOfWeek

    val dayEntries = (getFirstDayOfMonth(firstDayOfMonth)..daysInMonth).toImmutableList()
    val height = (((((dayEntries.size - 1) / DaysOfWeek) + ceil(1.0f - widthModifier)) * HeightMultiplier)).dp

    val modeModifier = if (isTabletUi) {
        modifier
            .fillMaxWidth(widthModifier)
            .wrapContentHeight()
    } else {
        modifier
            .fillMaxWidth(widthModifier)
            .height(height)
    }

    LazyVerticalGrid(
        modifier = modeModifier,
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

        items(dayEntries) {
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

private fun getFirstDayOfMonth(firstDayOfMonth: DayOfWeek) = -(firstDayOfMonth.value).minus(2)
