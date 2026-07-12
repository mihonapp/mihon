package eu.kanade.tachiyomi.ui.readinglist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.readinglist.model.ReadingListSummary
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

private val CBL_MIME_TYPES = arrayOf(
    "application/xml",
    "text/xml",
    "application/octet-stream",
    "*/*",
)

data object ReadingListsTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.reading_lists_tab),
            icon = rememberVectorPainter(Icons.Outlined.List),
        )

    override suspend fun onReselect(navigator: Navigator) = Unit

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { ReadingListsScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val documentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let(screenModel::importDocument)
        }
        val launchImport = remember(documentLauncher) {
            { documentLauncher.launch(CBL_MIME_TYPES) }
        }

        ReadingListsScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onImport = launchImport,
            onEditSources = screenModel::editSources,
        )

        when (val dialog = state.dialog) {
            is ReadingListsDialog.SourceSelection -> {
                SourceSelectionDialog(
                    dialog = dialog,
                    isSaving = state.isSaving,
                    onToggleSource = screenModel::toggleSource,
                    onSelectAll = screenModel::selectAllInstalledSources,
                    onClear = screenModel::clearSelectedSources,
                    onMoveSource = screenModel::moveSelectedSource,
                    onConfirm = screenModel::confirmSourceSelection,
                    onDismiss = screenModel::dismissDialog,
                )
            }
            null -> Unit
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                val message = when (event) {
                    is ReadingListsEvent.Imported -> context.getString(
                        R.string.reading_list_imported,
                        event.listName ?: context.getString(R.string.reading_list_untitled),
                    )
                    is ReadingListsEvent.ImportFailed -> context.getString(event.failure.stringRes)
                    ReadingListsEvent.SourcesUpdated -> context.getString(R.string.reading_list_sources_updated)
                    ReadingListsEvent.SelectInstalledSource -> context.getString(
                        R.string.reading_list_select_source_error,
                    )
                    ReadingListsEvent.ReadingListMissing -> context.getString(R.string.reading_list_missing_error)
                    ReadingListsEvent.SaveFailed -> context.getString(R.string.reading_list_save_error)
                }
                snackbarHostState.showSnackbar(message)
            }
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
    }
}

@Composable
private fun ReadingListsScreen(
    state: ReadingListsScreenState,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onEditSources: (Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(R.string.reading_lists_title),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                        )
                    }
                },
                text = {
                    Text(
                        text = stringResource(
                            if (state.isImporting) {
                                R.string.reading_list_importing
                            } else {
                                R.string.reading_list_import_cbl
                            },
                        ),
                    )
                },
                expanded = true,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            state.readingLists.isEmpty() -> ReadingListsEmptyScreen(
                contentPadding = paddingValues,
                onImport = onImport,
            )
            else -> ReadingListsContent(
                readingLists = state.readingLists,
                contentPadding = paddingValues,
                onEditSources = onEditSources,
            )
        }
    }
}

@Composable
private fun ReadingListsEmptyScreen(
    contentPadding: PaddingValues,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.List,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reading_lists_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onImport) {
                Text(stringResource(R.string.reading_list_import_cbl))
            }
        }
    }
}

