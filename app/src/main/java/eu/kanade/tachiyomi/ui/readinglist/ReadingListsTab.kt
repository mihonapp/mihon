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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.readinglist.model.ReadingListSummary
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.Locale

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
            onDelete = screenModel::requestDelete,
        )

        when (val dialog = state.dialog) {
            is ReadingListsDialog.SourceSelection -> {
                SourceSelectionDialog(
                    dialog = dialog,
                    isSaving = state.isSaving,
                    onPreferredLanguageChange = screenModel::setPreferredLanguage,
                    onToggleSource = screenModel::toggleSource,
                    onToggleSources = screenModel::toggleSources,
                    onSelectSources = screenModel::selectSources,
                    onClear = screenModel::clearSelectedSources,
                    onMoveSource = screenModel::moveSelectedSource,
                    onConfirm = screenModel::confirmSourceSelection,
                    onDismiss = screenModel::dismissDialog,
                )
            }
            is ReadingListsDialog.DeleteConfirmation -> {
                DeleteReadingListDialog(
                    readingList = dialog.readingList,
                    isDeleting = state.isDeleting,
                    onConfirm = screenModel::confirmDelete,
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
                    is ReadingListsEvent.Deleted -> context.getString(
                        R.string.reading_list_deleted,
                        event.listName ?: context.getString(R.string.reading_list_untitled),
                    )
                    ReadingListsEvent.SourcesUpdated -> context.getString(R.string.reading_list_sources_updated)
                    ReadingListsEvent.SelectInstalledSource -> context.getString(
                        R.string.reading_list_select_source_error,
                    )
                    ReadingListsEvent.ReadingListMissing -> context.getString(R.string.reading_list_missing_error)
                    ReadingListsEvent.SaveFailed -> context.getString(R.string.reading_list_save_error)
                    ReadingListsEvent.DeleteFailed -> context.getString(R.string.reading_list_delete_error)
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
    onDelete: (ReadingListSummary) -> Unit,
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
                onDelete = onDelete,
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
    onDelete: (ReadingListSummary) -> Unit,
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
                onDelete = { onDelete(readingList) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReadingListItem(
    readingList: ReadingListSummary,
    onEditSources: () -> Unit,
    onDelete: () -> Unit,
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
            Row {
                IconButton(onClick = onEditSources) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.reading_list_edit_sources),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.reading_list_delete),
                    )
                }
            }
        },
    )
}

