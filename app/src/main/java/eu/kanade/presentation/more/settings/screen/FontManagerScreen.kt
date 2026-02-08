package eu.kanade.presentation.more.settings.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.data.font.FontDownloadState
import eu.kanade.tachiyomi.data.font.FontInfo
import eu.kanade.tachiyomi.data.font.FontManager
import eu.kanade.tachiyomi.data.font.GoogleFontInfo
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FontManagerScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { FontManagerScreenModel() }
        val state by screenModel.state.collectAsState()
        
        val snackbarHostState = remember { SnackbarHostState() }
        
        var showAddFontSheet by remember { mutableStateOf(false) }
        var showGoogleFontsDialog by remember { mutableStateOf(false) }
        var fontToDelete by remember { mutableStateOf<FontInfo?>(null) }
        
        val fontPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                screenModel.importFont(it)
            }
        }
        
        LaunchedEffect(state.message) {
            state.message?.let { message ->
                snackbarHostState.showSnackbar(message)
                screenModel.clearMessage()
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Font Manager") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddFontSheet = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Font")
                }
            }
        ) { paddingValues ->
            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading fonts...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // System Fonts Section
                    item {
                        Text(
                            text = "System Fonts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    
                    items(state.systemFonts) { font ->
                        FontItem(
                            fontInfo = font,
                            isSelected = font.path == state.selectedFontPath,
                            onClick = { screenModel.selectFont(font) },
                            onDelete = null,
                        )
                    }
                    
                    // Custom Fonts Section
                    if (state.customFonts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Custom Fonts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        
                        items(state.customFonts) { font ->
                            FontItem(
                                fontInfo = font,
                                isSelected = font.path == state.selectedFontPath,
                                onClick = { screenModel.selectFont(font) },
                                onDelete = { fontToDelete = font },
                            )
                        }
                    }
                    
                    // Download Progress
                    state.downloadProgress?.let { progress ->
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Downloading font...",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Add Font Bottom Sheet
        if (showAddFontSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddFontSheet = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Add Font",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Import from device
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddFontSheet = false
                                fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype"))
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Import from Device",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Select a TTF or OTF file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Download from Google Fonts
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddFontSheet = false
                                showGoogleFontsDialog = true
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Download from Google Fonts",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Browse and download free fonts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        
        // Google Fonts Dialog
        if (showGoogleFontsDialog) {
            GoogleFontsDialog(
                googleFonts = state.googleFonts,
                isSearching = state.isSearchingGoogleFonts,
                onSearch = { screenModel.searchGoogleFonts(it) },
                onDownload = { 
                    screenModel.downloadGoogleFont(it.family)
                    showGoogleFontsDialog = false
                },
                onDismiss = { showGoogleFontsDialog = false },
            )
        }
        
        // Delete Confirmation Dialog
        fontToDelete?.let { font ->
            AlertDialog(
                onDismissRequest = { fontToDelete = null },
                title = { Text("Delete Font") },
                text = { Text("Are you sure you want to delete \"${font.name}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.deleteFont(font)
                            fontToDelete = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fontToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun FontItem(
    fontInfo: FontInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fontInfo.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (fontInfo.isCustom) "Custom Font" else "System Font",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleFontsDialog(
    googleFonts: List<GoogleFontInfo>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onDownload: (GoogleFontInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Fonts") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search fonts...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) }),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(googleFonts) { font ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDownload(font) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = font.family,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = font.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

class FontManagerScreenModel(
    private val fontManager: FontManager = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
) : StateScreenModel<FontManagerScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val systemFonts: List<FontInfo> = emptyList(),
        val customFonts: List<FontInfo> = emptyList(),
        val selectedFontPath: String = "",
        val googleFonts: List<GoogleFontInfo> = emptyList(),
        val isSearchingGoogleFonts: Boolean = false,
        val downloadProgress: Int? = null,
        val message: String? = null,
    )

    init {
        loadFonts()
        loadGoogleFonts("")
    }
    
    private fun loadFonts() {
        mutableState.update { it.copy(isLoading = true) }
        
        kotlinx.coroutines.MainScope().launch {
            val systemFonts = fontManager.getSystemFonts()
            val customFonts = fontManager.getInstalledFonts()
            val currentFont = readerPreferences.novelFontFamily().get()
            
            mutableState.update {
                it.copy(
                    isLoading = false,
                    systemFonts = systemFonts,
                    customFonts = customFonts,
                    selectedFontPath = currentFont,
                )
            }
        }
    }
    
    fun selectFont(font: FontInfo) {
        readerPreferences.novelFontFamily().set(font.path)
        mutableState.update { it.copy(selectedFontPath = font.path) }
    }
    
    fun importFont(uri: android.net.Uri) {
        kotlinx.coroutines.MainScope().launch {
            val result = fontManager.importFont(uri)
            result.fold(
                onSuccess = { font ->
                    loadFonts()
                    mutableState.update { it.copy(message = "Font \"${font.name}\" imported successfully") }
                },
                onFailure = { error ->
                    mutableState.update { it.copy(message = "Failed to import font: ${error.message}") }
                }
            )
        }
    }
    
    fun deleteFont(font: FontInfo) {
        kotlinx.coroutines.MainScope().launch {
            val success = fontManager.deleteFont(font)
            if (success) {
                loadFonts()
                // Reset to default font if deleted font was selected
                if (font.path == state.value.selectedFontPath) {
                    readerPreferences.novelFontFamily().set("sans-serif")
                    mutableState.update { it.copy(selectedFontPath = "sans-serif") }
                }
                mutableState.update { it.copy(message = "Font \"${font.name}\" deleted") }
            } else {
                mutableState.update { it.copy(message = "Failed to delete font") }
            }
        }
    }
    
    fun searchGoogleFonts(query: String) {
        mutableState.update { it.copy(isSearchingGoogleFonts = true) }
        
        kotlinx.coroutines.MainScope().launch {
            val fonts = fontManager.searchGoogleFonts(query)
            mutableState.update {
                it.copy(
                    isSearchingGoogleFonts = false,
                    googleFonts = fonts,
                )
            }
        }
    }
    
    private fun loadGoogleFonts(query: String) {
        searchGoogleFonts(query)
    }
    
    fun downloadGoogleFont(fontFamily: String) {
        kotlinx.coroutines.MainScope().launch {
            fontManager.downloadGoogleFont(fontFamily).collect { downloadState ->
                when (downloadState) {
                    is FontDownloadState.Downloading -> {
                        mutableState.update { it.copy(downloadProgress = downloadState.progress) }
                    }
                    is FontDownloadState.Success -> {
                        mutableState.update { it.copy(downloadProgress = null, message = "Font \"${fontFamily}\" downloaded") }
                        loadFonts()
                    }
                    is FontDownloadState.Error -> {
                        mutableState.update { it.copy(downloadProgress = null, message = "Download failed: ${downloadState.message}") }
                    }
                }
            }
        }
    }
    
    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }
}
