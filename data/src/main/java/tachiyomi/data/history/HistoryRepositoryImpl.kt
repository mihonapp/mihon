package tachiyomi.data.history

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.MangaCover

class HistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return handler.subscribeToList {
            historyViewQueries.history(query, HistoryMapper::mapHistoryWithRelations)
        }
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return handler.awaitOneOrNull {
            historyViewQueries.getLatestHistory(HistoryMapper::mapHistoryWithRelations)
        }
    }

    override suspend fun getTotalReadDuration(): Long {
        return handler.awaitOne { historyQueries.getReadDuration() }
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return handler.awaitList { historyQueries.getHistoryByMangaId(mangaId, HistoryMapper::mapHistory) }
    }

    override suspend fun resetHistory(historyId: Long) {
        try {
            handler.await { historyQueries.resetHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        try {
            handler.await { historyQueries.resetHistoryByMangaId(mangaId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await { historyQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        try {
            handler.await {
                historyQueries.upsert(
                    historyUpdate.chapterId,
                    historyUpdate.readAt,
                    historyUpdate.sessionReadDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun getReadDurationByManga(): List<ReadDurationByManga> {
        val raw = handler.awaitList {
            historyQueries.getReadDurationByManga {
                    manga_id, title, total_time_read, source_id, is_favorite, thumbnail_url, cover_last_modified ->
                ReadDurationByManga(
                    mangaId = manga_id,
                    title = title,
                    totalTimeRead = total_time_read,
                    cover = MangaCover(
                        mangaId = manga_id,
                        sourceId = source_id,
                        isMangaFavorite = is_favorite,
                        url = thumbnail_url,
                        lastModified = cover_last_modified,
                    )
                )
            }
        }
        // Merge duplicate manga entries (e.g. from source migrations) by normalized title. Sum all read times.
        return raw
            .groupBy { it.title.trim().lowercase() }
            .map { (_, group) ->
                val representative = group.maxWithOrNull(
                    compareBy(
                        { it.cover.isMangaFavorite },
                        { it.cover.url != null },
                        { it.totalTimeRead },
                    ),
                )!!
                representative.copy(totalTimeRead = group.sumOf { it.totalTimeRead })
            }
            .sortedByDescending { it.totalTimeRead }
            .take(30)
    }

    override suspend fun getReadDurationForManga(mangaId: Long): Long {
        return handler.awaitOne {
            historyQueries.getReadDurationForManga(mangaId)
        }
    }
}
