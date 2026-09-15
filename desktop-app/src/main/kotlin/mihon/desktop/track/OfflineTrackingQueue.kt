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
    val nextAttemptAt: Long = 0,
    val authenticationRequired: Boolean = false,
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
                current[existingIndex] =
                    update.copy(
                        retryCount = existing.retryCount,
                        nextAttemptAt = existing.nextAttemptAt,
                        authenticationRequired = existing.authenticationRequired,
                    )
            }
        } else {
            current.add(update)
        }
        saveUnsafe(current)
    }

    suspend fun enqueue(track: DesktopTrackRecord) = enqueue(
        QueuedTrackingUpdate(
            mangaId = track.mangaId,
            trackerId = track.trackerId,
            chapterNumber = track.lastChapterRead,
        ),
    )

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

    suspend fun updateRetry(
        update: QueuedTrackingUpdate,
        now: Long = System.currentTimeMillis(),
        authenticationRequired: Boolean = false,
    ) = mutex.withLock {
        val current = loadUnsafe().toMutableList()
        val index = current.indexOfFirst { it.mangaId == update.mangaId && it.trackerId == update.trackerId }
        if (index >= 0) {
            val retries = current[index].retryCount + 1
            val backoff = minOf(3_600_000L, 5_000L * (1L shl minOf(retries - 1, 10)))
            current[index] =
                current[index].copy(
                    retryCount = retries,
                    nextAttemptAt = now + backoff,
                    authenticationRequired = authenticationRequired,
                )
            saveUnsafe(current)
        }
    }

    suspend fun resumeAuthentication(trackerId: Long) = mutex.withLock {
        saveUnsafe(
            loadUnsafe().map {
                if (it.trackerId ==
                    trackerId
                ) {
                    it.copy(authenticationRequired = false, nextAttemptAt = 0, retryCount = 0)
                } else {
                    it
                }
            },
        )
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
