package tachiyomi.data.translation

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.translation.model.TranslatedChapter
import tachiyomi.domain.translation.model.TranslationInfo
import tachiyomi.domain.translation.repository.TranslatedChapterRepository
import java.io.File

class TranslatedChapterRepositoryImpl(
    private val context: Context,
    private val handler: DatabaseHandler,
) : TranslatedChapterRepository {

    private val translationsDir = File(context.filesDir, "translations").also { it.mkdirs() }

    private fun getTranslationFile(chapterId: Long, targetLanguage: String): File {
        return File(translationsDir, "${chapterId}_$targetLanguage.html")
    }

    override suspend fun getTranslatedChapter(chapterId: Long, targetLanguage: String): TranslatedChapter? {
        val record = handler.awaitOneOrNull {
            translated_chaptersQueries.getTranslatedChapter(chapterId, targetLanguage)
        } ?: return null

        val content = if (record.translated_content.isEmpty()) {
            val file = getTranslationFile(chapterId, targetLanguage)
            if (file.exists()) file.readText() else ""
        } else {
            record.translated_content
        }

        return record.toTranslatedChapter().copy(translatedContent = content)
    }

    override suspend fun getAllTranslationsForChapter(chapterId: Long): List<TranslatedChapter> {
        return handler.awaitList {
            translated_chaptersQueries.getAllTranslationsForChapter(chapterId)
        }.map { record ->
            val content = if (record.translated_content.isEmpty()) {
                val file = getTranslationFile(chapterId, record.target_language)
                if (file.exists()) file.readText() else ""
            } else {
                record.translated_content
            }
            record.toTranslatedChapter().copy(translatedContent = content)
        }
    }

    override suspend fun getTranslatedLanguagesForManga(mangaId: Long): List<String> {
        return handler.awaitList {
            translated_chaptersQueries.getTranslatedLanguagesForManga(mangaId)
        }
    }

    override suspend fun hasTranslation(chapterId: Long, targetLanguage: String): Boolean {
        return handler.awaitOne {
            translated_chaptersQueries.hasTranslation(chapterId, targetLanguage)
        }
    }

    override fun getChaptersWithTranslationsAsFlow(mangaId: Long): Flow<List<TranslationInfo>> {
        return handler.subscribeToList {
            translated_chaptersQueries.getChaptersWithTranslations(mangaId)
        }.map { list ->
            list.map { row ->
                TranslationInfo(
                    chapterId = row.chapter_id,
                    targetLanguage = row.target_language,
                    engineId = row.engine_id,
                    dateTranslated = row.date_translated,
                )
            }
        }
    }

    override suspend fun getChaptersWithTranslations(mangaId: Long): List<TranslationInfo> {
        return handler.awaitList {
            translated_chaptersQueries.getChaptersWithTranslations(mangaId)
        }.map { row ->
            TranslationInfo(
                chapterId = row.chapter_id,
                targetLanguage = row.target_language,
                engineId = row.engine_id,
                dateTranslated = row.date_translated,
            )
        }
    }

    override suspend fun getTranslatedChapterIds(mangaId: Long): Set<Long> {
        return handler.awaitList {
            translated_chaptersQueries.getChaptersWithTranslations(mangaId)
        }.map { it.chapter_id }.toSet()
    }

    override suspend fun upsertTranslation(translatedChapter: TranslatedChapter) {
        val file = getTranslationFile(translatedChapter.chapterId, translatedChapter.targetLanguage)
        file.writeText(translatedChapter.translatedContent)

        handler.await {
            translated_chaptersQueries.upsert(
                chapterId = translatedChapter.chapterId,
                mangaId = translatedChapter.mangaId,
                targetLanguage = translatedChapter.targetLanguage,
                engineId = translatedChapter.engineId,
                translatedContent = "",
                dateTranslated = translatedChapter.dateTranslated,
                isCached = translatedChapter.isCached,
            )
        }
    }

    override suspend fun deleteTranslation(chapterId: Long, targetLanguage: String) {
        getTranslationFile(chapterId, targetLanguage).delete()
        handler.await {
            translated_chaptersQueries.deleteTranslation(chapterId, targetLanguage)
        }
    }

    override suspend fun deleteAllForChapter(chapterId: Long) {
        val translations = handler.awaitList {
            translated_chaptersQueries.getAllTranslationsForChapter(chapterId)
        }
        translations.forEach {
            getTranslationFile(chapterId, it.target_language).delete()
        }

        handler.await {
            translated_chaptersQueries.deleteAllForChapter(chapterId)
        }
    }

    override suspend fun deleteAllForManga(mangaId: Long) {
        val translations = handler.awaitList {
            translated_chaptersQueries.getChaptersWithTranslations(mangaId)
        }
        translations.forEach {
            getTranslationFile(it.chapter_id, it.target_language).delete()
        }

        handler.await {
            translated_chaptersQueries.deleteAllForManga(mangaId)
        }
    }

    override suspend fun deleteAll() {
        translationsDir.deleteRecursively()
        translationsDir.mkdirs()
        handler.await {
            translated_chaptersQueries.deleteAll()
        }
    }

    override suspend fun getAll(): List<TranslatedChapter> {
        return handler.awaitList {
            translated_chaptersQueries.getAll()
        }.map { record ->
            val content = if (record.translated_content.isEmpty()) {
                val file = getTranslationFile(record.chapter_id, record.target_language)
                if (file.exists()) file.readText() else ""
            } else {
                record.translated_content
            }
            record.toTranslatedChapter().copy(translatedContent = content)
        }
    }

    override suspend fun getCacheSize(): Long {
        return translationsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    override suspend fun clearOldCache(olderThan: Long) {
        val toDelete = handler.awaitList {
            translated_chaptersQueries.getOldCachedTranslations(olderThan)
        }
        toDelete.forEach {
            getTranslationFile(it.chapter_id, it.target_language).delete()
        }
        handler.await {
            translated_chaptersQueries.clearOldCache(olderThan)
        }
    }

    private fun tachiyomi.data.Translated_chapters.toTranslatedChapter(): TranslatedChapter {
        return TranslatedChapter(
            id = _id,
            chapterId = chapter_id,
            mangaId = manga_id,
            targetLanguage = target_language,
            engineId = engine_id,
            translatedContent = translated_content,
            dateTranslated = date_translated,
            isCached = is_cached,
        )
    }
}
