package eu.kanade.presentation.updates

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
import androidx.compose.runtime.remember
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
import eu.kanade.presentation.updates.components.calendar.Calendar
import eu.kanade.tachiyomi.ui.updates.UpdateUpcomingScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate

@Composable
fun UpdateUpcomingScreen(
    state: UpdateUpcomingScreenModel.State,
    modifier: Modifier = Modifier,
    isTabletUi: Boolean = false,
    onClickUpcoming: (manga: Manga) -> Unit = {},
) {
    if (!isTabletUi) {
        UpdateUpcomingScreenSmallImpl(
            state = state,
            modifier = modifier,
            onClickUpcoming = onClickUpcoming,
        )
    } else {
        UpdateUpcomingScreenLargeImpl(
            state = state,
            isTabletUi = isTabletUi,
            modifier = modifier,
            onClickUpcoming = onClickUpcoming,
        )
    }
}

@Composable
internal fun UpdateUpcomingScreenSmallImpl(
    state: UpdateUpcomingScreenModel.State,
    modifier: Modifier = Modifier,
    onClickUpcoming: (manga: Manga) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            UpdateUpcomingToolbar()
        },
    ) { paddingValues ->

        UpdateUpcomingSmallContent(
            upcoming = state.items,
            events = state.events,
            contentPadding = paddingValues,
            onClickUpcoming = onClickUpcoming,
        )
    }
}

const val HelpUrl = "https://mihon.app/docs/faq/upcoming"

@Composable
internal fun UpdateUpcomingToolbar(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    Column(
        modifier = modifier,
    ) {
        TopAppBar(
            navigationIcon = {
                val canPop = remember { navigator.canPop }
                if (canPop) {
                    IconButton(onClick = navigator::pop) {
                        UpIcon()
                    }
                }
            },
            title = { AppBarTitle(stringResource(MR.strings.label_upcoming)) },
            actions = {
                val uriHandler = LocalUriHandler.current
                IconButton(onClick = { uriHandler.openUri(HelpUrl) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(MR.strings.upcoming_guide),
                    )
                }
            },
        )
    }
}

@Composable
internal fun UpdateUpcomingSmallContent(
    upcoming: ImmutableList<UpcomingUIModel>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    onClickUpcoming: (manga: Manga) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val dateToHeaderMap =
        upcoming.withIndex()
            .filter { it.value is UpcomingUIModel.Header }
            .associate { Pair((it.value as UpcomingUIModel.Header).date, it.index + 1) } // Offset 1 for Calendar

    val configuration = LocalConfiguration.current

    FastScrollLazyColumn(
        contentPadding = contentPadding,
        state = listState,
        modifier = modifier,
    ) {
        item(
            key = "upcoming-calendar",
        ) {
            Calendar(
                events = events,
                screenWidth = configuration.screenWidthDp.dp,
            ) { date ->
                dateToHeaderMap[date]?.let {
                    coroutineScope.launch {
                        listState.animateScrollToItem(it)
                    }
                }
            }
        }
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
                        upcoming = item.item,
                        onClick = onClickUpcoming,
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
internal fun UpdateUpcomingScreenLargeImpl(
    state: UpdateUpcomingScreenModel.State,
    isTabletUi: Boolean,
    modifier: Modifier = Modifier,
    onClickUpcoming: (manga: Manga) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = { UpdateUpcomingToolbar() },
    ) { contentPadding ->
        TwoPanelBox(
            modifier = Modifier.padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
            startContent = {
                UpdateUpcomingLargeCalendar(
                    upcoming = state.items,
                    listState = listState,
                    isTabletUi = isTabletUi,
                    events = state.events,
                    modifier = Modifier.padding(contentPadding),
                )
            },
            endContent = {
                UpdateUpcomingLargeContent(
                    upcoming = state.items,
                    listState = listState,
                    contentPadding = contentPadding,
                    onClickUpcoming = onClickUpcoming,
                )
            },
        )
    }
}

@Composable
internal fun UpdateUpcomingLargeCalendar(
    upcoming: ImmutableList<UpcomingUIModel>,
    isTabletUi: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val dateToHeaderMap =
        upcoming.withIndex()
            .filter { it.value is UpcomingUIModel.Header }
            .associate { Pair((it.value as UpcomingUIModel.Header).date, it.index) }

    Calendar(
        modifier = modifier,
        events = events,
        isTabletUi = isTabletUi,
        screenWidth = configuration.screenWidthDp.dp,
    ) { date ->
        dateToHeaderMap[date]?.let {
            coroutineScope.launch {
                listState.animateScrollToItem(it)
            }
        }
    }
}

@Composable
internal fun UpdateUpcomingLargeContent(
    upcoming: ImmutableList<UpcomingUIModel>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClickUpcoming: (manga: Manga) -> Unit,
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
                        upcoming = item.item,
                        onClick = onClickUpcoming,
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
    data class Item(val item: Manga) : UpcomingUIModel
}
