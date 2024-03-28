package mihon.feature.upcoming

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.isTabletUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import mihon.feature.upcoming.components.UpcomingItem
import mihon.feature.upcoming.components.calendar.Calendar
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate

@Composable
fun UpcomingScreenContent(
    state: UpcomingScreenModel.State,
    onClickUpcoming: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { UpcomingToolbar() },
        modifier = modifier,
    ) { paddingValues ->
        if (isTabletUi()) {
            UpcomingScreenLargeImpl(
                onClickUpcoming = onClickUpcoming,
                paddingValues = paddingValues,
                state = state,
            )
        } else {
            UpcomingScreenSmallImpl(
                onClickUpcoming = onClickUpcoming,
                paddingValues = paddingValues,
                state = state,
            )
        }
    }
}

@Composable
private fun UpcomingToolbar(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    Column(modifier = modifier) {
        TopAppBar(
            actions = {
                val uriHandler = LocalUriHandler.current
                IconButton(onClick = { uriHandler.openUri(Constants.URL_HELP_UPCOMING) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(MR.strings.upcoming_guide),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = navigator::pop) {
                    UpIcon()
                }
            },
            title = { AppBarTitle(stringResource(MR.strings.label_upcoming)) },
        )
    }
}

@Composable
private fun UpcomingScreenSmallImpl(
    onClickUpcoming: (manga: Manga) -> Unit,
    state: UpcomingScreenModel.State,
    paddingValues: PaddingValues,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    FastScrollLazyColumn(
        contentPadding = paddingValues,
        state = listState,
    ) {
        item(
            key = "upcoming-calendar",
        ) {
            Calendar(
                events = state.events,
                screenWidth = configuration.screenWidthDp.dp,
                onClickDay = { date ->
                    state.headerIndexes[date]?.let<Int, Unit> {
                        coroutineScope.launch {
                            listState.animateScrollToItem(it + 1)
                        }
                    }
                },
            )
        }
        items(
            items = state.items,
            key = { "upcoming-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is UpcomingUIModel.Header -> "header"
                    is UpcomingUIModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is UpcomingUIModel.Item -> {
                    UpcomingItem(
                        upcoming = item.manga,
                        onClick = { onClickUpcoming(item.manga) },
                    )
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

@Composable
private fun UpcomingScreenLargeImpl(
    state: UpcomingScreenModel.State,
    onClickUpcoming: (manga: Manga) -> Unit,
    paddingValues: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()

    TwoPanelBox(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(layoutDirection),
            end = paddingValues.calculateEndPadding(layoutDirection),
        ),
        startContent = {
            UpcomingLargeCalendar(
                events = state.events,
                headerIndexes = state.headerIndexes,
                listState = listState,
                modifier = Modifier.padding(paddingValues),
            )
        },
        endContent = {
            UpcomingLargeContent(
                upcoming = state.items,
                listState = listState,
                contentPadding = paddingValues,
                onClickUpcoming = onClickUpcoming,
            )
        },
    )
}

@Composable
private fun UpcomingLargeCalendar(
    events: ImmutableMap<LocalDate, Int>,
    headerIndexes: ImmutableMap<LocalDate, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    Calendar(
        modifier = modifier,
        events = events,
        screenWidth = configuration.screenWidthDp.dp,
        onClickDay = { date ->
            headerIndexes[date]?.let {
                coroutineScope.launch {
                    listState.animateScrollToItem(it)
                }
            }
        },
    )
}

@Composable
private fun UpcomingLargeContent(
    upcoming: ImmutableList<UpcomingUIModel>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onClickUpcoming: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
        state = listState,
        modifier = modifier,
    ) {
        items(
            items = upcoming,
            key = { "upcoming-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is UpcomingUIModel.Header -> "header"
                    is UpcomingUIModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is UpcomingUIModel.Item -> {
                    UpcomingItem(
                        upcoming = item.manga,
                        onClick = { onClickUpcoming(item.manga) },
                    )
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

sealed interface UpcomingUIModel {
    data class Header(val date: LocalDate) : UpcomingUIModel
    data class Item(val manga: Manga) : UpcomingUIModel
}
