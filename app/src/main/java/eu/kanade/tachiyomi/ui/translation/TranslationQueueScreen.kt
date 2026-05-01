package eu.kanade.tachiyomi.ui.translation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import eu.kanade.tachiyomi.data.translation.TranslationLogLevel
import eu.kanade.tachiyomi.data.translation.TranslationRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.data.Translation_jobs
import tachiyomi.data.Translation_logs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TranslationQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { TranslationQueueScreenModel() }
        val jobs by screenModel.jobs.collectAsState()
        val logs by screenModel.logs.collectAsState()
        val isRunning by TranslationJob.isRunningFlow(context).collectAsState(false)
        val activeJobCount by remember(jobs) {
            derivedStateOf { jobs.count { it.status !in FINISHED_STATUSES } }
        }

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
                                persistentListOf(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_cancel_all),
                                        onClick = { screenModel.cancelAll(context) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_translation_clear_logs),
                                        onClick = { screenModel.clearLogs() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_translation_clear_finished),
                                        onClick = { screenModel.clearFinished() },
                                    ),
                                ),
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
                            TranslationJob.stop(context)
                        } else {
                            TranslationJob.start(context)
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

            ScrollbarLazyColumn(contentPadding = contentPadding) {
                items(
                    count = jobs.size,
                    key = { index -> jobs[index]._id },
                ) { index ->
                    TranslationJobItem(
                        job = jobs[index],
                        onRetry = { screenModel.retry(context, jobs[index]) },
                        onCancel = { screenModel.cancel(jobs[index]) },
                    )
                    HorizontalDivider()
                }
                if (logs.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text(text = stringResource(MR.strings.pref_translation_logs)) },
                        )
                    }
                }
                items(
                    count = logs.take(LOG_LIMIT).size,
                    key = { index -> "log-${logs[index]._id}" },
                ) { index ->
                    TranslationLogItem(log = logs[index])
                    HorizontalDivider()
                }
            }
        }
    }

    private const val LOG_LIMIT = 100
}

private val FINISHED_STATUSES = setOf(
    TranslationJobStatus.Completed.value,
    TranslationJobStatus.Failed.value,
    TranslationJobStatus.Cancelled.value,
)

@Composable
private fun TranslationJobItem(
    job: Translation_jobs,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val isFinished = job.status in FINISHED_STATUSES
    ListItem(
        headlineContent = {
            Text(
                text = buildString {
                    append("#")
                    append(job._id)
                    append(" ")
                    append(job.scope)
                    job.chapter_id?.let {
                        append(" · ch ")
                        append(it)
                    }
                    job.page_index?.let {
                        append(" · p ")
                        append(it + 1)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(job.status)
                    append(" · ")
                    append(job.progress_current)
                    append("/")
                    append(job.progress_total)
                    append(" · ")
                    append(job.target_language.ifBlank { "app language" })
                    append(" · ")
                    append(job.model)
                    job.error_message?.let {
                        append("\n")
                        append(it)
                    }
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (isFinished) {
                Text(
                    text = stringResource(MR.strings.action_retry),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            } else {
                Text(
                    text = stringResource(MR.strings.action_cancel),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        },
        modifier = Modifier.then(
            if (isFinished) {
                Modifier.clickable(onClick = onRetry)
            } else {
                Modifier.clickable(onClick = onCancel)
            },
        ),
    )
}

@Composable
private fun TranslationLogItem(log: Translation_logs) {
    ListItem(
        headlineContent = {
            Text(
                text = "${log.level} · ${log.tag} · ${log.message}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when (log.level) {
                    TranslationLogLevel.Error.value -> MaterialTheme.colorScheme.error
                    TranslationLogLevel.Warning.value -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = log.details?.let {
            {
                Text(
                    text = it,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

private class TranslationQueueScreenModel(
    private val repository: TranslationRepository = Injekt.get(),
) : ScreenModel {

    val jobs = repository.observeJobs()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs = repository.observeLogs()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(context: Context, job: Translation_jobs) {
        screenModelScope.launchIO {
            repository.updateJobStatus(
                job = job,
                status = TranslationJobStatus.Queued,
                errorMessage = null,
            )
            TranslationJob.start(context)
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
                        job = it,
                        status = TranslationJobStatus.Cancelled,
                        errorMessage = null,
                    )
                }
            TranslationJob.stop(context)
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
}
