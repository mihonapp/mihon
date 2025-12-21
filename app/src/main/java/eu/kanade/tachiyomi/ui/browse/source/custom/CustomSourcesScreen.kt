package eu.kanade.tachiyomi.ui.browse.source.custom

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.source.custom.CustomNovelSource
import eu.kanade.tachiyomi.source.custom.CustomSourceConfig
import eu.kanade.tachiyomi.source.custom.CustomSourceManager
import eu.kanade.tachiyomi.source.custom.CustomSourceTemplates
import eu.kanade.tachiyomi.source.custom.SourceTestResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * Screen for managing custom novel sources
 */
class CustomSourcesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CustomSourcesScreenModel() }
        val sources by screenModel.customSources.collectAsState(initial = emptyList())
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var sourceToDelete by remember { mutableStateOf<Long?>(null) }
        var showImportDialog by remember { mutableStateOf(false) }

        // File picker for import
        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val json = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                        inputStream?.close()

                        val result = screenModel.importSource(json)
                        result.fold(
                            onSuccess = {
                                snackbarHostState.showSnackbar("Source imported successfully!")
                            },
                            onFailure = { e ->
                                snackbarHostState.showSnackbar("Import failed: ${e.message}")
                            },
                        )
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error reading file: ${e.message}")
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Custom Sources") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Import button
                        IconButton(onClick = { importLauncher.launch("application/json") }) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = "Import Source")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Source")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            if (sources.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onCreateClick = { showCreateDialog = true },
                    onImportClick = { importLauncher.launch("application/json") },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        CustomSourceCard(
                            name = source.name,
                            baseUrl = source.baseUrl,
                            lang = source.lang,
                            onEdit = {
                                navigator.push(CustomSourceEditorScreen(source.id))
                            },
                            onTest = {
                                navigator.push(CustomSourceTestScreen(source.id))
                            },
                            onDelete = { sourceToDelete = source.id },
                            onExport = {
                                // Export and share
                                scope.launch {
                                    val json = screenModel.exportSource(source.id)
                                    if (json != null) {
                                        try {
                                            // Create temp file
                                            val exportDir = File(context.cacheDir, "exports")
                                            exportDir.mkdirs()
                                            val exportFile = File(exportDir, "${source.name.replace(" ", "_")}.json")
                                            exportFile.writeText(json)

                                            // Get content URI via FileProvider
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                exportFile,
                                            )

                                            // Share intent
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "Custom Source: ${source.name}")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }

                                            context.startActivity(Intent.createChooser(shareIntent, "Export Source"))
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Export failed: ${e.message}")
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Could not export source")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        // Create dialog
        if (showCreateDialog) {
            CreateSourceDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, baseUrl, template ->
                    navigator.push(
                        CustomSourceEditorScreen(
                            sourceId = null,
                            templateName = template,
                            initialName = name,
                            initialBaseUrl = baseUrl,
                        ),
                    )
                    showCreateDialog = false
                },
                onUseWebViewSelector = { baseUrl ->
                    navigator.push(eu.kanade.tachiyomi.ui.customsource.ElementSelectorVoyagerScreen(baseUrl))
                    showCreateDialog = false
                },
            )
        }

        // Delete confirmation
        sourceToDelete?.let { id ->
            AlertDialog(
                onDismissRequest = { sourceToDelete = null },
                title = { Text("Delete Source") },
                text = { Text("Are you sure you want to delete this custom source?") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.deleteSource(id)
                        sourceToDelete = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sourceToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No Custom Sources",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a custom source to scrape novels from any website",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onCreateClick) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Source")
            }
            Button(onClick = onImportClick) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import")
            }
        }
    }
}

@Composable
private fun CustomSourceCard(
    name: String,
    baseUrl: String,
    lang: String,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Language: $lang",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onTest) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Test")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSourceDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, baseUrl: String, template: String) -> Unit,
    onUseWebViewSelector: (baseUrl: String) -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://") }
    var selectedTemplate by remember { mutableStateOf("Generic") }
    var expanded by remember { mutableStateOf(false) }
    var useWebView by remember { mutableStateOf(false) }

    val templates = remember { CustomSourceTemplates.getAll().keys.toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Source") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://example.com") },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // WebView selector option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = useWebView,
                        onCheckedChange = { useWebView = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Use WebView Element Selector",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Guided wizard to select elements visually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Template selection (only if not using WebView)
                if (!useWebView) {
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Template") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = { Text(template) },
                                    onClick = {
                                        selectedTemplate = template
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (useWebView) {
                        onUseWebViewSelector(baseUrl)
                    } else {
                        onCreate(name, baseUrl, selectedTemplate)
                    }
                },
                enabled = (useWebView && baseUrl.startsWith("http")) ||
                    (!useWebView && name.isNotBlank() && baseUrl.startsWith("http")),
            ) {
                Text(if (useWebView) "Open WebView Wizard" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Screen for testing a custom source
 */
class CustomSourceTestScreen(
    private val sourceId: Long,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CustomSourcesScreenModel() }
        val scope = rememberCoroutineScope()

        var testResult by remember { mutableStateOf<SourceTestResult?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Test Source") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Testing source...")
                } else if (testResult == null) {
                    Button(onClick = {
                        isLoading = true
                        scope.launch {
                            testResult = screenModel.testSource(sourceId)
                            isLoading = false
                        }
                    }) {
                        Text("Run Test")
                    }
                } else {
                    TestResultView(result = testResult!!)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        testResult = null
                    }) {
                        Text("Test Again")
                    }
                }
            }
        }
    }
}

