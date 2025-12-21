package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MassImportDialog(
    onDismissRequest: () -> Unit,
    onImportComplete: (added: Int, skipped: Int, errored: Int) -> Unit,
    initialText: String = "",
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var urlText by remember { mutableStateOf(initialText) }

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var fetchDetails by remember { mutableStateOf(true) }
    var syncChapterList by remember { mutableStateOf(false) }
    var didNotifyComplete by remember { mutableStateOf(false) }

    val massImportNovels = remember { Injekt.get<MassImportNovels>() }
    val progress by massImportNovels.progress.collectAsState()
    val result by massImportNovels.result.collectAsState()

    val getCategories = remember { Injekt.get<GetCategories>() }
    val categories by getCategories.subscribe().collectAsState(initial = emptyList())

    val isImporting = progress?.isRunning == true

    LaunchedEffect(result) {
        val r = result
        if (r == null) {
            didNotifyComplete = false
            return@LaunchedEffect
        }
        if (!didNotifyComplete) {
            didNotifyComplete = true
            onImportComplete(r.added.size, r.skipped.size, r.errored.size)

            // Remove skipped and errored URLs from input box
            val urlsToRemove = mutableSetOf<String>()
            urlsToRemove.addAll(r.skipped.map { it.url })
            urlsToRemove.addAll(r.errored.map { it.url })
            urlsToRemove.addAll(r.prefilterInvalid.map { it.first })
            urlsToRemove.addAll(r.prefilterDuplicates)
            urlsToRemove.addAll(r.prefilterAlreadyInLibrary)

            if (urlsToRemove.isNotEmpty()) {
                val remainingUrls = urlText.split("\n")
                    .filter { line ->
                        val trimmed = line.trim()
                        trimmed.isNotEmpty() && !urlsToRemove.any { url -> trimmed.contains(url) }
                    }
                    .joinToString("\n")
                urlText = remainingUrls
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            // Always allow dismissing, import continues in background
            onDismissRequest()
        },
        title = { Text("Mass Import Novels") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                val currentResult = result
                if (currentResult != null) {
                    // Show results
                    ImportResultsView(
                        result = currentResult,
                        onCopyErrored = { urls ->
                            context.copyToClipboard("Errored URLs", urls)
                        },
                    )
                } else if (isImporting) {
                    // Show progress
                    ImportProgressView(progress = progress)
                } else {
                    // Options
                    val userCategories = remember(categories) {
                        categories
                            .asSequence()
                            .filterNot(Category::isSystemCategory)
                            // Only show categories relevant to novels.
                            // Keep "All" and "Novel" categories; exclude manga-only categories.
                            .filter { it.contentType != Category.CONTENT_TYPE_MANGA }
                            .toList()
                    }
                    val defaultCategoryName = stringResource(MR.strings.default_category)
                    val selectedCategoryName = if (selectedCategoryId == null) {
                        defaultCategoryName
                    } else {
                        userCategories.firstOrNull { it.id == selectedCategoryId }?.visualName ?: defaultCategoryName
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                            value = selectedCategoryName,
                            onValueChange = {},
                            label = { Text("Category") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                )
                            },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        // Transparent click overlay
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { categoryDropdownExpanded = true },
                        )

                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(defaultCategoryName) },
                                onClick = {
                                    selectedCategoryId = null
                                    categoryDropdownExpanded = false
                                },
                            )
                            userCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.visualName) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        categoryDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = syncChapterList,
                            onCheckedChange = { syncChapterList = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sync chapter list during import",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = fetchDetails,
                            onCheckedChange = { fetchDetails = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetch metadata (description) during import",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

                    // URL analysis with prefiltering
                    val scope = rememberCoroutineScope()
                    var analysisResult by remember { mutableStateOf<MassImportNovels.UrlAnalysisResult?>(null) }
                    var isAnalyzing by remember { mutableStateOf(false) }

                    LaunchedEffect(urlText) {
                        // Debounce analysis
                        kotlinx.coroutines.delay(300)
                        if (urlText.isNotBlank()) {
                            isAnalyzing = true
                            analysisResult = massImportNovels.analyzeUrls(urlText)
                            isAnalyzing = false
                        } else {
                            analysisResult = null
                        }
                    }

                    if (isAnalyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Analyzing URLs...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        analysisResult?.let { analysis ->
                            Column {
                                Text(
                                    text = "✓ ${analysis.totalValid} valid URL(s) to import",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (analysis.alreadyInLibrary.isNotEmpty()) {
                                    Text(
                                        text = "○ ${analysis.alreadyInLibrary.size} already in library (will skip)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (analysis.duplicateUrls.isNotEmpty()) {
                                    Text(
                                        text = "○ ${analysis.duplicateUrls.size} duplicate(s) removed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (analysis.invalidUrls.isNotEmpty()) {
                                    Text(
                                        text = "✗ ${analysis.invalidUrls.size} invalid (no source/not URL)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } ?: run {
                            val urlCount = massImportNovels.parseUrls(urlText).size
                            Text(
                                text = "Found $urlCount URL(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val currentResult = result
            if (currentResult != null) {
                Row {
                    TextButton(onClick = {
                        massImportNovels.clear()
                        urlText = ""
                    }) {
                        Text("Start New Import")
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text("Close")
                    }
                }
            } else if (isImporting) {
                TextButton(onClick = { massImportNovels.cancel() }) {
                    Text("Cancel")
                }
            } else {
                TextButton(
                    onClick = {
                        val urls = massImportNovels.parseUrls(urlText)
                        massImportNovels.startImport(
                            urls = urls,
                            addToLibrary = true,
                            categoryId = selectedCategoryId,
                            fetchDetails = fetchDetails,
                            fetchChapters = syncChapterList,
                        )
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
            } else if (isImporting) {
                TextButton(onClick = onDismissRequest) {
                    Text("Hide")
                }
            }
        },
    )
}

@Composable
private fun CategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
    }
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

            if (progress.concurrency > 1) {
                Text(
                    text = "(${progress.activeImports.size} concurrent)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.current.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Show all active imports when concurrent
            if (progress.activeImports.isNotEmpty()) {
                Column {
                    progress.activeImports.take(5).forEach { url ->
                        Text(
                            text = "• ${url.take(50)}${if (url.length > 50) "..." else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (progress.activeImports.size > 5) {
                        Text(
                            text = "... and ${progress.activeImports.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = progress.currentUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

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

        // Prefilter results section
        val totalPrefiltered =
            result.prefilterInvalid.size + result.prefilterDuplicates.size + result.prefilterAlreadyInLibrary.size
        if (totalPrefiltered > 0) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Prefiltered ($totalPrefiltered):",
                style = MaterialTheme.typography.titleSmall,
            )

            if (result.prefilterAlreadyInLibrary.isNotEmpty()) {
                Text(
                    text = "○ ${result.prefilterAlreadyInLibrary.size} already in library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.prefilterDuplicates.isNotEmpty()) {
                Text(
                    text = "○ ${result.prefilterDuplicates.size} duplicate URL(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.prefilterInvalid.isNotEmpty()) {
                Text(
                    text = "○ ${result.prefilterInvalid.size} invalid URL(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
