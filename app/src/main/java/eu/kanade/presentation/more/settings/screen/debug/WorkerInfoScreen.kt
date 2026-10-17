package eu.kanade.presentation.more.settings.screen.debug

import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import java.util.UUID
import kotlin.time.Instant

class WorkerInfoScreen : Screen() {

    companion object {
        const val TITLE = "Worker info"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<WorkerInfoViewModel>()
        val enqueued by viewModel.enqueued.collectAsState()
        val finished by viewModel.finished.collectAsState()
        val running by viewModel.running.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = MaterialSymbols.Rounded.ContentCopy,
                                    onClick = {
                                        context.copyToClipboard(
                                            TITLE,
                                            enqueued + finished + viewModel.constructString(running),
                                        )
                                    },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                item { SectionTitle(title = "Enqueued") }
                item { SectionText(text = enqueued) }

                item { SectionTitle(title = "Finished") }
                item { SectionText(text = finished) }

                item { SectionTitle(title = "Running") }
                if (running.isEmpty()) {
                    item { SectionText(text = "-") }
                } else {
                    item {
                        Text(
                            text = stringResource(MR.strings.worker_info_long_press_hint),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(running, key = { it.id }) { workInfo ->
                        RunningWorker(
                            workInfo = workInfo,
                            details = viewModel.constructString(listOf(workInfo)),
                            onCancel = { viewModel.cancelWork(workInfo.id) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RunningWorker(workInfo: WorkInfo, details: String, onCancel: () -> Unit) {
        val context = LocalContext.current
        var showMenu by remember(workInfo.id) { mutableStateOf(false) }
        var showConfirmation by remember(workInfo.id) { mutableStateOf(false) }

        Box {
            SectionText(
                text = details,
                modifier = Modifier.combinedClickable(
                    onClick = { showMenu = true },
                    onLongClick = { showMenu = true },
                    onLongClickLabel = stringResource(MR.strings.worker_info_actions),
                ),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.worker_info_cancel_job)) },
                    onClick = {
                        showMenu = false
                        showConfirmation = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_copy_to_clipboard)) },
                    onClick = {
                        showMenu = false
                        context.copyToClipboard(TITLE, details)
                    },
                )
            }
        }

        if (showConfirmation) {
            AlertDialog(
                onDismissRequest = { showConfirmation = false },
                title = { Text(stringResource(MR.strings.worker_info_cancel_job)) },
                text = { Text(stringResource(MR.strings.worker_info_cancel_confirmation, workInfo.id.toString())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmation = false
                            onCancel()
                        },
                    ) {
                        Text(stringResource(MR.strings.worker_info_cancel_job))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmation = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    @Composable
    private fun SectionText(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            modifier = modifier,
            softWrap = false,
            fontFamily = FontFamily.Monospace,
        )
    }

    @Inject
    @ViewModelKey
    @ContributesIntoMap(AppScope::class)
    class WorkerInfoViewModel(
        private val context: Context,
    ) : ViewModel() {

        private val workManager = context.workManager

        val finished = workManager
            .getWorkInfosFlow(
                WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED),
            )
            .map(::constructString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

        val running = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

        val enqueued = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
            .map(::constructString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

        private val cancelling = mutableSetOf<UUID>()

        fun cancelWork(id: UUID) {
            if (!cancelling.add(id)) return

            viewModelScope.launch {
                try {
                    logcat(LogPriority.INFO) { "Worker info requested cancellation: id=$id" }
                    val requested = cancelActiveWorker(workManager, id)
                    logcat(LogPriority.INFO) {
                        "Worker info cancellation: id=$id, accepted=$requested"
                    }
                    context.toast(
                        if (requested) MR.strings.worker_info_cancel_requested else MR.strings.worker_info_not_active,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Worker info cancellation failed: id=$id" }
                    context.toast(MR.strings.worker_info_cancel_failed)
                } finally {
                    cancelling.remove(id)
                }
            }
        }

        fun constructString(list: List<WorkInfo>) = buildString {
            if (list.isEmpty()) {
                appendLine("-")
            } else {
                list.fastForEach { workInfo ->
                    appendLine("Id: ${workInfo.id}")
                    appendLine("Tags:")
                    workInfo.tags.forEach {
                        appendLine(" - $it")
                    }
                    appendLine("State: ${workInfo.state}")
                    if (workInfo.state == WorkInfo.State.ENQUEUED) {
                        val timestamp = Instant.fromEpochMilliseconds(workInfo.nextScheduleTimeMillis)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .toDateTimestampString(
                                UiPreferences.dateFormat(
                                    context.appGraph.uiPreferences.dateFormat.get(),
                                ),
                            )
                        appendLine("Next scheduled run: $timestamp")
                        appendLine("Attempt #${workInfo.runAttemptCount + 1}")
                    }
                    appendLine()
                }
            }
        }
    }
}
