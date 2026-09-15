package mihon.desktop.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DownloadRecoveryReport(val message: String, val recoveredFrom: Path? = null, val preservedFile: Path? = null)

class DownloadStore(
    private val storeFile: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {
    private val lock = Any()
    private val backupFile = storeFile.resolveSibling("${storeFile.fileName}.bak")
    private val _recoveryReport = MutableStateFlow<DownloadRecoveryReport?>(null)
    val recoveryReport: StateFlow<DownloadRecoveryReport?> = _recoveryReport.asStateFlow()

    fun save(queue: List<DesktopDownload>) = synchronized(lock) {
        storeFile.parent?.let { Files.createDirectories(it) }
        if (Files.exists(storeFile)) {
            if (runCatching { decode(storeFile) }.isSuccess) {
                atomicWrite(backupFile, Files.readString(storeFile))
            } else {
                val preserved = storeFile.resolveSibling("${storeFile.fileName}.${UUID.randomUUID()}.corrupt")
                Files.copy(storeFile, preserved)
                _recoveryReport.value =
                    DownloadRecoveryReport("下载队列损坏，已保留原文件", backupFile.takeIf { Files.exists(it) }, preserved)
            }
        }
        atomicWrite(storeFile, json.encodeToString(queue))
    }

    private fun atomicWrite(file: Path, content: String) {
        val temp = file.resolveSibling("${file.fileName}.tmp")
        try {
            Files.writeString(temp, content)
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun decode(file: Path): List<DesktopDownload> = json.decodeFromString(Files.readString(file))

    fun restore(): List<DesktopDownload> = synchronized(lock) {
        _recoveryReport.value = null
        val raw = if (!Files.exists(storeFile) && !Files.exists(backupFile)) {
            emptyList()
        } else {
            try {
                decode(storeFile)
            } catch (error: Exception) {
                val backup = runCatching { decode(backupFile) }.getOrNull()
                _recoveryReport.value = DownloadRecoveryReport(
                    if (backup !=
                        null
                    ) {
                        "下载队列无法读取，已从最近有效快照恢复：${error.message}"
                    } else {
                        "下载队列及快照无法读取，原文件已保留：${error.message}"
                    },
                    recoveredFrom = backupFile.takeIf { backup != null },
                    preservedFile = storeFile.takeIf { Files.exists(it) },
                )
                backup.orEmpty()
            }
        }
        raw.map { if (it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.QUEUED) else it }
    }

    fun clear() = synchronized(lock) {
        Files.deleteIfExists(storeFile)
        Files.deleteIfExists(backupFile)
        _recoveryReport.value = null
    }
}
