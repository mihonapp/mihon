package eu.kanade.presentation.library.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hippo.unifile.UniFile
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.archive.EpubReader
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.metadata.fillMetadata
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

data class EpubFileInfo(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val author: String?,
    val description: String?,
    val coverUri: Uri? = null,
)

data class ImportProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String,
    val isRunning: Boolean,
)

data class ImportResult(
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String>,
)

@Composable
fun ImportEpubDialog(
    onDismissRequest: () -> Unit,
    onImportComplete: (success: Int, errors: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles = remember { mutableStateListOf<EpubFileInfo>() }
    var customTitle by remember { mutableStateOf("") }
    var combineAsOneNovel by remember { mutableStateOf(false) }

    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    val storageManager = remember { Injekt.get<StorageManager>() }

    // File picker for multiple EPUB files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            isLoadingFiles = true
            scope.launch {
                val fileInfos = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri) ?: return@mapNotNull null
                            val fileName = getFileNameFromUri(context, uri) ?: "unknown.epub"

                            // Create a temporary file to read EPUB metadata
                            val tempFile = File.createTempFile("epub_import_", ".epub", context.cacheDir)
                            tempFile.outputStream().use { out ->
                                inputStream.copyTo(out)
                            }
                            inputStream.close()

                            val uniFile = UniFile.fromFile(tempFile)
                            val epubReader = EpubReader(uniFile!!.archiveReader(context))

                            val manga = SManga.create()
                            val chapter = SChapter.create()
                            epubReader.fillMetadata(manga, chapter)

                            // Extract title from chapter (fillMetadata puts it there) or use filename
                            val title = if (chapter.name.isNotBlank()) chapter.name else fileName.removeSuffix(".epub")
                            // Set manga title from chapter name for consistency
                            manga.title = title

                            // Extract cover image from EPUB
                            var coverUri: Uri? = null
                            try {
                                val coverHref = manga.thumbnail_url
                                val coverExt = coverHref?.substringAfterLast('.', "png")?.takeIf { it.length <= 4 }
                                    ?: "png"
                                val coverStream = if (!coverHref.isNullOrBlank()) {
                                    if (coverHref.startsWith("http://") || coverHref.startsWith("https://")) {
                                        try {
                                            val connection = URL(coverHref).openConnection() as HttpURLConnection
                                            connection.connectTimeout = 10_000
                                            connection.readTimeout = 10_000
                                            val bytes = connection.inputStream.use { it.readBytes() }
                                            ByteArrayInputStream(bytes)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } else {
                                        epubReader.getInputStream(coverHref)
                                    }
                                } else {
                                    // Fallback: try common cover filenames
                                    val commonNames = listOf(
                                        "cover.jpg", "cover.jpeg", "cover.png",
                                        "OEBPS/cover.jpg", "OEBPS/cover.jpeg", "OEBPS/cover.png",
                                        "Images/cover.jpg", "Images/cover.jpeg", "Images/cover.png"
                                    )
                                    var stream: java.io.InputStream? = null
                                    for (name in commonNames) {
                                        stream = epubReader.getInputStream(name)
                                        if (stream != null) break
                                    }
                                    stream
                                }

                                coverStream?.use { stream ->
                                    val coverFile = File.createTempFile("epub_cover_", ".${coverExt}", context.cacheDir)
                                    coverFile.outputStream().use { out ->
                                        stream.copyTo(out)
                                    }
                                    coverUri = UniFile.fromFile(coverFile)?.uri
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG, e) { "Could not extract cover from EPUB" }
                            }

                            epubReader.close()

                            tempFile.delete()

                            EpubFileInfo(
                                uri = uri,
                                fileName = fileName,
                                title = title,
                                author = manga.author,
                                description = manga.description,
                                coverUri = coverUri,
                            )
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Failed to parse EPUB: $uri" }
                            null
                        }
                    }
                }
                selectedFiles.clear()
                selectedFiles.addAll(fileInfos)
                if (fileInfos.size == 1) {
                    customTitle = fileInfos.first().title
                }
                isLoadingFiles = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (importProgress?.isRunning != true) {
                onDismissRequest()
            }
        },
        title = { Text("Import EPUB") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    importResult != null -> {
                        ImportResultView(importResult!!)
                    }
                    importProgress != null -> {
                        ImportProgressView(importProgress!!)
                    }
                    else -> {
                        // Selection UI
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select EPUB files")
                        }

                        if (selectedFiles.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))

                            // Show selected files
                            Text(
                                text = "Selected files:",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            selectedFiles.forEach { file ->
                                Text(
                                    text = "• ${file.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Custom title (for single file or combined)
                            if (selectedFiles.size == 1 || combineAsOneNovel) {
                                OutlinedTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    label = { Text("Novel title") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            // Combine option for multiple files
                            if (selectedFiles.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { combineAsOneNovel = !combineAsOneNovel },
                                ) {
                                    Checkbox(
                                        checked = combineAsOneNovel,
                                        onCheckedChange = { combineAsOneNovel = it },
                                    )
                                    Text("Combine all files into one novel")
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                importResult != null -> {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(MR.strings.action_close))
                    }
                }
                importProgress != null -> {
                    // No button while importing
                }
                else -> {
                    TextButton(
                        onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                scope.launch {
                                    importProgress = ImportProgress(0, selectedFiles.size, "", true)
                                    val result = importEpubFiles(
                                        context = context,
                                        files = selectedFiles.toList(),
                                        customTitle = customTitle.ifBlank { null },
                                        combineAsOne = combineAsOneNovel,
                                        categoryId = null,
                                        storageManager = storageManager,
                                        onProgress = { current, total, fileName ->
                                            importProgress = ImportProgress(current, total, fileName, true)
                                        },
                                    )
                                    importProgress = null
                                    importResult = result
                                    onImportComplete(result.successCount, result.errorCount)
                                }
                            }
                        },
                        enabled = selectedFiles.isNotEmpty(),
                    ) {
                        Text("Import")
                    }
                }
            }
        },
        dismissButton = {
            if (importProgress?.isRunning != true && importResult == null) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ImportProgressView(progress: ImportProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Importing ${progress.current}/${progress.total}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.current.toFloat() / progress.total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = progress.currentFileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportResultView(result: ImportResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Import Complete",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "✓ ${result.successCount} imported successfully",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (result.errorCount > 0) {
            Text(
                text = "✗ ${result.errorCount} failed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            result.errors.forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }
}

private suspend fun importEpubFiles(
    context: android.content.Context,
    files: List<EpubFileInfo>,
    customTitle: String?,
    combineAsOne: Boolean,
    categoryId: Long?,
    storageManager: StorageManager,
    onProgress: (current: Int, total: Int, fileName: String) -> Unit,
): ImportResult = withContext(Dispatchers.IO) {
    val errors = mutableListOf<String>()
    var successCount = 0

    val localNovelsDir = storageManager.getLocalNovelSourceDirectory()
    if (localNovelsDir == null) {
        return@withContext ImportResult(0, files.size, listOf("Local novels directory not found"))
    }

    if (combineAsOne && files.size > 1) {
        // Combine all files into one novel folder
        onProgress(1, 1, customTitle ?: files.first().title)

        try {
            val novelTitle = customTitle ?: files.first().title
            val sanitizedTitle = sanitizeFileName(novelTitle)

            // Create novel folder
            val novelDir = localNovelsDir.createDirectory(sanitizedTitle)
            if (novelDir == null) {
                errors.add("Failed to create directory for: $novelTitle")
            } else {
                // Copy each file as a chapter
                files.forEachIndexed { index, file ->
                    val chapterFileName = "Chapter ${index + 1} - ${file.fileName}"
                    val destFile = novelDir.createFile(chapterFileName)
                    if (destFile != null) {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            destFile.openOutputStream()?.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                // Copy cover from first file if available
                val firstCoverUri = files.firstOrNull()?.coverUri
                if (firstCoverUri != null) {
                    try {
                        val coverFileName = "cover.png"
                        val coverDestFile = novelDir.createFile(coverFileName)
                        if (coverDestFile != null) {
                            context.contentResolver.openInputStream(firstCoverUri)?.use { input ->
                                coverDestFile.openOutputStream()?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) { "Failed to copy cover for combined novel" }
                    }
                }
                successCount = 1
            }
        } catch (e: Exception) {
            errors.add("Failed to combine files: ${e.message}")
        }
    } else {
        // Import each file as a separate novel
        files.forEachIndexed { index, file ->
            onProgress(index + 1, files.size, file.fileName)

            try {
                val novelTitle = if (files.size == 1 && customTitle != null) {
                    customTitle
                } else {
                    file.title
                }
                val sanitizedTitle = sanitizeFileName(novelTitle)

                // Create novel folder
                var novelDir = localNovelsDir.findFile(sanitizedTitle)
                if (novelDir == null) {
                    novelDir = localNovelsDir.createDirectory(sanitizedTitle)
                }

                if (novelDir == null) {
                    errors.add("Failed to create directory for: ${file.fileName}")
                } else {
                    // Copy the epub file
                    val destFile = novelDir.createFile(file.fileName)
                    if (destFile != null) {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            destFile.openOutputStream()?.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Copy cover if available
                        val coverUri = file.coverUri
                        if (coverUri != null) {
                            try {
                                val coverFileName = "cover.png"
                                val coverDestFile = novelDir.createFile(coverFileName)
                                if (coverDestFile != null) {
                                    context.contentResolver.openInputStream(coverUri)?.use { input ->
                                        coverDestFile.openOutputStream()?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG, e) { "Failed to copy cover for: ${file.fileName}" }
                            }
                        }

                        successCount++
                    } else {
                        errors.add("Failed to create file: ${file.fileName}")
                    }
                }
            } catch (e: Exception) {
                errors.add("${file.fileName}: ${e.message}")
            }
        }
    }

    ImportResult(successCount, errors.size, errors)
}

private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
}
