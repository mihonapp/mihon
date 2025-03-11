package eu.kanade.presentation.updates.failed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.updates.components.FailedUpdatesBottomActionMenu
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal

class FailedUpdatesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        val screenModel = rememberScreenModel { FailedUpdatesScreenModel(context) }
        val state by screenModel.state.collectAsState()
        val failedUpdatesListState = rememberLazyListState()

        val previousGroupByMode = remember { mutableStateOf(state.groupByMode) }

        Scaffold(
            topBar = { scrollBehavior ->
                FailedUpdatesAppBar(
                    groupByMode = state.groupByMode,
                    items = state.items,
                    selected = state.selected,
                    onSelectAll = { screenModel.toggleAllSelection(true) },
                    onDismissAll = { screenModel.dismissManga(state.items) },
                    isAllExpanded = state.expanded.values.all { it },
                    onExpandAll = { screenModel.expandAll() },
                    onContractAll = { screenModel.contractAll() },
                    onInvertSelection = { screenModel.invertSelection() },
                    onCancelActionMode = { screenModel.toggleAllSelection(false) },
                    scrollBehavior = scrollBehavior,
                    onClickGroup = screenModel::runGroupBy,
                    onClickSort = screenModel::runSortAction,
                    sortState = state.sortMode,
                    descendingOrder = state.descendingOrder,
                    navigateUp = navigator::pop,
                    errorCount = state.items.size,
                )
            },
            bottomBar = {
                FailedUpdatesBottomActionMenu(
                    visible = state.selectionMode,
                    onDeleteClicked = { screenModel.openDeleteMangaDialog(state.selected) },
                    onDismissClicked = { screenModel.dismissManga(state.selected) },
                    onInfoClicked = { errorMessage ->
                        screenModel.openErrorMessageDialog(errorMessage)
                    },
                    selected = state.selected,
                    groupingMode = state.groupByMode,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = state.items.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(text = stringResource(MR.strings.label_help)) },
                        icon = {
                            Icon(imageVector = Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                        },
                        onClick = { uriHandler.openUri(Constants.URL_HELP) },
                        expanded = failedUpdatesListState.shouldExpandFAB(),
                    )
                }
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))

                state.items.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.information_no_update_errors,
                    modifier = Modifier.padding(contentPadding),
                    happyFace = true,
                )

                else -> {
                    when (state.groupByMode) {
                        GroupByMode.NONE -> {
                            FastScrollLazyColumn(
                                contentPadding = contentPadding,
                                state = failedUpdatesListState,
                            ) {
                                failedUpdatesUiItems(
                                    items = state.items,
                                    selectionMode = state.selectionMode,
                                    onClick = { item ->
                                        navigator.push(
                                            MangaScreen(item.libraryManga.manga.id),
                                        )
                                    },
                                    onSelected = screenModel::toggleSelection,
                                    groupingMode = state.groupByMode,
                                )
                            }
                        }

                        GroupByMode.BY_SOURCE -> {
                            val categoryMap = screenModel.categoryMap(
                                state.items,
                                GroupByMode.BY_SOURCE,
                                state.sortMode,
                                state.descendingOrder,
                            )

                            LaunchedEffect(state.groupByMode) {
                                val currentGroupByMode = state.groupByMode

                                if (previousGroupByMode.value != currentGroupByMode) {
                                    screenModel.initializeExpandedMap(categoryMap)
                                }

                                previousGroupByMode.value = currentGroupByMode
                            }

                            CategoryList(
                                contentPadding = contentPadding,
                                selectionMode = state.selectionMode,
                                onMangaClick = { item ->
                                    navigator.push(
                                        MangaScreen(item.libraryManga.manga.id),
                                    )
                                },
                                onGroupSelected = screenModel::groupSelection,
                                onSelected = { item, selected, userSelected, fromLongPress ->
                                    screenModel.toggleSelection(item, selected, userSelected, fromLongPress, true)
                                },
                                categoryMap = categoryMap,
                                onExpandedMapChange = screenModel::updateExpandedMap,
                                expanded = state.expanded,
                                sourcesCount = state.sourcesCount,
                                onClickIcon = { errorMessage ->
                                    screenModel.openErrorMessageDialog(errorMessage)
                                },
                                onLongClickIcon = { errorMessage ->
                                    context.copyToClipboard(errorMessage, errorMessage)
                                },
                                lazyListState = failedUpdatesListState,
                            )
                        }
                    }
                }
            }
        }
        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        screenModel.toggleAllSelection(false)
                    },
                )
            }
            is Dialog.ShowErrorMessage -> {
                ErrorMessageDialog(
                    onDismissRequest = onDismissRequest,
                    onCopyClick = {
                        context.copyToClipboard(dialog.errorMessage, dialog.errorMessage)
                        screenModel.toggleAllSelection(false)
                    },
                    errorMessage = dialog.errorMessage,
                )
            }
            null -> {}
        }
    }
}

