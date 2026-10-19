package eu.kanade.presentation.more.settings.screen.debug

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.rememberIs24HourSystem
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import java.time.format.DateTimeFormatter
import java.util.Locale
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

        val is24HourSystem = rememberIs24HourSystem()
        // Keyed on the system 12/24h setting, which can change while the app is backgrounded.
        // Date/time prefs only change in Appearance, which recreates this screen on return.
        LaunchedEffect(is24HourSystem) { viewModel.onSystemTimeFormatChanged(is24HourSystem) }

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
                                        context.copyToClipboard(TITLE, enqueued + finished + running)
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
                item { SectionText(text = running) }
            }
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
    private fun SectionText(text: String) {
        Text(
            text = text,
            softWrap = false,
            fontFamily = FontFamily.Monospace,
        )
    }

    @Inject
    @ViewModelKey
    @ContributesIntoMap(AppScope::class)
    class WorkerInfoViewModel(
        context: Context,
    ) : ViewModel() {

        private val workManager = context.workManager
        private val dateFormatPref = context.appGraph.uiPreferences.dateFormat
        private val timeFormatPref = context.appGraph.uiPreferences.timeFormat
        private val is24HourSystem = MutableStateFlow(DateFormat.is24HourFormat(context))

        private val dateTimeFormatter = combine(
            dateFormatPref.changes(),
            timeFormatPref.changes(),
            is24HourSystem,
        ) { date, time, is24 ->
            UiPreferences.dateTimeFormat(date, time, Locale.getDefault(), is24)
        }

        fun onSystemTimeFormatChanged(value: Boolean) {
            is24HourSystem.value = value
        }

        val finished = combine(
            workManager.getWorkInfosFlow(
                WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED),
            ),
            dateTimeFormatter,
            ::constructString,
        )
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

        val running = combine(
            workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.RUNNING)),
            dateTimeFormatter,
            ::constructString,
        )
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

        val enqueued = combine(
            workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED)),
            dateTimeFormatter,
            ::constructString,
        )
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

        private fun constructString(
            list: List<WorkInfo>,
            dateTimeFormatter: DateTimeFormatter,
        ) = buildString {
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
                            .toDateTimestampString(dateTimeFormatter)
                        appendLine("Next scheduled run: $timestamp")
                        appendLine("Attempt #${workInfo.runAttemptCount + 1}")
                    }
                    appendLine()
                }
            }
        }
    }
}
