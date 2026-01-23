package eu.kanade.presentation.library.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.interactor.MassImportNovels
import eu.kanade.tachiyomi.data.massimport.MassImportJob
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
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

    // Accumulate URLs from initial text and any new additions
    var pendingUrls by remember { mutableStateOf(initialText) }
    var urlText by remember { mutableStateOf("") }

    // File picker launcher for reading URLs from files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() } ?: return@let
                    
                    // Add file content to pending URLs
                    pendingUrls = if (pendingUrls.isBlank()) {
                        content
                    } else {
                        "$pendingUrls\n$content"
                    }
                    context.toast("Added ${content.lines().filter { it.isNotBlank() }.size} URLs from file")
                } catch (e: Exception) {
                    context.toast("Error reading file: ${e.message}")
                }
            }
        }
    )

    // Merge initialText into pendingUrls when dialog opens
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            pendingUrls = if (pendingUrls.isBlank()) {
                initialText
            } else {
                "$pendingUrls\n$initialText"
            }
        }
    }

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var fetchDetails by remember { mutableStateOf(true) }
    var syncChapterList by remember { mutableStateOf(false) }

    val massImportNovels = remember { Injekt.get<MassImportNovels>() }
    
    val queue by MassImportJob.sharedQueue.collectAsState()
    
    val getCategories = remember { Injekt.get<GetCategories>() }
    val categories by getCategories.subscribe().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 900.dp),
        title = { Text("Mass Import Novels") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Queue Section
                if (queue.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Queue (${queue.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row {
                            // Clear completed button
                            val hasCompleted = queue.any { it.status == MassImportJob.BatchStatus.Completed || it.status == MassImportJob.BatchStatus.Cancelled }
                            if (hasCompleted) {
                                TextButton(
                                    onClick = { MassImportJob.clearCompleted() },
                                ) {
                                    Text("Clear Done", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            // Cancel all button
                            val hasActive = queue.any { it.status == MassImportJob.BatchStatus.Pending || it.status == MassImportJob.BatchStatus.Running }
                            if (hasActive) {
                                TextButton(
                                    onClick = { MassImportJob.stop(context) },
                                ) {
                                    Text("Cancel All", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(queue.reversed()) { batch ->
                            BatchItem(
                                batch = batch,
                                onCancel = { MassImportJob.cancelBatch(context, batch.id) },
                                onCopyUrls = {
                                    context.copyToClipboard("URLs", batch.urls.joinToString("\n"))
                                    context.toast("Copied ${batch.urls.size} URLs")
                                },
                                onCopyErrors = {
                                    val errors = batch.erroredUrls.joinToString("\n")
                                    if (errors.isNotBlank()) {
                                        context.copyToClipboard("Errors", errors)
                                        context.toast("Copied ${batch.erroredUrls.size} error URLs")
                                    }
                                },
                                onRemove = { MassImportJob.removeBatch(batch.id) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                }

                // Add New Section
                Text(
                    text = "Add New Batch",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Category Selection
                val userCategories = remember(categories) {
                    categories
                        .asSequence()
                        .filterNot(Category::isSystemCategory)
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
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
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
                        text = "Sync chapter list",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

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
                        text = "Fetch metadata",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pending URLs section (URLs waiting to be queued)
                if (pendingUrls.isNotBlank()) {
                    val pendingCount = pendingUrls.lines().filter { it.isNotBlank() }.size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$pendingCount URL(s) pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { pendingUrls = "" }) {
                            Text("Clear")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // URL Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Add more URLs (one per line)") },
                        placeholder = { Text("https://example.com/novel/123") },
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )

                    Column {
                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.let {
                                    urlText = it.text
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "Paste",
                            )
                        }
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("text/*"))
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = "Load from file",
                            )
                        }
                        if (urlText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    // Add to pending URLs
                                    pendingUrls = if (pendingUrls.isBlank()) {
                                        urlText
                                    } else {
                                        "$pendingUrls\n$urlText"
                                    }
                                    urlText = ""
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = "Add to pending",
                                )
                            }
                        }
                    }
                }

                // Analysis
                val scope = rememberCoroutineScope()
                var analysisResult by remember { mutableStateOf<MassImportNovels.UrlAnalysisResult?>(null) }
                var isAnalyzing by remember { mutableStateOf(false) }

                LaunchedEffect(urlText) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyzing...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    analysisResult?.let { analysis ->
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "✓ ${analysis.totalValid} valid URL(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (analysis.alreadyInLibrary.isNotEmpty()) {
                                Text(
                                    text = "○ ${analysis.alreadyInLibrary.size} already in library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val allUrlsText = listOf(pendingUrls, urlText)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            val hasUrls = allUrlsText.isNotBlank()
            
            TextButton(
                onClick = {
                    val urls = massImportNovels.parseUrls(allUrlsText)
                    // Deduplicate URLs before adding to queue
                    val uniqueUrls = urls.toSet().toList()
                    MassImportJob.start(
                        context = context,
                        urls = uniqueUrls,
                        addToLibrary = true,
                        categoryId = selectedCategoryId ?: 0L,
                        fetchChapters = syncChapterList,
                    )
                    urlText = ""
                    pendingUrls = ""
                },
                enabled = hasUrls,
            ) {
                Text("Add to Queue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun BatchItem(
    batch: MassImportJob.Batch,
    onCancel: () -> Unit,
    onCopyUrls: () -> Unit,
    onCopyErrors: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row with status and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (batch.status) {
                            MassImportJob.BatchStatus.Pending -> "⏳ Pending"
                            MassImportJob.BatchStatus.Running -> "▶ Running"
                            MassImportJob.BatchStatus.Completed -> "✓ Completed"
                            MassImportJob.BatchStatus.Cancelled -> "✕ Cancelled"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (batch.status) {
                            MassImportJob.BatchStatus.Running -> MaterialTheme.colorScheme.primary
                            MassImportJob.BatchStatus.Completed -> MaterialTheme.colorScheme.tertiary
                            MassImportJob.BatchStatus.Cancelled -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${batch.progress}/${batch.total}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                // Action buttons
                Row {
                    // Expand/collapse details
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = "Details",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    // Cancel button (only for pending/running)
                    if (batch.status == MassImportJob.BatchStatus.Pending || batch.status == MassImportJob.BatchStatus.Running) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cancel,
                                contentDescription = "Cancel",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    // Remove button (only for completed/cancelled)
                    if (batch.status == MassImportJob.BatchStatus.Completed || batch.status == MassImportJob.BatchStatus.Cancelled) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Progress bar for running
            if (batch.status == MassImportJob.BatchStatus.Running) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { batch.progress.toFloat() / batch.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            
            // Summary for completed
            if (batch.status == MassImportJob.BatchStatus.Completed || batch.status == MassImportJob.BatchStatus.Cancelled) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✓${batch.added}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "○${batch.skipped}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (batch.errored > 0) {
                        Text(
                            text = "✕${batch.errored}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onCopyUrls,
                        label = { Text("Copy All URLs", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    
                    if (batch.errored > 0) {
                        AssistChip(
                            onClick = onCopyErrors,
                            label = { Text("Copy Errors", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.error,
                                leadingIconContentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                
                // Show first few URLs as preview
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "URLs (${batch.urls.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                batch.urls.take(3).forEach { url ->
                    Text(
                        text = url.take(50) + if (url.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (batch.urls.size > 3) {
                    Text(
                        text = "... and ${batch.urls.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Show errors if any
                if (batch.erroredUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Errors (${batch.erroredUrls.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    batch.erroredUrls.take(3).forEach { url ->
                        Text(
                            text = url.take(50) + if (url.length > 50) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                    if (batch.erroredUrls.size > 3) {
                        Text(
                            text = "... and ${batch.erroredUrls.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
