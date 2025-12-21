package eu.kanade.tachiyomi.data.translation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File

/**
 * Cache for storing translated chapter content.
 */
class TranslationCache(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(context.cacheDir, "translations")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Get the cache file for a chapter translation.
     */
    private fun getCacheFile(
        mangaId: Long,
        chapterId: Long,
        targetLanguage: String,
    ): File {
        val mangaDir = File(cacheDir, mangaId.toString())
        if (!mangaDir.exists()) {
            mangaDir.mkdirs()
        }
        return File(mangaDir, "${chapterId}_$targetLanguage.json")
    }

    /**
     * Check if a translation exists in the cache.
     */
    fun hasTranslation(
        mangaId: Long,
        chapterId: Long,
        targetLanguage: String,
    ): Boolean {
        return getCacheFile(mangaId, chapterId, targetLanguage).exists()
    }

    /**
     * Get a cached translation.
     */
    fun getTranslation(
        mangaId: Long,
        chapterId: Long,
        targetLanguage: String,
    ): CachedTranslation? {
        val file = getCacheFile(mangaId, chapterId, targetLanguage)
        if (!file.exists()) return null

        return try {
            json.decodeFromString<CachedTranslation>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a translation to the cache.
     */
    fun saveTranslation(
        mangaId: Long,
        chapterId: Long,
        sourceLanguage: String,
        targetLanguage: String,
        originalContent: String,
        translatedContent: String,
        engineId: String,
    ) {
        val cached = CachedTranslation(
            chapterId = chapterId,
            mangaId = mangaId,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            originalContent = originalContent,
            translatedContent = translatedContent,
            engineId = engineId,
            createdAt = System.currentTimeMillis(),
        )

        val file = getCacheFile(mangaId, chapterId, targetLanguage)
        file.writeText(json.encodeToString(cached))
    }

    /**
     * Delete a cached translation.
     */
    fun deleteTranslation(
        mangaId: Long,
        chapterId: Long,
        targetLanguage: String,
    ) {
        val file = getCacheFile(mangaId, chapterId, targetLanguage)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Delete all translations for a manga.
     */
    fun deleteAllForManga(mangaId: Long) {
        val mangaDir = File(cacheDir, mangaId.toString())
        if (mangaDir.exists()) {
            mangaDir.deleteRecursively()
        }
    }

    /**
     * Clear the entire translation cache.
     */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Get the total cache size in bytes.
     */
    fun getCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Get the number of cached translations.
     */
    fun getCacheCount(): Int {
        return cacheDir.walkTopDown().filter { it.isFile && it.extension == "json" }.count()
    }

    @Serializable
    data class CachedTranslation(
        val chapterId: Long,
        val mangaId: Long,
        val sourceLanguage: String,
        val targetLanguage: String,
        val originalContent: String,
        val translatedContent: String,
        val engineId: String,
        val createdAt: Long,
    )
}
