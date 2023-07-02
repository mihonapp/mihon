package eu.kanade.presentation.libraryUpdateError

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    onMultiMigrateClicked: (() -> Unit),
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onErrorSelected: (LibraryUpdateErrorItem, Boolean, Boolean, Boolean) -> Unit,
    navigateUp: () -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    Scaffold(
        topBar = { scrollBehavior ->
            LibraryUpdateErrorsAppBar(
                title = stringResource(
                    MR.strings.label_library_update_errors,
                    state.items.size,
                ),
                actionModeCounter = state.selected.size,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = onInvertSelection,
                onCancelActionMode = { onSelectAll(false) },
                scrollBehavior = scrollBehavior,
                navigateUp = navigateUp,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.selected.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                val scope = rememberCoroutineScope()
                Surface(
                    modifier = modifier,
                    shape = MaterialTheme.shapes.large.copy(
                        bottomEnd = ZeroCornerSize,
                        bottomStart = ZeroCornerSize,
                    ),
                    tonalElevation = 3.dp,
                ) {
                    val haptic = LocalHapticFeedback.current
                    val confirm = remember { mutableStateListOf(false) }
                    var resetJob: Job? = remember { null }
                    val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        (0 until 1).forEach { i -> confirm[i] = i == toConfirmIndex }
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
                            title = stringResource(MR.strings.migrate),
                            icon = Icons.Outlined.FindReplace,
                            toConfirm = confirm[0],
                            onLongClick = { onLongClickItem(0) },
                            onClick = onMultiMigrateClicked,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            state.items.isEmpty() -> EmptyScreen(
                message = stringResource(MR.strings.info_empty_library_update_errors),
                modifier = Modifier.padding(paddingValues),
            )

            else -> {
                FastScrollLazyColumn(
                    contentPadding = paddingValues,
                ) {
                    libraryUpdateErrorUiItems(
                        uiModels = state.getUiModel(),
                        selectionMode = state.selectionMode,
                        onErrorSelected = onErrorSelected,
                        onClick = onClick,
                        onClickCover = onClickCover,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    onLongClick: () -> Unit,
    onClick: (() -> Unit),
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(if (toConfirm) 2f else 1f)
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
            )
        }
        content?.invoke()
    }
}

@Composable
private fun LibraryUpdateErrorsAppBar(
    modifier: Modifier = Modifier,
    title: String,
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: () -> Unit,
) {
    AppBar(
        modifier = modifier,
        title = title,
        scrollBehavior = scrollBehavior,
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(MR.strings.action_select_all),
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Outlined.FlipToBack,
                    contentDescription = stringResource(MR.strings.action_select_inverse),
                )
            }
        },
        navigateUp = navigateUp,
    )
}
