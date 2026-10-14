package eu.kanade.presentation.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.LinkedSourceSelectionViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LinkedSourceScanlatorDialog(viewModel: LinkedSourceSelectionViewModel) {
    val pending by viewModel.scanlatorSelection.collectAsState()
    val selection = pending ?: return
    var selected by remember(selection.target.source, selection.target.url) { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = viewModel::dismissScanlators,
        title = { Text(stringResource(MR.strings.linked_source_select_scanlators)) },
        text = {
            Column {
                Text(selection.sourceName)
                Text(selection.target.title)
                when {
                    selection.loading -> CircularProgressIndicator()
                    selection.error != null -> Text(selection.error)
                    else -> {
                        Text(stringResource(MR.strings.linked_source_scanlators_hint))
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(selection.available.toList(), key = { it }) { scanlator ->
                                val checked = scanlator in selected
                                val toggle = { selected = if (checked) selected - scanlator else selected + scanlator }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable(onClick = toggle),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = { toggle() })
                                    Column {
                                        Text(
                                            scanlator.ifBlank {
                                                stringResource(MR.strings.linked_source_unknown_scanlator)
                                            },
                                        )
                                        selection.summaries[scanlator]?.let { summary ->
                                            val count = pluralStringResource(
                                                MR.plurals.manga_num_chapters,
                                                summary.chapterCount,
                                                summary.chapterCount,
                                            )
                                            val first = summary.firstChapter
                                            val last = summary.lastChapter
                                            val range = when {
                                                first == null || last == null -> null
                                                first == last -> stringResource(
                                                    MR.strings.linked_source_scanlator_single_chapter,
                                                    first.toString().removeSuffix(".0"),
                                                )
                                                else -> stringResource(
                                                    MR.strings.linked_source_scanlator_chapter_range,
                                                    first.toString().removeSuffix(".0"),
                                                    last.toString().removeSuffix(".0"),
                                                )
                                            }
                                            Text(
                                                text = listOfNotNull(count, range).joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissScanlators) { Text(stringResource(MR.strings.action_cancel)) }
        },
        confirmButton = {
            if (selection.error != null) {
                TextButton(onClick = viewModel::retryScanlators) { Text(stringResource(MR.strings.action_retry)) }
            } else if (!selection.loading) {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = { viewModel.confirmScanlators(selected) },
                ) { Text(stringResource(MR.strings.action_ok)) }
            }
        },
    )
}
