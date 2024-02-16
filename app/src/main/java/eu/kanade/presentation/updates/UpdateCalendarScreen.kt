package eu.kanade.presentation.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import eu.kanade.presentation.components.UpIcon
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.updates.calendar.Calendar
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.Surface
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale


@Composable
fun UpdateCalendarScreen() {

    Scaffold(
        topBar = {
            UpdateCalendarToolbar()
        }
    ) { paddingValues ->
        Surface(onClick = {}) {
            UpdateCalendarContent(paddingValues)
        }
    }
}

const val HELP_URL = "https://mihon.app/docs/faq/upcoming"

@Composable
internal fun UpdateCalendarToolbar(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.currentOrThrow
    Column {
        TopAppBar(
            navigationIcon = {
                val canPop = remember { navigator.canPop }
                if (canPop) {
                    IconButton(onClick = navigator::pop) {
                        UpIcon()
                    }
                }
            },
            title = { Text(text = "Upcoming") },
            actions = {
                val uriHandler = LocalUriHandler.current
                IconButton(onClick = { uriHandler.openUri(HELP_URL) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Upcoming Guide"
                    )
                }
            },
        )
    }
}
@Composable
internal fun UpdateCalendarContent(contentPadding: PaddingValues) {
    Column(modifier = Modifier.padding(contentPadding)) {
        Calendar()

    }
}

@Composable
fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
private fun Day(day: CalendarDay, isSelected: Boolean, onClick: (CalendarDay) -> Unit) {
    Box(
        modifier = Modifier.run {
            aspectRatio(1f) // This is important for square-sizing!
                .testTag("MonthDay")
                .padding(6.dp)
                .clip(CircleShape)
                .background(color = Color.Transparent)
                // Disable clicks on inDates/outDates
                .clickable(
                    enabled = day.position == DayPosition.MonthDate,
                    onClick = { onClick(day) },
                )
        },
        contentAlignment = Alignment.Center,
    ) {
        val textColor = when (day.position) {
            // Color.Unspecified will use the default text color from the current theme
            DayPosition.MonthDate -> if (isSelected) Color.White else Color.Unspecified
            DayPosition.InDate, DayPosition.OutDate -> colorResource(R.color.accent_blue)
        }
        Text(
            text = day.date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 14.sp,
        )
    }
}
