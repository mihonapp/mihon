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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.util.isTabletUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
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
    labelFormat: (DayOfWeek) -> String = {
        it.getDisplayName(
            TextStyle.NARROW,
            Locale.getDefault(),
        )
    },
    onClickDay: (day: LocalDate) -> Unit = {},
) {
    val currentYearMonth = remember { mutableStateOf(YearMonth.now()) }
    val isTabletUi = isTabletUi()

    val localFirstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value
    val weekDays = remember {
        (0 until DaysOfWeek)
            .map { DayOfWeek.of(((localFirstDayOfWeek - 1 + it) % DaysOfWeek) + 1) }
            .toImmutableList()
    }

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
            yearMonth = currentYearMonth.value,
            onPreviousClick = { currentYearMonth.value = currentYearMonth.value.minusMonths(1L) },
            onNextClick = { currentYearMonth.value = currentYearMonth.value.plusMonths(1L) },
        )
        Spacer(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            CalendarGrid(
                weekDays = weekDays,
                labelFormat = labelFormat,
                currentYearMonth = currentYearMonth.value,
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
    weekDays: ImmutableList<DayOfWeek>,
    labelFormat: (DayOfWeek) -> String,
    currentYearMonth: YearMonth,
    isTabletUi: Boolean,
    events: ImmutableMap<LocalDate, Int>,
    modifier: Modifier = Modifier,
    onClickDay: (day: LocalDate) -> Unit = {},
    widthModifier: Float = 1.0F,
) {
    val daysInMonth = currentYearMonth.month.length(true)
    val startDayOfMonth = LocalDate.of(currentYearMonth.year, currentYearMonth.monthValue, 1)
    val firstDayOfMonth = startDayOfMonth.dayOfWeek

    // The lower bound for Calendar Days, between -5 and 1 to provide cell offset
    val dayEntries = (-weekDays.indexOf(firstDayOfMonth) + 1..daysInMonth).toImmutableList()
    val height = (((dayEntries.size - 1) / DaysOfWeek + ceil(1.0f - widthModifier)) * HeightMultiplier).dp

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
        items(weekDays) { item ->
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
                val localDate = LocalDate.of(currentYearMonth.year, currentYearMonth.month, it)
                CalendarDay(
                    date = localDate,
                    onDayClick = onClickDay,
                    events = events[localDate] ?: 0,
                )
            }
        }
    }
}
