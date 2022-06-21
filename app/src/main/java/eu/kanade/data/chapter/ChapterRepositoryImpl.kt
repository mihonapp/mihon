package eu.kanade.data.chapter

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.toLong
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class ChapterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
                    chaptersQueries.insert(
                        chapter.mangaId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.read,
                        chapter.bookmark,
                        chapter.lastPageRead,
                        chapter.chapterNumber,
                        chapter.sourceOrder,
                        chapter.dateFetch,
                        chapter.dateUpload,
                    )
                    val lastInsertId = chaptersQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        handler.await {
            chaptersQueries.update(
                chapterUpdate.mangaId,
                chapterUpdate.url,
                chapterUpdate.name,
                chapterUpdate.scanlator,
                chapterUpdate.read?.toLong(),
                chapterUpdate.bookmark?.toLong(),
                chapterUpdate.lastPageRead,
                chapterUpdate.chapterNumber?.toDouble(),
                chapterUpdate.sourceOrder,
                chapterUpdate.dateFetch,
                chapterUpdate.dateUpload,
                chapterId = chapterUpdate.id,
            )
        }
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { chapterUpdate ->
                chaptersQueries.update(
                    chapterUpdate.mangaId,
                    chapterUpdate.url,
                    chapterUpdate.name,
                    chapterUpdate.scanlator,
                    chapterUpdate.read?.toLong(),
                    chapterUpdate.bookmark?.toLong(),
                    chapterUpdate.lastPageRead,
                    chapterUpdate.chapterNumber?.toDouble(),
                    chapterUpdate.sourceOrder,
                    chapterUpdate.dateFetch,
                    chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { chaptersQueries.removeChaptersWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList { chaptersQueries.getChaptersByMangaId(mangaId, chapterMapper) }
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long): Flow<List<Chapter>> {
        return handler.subscribeToList { chaptersQueries.getChaptersByMangaId(mangaId, chapterMapper) }
    }
}
