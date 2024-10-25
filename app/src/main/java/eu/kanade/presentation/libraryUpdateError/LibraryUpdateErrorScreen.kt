package eu.kanade.presentation.libraryUpdateError

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.libraryUpdateError.components.libraryUpdateErrorUiItems
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorItem
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryUpdateErrorScreen(
    state: LibraryUpdateErrorScreenState,
    modifier: Modifier = Modifier,
    onClick: (LibraryUpdateErrorItem) -> Unit,
    onClickCover: (LibraryUpdateErrorItem) -> Unit,
    navigateUp: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val enableScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val enableScrollToBottom by remember {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    val headerIndexes = remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(state) {
        headerIndexes.value = state.getHeaderIndexes()
    }
    val enableScrollToPrevious by remember {
        derivedStateOf {
            headerIndexes.value.any { it < listState.firstVisibleItemIndex }
        }
    }
    val enableScrollToNext by remember {
        derivedStateOf {
            headerIndexes.value.any { it > listState.firstVisibleItemIndex }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            LibraryUpdateErrorsAppBar(
                title = stringResource(
                    MR.strings.label_library_update_errors,
                    state.items.size,
                ),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            LibraryUpdateErrorsBottomBar(
                modifier = modifier,
                enableScrollToTop = enableScrollToTop,
                enableScrollToBottom = enableScrollToBottom,
                scrollToTop = {
                    scope.launch {
                        listState.scrollToItem(0)
                    }
                },
                scrollToBottom = {
                    scope.launch {
                        listState.scrollToItem(state.items.size - 1)
                    }
                },
                enableScrollToPrevious = enableScrollToTop && enableScrollToPrevious,
                enableScrollToNext = enableScrollToBottom && enableScrollToNext,
                scrollToPrevious = {
                    scope.launch {
                        listState.scrollToItem(
                            state.getHeaderIndexes()
                                .filter { it < listState.firstVisibleItemIndex }
                                .maxOrNull() ?: 0,
                        )
                    }
                },
                scrollToNext = {
                    scope.launch {
                        listState.scrollToItem(
                            state.getHeaderIndexes()
                                .filter { it > listState.firstVisibleItemIndex }
                                .minOrNull() ?: 0,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                message = stringResource(MR.strings.info_empty_library_update_errors),
                modifier = Modifier.padding(contentPadding),
            )

            else -> {
                FastScrollLazyColumn(
                    // Using modifier instead of contentPadding so we can use stickyHeader
                    modifier = Modifier.padding(contentPadding),
                    state = listState,
                ) {
                    libraryUpdateErrorUiItems(
                        uiModels = state.getUiModel(),
                        onClick = onClick,
                        onClickCover = onClickCover,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorsBottomBar(
    modifier: Modifier = Modifier,
    enableScrollToTop: Boolean,
    enableScrollToBottom: Boolean,
    scrollToTop: () -> Unit,
    scrollToBottom: () -> Unit,
    enableScrollToPrevious: Boolean,
    enableScrollToNext: Boolean,
    scrollToPrevious: () -> Unit,
    scrollToNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large.copy(
            bottomEnd = ZeroCornerSize,
            bottomStart = ZeroCornerSize,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        val haptic = LocalHapticFeedback.current
        val confirm = remember { mutableStateListOf(false, false, false, false) }
        var resetJob: Job? = remember { null }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            (0 until 4).forEach { i -> confirm[i] = i == toConfirmIndex }
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(1.seconds)
                if (isActive) confirm[toConfirmIndex] = false
            }
        }
        Row(
            modifier = Modifier
                .padding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Button(
                title = stringResource(MR.strings.action_scroll_to_top),
                icon = Icons.Outlined.VerticalAlignTop,
                toConfirm = confirm[0],
                onLongClick = { onLongClickItem(0) },
                onClick = if (enableScrollToTop) {
                    scrollToTop
                } else {
                    {}
                },
                enabled = enableScrollToTop,
            )
            Button(
                title = stringResource(MR.strings.action_scroll_to_previous),
                icon = Icons.Outlined.ArrowUpward,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = if (enableScrollToPrevious) {
                    scrollToPrevious
                } else {
                    {}
                },
                enabled = enableScrollToPrevious,
            )
            Button(
                title = stringResource(MR.strings.action_scroll_to_next),
                icon = Icons.Outlined.ArrowDownward,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
                onClick = if (enableScrollToNext) {
                    scrollToNext
                } else {
                    {}
                },
                enabled = enableScrollToNext,
            )
            Button(
                title = stringResource(MR.strings.action_scroll_to_bottom),
                icon = Icons.Outlined.VerticalAlignBottom,
                toConfirm = confirm[3],
                onLongClick = { onLongClickItem(3) },
                onClick = if (enableScrollToBottom) {
                    scrollToBottom
                } else {
                    {}
                },
                enabled = enableScrollToBottom,
            )
        }
    }
}

@Composable
private fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    enabled: Boolean,
    onLongClick: () -> Unit,
    onClick: (() -> Unit),
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(if (toConfirm) 2f else 1f)
    val animatedColor by animateColorAsState(
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f,
            )
        },
    )
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = animatedColor,
        )
        AnimatedVisibility(
            visible = toConfirm,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Visible,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor,
            )
        }
        content?.invoke()
    }
}

@Composable
private fun LibraryUpdateErrorsAppBar(
    title: String,
    navigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = title,
        navigateUp = navigateUp,
        scrollBehavior = scrollBehavior,
    )
}
