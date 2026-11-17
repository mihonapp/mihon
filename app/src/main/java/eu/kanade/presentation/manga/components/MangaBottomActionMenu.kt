package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.DonePrevious
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.BookmarkAdd
import mihon.icons.materialsymbols.rounded.BookmarkRemove
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.RemoveDone
import mihon.icons.materialsymbols.rounded.SwapCalls
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaBottomActionMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onMarkAsReadClicked: (() -> Unit)? = null,
    onMarkAsUnreadClicked: (() -> Unit)? = null,
    onMarkPreviousAsReadClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
) {
    val hasBookmark = onBookmarkClicked != null
    val hasRemoveBookmark = onRemoveBookmarkClicked != null
    val hasMarkAsRead = onMarkAsReadClicked != null
    val hasMarkAsUnread = onMarkAsUnreadClicked != null
    val hasMarkPreviousAsRead = onMarkPreviousAsReadClicked != null
    val hasDownload = onDownloadClicked != null
    val hasDelete = onDeleteClicked != null

    val hasAnyAction = hasBookmark || hasRemoveBookmark || hasMarkAsRead ||
        hasMarkAsUnread || hasMarkPreviousAsRead || hasDownload || hasDelete

    val lastActions = remember { ActionConfigHolder() }
    SideEffect {
        if (visible && hasAnyAction) {
            lastActions.set(
                bookmark = hasBookmark,
                removeBookmark = hasRemoveBookmark,
                markAsRead = hasMarkAsRead,
                markAsUnread = hasMarkAsUnread,
                markPreviousAsRead = hasMarkPreviousAsRead,
                download = hasDownload,
                delete = hasDelete,
            )
        }
    }

    val showBookmark = if (visible) hasBookmark else lastActions.bookmark
    val showRemoveBookmark = if (visible) hasRemoveBookmark else lastActions.removeBookmark
    val showMarkAsRead = if (visible) hasMarkAsRead else lastActions.markAsRead
    val showMarkAsUnread = if (visible) hasMarkAsUnread else lastActions.markAsUnread
    val showMarkPreviousAsRead = if (visible) hasMarkPreviousAsRead else lastActions.markPreviousAsRead
    val showDownload = if (visible) hasDownload else lastActions.download
    val showDelete = if (visible) hasDelete else lastActions.delete

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false, false, false, false, false) }
            var resetJob by remember { mutableStateOf<Job?>(null) }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                if (visible) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    confirm.indices.forEach { i -> confirm[i] = i == toConfirmIndex }
                    resetJob?.cancel()
                    resetJob = scope.launch {
                        delay(1.seconds)
                        if (isActive) confirm[toConfirmIndex] = false
                    }
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
                if (showBookmark) {
                    Button(
                        title = stringResource(MR.strings.action_bookmark),
                        icon = MaterialSymbols.Rounded.BookmarkAdd,
                        toConfirm = confirm[0],
                        onLongClick = { onLongClickItem(0) },
                        onClick = if (visible && onBookmarkClicked != null) onBookmarkClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showRemoveBookmark) {
                    Button(
                        title = stringResource(MR.strings.action_remove_bookmark),
                        icon = MaterialSymbols.Rounded.BookmarkRemove,
                        toConfirm = confirm[1],
                        onLongClick = { onLongClickItem(1) },
                        onClick = if (visible && onRemoveBookmarkClicked != null) onRemoveBookmarkClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showMarkAsRead) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_read),
                        icon = MaterialSymbols.Rounded.DoneAll,
                        toConfirm = confirm[2],
                        onLongClick = { onLongClickItem(2) },
                        onClick = if (visible && onMarkAsReadClicked != null) onMarkAsReadClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showMarkAsUnread) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_unread),
                        icon = MaterialSymbols.Rounded.RemoveDone,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = if (visible && onMarkAsUnreadClicked != null) onMarkAsUnreadClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showMarkPreviousAsRead) {
                    Button(
                        title = stringResource(MR.strings.action_mark_previous_as_read),
                        icon = CustomMaterialSymbols.Rounded.DonePrevious,
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = if (visible && onMarkPreviousAsReadClicked != null) onMarkPreviousAsReadClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showDownload) {
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = MaterialSymbols.Rounded.Download,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = if (visible && onDownloadClicked != null) onDownloadClicked else EmptyLambda,
                        enabled = visible,
                    )
                }
                if (showDelete) {
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = MaterialSymbols.Rounded.Delete,
                        toConfirm = confirm[6],
                        onLongClick = { onLongClickItem(6) },
                        onClick = if (visible && onDeleteClicked != null) onDeleteClicked else EmptyLambda,
                        enabled = visible,
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
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (toConfirm) 2f else 1f,
        label = "weight",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                enabled = enabled,
                interactionSource = null,
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
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
        }
        content?.invoke()
    }
}

