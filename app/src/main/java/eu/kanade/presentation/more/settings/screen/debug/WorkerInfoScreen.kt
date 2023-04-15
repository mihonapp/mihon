package eu.kanade.presentation.more.settings.screen.debug

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.presentation.core.components.LazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.plus

object WorkerInfoScreen : Screen() {

    const val title = "Worker info"

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { Model(context) }
        val enqueued by screenModel.enqueued.collectAsState()
        val finished by screenModel.finished.collectAsState()
        val running by screenModel.running.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                context.copyToClipboard(title, enqueued + finished + running)
                            },
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        }
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

    private class Model(context: Context) : ScreenModel {
        private val workManager = context.workManager

        val finished = workManager
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED))
            .asFlow()
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val running = workManager
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .asFlow()
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val enqueued = workManager
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
            .asFlow()
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        private fun constructString(list: List<WorkInfo>) = buildString {
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
                    appendLine()
                }
            }
        }
    }
}
