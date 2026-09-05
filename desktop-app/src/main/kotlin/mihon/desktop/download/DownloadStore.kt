package mihon.desktop.download

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DownloadStore(
    private val storeFile: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {
    private val lock = Any()

    fun save(queue: List<DesktopDownload>) {
        synchronized(lock) {
            val parent = storeFile.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val tempFile = storeFile.resolveSibling("${storeFile.fileName}.tmp")
            val content = json.encodeToString(queue)
            Files.writeString(tempFile, content)
            try {
                Files.move(
                    tempFile,
                    storeFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun restore(): List<DesktopDownload> {
        synchronized(lock) {
            if (!Files.exists(storeFile)) {
                return emptyList()
            }
            return try {
                val content = Files.readString(storeFile)
                val raw = json.decodeFromString<List<DesktopDownload>>(content)
                // Crash recovery: Any in-flight download is reset to QUEUED so it can resume
                raw.map { download ->
                    if (download.status == DownloadStatus.DOWNLOADING) {
                        download.copy(status = DownloadStatus.QUEUED)
                    } else {
                        download
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            Files.deleteIfExists(storeFile)
        }
    }
}
