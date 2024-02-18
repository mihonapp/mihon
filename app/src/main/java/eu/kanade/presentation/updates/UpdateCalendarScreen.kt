package eu.kanade.presentation.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.ui.updates.calendar.Calendar
import eu.kanade.tachiyomi.ui.updates.calendar.UpdateCalendarScreenModel
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale


@Composable
fun UpdateCalendarScreen(
    state: UpdateCalendarScreenModel.State,
) {

    Scaffold(
        topBar = {
            UpdateCalendarToolbar()
        },
    ) { paddingValues ->
        state.items.let {
            UpdateCalendarContent(it, paddingValues)
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
                        contentDescription = "Upcoming Guide",
                    )
                }
            },
        )
    }
}

@Composable
internal fun UpdateCalendarContent(
    upcoming: List<UpcomingUIModel>,
    contentPadding: PaddingValues,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        item { Calendar() }
        items(
            items = upcoming,
            key = null,
            contentType = {
                when (it) {
                    is UpcomingUIModel.Header -> "header"
                    is UpcomingUIModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is UpcomingUIModel.Item -> {
                    UpcomingItem(upcoming = item.item)
                }

                is UpcomingUIModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemPlacement(),
                        text = relativeDateText(item.date),
                    )
                }
            }

        }
    }
}

//Calendar()

@Composable
fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

sealed interface UpcomingUIModel {
    data class Header(val date: LocalDate) : UpcomingUIModel

    data class Item(val item: Manga) : UpcomingUIModel
}
