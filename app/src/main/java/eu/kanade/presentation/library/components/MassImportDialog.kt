package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.interactor.MassImportNovels
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MassImportDialog(
    onDismissRequest: () -> Unit,
    onImportComplete: (added: Int, skipped: Int, errored: Int) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<MassImportNovels.ImportProgress?>(null) }
    var result by remember { mutableStateOf<MassImportNovels.ImportResult?>(null) }

    val massImportNovels = remember { Injekt.get<MassImportNovels>() }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting) {
                onDismissRequest()
            } else {
                isCancelled = true
            }
        },
        title = { Text("Mass Import Novels") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
            ) {
                if (result != null) {
                    // Show results
                    ImportResultsView(
                        result = result!!,
                        onCopyErrored = { urls ->
                            context.copyToClipboard("Errored URLs", urls)
                        },
                    )
                } else if (isImporting) {
                    // Show progress
                    ImportProgressView(progress = progress)
                } else {
                    // Show input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("URLs (one per line)") },
                            placeholder = { Text("https://example.com/novel/123") },
                            minLines = 5,
                            maxLines = 10,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )

                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.let {
                                    urlText = it.text
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "Paste from clipboard",
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val urlCount = massImportNovels.parseUrls(urlText).size
                    Text(
                        text = "Found $urlCount URL(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(onClick = onDismissRequest) {
                    Text("Close")
                }
            } else if (isImporting) {
                TextButton(onClick = { isCancelled = true }) {
                    Text("Cancel")
                }
            } else {
                TextButton(
                    onClick = {
                        scope.launch {
                            isImporting = true
                            isCancelled = false
                            val urls = massImportNovels.parseUrls(urlText)

                            val importResult = withContext(Dispatchers.IO) {
                                massImportNovels.import(
                                    urls = urls,
                                    addToLibrary = true,
                                    onProgress = { p -> progress = p },
                                    isCancelled = { isCancelled },
                                )
                            }

                            result = importResult
                            isImporting = false
                            onImportComplete(
                                importResult.added.size,
                                importResult.skipped.size,
                                importResult.errored.size,
                            )
                        }
                    },
                    enabled = urlText.isNotBlank(),
                ) {
                    Text("Import")
                }
            }
        },
        dismissButton = {
            if (result == null && !isImporting) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ImportProgressView(
    progress: MassImportNovels.ImportProgress?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (progress != null) {
            Text(
                text = "Importing ${progress.current}/${progress.total}",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.current.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = progress.currentUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            Text(
                text = progress.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Starting import...")
        }
    }
}

@Composable
private fun ImportResultsView(
    result: MassImportNovels.ImportResult,
    onCopyErrored: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Summary
        Text(
            text = "Import Complete",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ResultStat("Added", result.added.size, MaterialTheme.colorScheme.primary)
            ResultStat("Skipped", result.skipped.size, MaterialTheme.colorScheme.tertiary)
            ResultStat("Errors", result.errored.size, MaterialTheme.colorScheme.error)
        }

        if (result.errored.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Errors:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Group errors by error message
            val groupedErrors = result.errored.groupBy { it.error }

            groupedErrors.forEach { (errorType, errors) ->
                Text(
                    text = "$errorType (${errors.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                errors.take(3).forEach { error ->
                    Text(
                        text = "  • ${error.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (errors.size > 3) {
                    Text(
                        text = "  ...and ${errors.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        onCopyErrored(result.errored.joinToString("\n") { it.url })
                    },
                ) {
                    Text("Copy all URLs")
                }

                TextButton(
                    onClick = {
                        // Copy grouped by error type
                        val grouped = result.errored.groupBy { it.error }
                        val text = grouped.entries.joinToString("\n\n") { (error, urls) ->
                            "=== $error ===\n${urls.joinToString("\n") { it.url }}"
                        }
                        onCopyErrored(text)
                    },
                ) {
                    Text("Copy grouped")
                }
            }
        }
    }
}

@Composable
private fun ResultStat(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
