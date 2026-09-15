package mihon.desktop.library.update

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

enum class LibraryUpdateStatus { IDLE, RUNNING, COMPLETED, FAILED, CANCELLED }

data class LibraryUpdateRunState(
    val status: LibraryUpdateStatus = LibraryUpdateStatus.IDLE,
    val lastAttemptEpochMillis: Long = 0,
    val lastCompletedEpochMillis: Long = 0,
    val retryAfterEpochMillis: Long = 0,
    val retryCount: Int = 0,
    val failedMangaIds: Set<Long> = emptySet(),
    val error: String? = null,
)

internal class LibraryUpdateStateStore(private val file: Path?) {
    fun load(): LibraryUpdateRunState {
        if (file == null || !Files.exists(file)) return LibraryUpdateRunState()
        return try {
            val values = Properties().apply { Files.newInputStream(file).use { load(it) } }
            LibraryUpdateRunState(
                status = LibraryUpdateStatus.valueOf(values.getProperty("status", "IDLE")),
                lastAttemptEpochMillis = values.getProperty("attempt", "0").toLong(),
                lastCompletedEpochMillis = values.getProperty("completed", "0").toLong(),
                retryAfterEpochMillis = values.getProperty("retryAfter", "0").toLong(),
                retryCount = values.getProperty("retryCount", "0").toInt().coerceIn(0, 16),
                failedMangaIds = values.getProperty("failed", "").split(',').mapNotNull { it.toLongOrNull() }.toSet(),
                error = values.getProperty("error"),
            )
        } catch (error: Exception) {
            LibraryUpdateRunState(
                status = LibraryUpdateStatus.FAILED,
                error = "Cannot read update recovery state: ${error.message}",
            )
        }
    }

    fun save(state: LibraryUpdateRunState) {
        if (file == null) return
        Files.createDirectories(file.toAbsolutePath().parent)
        val values = Properties().apply {
            setProperty("status", state.status.name)
            setProperty("attempt", state.lastAttemptEpochMillis.toString())
            setProperty("completed", state.lastCompletedEpochMillis.toString())
            setProperty("retryAfter", state.retryAfterEpochMillis.toString())
            setProperty("retryCount", state.retryCount.toString())
            setProperty("failed", state.failedMangaIds.joinToString(","))
            state.error?.let { setProperty("error", it) }
        }
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { values.store(it, "Library update recovery") }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
