package mihon.desktop.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.BackupDecodeException
import mihon.desktop.library.backup.BackupValidationException
import mihon.desktop.library.local.LocalImportRejected
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.PreferenceSkipReason
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser

data class SanitizedImportResult(
    val reportId: Long,
    val counts: ImportCounts,
    val skipCategories: List<String>,
)

sealed interface ImportActionState {
    data object Idle : ImportActionState
    data object Running : ImportActionState
    data class Completed(val result: SanitizedImportResult) : ImportActionState
    data class Rejected(val category: String) : ImportActionState
    data class Failed(val message: String) : ImportActionState
}

class LibraryImportController(
    private val importBackup: (Path, Long) -> ImportReport,
    private val importLocal: (Path, Path, Long) -> ImportReport,
    private val localLibraryRoot: Path,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        backupImporter: AndroidBackupImporter,
        localImporter: LocalMangaImporter,
        localLibraryRoot: Path,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        importBackup = backupImporter::import,
        importLocal = localImporter::import,
        localLibraryRoot = localLibraryRoot,
        ioDispatcher = ioDispatcher,
        clock = clock,
    )

    suspend fun importBackup(path: Path): ImportActionState = runImport {
        importBackup(path, clock())
    }

    suspend fun importLocal(path: Path): ImportActionState = runImport {
        importLocal(path, localLibraryRoot, clock())
    }

    private suspend fun runImport(import: () -> ImportReport): ImportActionState = withContext(ioDispatcher) {
        try {
            ImportActionState.Completed(import().sanitized())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: BackupDecodeException) {
            ImportActionState.Rejected("INVALID_ANDROID_BACKUP")
        } catch (_: BackupValidationException) {
            ImportActionState.Rejected("INVALID_ANDROID_BACKUP")
        } catch (_: LocalImportRejected) {
            ImportActionState.Rejected("UNSAFE_LOCAL_DIRECTORY")
        } catch (_: Exception) {
            ImportActionState.Failed("Import failed. Check the selected file and try again.")
        }
    }
}

class LibraryImportActions(
    private val chooseBackup: () -> Path? = ::chooseAndroidBackup,
    private val chooseLocal: () -> Path? = ::chooseLocalMangaDirectory,
    private val importBackup: suspend (Path) -> ImportActionState,
    private val importLocal: suspend (Path) -> ImportActionState,
) {
    suspend fun chooseAndImportBackup(): ImportActionState =
        chooseBackup()?.let { importBackup(it) } ?: ImportActionState.Idle

    suspend fun chooseAndImportLocal(): ImportActionState =
        chooseLocal()?.let { importLocal(it) } ?: ImportActionState.Idle
}

private fun ImportReport.sanitized() = SanitizedImportResult(
    reportId = id,
    counts = counts,
    skipCategories = items.asSequence()
        .filter { it.outcome.equals("SKIPPED", ignoreCase = true) || it.reason != null }
        .map { item ->
            item.reason?.takeIf(SAFE_SKIP_REASON_NAMES::contains) ?: UNKNOWN_SKIP_REASON
        }
        .distinct()
        .sorted()
        .toList(),
)

private val SAFE_SKIP_REASON_NAMES = PreferenceSkipReason.entries.mapTo(mutableSetOf()) { it.name }
private const val UNKNOWN_SKIP_REASON = "UNKNOWN_SKIP_REASON"

private fun chooseAndroidBackup(): Path? {
    val dialog = FileDialog(null as Frame?, "Import Android backup", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".tachibk", ignoreCase = true) }
        file = "*.tachibk"
    }
    return try {
        dialog.isVisible = true
        val selectedDirectory = dialog.directory ?: return null
        val selectedFile = dialog.file ?: return null
        if (!selectedFile.endsWith(".tachibk", ignoreCase = true)) return null
        Path.of(selectedDirectory, selectedFile)
    } finally {
        dialog.dispose()
    }
}

private fun chooseLocalMangaDirectory(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import local manga"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val selected = chooser.selectedFile?.toPath() ?: return null
    return selected.takeIf { chooser.selectedFile.isDirectory }
}
