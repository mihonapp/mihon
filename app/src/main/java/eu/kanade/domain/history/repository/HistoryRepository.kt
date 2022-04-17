package eu.kanade.domain.history.repository

import eu.kanade.data.history.local.HistoryPagingSource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory

interface HistoryRepository {

    fun getHistory(query: String): HistoryPagingSource

    suspend fun getHistory(limit: Int, page: Int, query: String): List<MangaChapterHistory>

    suspend fun getNextChapterForManga(manga: Manga, chapter: Chapter): Chapter?

    suspend fun resetHistory(history: History): Boolean

    suspend fun resetHistoryByMangaId(mangaId: Long): Boolean

    suspend fun deleteAllHistory(): Boolean
}
