package eu.kanade.tachiyomi.data.translation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.storage.extension
import tachiyomi.domain.translation.model.TranslationStatus
import tachiyomi.domain.translation.model.TranslationTask
import java.io.File

/**
 * Store for persisting translation queue across app restarts.
 */
class TranslationStore(
    context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val storeFile = File(context.cacheDir, "translation_queue.json")

    /**
     * Save the current translation queue to storage.
     */
    fun save(queue: List<TranslationTask>) {
        try {
            val serializable = queue.map { task ->
                SerializableTask(
                    id = task.id,
                    chapterId = task.chapterId,
                    mangaId = task.mangaId,
                    sourceLanguage = task.sourceLanguage,
                    targetLanguage = task.targetLanguage,
                    engineId = task.engineId,
                    priority = task.priority,
                    status = task.status.name,
                    errorMessage = task.errorMessage,
                    createdAt = task.createdAt,
                )
            }
            storeFile.writeText(json.encodeToString(serializable))
        } catch (e: Exception) {
            // Ignore save errors
        }
    }

    /**
     * Load the translation queue from storage.
     */
    fun load(): List<TranslationTask> {
        return try {
            if (!storeFile.exists()) return emptyList()

            val serializable = json.decodeFromString<List<SerializableTask>>(storeFile.readText())
            serializable.mapNotNull { task ->
                try {
                    TranslationTask(
                        id = task.id,
                        chapterId = task.chapterId,
                        mangaId = task.mangaId,
                        sourceLanguage = task.sourceLanguage,
                        targetLanguage = task.targetLanguage,
                        engineId = task.engineId,
                        priority = task.priority,
                        status = TranslationStatus.valueOf(task.status),
                        errorMessage = task.errorMessage,
                        createdAt = task.createdAt,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear the stored queue.
     */
    fun clear() {
        try {
            if (storeFile.exists()) {
                storeFile.delete()
            }
        } catch (e: Exception) {
            // Ignore clear errors
        }
    }

    @Serializable
    private data class SerializableTask(
        val id: Long,
        val chapterId: Long,
        val mangaId: Long,
        val sourceLanguage: String,
        val targetLanguage: String,
        val engineId: Long,
        val priority: Int,
        val status: String,
        val errorMessage: String?,
        val createdAt: Long,
    )
}