@Composable
private fun FailedUpdatesAppBar(
    groupByMode: GroupByMode,
    items: List<FailedUpdatesManga>,
    modifier: Modifier = Modifier,
    selected: List<FailedUpdatesManga>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    onClickSort: (SortingMode) -> Unit,
    onClickGroup: (GroupByMode) -> Unit,
    onDismissAll: () -> Unit,
    isAllExpanded: Boolean,
    onExpandAll: () -> Unit,
    onContractAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    sortState: SortingMode,
    descendingOrder: Boolean? = null,
    navigateUp: (() -> Unit)?,
    errorCount: Int,
) {
    if (selected.isNotEmpty()) {
        FailedUpdatesActionAppBar(
            modifier = modifier,
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onCancelActionMode = onCancelActionMode,
            scrollBehavior = scrollBehavior,
            navigateUp = navigateUp,
            actionModeCounter = selected.size,
        )
        BackHandler(
            onBack = onCancelActionMode,
        )
    } else {
        AppBar(
            navigateUp = navigateUp,
            titleContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.label_failed_updates),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, false),
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (errorCount > 0) {
                        val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                        Pill(
                            text = "$errorCount",
                            modifier = Modifier.padding(start = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground
                                .copy(alpha = pillAlpha),
                            fontSize = 14.sp,
                        )
                    }
                }
            },
            actions = {
                if (items.isNotEmpty()) {
                    val filterTint = LocalContentColor.current
                    var sortExpanded by remember { mutableStateOf(false) }
                    val onSortDismissRequest = { sortExpanded = false }
                    var mainExpanded by remember { mutableStateOf(false) }
                    val onDismissRequest = { mainExpanded = false }
                    SortDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = onSortDismissRequest,
                        onSortClicked = onClickSort,
                        sortState = sortState,
                        descendingOrder = descendingOrder,
                    )
                    DropdownMenu(expanded = mainExpanded, onDismissRequest = onDismissRequest) {
                        NestedMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_groupBy)) },
                            children = { closeMenu ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_group_by_source)) },
                                    onClick = {
                                        onClickGroup(GroupByMode.BY_SOURCE)
                                        closeMenu()
                                        onDismissRequest()
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(MR.strings.action_sortBy)) },
                            onClick = {
                                onDismissRequest()
                                sortExpanded = !sortExpanded
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowRight,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    val actions = mutableListOf<AppBar.AppBarAction>()
                    actions += AppBar.Action(
                        title = stringResource(MR.strings.action_sort),
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        iconTint = filterTint,
                        onClick = { mainExpanded = !mainExpanded },
                    )
                    actions += AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_dismiss_all),
                        onClick = onDismissAll,
                    )
                    if (groupByMode != GroupByMode.NONE) {
                        actions += if (isAllExpanded) {
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_contract_all),
                                onClick = {
                                    onContractAll()
                                },
                            )
                        } else {
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_expand_all),
                                onClick = {
                                    onExpandAll()
                                },
                            )
                        }
                    }
                    if (groupByMode != GroupByMode.NONE) {
                        actions += AppBar.OverflowAction(
                            title = stringResource(MR.strings.action_ungroup),
                            onClick = { onClickGroup(GroupByMode.NONE) },
                        )
                    }
                    AppBarActions(actions.toImmutableList())
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun FailedUpdatesActionAppBar(
    modifier: Modifier = Modifier,
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: (() -> Unit)?,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(MR.strings.label_failed_updates),
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            AppBarActions(
                listOf(
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
                ).toImmutableList(),
            )
        },
        scrollBehavior = scrollBehavior,
        navigateUp = navigateUp,
    )
}

@Composable
fun SortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSortClicked: (SortingMode) -> Unit,
    sortState: SortingMode,
    descendingOrder: Boolean? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        SortItem(
            label = stringResource(MR.strings.action_sort_A_Z),
            sortDescending = descendingOrder.takeIf { sortState == SortingMode.BY_ALPHABET },
            onClick = { onSortClicked(SortingMode.BY_ALPHABET) },
        )
    }
}
