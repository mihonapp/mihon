package eu.kanade.tachiyomi.ui.translation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationJobStatus
import eu.kanade.tachiyomi.data.translation.GroupedTranslationLog
import eu.kanade.tachiyomi.data.translation.TranslationLogDetailsFormatter
import eu.kanade.tachiyomi.data.translation.TranslationLogLevel
import eu.kanade.tachiyomi.data.translation.TranslationLogUiItem
import eu.kanade.tachiyomi.data.translation.TranslationLogUiModel
import eu.kanade.tachiyomi.data.translation.TranslationQueueChapterGroup
import eu.kanade.tachiyomi.data.translation.TranslationQueueGroup
import eu.kanade.tachiyomi.data.translation.TranslationQueueItem
import eu.kanade.tachiyomi.data.translation.TranslationQueueTypeFilter
import eu.kanade.tachiyomi.data.translation.TranslationQueueUiModel
import eu.kanade.tachiyomi.data.translation.TranslationRepository
import eu.kanade.tachiyomi.data.translation.TranslationRetryPlanner
import eu.kanade.tachiyomi.data.translation.TranslationSetupValidator
import eu.kanade.tachiyomi.data.translation.TranslationWorkStartPolicy
import eu.kanade.tachiyomi.data.translation.normalizedParallelRetryLanes
import eu.kanade.tachiyomi.data.translation.isRetryableFromQueue
import eu.kanade.tachiyomi.data.translation.toJob
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.data.Translation_jobs
import tachiyomi.data.Translation_logs
import tachiyomi.i18n.MR
import tachiyomi.domain.translation.service.TranslationLogSwipeAction
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.domain.translation.service.TranslationQueueSwipeAction
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Date

object TranslationQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { TranslationQueueScreenModel() }
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        val jobs by screenModel.jobs.collectAsState()
        val logs by screenModel.logs.collectAsState()
        val isRunning by TranslationJob.isRunningFlow(context).collectAsState(false)
        val queueSwipeStart = translationPreferences.queueSwipeStartAction.get()
        val queueSwipeEnd = translationPreferences.queueSwipeEndAction.get()
        val logSwipeStart = translationPreferences.logSwipeStartAction.get()
        val logSwipeEnd = translationPreferences.logSwipeEndAction.get()
        var selectedLog by remember { mutableStateOf<GroupedTranslationLog?>(null) }
        var confirmCancelJob by remember { mutableStateOf<TranslationQueueItem?>(null) }
        var levelFilter by remember { mutableStateOf<String?>(null) }
        var tagFilter by remember { mutableStateOf<String?>(null) }
        var jobFilter by remember { mutableStateOf<Long?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var selectedQueueFilters by remember { mutableStateOf(emptySet<TranslationQueueTypeFilter>()) }
        var collapsedQueueGroups by remember { mutableStateOf(emptySet<String>()) }
        var inlineLogJobIds by remember { mutableStateOf(emptySet<Long>()) }
        var logsExpanded by rememberSaveable { mutableStateOf(false) }
        var showClearAllConfirmation by remember { mutableStateOf(false) }
        val logItems by remember(logs) {
            derivedStateOf { logs.map(Translation_logs::toUiItem) }
        }
        val queueUiState by remember(jobs, logItems, selectedQueueFilters) {
            derivedStateOf {
                TranslationQueueUiModel.derive(
                    items = jobs,
                    logs = logItems,
                    filters = selectedQueueFilters,
                )
            }
        }
        val activeJobCount = queueUiState.activeJobCount
        val queueGroups = queueUiState.queueGroups
        val groupedLogsByJob = queueUiState.groupedLogsByJob
        val groupedLogs by remember(logItems, levelFilter, tagFilter, jobFilter, searchQuery) {
            derivedStateOf {
                val query = searchQuery.trim()
                val filteredLogs = logItems.filter { log ->
                    (levelFilter == null || log.level == levelFilter) &&
                        (tagFilter == null || log.tag == tagFilter) &&
                        (jobFilter == null || log.jobId == jobFilter) &&
                        (
                            query.isBlank() ||
                                log.level.contains(query, ignoreCase = true) ||
                                log.tag.contains(query, ignoreCase = true) ||
                                log.message.contains(query, ignoreCase = true) ||
                                log.details.orEmpty().contains(query, ignoreCase = true) ||
                                log.jobId?.toString()?.contains(query) == true ||
                                log.pageId?.toString()?.contains(query) == true
                            )
                }
                TranslationLogUiModel.groupAdjacent(filteredLogs)
            }
        }
        val activeFilterSummary = listOfNotNull(
            levelFilter?.let { "level=$it" },
            tagFilter?.let { "tag=$it" },
            jobFilter?.let { "job=$it" },
        ).joinToString(" · ")

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(MR.strings.label_translation_queue),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeJobCount > 0) {
                                Pill(
                                    text = "$activeJobCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if (jobs.isNotEmpty() || logs.isNotEmpty()) {
                            AppBarActions(
                                buildList<AppBar.AppBarAction> {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_cancel_all),
                                            onClick = { screenModel.cancelAll(context) },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_translation_clear_finished),
                                            onClick = { screenModel.clearFinished() },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.translation_log_filter_all),
                                            onClick = {
                                                levelFilter = null
                                                tagFilter = null
                                                jobFilter = null
                                                searchQuery = ""
                                                selectedQueueFilters = emptySet()
                                            },
                                        ),
                                    )
                                    listOf(
                                        TranslationLogLevel.Error.value,
                                        TranslationLogLevel.Warning.value,
                                        TranslationLogLevel.Info.value,
                                        TranslationLogLevel.Debug.value,
                                    ).forEach { level ->
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.translation_log_filter_level, level),
                                                onClick = { levelFilter = level },
                                            ),
                                        )
                                    }
                                    logs.map { it.tag }.distinct().sorted().take(8).forEach { tag ->
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.translation_log_filter_tag, tag),
                                                onClick = { tagFilter = tag },
                                            ),
                                        )
                                    }
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_translation_clear_logs),
                                            onClick = { screenModel.clearLogs() },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_translation_clear_queue_logs),
                                            onClick = { showClearAllConfirmation = true },
                                        ),
                                    )
                                }.toPersistentList(),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    text = {
                        Text(
                            text = stringResource(
                                if (isRunning) MR.strings.action_pause else MR.strings.action_resume,
                            ),
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (isRunning) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        if (isRunning) {
                            TranslationJob.stop(context, reason = "queue_pause")
                        } else {
                            screenModel.resume(context)
                        }
                    },
                    modifier = Modifier.animateFloatingActionButton(
                        visible = activeJobCount > 0,
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            if (jobs.isEmpty() && logs.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.information_no_translation_jobs,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            Column(modifier = Modifier.padding(contentPadding)) {
                if (jobs.isNotEmpty()) {
                    TranslationQueueFilterRow(
                        selectedFilters = selectedQueueFilters,
                        onToggle = { filter ->
                            selectedQueueFilters = if (filter in selectedQueueFilters) {
                                selectedQueueFilters - filter
                            } else {
                                selectedQueueFilters + filter
                            }
                        },
                    )
                }

                ScrollbarLazyColumn(modifier = Modifier.weight(1f)) {
                    queueGroups.forEach { group ->
                        val expanded = group.key !in collapsedQueueGroups
                        stickyHeader(key = "group-${group.key}") {
                            TranslationQueueGroupHeader(
                                group = group,
                                expanded = expanded,
                                onClick = {
                                    collapsedQueueGroups = if (expanded) {
                                        collapsedQueueGroups + group.key
                                    } else {
                                        collapsedQueueGroups - group.key
                                    }
                                },
                                onRetry = {
                                    screenModel.retry(
                                        context = context,
                                        jobs = TranslationQueueUiModel.retryableItems(group.items).map { it.toJob() },
                                    )
                                },
                            )
                        }
                        if (expanded) {
                            group.chapterGroups.forEach { chapterGroup ->
                                item(key = "chapter-${chapterGroup.key}") {
                                    TranslationQueueChapterHeader(
                                        group = chapterGroup,
                                        onRetry = {
                                            screenModel.retry(
                                                context = context,
                                                jobs = TranslationQueueUiModel.retryableItems(chapterGroup.items).map { it.toJob() },
                                            )
                                        },
                                    )
                                }
                                items(
                                    count = chapterGroup.items.size,
                                    key = { index -> chapterGroup.items[index].id },
                                ) { index ->
                                    val job = chapterGroup.items[index]
                                    TranslationJobItem(
                                        job = job,
                                        startAction = queueSwipeStart,
                                        endAction = queueSwipeEnd,
                                        onRetry = { screenModel.retry(context, job.toJob()) },
                                        onCancel = { confirmCancelJob = job },
                                        onViewLogs = {
                                            inlineLogJobIds = if (job.id in inlineLogJobIds) {
                                                inlineLogJobIds - job.id
                                            } else {
                                                inlineLogJobIds + job.id
                                            }
                                        },
                                    )
                                    if (job.id in inlineLogJobIds) {
                                        groupedLogsByJob[job.id].orEmpty().forEach { log ->
                                            TranslationLogItem(
                                                log = log,
                                                indent = 32.dp,
                                                startAction = logSwipeStart,
                                                endAction = logSwipeEnd,
                                                onClick = { selectedLog = log },
                                                onCopy = {
                                                    context.copyToClipboard(
                                                        context.contextStringResource(MR.strings.translation_log_details),
                                                        formatLogDetails(log),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    if (logs.isNotEmpty()) {
                        item {
                            ListItem(
                                headlineContent = { Text(text = stringResource(MR.strings.pref_translation_logs)) },
                                supportingContent = activeFilterSummary.takeIf { it.isNotBlank() }?.let {
                                    { Text(text = it) }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (activeFilterSummary.isNotBlank()) {
                                            Text(
                                                text = stringResource(MR.strings.translation_log_filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable {
                                                        levelFilter = null
                                                        tagFilter = null
                                                        jobFilter = null
                                                        searchQuery = ""
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                            )
                                        }
                                        Text(
                                            text = stringResource(
                                                if (logsExpanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { logsExpanded = !logsExpanded },
                            )
                        }
                        if (logsExpanded) {
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text(text = stringResource(MR.strings.action_search_hint)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(
                                count = groupedLogs.size,
                                key = { index -> "log-${groupedLogs[index].id}" },
                            ) { index ->
                                TranslationLogItem(
                                    log = groupedLogs[index],
                                    indent = 0.dp,
                                    startAction = logSwipeStart,
                                    endAction = logSwipeEnd,
                                    onClick = { selectedLog = groupedLogs[index] },
                                    onCopy = {
                                        context.copyToClipboard(
                                            context.contextStringResource(MR.strings.translation_log_details),
                                            formatLogDetails(groupedLogs[index]),
                                        )
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        selectedLog?.let { log ->
            TranslationLogDetailsDialog(
                log = log,
                onDismissRequest = { selectedLog = null },
                onCopy = {
                    context.copyToClipboard(
                        context.contextStringResource(MR.strings.translation_log_details),
                        formatLogDetails(log),
                    )
                },
            )
        }

        if (showClearAllConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirmation = false },
                title = { Text(text = stringResource(MR.strings.pref_translation_clear_queue_logs)) },
                text = { Text(text = stringResource(MR.strings.pref_translation_clear_queue_logs_summary)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearAllConfirmation = false
                            screenModel.clearAllQueuesAndLogs(context)
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirmation = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        confirmCancelJob?.let { job ->
            AlertDialog(
                onDismissRequest = { confirmCancelJob = null },
                title = { Text(text = stringResource(MR.strings.action_cancel)) },
                text = { Text(text = job.mangaTitle) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmCancelJob = null
                            screenModel.cancel(job.toJob())
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmCancelJob = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

private val FINISHED_STATUSES = setOf(
    TranslationJobStatus.Completed.value,
    TranslationJobStatus.Failed.value,
    TranslationJobStatus.Cancelled.value,
)

@Composable
private fun TranslationQueueFilterRow(
    selectedFilters: Set<TranslationQueueTypeFilter>,
    onToggle: (TranslationQueueTypeFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(TranslationQueueTypeFilter.entries) { filter ->
            FilterChip(
                selected = filter in selectedFilters,
                onClick = { onToggle(filter) },
                label = { Text(text = queueFilterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun TranslationQueueGroupHeader(
    group: TranslationQueueGroup,
    expanded: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = group.mangaTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append("${group.items.size} job(s)")
                    val statusSummary = group.statusCounts
                        .toSortedMap()
                        .entries
                        .joinToString(" · ") { "${it.key}:${it.value}" }
                    if (statusSummary.isNotBlank()) {
                        append(" · ")
                        append(statusSummary)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.retryableCount > 0) {
                    Text(
                        text = stringResource(MR.strings.action_retry),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                Text(
                    text = stringResource(if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun TranslationQueueChapterHeader(
    group: TranslationQueueChapterGroup,
    onRetry: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = group.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append("${group.items.size} job(s)")
                    val statusSummary = group.statusCounts
                        .toSortedMap()
                        .entries
                        .joinToString(" · ") { "${it.key}:${it.value}" }
                    if (statusSummary.isNotBlank()) {
                        append(" · ")
                        append(statusSummary)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (group.retryableCount > 0) {
                Text(
                    text = stringResource(MR.strings.action_retry),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        },
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun TranslationJobItem(
    job: TranslationQueueItem,
    startAction: TranslationQueueSwipeAction,
    endAction: TranslationQueueSwipeAction,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onViewLogs: () -> Unit,
) {
    val isFinished = job.status in FINISHED_STATUSES
    val status = TranslationJobStatus.entries.firstOrNull { it.value == job.status }
    val canRetry = isFinished || status?.isRetryableFromQueue() == true
    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(queueSwipeAction(startAction, canRetry, onRetry, onCancel, onViewLogs)),
        endActions = listOfNotNull(queueSwipeAction(endAction, canRetry, onRetry, onCancel, onViewLogs)),
        swipeThreshold = 56.dp,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = job.mangaTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = statusColor(job.status),
                )
            },
            supportingContent = {
                Text(
                    text = buildString {
                        append(job.chapterName ?: "Chapter ${job.chapterId ?: "-"}")
                        job.pageIndex?.let {
                            append(" · p ")
                            append(it + 1)
                        }
                        append("\n")
                        append(job.status)
                        append(" · ")
                        append(job.progressCurrent)
                        append("/")
                        append(job.progressTotal)
                        append(" · ")
                        append(job.targetLanguage.ifBlank { "app language" })
                        append(" · ")
                        append(job.model)
                        job.errorMessage?.let {
                            append("\n")
                            append(it)
                        }
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.pref_translation_logs),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onViewLogs)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                    Text(
                        text = stringResource(if (canRetry) MR.strings.action_retry else MR.strings.action_cancel),
                        color = if (canRetry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable(onClick = if (canRetry) onRetry else onCancel)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            },
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun TranslationLogItem(
    log: GroupedTranslationLog,
    indent: androidx.compose.ui.unit.Dp,
    startAction: TranslationLogSwipeAction,
    endAction: TranslationLogSwipeAction,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val item = log.first
    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(logSwipeAction(startAction, onClick, onCopy)),
        endActions = listOfNotNull(logSwipeAction(endAction, onClick, onCopy)),
        swipeThreshold = 56.dp,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = buildString {
                        append(item.level)
                        append(" · ")
                        append(item.tag)
                        append(" · ")
                        append(item.message)
                        if (log.count > 1) {
                            append(" ×")
                            append(log.count)
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when (item.level) {
                        TranslationLogLevel.Error.value -> MaterialTheme.colorScheme.error
                        TranslationLogLevel.Warning.value -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            supportingContent = item.detailsPreview?.let {
                {
                    Text(
                        text = it,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            modifier = Modifier
                .padding(start = indent)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun TranslationLogDetailsDialog(
    log: GroupedTranslationLog,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.translation_log_details)) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = formatLogDetails(log))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text(text = stringResource(MR.strings.action_copy_to_clipboard))
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
private fun queueSwipeAction(
    action: TranslationQueueSwipeAction,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onViewLogs: () -> Unit,
): SwipeAction? {
    return when (action) {
        TranslationQueueSwipeAction.Disabled -> null
        TranslationQueueSwipeAction.ViewLogs -> textSwipeAction(
            label = stringResource(MR.strings.pref_translation_logs),
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = onViewLogs,
        )
        TranslationQueueSwipeAction.RetryOrLogs -> textSwipeAction(
            label = stringResource(if (canRetry) MR.strings.action_retry else MR.strings.pref_translation_logs),
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = if (canRetry) onRetry else onViewLogs,
        )
        TranslationQueueSwipeAction.CancelOrDelete -> textSwipeAction(
            label = stringResource(MR.strings.action_cancel),
            background = MaterialTheme.colorScheme.errorContainer,
            onSwipe = onCancel,
        )
    }
}

@Composable
private fun logSwipeAction(
    action: TranslationLogSwipeAction,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
): SwipeAction? {
    return when (action) {
        TranslationLogSwipeAction.Disabled -> null
        TranslationLogSwipeAction.OpenDetails -> textSwipeAction(
            label = stringResource(MR.strings.translation_log_details),
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = onOpen,
        )
        TranslationLogSwipeAction.CopyDetails -> textSwipeAction(
            label = stringResource(MR.strings.action_copy_to_clipboard),
            background = MaterialTheme.colorScheme.secondaryContainer,
            onSwipe = onCopy,
        )
    }
}

@Composable
private fun textSwipeAction(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    onSwipe: () -> Unit,
): SwipeAction {
    return SwipeAction(
        icon = {
            Text(
                text = label,
                color = contentColorFor(background),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        background = background,
        onSwipe = onSwipe,
    )
}

private class TranslationQueueScreenModel(
    private val repository: TranslationRepository = Injekt.get(),
    private val setupValidator: TranslationSetupValidator = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) : ScreenModel {

    val jobs = repository.observeJobsForQueue()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs = repository.observeLogs()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(context: Context, job: Translation_jobs) {
        retry(context, listOf(job))
    }

    fun retry(context: Context, jobs: List<Translation_jobs>) {
        if (jobs.isEmpty()) return
        screenModelScope.launchIO {
            val setup = setupValidator.readiness()
            val decision = TranslationRetryPlanner.manualRetry(setup.ready)
            if (!decision.allowed) {
                repository.insertLog(
                    jobId = jobs.firstOrNull()?._id,
                    pageId = null,
                    level = TranslationLogLevel.Warning,
                    tag = "queue",
                    message = "Retry blocked",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "manual_retry_blocked",
                        jobId = jobs.firstOrNull()?._id,
                        previousStatus = jobs.firstOrNull()?.status,
                        nextStatus = jobs.firstOrNull()?.status,
                        reason = setup.message,
                        extra = mapOf("job_count" to jobs.size),
                    ),
                )
                withUIContext {
                    context.toast(setup.message)
                }
                return@launchIO
            }
            val retried = repository.retryJobs(jobs, forceOverwrite = decision.forceOverwrite)
            repository.insertLog(
                jobId = jobs.firstOrNull()?._id,
                pageId = null,
                level = TranslationLogLevel.Info,
                tag = "queue",
                message = "Manually retried translation job",
                details = TranslationLogDetailsFormatter.queueState(
                    action = "manual_retry",
                    jobId = jobs.firstOrNull()?._id,
                    previousStatus = jobs.firstOrNull()?.status,
                    nextStatus = TranslationJobStatus.ManualRetry.value,
                    extra = mapOf(
                        "manual_retry_force_overwrite" to decision.forceOverwrite,
                        "requested_jobs" to jobs.size,
                        "retried_jobs" to retried,
                    ),
                ),
            )
            val pendingRetryJobs = repository.countPendingManualRetryJobs()
            val workerCount = preferences.normalizedParallelRetryLanes(pendingRetryJobs)
            TranslationJob.startManualRetryWorkers(
                context = context,
                workerCount = workerCount,
                reason = "manual_retry",
            )
        }
    }

    fun resume(context: Context) {
        screenModelScope.launchIO {
            val setup = setupValidator.readiness()
            val result = if (setup.ready) {
                @Suppress("DEPRECATION")
                val includeRunning = !TranslationJob.isRunning(context)
                repository.resumeAllJobs(
                    skipExistingOverlays = true,
                    includeRunning = includeRunning,
                )
            } else {
                repository.insertLog(
                    jobId = null,
                    pageId = null,
                    level = TranslationLogLevel.Warning,
                    tag = "queue",
                    message = "Resume skipped",
                    details = setup.message,
                )
                null
            }
            if (result != null && result.requeued > 0) {
                withUIContext {
                    context.toast(context.contextStringResource(MR.strings.translation_resume_requeued, result.requeued))
                }
            } else if (!setup.ready) {
                withUIContext {
                    context.toast(setup.message)
                }
            } else if (result != null) {
                withUIContext {
                    context.toast(context.contextStringResource(MR.strings.translation_nothing_to_queue))
                }
            }
            if (setup.ready && result != null && result.requeued > 0) {
                TranslationJob.start(
                    context = context,
                    policy = TranslationWorkStartPolicy.Keep,
                    reason = "queue_resume",
                )
            }
        }
    }

    fun cancel(job: Translation_jobs) {
        screenModelScope.launchIO {
            repository.updateJobStatus(
                job = job,
                status = TranslationJobStatus.Cancelled,
                errorMessage = null,
            )
        }
    }

    fun cancelAll(context: Context) {
        screenModelScope.launchIO {
            jobs.value
                .filter { it.status !in FINISHED_STATUSES }
                .forEach {
                    repository.updateJobStatus(
                        job = it.toJob(),
                        status = TranslationJobStatus.Cancelled,
                        errorMessage = null,
                    )
                }
            TranslationJob.stop(context, reason = "queue_cancel_all")
        }
    }

    fun clearFinished() {
        screenModelScope.launchIO {
            repository.clearFinishedJobs()
        }
    }

    fun clearLogs() {
        screenModelScope.launchIO {
            repository.clearLogs()
        }
    }

    fun clearAllQueuesAndLogs(context: Context) {
        screenModelScope.launchIO {
            TranslationJob.stop(context, reason = "queue_clear_all")
            repository.clearAllJobsAndLogs()
        }
    }
}

private fun formatLogDetails(log: GroupedTranslationLog): String {
    val item = log.first
    return buildString {
        appendLine("Time: ${formatLogTimestamp(item.createdAt)}")
        if (log.count > 1) {
            appendLine("Count: ${log.count}")
            appendLine("First: ${formatLogTimestamp(log.firstCreatedAt)}")
            appendLine("Latest: ${formatLogTimestamp(log.latestCreatedAt)}")
        }
        appendLine("Level: ${item.level}")
        appendLine("Tag: ${item.tag}")
        appendLine("Job: ${item.jobId ?: "-"}")
        appendLine("Page: ${item.pageId ?: "-"}")
        appendLine("Message: ${item.message}")
        item.details?.let {
            appendLine()
            appendLine("Details:")
            append(it)
        }
    }
}

private fun Translation_logs.toUiItem(): TranslationLogUiItem {
    return TranslationLogUiItem(
        id = _id,
        jobId = job_id,
        pageId = page_id,
        createdAt = created_at,
        level = level,
        tag = tag,
        message = message,
        details = details,
    )
}

@Composable
private fun queueFilterLabel(filter: TranslationQueueTypeFilter): String {
    return when (filter) {
        TranslationQueueTypeFilter.Waiting -> "Waiting"
        TranslationQueueTypeFilter.Translating -> "Translating"
        TranslationQueueTypeFilter.Paused -> stringResource(MR.strings.paused)
        TranslationQueueTypeFilter.Error -> "Error"
        TranslationQueueTypeFilter.Done -> stringResource(MR.strings.completed)
        TranslationQueueTypeFilter.Cancelled -> stringResource(MR.strings.cancelled)
    }
}

private fun formatLogTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))
}

@Composable
private fun statusColor(status: String) = when (status) {
    TranslationJobStatus.Failed.value -> MaterialTheme.colorScheme.error
    TranslationJobStatus.PausedAuth.value,
    TranslationJobStatus.PausedQuota.value -> MaterialTheme.colorScheme.tertiary
    TranslationJobStatus.Running.value -> MaterialTheme.colorScheme.primary
    TranslationJobStatus.Completed.value -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface
}
