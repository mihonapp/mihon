package mihon.desktop.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.library.backup.AndroidBackupExporter
import mihon.desktop.preferences.DesktopPreferenceStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.io.path.name

class DesktopBackupScheduler(
    private val backupExporter: AndroidBackupExporter,
    private val preferenceStore: DesktopPreferenceStore,
    private val defaultBackupDir: Path,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var scheduledJob: Job? = null
    private val backupMutex = Mutex()

    @Volatile
    var lastResult: BackupRunResult? = null
        private set
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun start() {
        if (scheduledJob?.isActive == true) return
        scheduledJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    checkAndRunAutoBackup()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Ignore background scheduler errors and continue
                }
                delay(60_000L) // check every minute
            }
        }
    }

    fun stop() {
        scheduledJob?.cancel()
        scheduledJob = null
    }

    suspend fun checkAndRunAutoBackup(): Path? = backupMutex.withLock {
        val prefs = preferenceStore.load()
        if (prefs.backupIntervalHours <= 0) return@withLock null

        val intervalMillis = prefs.backupIntervalHours.toLong() * 3_600_000L
        val now = clock()
        if (now - prefs.lastAutoBackupEpochMillis >= intervalMillis) {
            val path = performBackupLocked(isManual = false)
            val updated = preferenceStore.load().copy(lastAutoBackupEpochMillis = now)
            preferenceStore.save(updated)
            return@withLock path
        }
        null
    }

    suspend fun performBackup(isManual: Boolean = false): Path = backupMutex.withLock {
        performBackupLocked(isManual)
    }

    private suspend fun performBackupLocked(isManual: Boolean): Path = withContext(Dispatchers.IO) {
        try {
            writeBackup(isManual)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastResult = BackupRunResult(null, clock(), error.message ?: error.javaClass.simpleName)
            throw error
        }
    }

    private fun writeBackup(isManual: Boolean): Path {
        val prefs = preferenceStore.load()
        val targetDir = resolveBackupDirectory(prefs.backupStoragePath)
        Files.createDirectories(targetDir)

        val timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneId.systemDefault())
        val fileName = "mihon_backup_${formatter.format(timestamp)}_${UUID.randomUUID()}.tachibk"
        val targetFile = targetDir.resolve(fileName)

        backupExporter.export(targetFile)
        val warnings = pruneOldBackups(targetDir, prefs.backupRetentionCount, targetFile)

        if (!isManual) {
            val updated = preferenceStore.load().copy(lastAutoBackupEpochMillis = clock())
            preferenceStore.save(updated)
        }

        lastResult = BackupRunResult(targetFile, clock(), retentionWarnings = warnings)
        return targetFile
    }

    fun pruneOldBackups(dir: Path, maxRetention: Int, newestBackup: Path? = null): List<String> {
        if (maxRetention <= 0 || !Files.isDirectory(dir)) return emptyList()
        val warnings = mutableListOf<String>()
        try {
            val backups = Files.list(dir).use { stream ->
                stream.filter { path ->
                    val name = path.name
                    name.startsWith("mihon_backup_") && name.endsWith(".tachibk") && Files.isRegularFile(path)
                }.sorted { p1, p2 ->
                    when {
                        p1 == p2 -> 0
                        p1 == newestBackup -> -1
                        p2 == newestBackup -> 1
                        else -> {
                            val timeOrder = Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1))
                            if (timeOrder != 0) timeOrder else p2.name.compareTo(p1.name)
                        }
                    }
                }.toList()
            }
            for (oldFile in backups.drop(maxRetention)) {
                try {
                    Files.deleteIfExists(oldFile)
                } catch (error: IOException) {
                    warnings += "Cannot remove ${oldFile.fileName}: ${error.message}"
                }
            }
        } catch (error: Exception) {
            warnings += "Cannot scan backup retention: ${error.message}"
        }
        return warnings
    }
    fun resolveBackupDirectory(customPath: String): Path {
        if (customPath.isNotBlank()) {
            try {
                return Path.of(customPath).toAbsolutePath().normalize()
            } catch (_: Exception) {
                // fall through
            }
        }
        return defaultBackupDir.toAbsolutePath().normalize()
    }
}

data class BackupRunResult(
    val path: Path?,
    val completedAtEpochMillis: Long,
    val error: String? = null,
    val retentionWarnings: List<String> = emptyList(),
)
