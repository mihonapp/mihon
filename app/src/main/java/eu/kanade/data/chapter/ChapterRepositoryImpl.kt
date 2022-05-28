package eu.kanade.data.chapter

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.toLong
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class ChapterRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        try {
            databaseHandler.await {
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
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
