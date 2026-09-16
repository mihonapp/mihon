package mihon.desktop.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

const val UPCOMING_CALENDAR_TEST_TAG = "upcoming_calendar"
const val UPCOMING_MONTH_HEADER_TEST_TAG = "upcoming_month_header"
const val UPCOMING_PREVIOUS_MONTH_TEST_TAG = "upcoming_previous_month"
const val UPCOMING_NEXT_MONTH_TEST_TAG = "upcoming_next_month"
const val UPCOMING_DAY_TEST_TAG_PREFIX = "upcoming_day_"

private const val DAYS_PER_WEEK = 7
private const val MAX_INDICATORS = 3

/**
 * Month calendar with a locale-aware weekday row and per-day event indicators.
 *
 * Only days that belong to [selectedMonth] are rendered; leading/trailing cells are blank, which
 * mirrors Mihon's Android Upcoming calendar.
 */
@Composable
fun UpcomingCalendar(
    selectedMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    eventCounts: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val locale = strings.locale
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val monthTitle = remember(selectedMonth, locale) {
        selectedMonth.format(
            DateTimeFormatter.ofPattern(
                if (locale.language ==
                    "zh"
                ) {
                    "yyyy年M月"
                } else {
                    "MMMM yyyy"
                },
                locale,
            ),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag(UPCOMING_CALENDAR_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(UPCOMING_MONTH_HEADER_TEST_TAG),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.testTag(UPCOMING_PREVIOUS_MONTH_TEST_TAG),
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = strings.text(UiText.PreviousMonth))
                }
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag(UPCOMING_NEXT_MONTH_TEST_TAG),
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = strings.text(UiText.NextMonth))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(DAYS_PER_WEEK) { index ->
                val day = firstDayOfWeek.plus(index.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val cells = remember(selectedMonth, firstDayOfWeek) {
            buildList<LocalDate?> {
                val firstDate = selectedMonth.atDay(1)
                val leadingBlanks = (firstDate.dayOfWeek.value - firstDayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
                repeat(leadingBlanks) { add(null) }
                for (day in 1..selectedMonth.lengthOfMonth()) {
                    add(selectedMonth.atDay(day))
                }
                while (size % DAYS_PER_WEEK != 0) add(null)
            }
        }

        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            UpcomingCalendarDay(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                isPast = date < today,
                                eventCount = eventCounts[date] ?: 0,
                                onClick = { onSelectDate(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingCalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isPast: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val indicatorColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(background)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .testTag(UPCOMING_DAY_TEST_TAG_PREFIX + date),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
        if (eventCount > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(eventCount.coerceAtMost(MAX_INDICATORS)) { index ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(indicatorColor.copy(alpha = 1f - index * 0.2f)),
                    )
                }
            }
        }
    }
}