@Composable
private fun SourceSelectionDialog(
    dialog: ReadingListsDialog.SourceSelection,
    isSaving: Boolean,
    onPreferredLanguageChange: (String) -> Unit,
    onToggleSource: (Long) -> Unit,
    onToggleSources: (List<Long>) -> Unit,
    onSelectSources: (List<Long>) -> Unit,
    onClear: () -> Unit,
    onMoveSource: (Long, Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember(dialog.mode) { mutableStateOf("") }
    var languageMenuExpanded by remember(dialog.mode) { mutableStateOf(false) }
    val expandedGroups = remember(dialog.mode) { mutableStateMapOf<String, Boolean>() }
    val availableLanguages = remember(dialog.sourceGroups) {
        availableReadingListSourceLanguages(dialog.sourceGroups)
    }
    val preferredLanguage = dialog.preferredLanguage.takeIf { language ->
        language == BasePreferences.READING_LIST_ALL_LANGUAGES || language in availableLanguages
    } ?: BasePreferences.READING_LIST_ALL_LANGUAGES
    val filteredGroups = remember(dialog.sourceGroups, preferredLanguage, searchQuery) {
        filterReadingListSourceGroups(
            groups = dialog.sourceGroups,
            preferredLanguage = preferredLanguage,
            query = searchQuery,
        )
    }
    val visibleSourceIds = filteredGroups
        .flatMap(ReadingListSourceGroup::sources)
        .filter(ReadingListSourceOption::installed)
        .map(ReadingListSourceOption::id)
    val selectedPriorities = dialog.selectedSourceIds
        .withIndex()
        .associate { indexedValue -> indexedValue.value to indexedValue.index + 1 }

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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reading_list_source_search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.reading_list_clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = stringResource(R.string.reading_list_default_language),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(languageDisplayName(preferredLanguage))
                        }
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reading_list_all_languages)) },
                            onClick = {
                                languageMenuExpanded = false
                                onPreferredLanguageChange(BasePreferences.READING_LIST_ALL_LANGUAGES)
                            },
                        )
                        availableLanguages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(languageDisplayName(language)) },
                                onClick = {
                                    languageMenuExpanded = false
                                    onPreferredLanguageChange(language)
                                },
                            )
                        }
                    }
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
                            onClick = { onSelectSources(visibleSourceIds) },
                            enabled = !isSaving && visibleSourceIds.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.reading_list_select_visible))
                        }
                        TextButton(
                            onClick = onClear,
                            enabled = !isSaving && dialog.selectedSourceIds.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.reading_list_clear_selection))
                        }
                    }
                }

                if (filteredGroups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.reading_list_no_matching_sources),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        filteredGroups.forEach { group ->
                            val collapsible = group.installed && group.sources.size > 1
                            val expanded = !collapsible || expandedGroups[group.key] == true
                            item(key = "group-${group.key}") {
                                SourceGroupHeader(
                                    group = group,
                                    selectedSourceIds = dialog.selectedSourceIds,
                                    selectedPriorities = selectedPriorities,
                                    expanded = expanded,
                                    collapsible = collapsible,
                                    enabled = !isSaving,
                                    onToggleSource = onToggleSource,
                                    onToggleSources = onToggleSources,
                                    onMoveSource = onMoveSource,
                                    onToggleExpanded = {
                                        expandedGroups[group.key] = !expanded
                                    },
                                )
                            }

                            if (!group.installed || (collapsible && expanded)) {
                                items(
                                    items = group.sources,
                                    key = { source -> "${group.key}-${source.id}" },
                                ) { source ->
                                    val priority = selectedPriorities[source.id]
                                    val selected = priority != null
                                    SourceSelectionRow(
                                        source = source,
                                        selected = selected,
                                        priority = priority,
                                        canMoveUp = priority != null && priority > 1,
                                        canMoveDown = priority != null && priority < dialog.selectedSourceIds.size,
                                        enabled = !isSaving && (source.installed || selected),
                                        modifier = Modifier.padding(start = 24.dp),
                                        onToggle = { onToggleSource(source.id) },
                                        onMoveUp = { onMoveSource(source.id, -1) },
                                        onMoveDown = { onMoveSource(source.id, 1) },
                                    )
                                }
                            }
                        }
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
private fun SourceGroupHeader(
    group: ReadingListSourceGroup,
    selectedSourceIds: List<Long>,
    selectedPriorities: Map<Long, Int>,
    expanded: Boolean,
    collapsible: Boolean,
    enabled: Boolean,
    onToggleSource: (Long) -> Unit,
    onToggleSources: (List<Long>) -> Unit,
    onMoveSource: (Long, Int) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    if (!group.installed) {
        Text(
            text = stringResource(R.string.reading_list_unavailable_sources),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        return
    }

    val sourceIds = group.sources.map(ReadingListSourceOption::id)
    val selectedCount = sourceIds.count(selectedSourceIds::contains)
    val languages = group.sources
        .map(ReadingListSourceOption::language)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString { language -> language.uppercase(Locale.ROOT) }

    if (!collapsible) {
        val source = group.sources.single()
        val priority = selectedPriorities[source.id]
        val selected = priority != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = { onToggleSource(source.id) })
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSource(source.id) },
                enabled = enabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.extensionName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (priority == null) {
                        source.language.uppercase(Locale.ROOT)
                    } else {
                        stringResource(
                            R.string.reading_list_source_language_and_priority,
                            source.language.uppercase(Locale.ROOT),
                            priority,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                SourcePriorityButtons(
                    enabled = enabled,
                    canMoveUp = priority != null && priority > 1,
                    canMoveDown = priority != null && priority < selectedSourceIds.size,
                    onMoveUp = { onMoveSource(source.id, -1) },
                    onMoveDown = { onMoveSource(source.id, 1) },
                )
            }
        }
        return
    }

    val toggleState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == sourceIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleExpanded)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = toggleState,
            onClick = { onToggleSources(sourceIds) },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.extensionName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.reading_list_source_group_summary,
                    selectedCount,
                    sourceIds.size,
                    languages,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onToggleExpanded,
            enabled = enabled,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.reading_list_collapse_source_group
                    } else {
                        R.string.reading_list_expand_source_group
                    },
                ),
            )
        }
    }
}

@Composable
private fun SourceSelectionRow(
    source: ReadingListSourceOption,
    selected: Boolean,
    priority: Int?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = modifier
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
                priority != null -> stringResource(
                    R.string.reading_list_source_language_and_priority,
                    source.language.uppercase(Locale.ROOT),
                    priority,
                )
                else -> source.language.uppercase(Locale.ROOT)
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
            SourcePriorityButtons(
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
            )
        }
    }
}

@Composable
private fun SourcePriorityButtons(
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
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

@Composable
private fun languageDisplayName(language: String): String {
    if (language == BasePreferences.READING_LIST_ALL_LANGUAGES) {
        return stringResource(R.string.reading_list_all_languages)
    }

    val locale = Locale.forLanguageTag(language.replace('_', '-'))
    val displayName = locale.getDisplayName(Locale.getDefault())
        .takeIf { name -> name.isNotBlank() && !name.equals(language, ignoreCase = true) }
        ?.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
        }
    val code = language.uppercase(Locale.ROOT)
    return if (displayName == null) code else "$displayName ($code)"
}

@Composable
private fun DeleteReadingListDialog(
    readingList: ReadingListSummary,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = readingList.name ?: stringResource(R.string.reading_list_untitled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_list_delete_title)) },
        text = { Text(stringResource(R.string.reading_list_delete_message, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.reading_list_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
            ) {
                Text(stringResource(R.string.reading_list_cancel_action))
            }
        },
    )
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
