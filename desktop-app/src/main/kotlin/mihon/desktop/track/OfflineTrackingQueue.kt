package mihon.desktop.track

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
data class QueuedTrackingUpdate(
    val mangaId: Long,
    val trackerId: Long,
    val chapterNumber: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)

class OfflineTrackingQueue(
    private val queueFile: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {
    private val mutex = Mutex()

    suspend fun enqueue(update: QueuedTrackingUpdate) = mutex.withLock {
        val current = loadUnsafe().toMutableList()
        // Replace existing pending update for the same manga and tracker if chapter is higher
        val existingIndex = current.indexOfFirst { it.mangaId == update.mangaId && it.trackerId == update.trackerId }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            if (update.chapterNumber >= existing.chapterNumber) {
                current[existingIndex] = update
            }
        } else {
            current.add(update)
        }
        saveUnsafe(current)
    }

    suspend fun peekAll(): List<QueuedTrackingUpdate> = mutex.withLock {
        loadUnsafe()
    }

    suspend fun remove(update: QueuedTrackingUpdate) = mutex.withLock {
        val current = loadUnsafe().toMutableList()
        current.removeAll {
            it.mangaId == update.mangaId && it.trackerId == update.trackerId &&
                it.chapterNumber == update.chapterNumber
        }
        saveUnsafe(current)
    }

    suspend fun updateRetry(update: QueuedTrackingUpdate) = mutex.withLock {
        val current = loadUnsafe().toMutableList()
        val index = current.indexOfFirst { it.mangaId == update.mangaId && it.trackerId == update.trackerId }
        if (index >= 0) {
            current[index] = current[index].copy(retryCount = current[index].retryCount + 1)
            saveUnsafe(current)
        }
    }

    suspend fun clear() = mutex.withLock {
        saveUnsafe(emptyList())
    }

    private fun loadUnsafe(): List<QueuedTrackingUpdate> {
        if (!Files.exists(queueFile)) return emptyList()
        return try {
            val text = Files.readString(queueFile)
            if (text.isBlank()) emptyList() else json.decodeFromString(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveUnsafe(items: List<QueuedTrackingUpdate>) {
        val parent = queueFile.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val temp = queueFile.resolveSibling("${queueFile.fileName}.tmp")
        val content = json.encodeToString(items)
        Files.writeString(temp, content)
        Files.move(temp, queueFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
