package eu.kanade.presentation.failedupdate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.failedupdate.FailedUpdatesScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun FailedUpdatesScreen(
    state: FailedUpdatesScreenModel.State,
    onClickCover: (MangaUpdateErrorWithManga) -> Unit,
    onClickItem: (MangaUpdateErrorWithManga) -> Unit,
    onClickMigrate: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearError: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onToggleSelection: (MangaUpdateErrorWithManga, Boolean) -> Unit,
    navigateUp: () -> Unit,
) {
    BackHandler(enabled = state.selectionMode) {
        onSelectAll(false)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            FailedUpdatesAppBar(
                onNavigateUp = navigateUp,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = onInvertSelection,
                onClearAll = onClearAll,
                onDeleteSelected = onDeleteSelected,
                onStartSelection = { onSelectAll(true) },
                actionModeCounter = state.selectedIds.size,
                onCancelActionMode = { onSelectAll(false) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (state.selectionMode) {
                FailedUpdatesBottomBar(
                    onMigrateSelected = onClickMigrate,
                )
            }
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_empty_failed_updates,
                modifier = Modifier.padding(contentPadding),
                isHappy = true,
            )
            else -> {
                FailedUpdatesList(
                    items = state.items,
                    selectedIds = state.selectedIds,
                    selectionMode = state.selectionMode,
                    onClickCover = onClickCover,
                    onClickItem = onClickItem,
                    onClearError = onClearError,
                    onToggleSelection = onToggleSelection,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun FailedUpdatesAppBar(
    onNavigateUp: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onStartSelection: () -> Unit,
    actionModeCounter: Int,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = stringResource(MR.strings.label_failed_updates),
        navigateUp = onNavigateUp,
        actions = {
            if (actionModeCounter == 0) {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_select_all),
                            icon = Icons.Outlined.SelectAll,
                            onClick = onStartSelection,
                        ),
                        AppBar.Action(
                            title = stringResource(MR.strings.action_remove_everything),
                            icon = Icons.Outlined.DeleteSweep,
                            onClick = onClearAll,
                        ),
                    ),
                )
            }
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onInvertSelection,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        onClick = onDeleteSelected,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun FailedUpdatesBottomBar(
    onMigrateSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                WindowInsets.navigationBars
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues(),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(onClick = onMigrateSelected) {
            Text(text = stringResource(MR.strings.action_migrate_selected))
        }
    }
}

@Composable
private fun FailedUpdatesList(
    items: List<MangaUpdateErrorWithManga>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    onClickCover: (MangaUpdateErrorWithManga) -> Unit,
    onClickItem: (MangaUpdateErrorWithManga) -> Unit,
    onClearError: (Long) -> Unit,
    onToggleSelection: (MangaUpdateErrorWithManga, Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = items,
            key = { it.error.mangaId },
        ) { item ->
            FailedUpdateItem(
                item = item,
                selectionMode = selectionMode,
                selected = item.manga.id in selectedIds,
                onClickCover = { onClickCover(item) },
                onClickItem = { onClickItem(item) },
                onClearError = { onClearError(item.manga.id) },
                onToggleSelection = { selected -> onToggleSelection(item, selected) },
            )
        }
    }
}

private val FailedUpdateItemHeight = 96.dp

@Composable
private fun FailedUpdateItem(
    item: MangaUpdateErrorWithManga,
    selectionMode: Boolean,
    selected: Boolean,
    onClickCover: () -> Unit,
    onClickItem: () -> Unit,
    onClearError: () -> Unit,
    onToggleSelection: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection(!selected)
                    } else {
                        onClickItem()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelection(true)
                },
            )
            .height(FailedUpdateItemHeight)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)
            .alpha(if (selected) 0.38f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggleSelection,
            )
        }

        MangaCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = MangaCoverData(
                mangaId = item.manga.id,
                sourceId = item.manga.source,
                isMangaFavorite = item.manga.favorite,
                url = item.manga.thumbnailUrl,
                lastModified = item.manga.coverLastModified,
            ),
            onClick = if (!selectionMode) onClickCover else null,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            Text(
                text = item.manga.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.error.errorMessage ?: stringResource(MR.strings.unknown_error),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeDateText(item.error.timestamp),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!selectionMode) {
            IconButton(onClick = onClearError) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun FailedUpdatesClearAllDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_remove_everything)) },
        text = { Text(text = stringResource(MR.strings.confirm_clear_all_failed_updates)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
fun FailedUpdatesDeleteSelectedDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_delete)) },
        text = { Text(text = stringResource(MR.strings.confirm_delete_selected_failed_updates)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
