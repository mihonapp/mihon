package eu.kanade.presentation.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.history.components.HistoryHeader
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.history.components.HistoryItemShimmer
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.shimmerGradient
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.recent.history.HistoryPresenter
import eu.kanade.tachiyomi.ui.recent.history.HistoryState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: HistoryPresenter,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations, Boolean) -> Unit,
) {
    val state by presenter.state.collectAsState()
    when (state) {
        is HistoryState.Loading -> LoadingScreen()
        is HistoryState.Error -> Text(text = (state as HistoryState.Error).error.message!!)
        is HistoryState.Success ->
            HistoryContent(
                nestedScroll = nestedScrollInterop,
                history = (state as HistoryState.Success).uiModels.collectAsLazyPagingItems(),
                onClickCover = onClickCover,
                onClickResume = onClickResume,
                onClickDelete = onClickDelete,
            )
    }
}

@Composable
fun HistoryContent(
    history: LazyPagingItems<HistoryUiModel>,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations, Boolean) -> Unit,
    preferences: PreferencesHelper = Injekt.get(),
    nestedScroll: NestedScrollConnection,
) {
    if (history.loadState.refresh is LoadState.NotLoading && history.itemCount == 0) {
        EmptyScreen(textResource = R.string.information_no_recent_manga)
        return
    }

    val relativeTime: Int = remember { preferences.relativeTime().get() }
    val dateFormat: DateFormat = remember { preferences.dateFormat() }

    var removeState by remember { mutableStateOf<HistoryWithRelations?>(null) }

    val scrollState = rememberLazyListState()

    ScrollbarLazyColumn(
        modifier = Modifier
            .nestedScroll(nestedScroll),
        contentPadding = WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
        state = scrollState,
    ) {
        items(history) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    HistoryHeader(
                        modifier = Modifier
                            .animateItemPlacement(),
                        date = item.date,
                        relativeTime = relativeTime,
                        dateFormat = dateFormat,
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    HistoryItem(
                        modifier = Modifier.animateItemPlacement(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { removeState = value },
                    )
                }
                null -> {
                    val transition = rememberInfiniteTransition()
                    val translateAnimation = transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 1000,
                                easing = LinearEasing,
                            ),
                        ),
                    )

                    val brush = remember {
                        linearGradient(
                            colors = shimmerGradient,
                            start = Offset(0f, 0f),
                            end = Offset(
                                x = translateAnimation.value,
                                y = 00f,
                            ),
                        )
                    }
                    HistoryItemShimmer(brush = brush)
                }
            }
        }
    }

    if (removeState != null) {
        RemoveHistoryDialog(
            onPositive = { all ->
                onClickDelete(removeState!!, all)
                removeState = null
            },
            onNegative = { removeState = null },
        )
    }
}

@Composable
fun RemoveHistoryDialog(
    onPositive: (Boolean) -> Unit,
    onNegative: () -> Unit,
) {
    var removeEverything by remember { mutableStateOf(false) }

    AlertDialog(
        title = {
            Text(text = stringResource(R.string.action_remove))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.dialog_with_checkbox_remove_description))
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .toggleable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            value = removeEverything,
                            onValueChange = { removeEverything = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = removeEverything,
                        onCheckedChange = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.dialog_with_checkbox_reset),
                    )
                }
            }
        },
        onDismissRequest = onNegative,
        confirmButton = {
            TextButton(onClick = { onPositive(removeEverything) }) {
                Text(text = stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onNegative) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

sealed class HistoryUiModel {
    data class Header(val date: Date) : HistoryUiModel()
    data class Item(val item: HistoryWithRelations) : HistoryUiModel()
}