@Composable
private fun TestResultView(result: SourceTestResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = result.sourceName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (result.overallSuccess) "✓ All tests passed" else "✗ Some tests failed",
            color = if (result.overallSuccess) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        result.steps.forEach { (stepName, stepResult) ->
            val containerColor = if (stepResult.success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val contentColor = if (stepResult.success) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stepName.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = stepResult.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                    stepResult.data?.forEach { (key, value) ->
                        Text(
                            text = "$key: $value",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen for editing a custom source configuration
 */
class CustomSourceEditorScreen(
    private val sourceId: Long?,
    private val templateName: String? = null,
    private val initialName: String? = null,
    private val initialBaseUrl: String? = null,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CustomSourcesScreenModel() }
        val scope = rememberCoroutineScope()

        // Load existing config or create from template
        val initialConfig = remember {
            if (sourceId != null) {
                screenModel.getSourceConfig(sourceId)
            } else if (templateName != null && initialName != null && initialBaseUrl != null) {
                screenModel.createFromTemplate(templateName, initialName, initialBaseUrl)
            } else {
                null
            }
        }

        // State for form fields
        var name by remember { mutableStateOf(initialConfig?.name ?: "") }
        var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: "https://") }
        var popularUrl by remember { mutableStateOf(initialConfig?.popularUrl ?: "") }
        var latestUrl by remember { mutableStateOf(initialConfig?.latestUrl ?: "") }
        var searchUrl by remember { mutableStateOf(initialConfig?.searchUrl ?: "") }

        // New fields for source type and cloudflare
        var useCloudflare by remember { mutableStateOf(initialConfig?.useCloudflare ?: true) }
        var reverseChapters by remember { mutableStateOf(initialConfig?.reverseChapters ?: false) }
        var useNewChapterEndpoint by remember { mutableStateOf(initialConfig?.useNewChapterEndpoint ?: false) }
        var postSearch by remember { mutableStateOf(initialConfig?.postSearch ?: false) }

        // Selectors
        var popularListSelector by remember {
            mutableStateOf(initialConfig?.selectors?.popular?.list ?: "")
        }
        var popularTitleSelector by remember {
            mutableStateOf(initialConfig?.selectors?.popular?.title ?: "")
        }
        var popularCoverSelector by remember {
            mutableStateOf(initialConfig?.selectors?.popular?.cover ?: "")
        }
        var detailsTitleSelector by remember {
            mutableStateOf(initialConfig?.selectors?.details?.title ?: "")
        }
        var detailsDescriptionSelector by remember {
            mutableStateOf(initialConfig?.selectors?.details?.description ?: "")
        }
        var chaptersListSelector by remember {
            mutableStateOf(initialConfig?.selectors?.chapters?.list ?: "")
        }
        var contentPrimarySelector by remember {
            mutableStateOf(initialConfig?.selectors?.content?.primary ?: "")
        }

        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (sourceId != null) "Edit Source" else "Create Source") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Basic Info Section
                Text(
                    text = "Basic Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name *") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL *") },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Source Options Section
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Source Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Cloudflare option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = useCloudflare,
                        onCheckedChange = { useCloudflare = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Use Cloudflare Bypass", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Enable for sites protected by Cloudflare",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Reverse chapters option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = reverseChapters,
                        onCheckedChange = { reverseChapters = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Reverse Chapter Order", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Enable if chapters are listed newest first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // New chapter endpoint option (for Madara sites)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = useNewChapterEndpoint,
                        onCheckedChange = { useNewChapterEndpoint = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Use New Chapter Endpoint", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "For Madara sites using /ajax/chapters/ instead of admin-ajax.php",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // POST search option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = postSearch,
                        onCheckedChange = { postSearch = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Use POST for Search", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Enable if the site uses POST requests for search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // URLs Section
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "URL Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Use {baseUrl}, {page}, {query} as placeholders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = popularUrl,
                    onValueChange = { popularUrl = it },
                    label = { Text("Popular URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("{baseUrl}/popular?page={page}") },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = latestUrl,
                    onValueChange = { latestUrl = it },
                    label = { Text("Latest URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchUrl,
                    onValueChange = { searchUrl = it },
                    label = { Text("Search URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("{baseUrl}/search?q={query}&page={page}") },
                )

                // Selectors Section
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CSS Selectors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Use browser DevTools to find CSS selectors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Popular/List selectors
                Text("Novel List (Popular/Latest)", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = popularListSelector,
                    onValueChange = { popularListSelector = it },
                    label = { Text("List Item Selector *") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(".novel-item, .book-card") },
                )
                OutlinedTextField(
                    value = popularTitleSelector,
                    onValueChange = { popularTitleSelector = it },
                    label = { Text("Title Selector") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = popularCoverSelector,
                    onValueChange = { popularCoverSelector = it },
                    label = { Text("Cover Image Selector") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Details selectors
                Text("Novel Details", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = detailsTitleSelector,
                    onValueChange = { detailsTitleSelector = it },
                    label = { Text("Title Selector *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detailsDescriptionSelector,
                    onValueChange = { detailsDescriptionSelector = it },
                    label = { Text("Description Selector") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chapter selectors
                Text("Chapters", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = chaptersListSelector,
                    onValueChange = { chaptersListSelector = it },
                    label = { Text("Chapter List Selector *") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Content selector
                Text("Chapter Content", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = contentPrimarySelector,
                    onValueChange = { contentPrimarySelector = it },
                    label = { Text("Content Selector *") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(".chapter-content, #content") },
                )

                // Error message
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Save button
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            errorMessage = null

                            val config = buildConfig(
                                name, baseUrl, popularUrl, latestUrl, searchUrl,
                                popularListSelector, popularTitleSelector, popularCoverSelector,
                                detailsTitleSelector, detailsDescriptionSelector,
                                chaptersListSelector, contentPrimarySelector,
                                sourceId, useCloudflare, reverseChapters, useNewChapterEndpoint, postSearch,
                            )

                            val result = if (sourceId != null) {
                                screenModel.updateSource(sourceId, config)
                            } else {
                                screenModel.createSource(config)
                            }

                            isSaving = false

                            result.fold(
                                onSuccess = { navigator.pop() },
                                onFailure = { errorMessage = it.message },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && name.isNotBlank() && baseUrl.isNotBlank() &&
                        popularUrl.isNotBlank() && searchUrl.isNotBlank() &&
                        popularListSelector.isNotBlank() && detailsTitleSelector.isNotBlank() &&
                        chaptersListSelector.isNotBlank() && contentPrimarySelector.isNotBlank(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    } else {
                        Text("Save Source")
                    }
                }
            }
        }
    }

    private fun buildConfig(
        name: String,
        baseUrl: String,
        popularUrl: String,
        latestUrl: String,
        searchUrl: String,
        popularListSelector: String,
        popularTitleSelector: String,
        popularCoverSelector: String,
        detailsTitleSelector: String,
        detailsDescriptionSelector: String,
        chaptersListSelector: String,
        contentPrimarySelector: String,
        existingId: Long?,
        useCloudflare: Boolean,
        reverseChapters: Boolean,
        useNewChapterEndpoint: Boolean,
        postSearch: Boolean,
    ): CustomSourceConfig {
        return CustomSourceConfig(
            name = name,
            baseUrl = baseUrl.trimEnd('/'),
            id = existingId,
            popularUrl = popularUrl,
            latestUrl = latestUrl.ifBlank { null },
            searchUrl = searchUrl,
            selectors = eu.kanade.tachiyomi.source.custom.SourceSelectors(
                popular = eu.kanade.tachiyomi.source.custom.MangaListSelectors(
                    list = popularListSelector,
                    title = popularTitleSelector.ifBlank { null },
                    cover = popularCoverSelector.ifBlank { null },
                ),
                details = eu.kanade.tachiyomi.source.custom.DetailSelectors(
                    title = detailsTitleSelector,
                    description = detailsDescriptionSelector.ifBlank { null },
                ),
                chapters = eu.kanade.tachiyomi.source.custom.ChapterSelectors(
                    list = chaptersListSelector,
                ),
                content = eu.kanade.tachiyomi.source.custom.ContentSelectors(
                    primary = contentPrimarySelector,
                ),
            ),
            useCloudflare = useCloudflare,
            reverseChapters = reverseChapters,
            useNewChapterEndpoint = useNewChapterEndpoint,
            postSearch = postSearch,
        )
    }
}
