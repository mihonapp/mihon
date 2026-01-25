package eu.kanade.presentation.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.tachiyomi.util.backup.BackupConstants
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Custom folder/file picker dialog for secure environment compatibility.
 *
 * This dialog provides a file browser that works within secure environment restrictions
 * where the Storage Access Framework (SAF) cannot access files. It supports both
 * folder selection and backup file selection modes.
 *
 * @param initialPath Starting directory path
 * @param onDismiss Callback invoked when dialog is dismissed
 * @param onFolderSelected Callback invoked when a folder is selected (FOLDER mode)
 * @param mode Picker mode: FOLDER for directory selection, FILE for backup file selection
 * @param onFileSelected Optional callback invoked when a file is selected (FILE mode)
 */
@Composable
fun FolderPickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit,
    mode: PickerMode = PickerMode.FOLDER,
    onFileSelected: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Capture strings at composable level for use in callbacks
    val errorCreateFolder = stringResource(MR.strings.folder_picker_error_create)
    val errorAccessDirectory = stringResource(MR.strings.folder_picker_error_access)
    val errorPermissionRequired = stringResource(MR.strings.folder_picker_permission_required)

    // Remember date formatter - DateTimeFormatter is thread-safe unlike SimpleDateFormat
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(BackupConstants.DATE_FORMAT_PATTERN)
            .withZone(ZoneId.systemDefault())
    }

    // File system navigator for testable business logic
    val navigator = remember { FileSystemNavigator() }

    var currentPath by remember { mutableStateOf(initialPath) }
    var items by remember { mutableStateOf<List<PickerItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var fileListRefreshCounter by remember { mutableStateOf(0) }
    var pendingFolderName by remember { mutableStateOf<String?>(null) }
    var pendingFolderPath by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    /**
     * Helper function to create a folder and select it, or show error message.
     * Thread-safe: prevents concurrent folder creation operations.
     */
    fun createFolderAndSelect(parentPath: String, folderName: String): Boolean {
        if (isProcessing) return false
        isProcessing = true

        val result = navigator.createFolder(parentPath, folderName)
            .onSuccess { folder ->
                onFolderSelected(folder.absolutePath)
                onDismiss()
            }
            .onFailure { error ->
                errorMessage = errorCreateFolder
            }

        isProcessing = false
        return result.isSuccess
    }

    // Check permission (dynamically updated)
    var hasPermission by remember {
        mutableStateOf(navigator.hasAllFilesPermission())
    }

    // Refresh when app comes back to foreground (after granting permission)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hadPermission = hasPermission
                // Update permission status
                hasPermission = navigator.hasAllFilesPermission()

                // If permission was just granted and we have a pending folder, create it
                if (!hadPermission && hasPermission && pendingFolderName != null && pendingFolderPath != null) {
                    createFolderAndSelect(pendingFolderPath!!, pendingFolderName!!)
                    // Clear pending folder
                    pendingFolderName = null
                    pendingFolderPath = null
                } else {
                    // Just refresh file list
                    fileListRefreshCounter++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load folders (and files if needed) when path changes or refresh is triggered
    LaunchedEffect(currentPath, fileListRefreshCounter) {
        navigator.listDirectory(currentPath, mode)
            .onSuccess { filesList ->
                items = filesList
                errorMessage = null
            }
            .onFailure { error ->
                errorMessage = errorAccessDirectory
                items = emptyList()
            }
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(MR.strings.folder_picker_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(MR.strings.folder_picker_folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            // Check if we have MANAGE_EXTERNAL_STORAGE permission on Android 11+
                            val currentHasPermission = navigator.hasAllFilesPermission()

                            if (!currentHasPermission) {
                                // Save folder info for later creation
                                pendingFolderName = newFolderName
                                pendingFolderPath = currentPath
                                showCreateFolderDialog = false
                                errorMessage = errorPermissionRequired
                                // Request permission
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                                context.startActivity(intent)
                            } else {
                                // Try to create folder immediately
                                createFolderAndSelect(currentPath, newFolderName)
                                showCreateFolderDialog = false
                            }
                        } else {
                            showCreateFolderDialog = false
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button (go to parent directory)
                    val parentPath = navigator.getParentPath(currentPath)
                    if (parentPath != null) {
                        IconButton(onClick = { currentPath = parentPath }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(MR.strings.folder_picker_go_up),
                            )
                        }
                    }

                    Text(
                        text = if (mode == PickerMode.FILE) {
                            stringResource(MR.strings.folder_picker_title_file)
                        } else {
                            stringResource(MR.strings.folder_picker_title_folder)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                // Current path
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Show permission warning if needed
                if (!hasPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.folder_picker_permission_required),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                                context.startActivity(intent)
                            },
                        ) {
                            Text(stringResource(MR.strings.folder_picker_grant_permission))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (items.isEmpty()) {
                    Text(
                        text = if (mode == PickerMode.FILE) {
                            stringResource(MR.strings.folder_picker_no_files)
                        } else {
                            stringResource(MR.strings.folder_picker_no_folders)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        items(items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Prevent concurrent operations (double-click protection)
                                        if (isProcessing) return@clickable
                                        isProcessing = true

                                        try {
                                            // Validate file still exists before using
                                            if (item.file.exists()) {
                                                if (item.type == PickerItemType.FOLDER) {
                                                    currentPath = item.file.absolutePath
                                                } else {
                                                    onFileSelected?.invoke(item.file.absolutePath)
                                                }
                                            } else {
                                                errorMessage = errorAccessDirectory
                                                fileListRefreshCounter++ // Refresh list
                                            }
                                        } catch (e: SecurityException) {
                                            errorMessage = errorAccessDirectory
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (item.type == PickerItemType.FOLDER) {
                                        Icons.Default.Folder
                                    } else {
                                        Icons.AutoMirrored.Filled.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = if (item.type == PickerItemType.FOLDER) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.file.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )

                                        val detailText = remember(item.file, item.type) {
                                        try {
                                            if (item.type == PickerItemType.FILE) {
                                                val timestamp = item.file.lastModified()
                                                val lastModified = dateFormatter.format(
                                                    Instant.ofEpochMilli(timestamp),
                                                )
                                                val size = android.text.format.Formatter.formatFileSize(
                                                    context,
                                                    item.file.length(),
                                                )
                                                "$lastModified • $size"
                                            } else {
                                                val itemCount = item.file.listFiles()?.size ?: 0
                                                if (itemCount == 1) "1 item" else "$itemCount items"
                                            }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                                    detailText?.let { text ->
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == PickerMode.FOLDER) {
                Row {
                    TextButton(
                        onClick = { showCreateFolderDialog = true },
                    ) {
                        Text(stringResource(MR.strings.folder_picker_create_folder))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onFolderSelected(currentPath)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(MR.strings.folder_picker_select_this_folder))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

enum class PickerMode {
    FOLDER,
    FILE,
}

internal data class PickerItem(
    val file: File,
    val type: PickerItemType,
)

internal enum class PickerItemType {
    FOLDER,
    FILE,
}