@Composable
private fun ReadingListsContent(
    readingLists: List<ReadingListSummary>,
    contentPadding: PaddingValues,
    onEditSources: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(
            items = readingLists,
            key = ReadingListSummary::id,
        ) { readingList ->
            ReadingListItem(
                readingList = readingList,
                onEditSources = { onEditSources(readingList.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReadingListItem(
    readingList: ReadingListSummary,
    onEditSources: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEditSources),
        headlineContent = {
            Text(
                text = readingList.name ?: stringResource(R.string.reading_list_untitled),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(
                        R.string.reading_list_entries_and_sources,
                        readingList.entryCount,
                        readingList.sourceCount,
                    ),
                )
                readingList.currentPosition?.let { position ->
                    Text(
                        text = stringResource(
                            R.string.reading_list_progress,
                            (position + 1).coerceAtMost(readingList.entryCount),
                            readingList.entryCount,
                        ),
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onEditSources) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.reading_list_edit_sources),
                )
            }
        },
    )
}

@Composable
private fun SourceSelectionDialog(
    dialog: ReadingListsDialog.SourceSelection,
    isSaving: Boolean,
    onToggleSource: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onMoveSource: (Long, Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedSources = dialog.selectedSourceIds.mapNotNull { sourceId ->
        dialog.sources.firstOrNull { source -> source.id == sourceId }
    }
    val unselectedSources = dialog.sources.filterNot { source -> source.id in dialog.selectedSourceIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.reading_list_source_dialog_title))
                Text(
                    text = dialog.listName ?: stringResource(R.string.reading_list_untitled),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reading_list_source_dialog_explanation))
                Text(
                    text = stringResource(
                        R.string.reading_list_entries_and_sources,
                        dialog.entryCount,
                        dialog.selectedSourceIds.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (dialog.warningCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.reading_list_warning_count,
                            dialog.warningCount,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!dialog.hasInstalledSources) {
                    Text(
                        text = stringResource(R.string.reading_list_no_installed_sources),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onSelectAll,
                            enabled = !isSaving,
                        ) {
                            Text(stringResource(R.string.reading_list_select_all))
                        }
                        TextButton(
                            onClick = onClear,
                            enabled = !isSaving && dialog.selectedSourceIds.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.reading_list_clear_selection))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(
                        items = selectedSources,
                        key = ReadingListSourceOption::id,
                    ) { source ->
                        SourceSelectionRow(
                            source = source,
                            selected = true,
                            priority = dialog.selectedSourceIds.indexOf(source.id) + 1,
                            canMoveUp = dialog.selectedSourceIds.firstOrNull() != source.id,
                            canMoveDown = dialog.selectedSourceIds.lastOrNull() != source.id,
                            enabled = !isSaving,
                            onToggle = { onToggleSource(source.id) },
                            onMoveUp = { onMoveSource(source.id, -1) },
                            onMoveDown = { onMoveSource(source.id, 1) },
                        )
                    }
                    items(
                        items = unselectedSources,
                        key = ReadingListSourceOption::id,
                    ) { source ->
                        SourceSelectionRow(
                            source = source,
                            selected = false,
                            priority = null,
                            canMoveUp = false,
                            canMoveDown = false,
                            enabled = !isSaving && source.installed,
                            onToggle = { onToggleSource(source.id) },
                            onMoveUp = {},
                            onMoveDown = {},
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = dialog.canConfirm && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        when (dialog.mode) {
                            is SourceSelectionMode.Import -> R.string.reading_list_import_action
                            is SourceSelectionMode.Edit -> R.string.reading_list_save_action
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.reading_list_cancel_action))
            }
        },
    )
}

@Composable
private fun SourceSelectionRow(
    source: ReadingListSourceOption,
    selected: Boolean,
    priority: Int?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (source.installed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val supportingText = when {
                !source.installed -> stringResource(R.string.reading_list_unavailable_source)
                priority != null -> stringResource(R.string.reading_list_source_priority, priority)
                else -> source.language.uppercase()
            }
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            IconButton(
                onClick = onMoveUp,
                enabled = enabled && canMoveUp,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.reading_list_move_source_up),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = enabled && canMoveDown,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.reading_list_move_source_down),
                )
            }
        }
    }
}

private val CblImportFailure.stringRes: Int
    @StringRes get() = when (this) {
        CblImportFailure.EMPTY_DOCUMENT -> R.string.reading_list_import_empty_error
        CblImportFailure.FILE_TOO_LARGE -> R.string.reading_list_import_too_large_error
        CblImportFailure.UNSAFE_XML -> R.string.reading_list_import_unsafe_error
        CblImportFailure.MALFORMED_XML -> R.string.reading_list_import_malformed_error
        CblImportFailure.INVALID_READING_LIST -> R.string.reading_list_import_invalid_error
        CblImportFailure.EMPTY_READING_LIST -> R.string.reading_list_import_no_entries_error
        CblImportFailure.CANNOT_OPEN -> R.string.reading_list_import_open_error
        CblImportFailure.UNKNOWN -> R.string.reading_list_import_unknown_error
    }
