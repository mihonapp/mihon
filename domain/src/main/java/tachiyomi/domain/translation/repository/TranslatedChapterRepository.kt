package tachiyomi.domain.translation.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.translation.model.TranslatedChapter
import tachiyomi.domain.translation.model.TranslationInfo

/**
 * Repository interface for managing translated chapters.
 */
interface TranslatedChapterRepository {

    /**
     * Get a translated chapter by chapter ID and target language.
     */
    suspend fun getTranslatedChapter(chapterId: Long, targetLanguage: String): TranslatedChapter?

    /**
     * Get all translations for a chapter.
     */
    suspend fun getAllTranslationsForChapter(chapterId: Long): List<TranslatedChapter>

    /**
     * Get all translated languages for a manga.
     */
    suspend fun getTranslatedLanguagesForManga(mangaId: Long): List<String>

    /**
     * Check if a chapter has a translation for a specific language.
     */
    suspend fun hasTranslation(chapterId: Long, targetLanguage: String): Boolean

    /**
     * Get chapters with translations for a manga.
     */
    fun getChaptersWithTranslationsAsFlow(mangaId: Long): Flow<List<TranslationInfo>>

    /**
     * Get chapters with translations for a manga (suspend).
     */
    suspend fun getChaptersWithTranslations(mangaId: Long): List<TranslationInfo>

    /**
     * Get set of chapter IDs that have any translation for a manga.
     */
    suspend fun getTranslatedChapterIds(mangaId: Long): Set<Long>

    /**
     * Insert or update a translated chapter.
     */
    suspend fun upsertTranslation(translatedChapter: TranslatedChapter)

    /**
     * Delete a translation.
     */
    suspend fun deleteTranslation(chapterId: Long, targetLanguage: String)

    suspend fun deleteAll()

    suspend fun getAll(): List<TranslatedChapter>

    /**
     * Delete all translations for a chapter.
     */
    suspend fun deleteAllForChapter(chapterId: Long)

    /**
     * Delete all translations for a manga.
     */
    suspend fun deleteAllForManga(mangaId: Long)

    /**
     * Get the total cache size in bytes.
     */
    suspend fun getCacheSize(): Long

    /**
     * Clear old cached translations.
     */
    suspend fun clearOldCache(olderThan: Long)
}