@Composable
fun LibraryBottomActionMenu(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onDeleteClicked: () -> Unit,
    onMigrateClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false, false, false, false) }
            var resetJob by remember { mutableStateOf<Job?>(null) }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                confirm.indices.forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            val itemOverflow = onDownloadClicked != null
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(MR.strings.action_move_category),
                    icon = MaterialSymbols.AutoMirroredRounded.Label,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = onChangeCategoryClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_read),
                    icon = MaterialSymbols.Rounded.DoneAll,
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                    onClick = onMarkAsReadClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_unread),
                    icon = MaterialSymbols.Rounded.RemoveDone,
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                    onClick = onMarkAsUnreadClicked,
                )
                if (onDownloadClicked != null) {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = MaterialSymbols.Rounded.Download,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = { downloadExpanded = !downloadExpanded },
                    ) {
                        DownloadDropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = { downloadExpanded = false },
                            onDownloadClicked = onDownloadClicked,
                            offset = BottomBarMenuDpOffset,
                        )
                    }
                }
                if (!itemOverflow) {
                    Button(
                        title = stringResource(MR.strings.migrate),
                        icon = MaterialSymbols.Rounded.SwapCalls,
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = onMigrateClicked,
                    )
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = MaterialSymbols.Rounded.Delete,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = onDeleteClicked,
                    )
                } else {
                    var overflowMenuOpen by remember { mutableStateOf(false) }
                    Button(
                        title = stringResource(MR.strings.label_more),
                        icon = MaterialSymbols.Rounded.MoreVert,
                        toConfirm = false,
                        onLongClick = {},
                        onClick = { overflowMenuOpen = true },
                    ) {
                        DropdownMenu(
                            expanded = overflowMenuOpen,
                            onDismissRequest = { overflowMenuOpen = false },
                            offset = BottomBarMenuDpOffset,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.migrate)) },
                                onClick = onMigrateClicked,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_delete)) },
                                onClick = onDeleteClicked,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BottomBarMenuDpOffset = DpOffset(0.dp, 0.dp)

private class ActionConfigHolder(
    var bookmark: Boolean = false,
    var removeBookmark: Boolean = false,
    var markAsRead: Boolean = false,
    var markAsUnread: Boolean = false,
    var markPreviousAsRead: Boolean = false,
    var download: Boolean = false,
    var delete: Boolean = false,
) {
    fun set(
        bookmark: Boolean,
        removeBookmark: Boolean,
        markAsRead: Boolean,
        markAsUnread: Boolean,
        markPreviousAsRead: Boolean,
        download: Boolean,
        delete: Boolean,
    ) {
        this.bookmark = bookmark
        this.removeBookmark = removeBookmark
        this.markAsRead = markAsRead
        this.markAsUnread = markAsUnread
        this.markPreviousAsRead = markPreviousAsRead
        this.download = download
        this.delete = delete
    }
}

private val EmptyLambda: () -> Unit = {}
