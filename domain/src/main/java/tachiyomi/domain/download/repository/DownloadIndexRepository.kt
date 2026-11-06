package tachiyomi.domain.download.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.download.model.DownloadIndexEntry
import tachiyomi.domain.download.model.DownloadIndexMangaKey

interface DownloadIndexRepository {
    fun getChapterDirsAsFlow(sourceId: Long, mangaDir: String): Flow<Set<String>>
    fun getAllChapterDirsAsFlow(): Flow<Map<DownloadIndexMangaKey, Set<String>>>
    fun getAllMangaChapterDirCountsAsFlow(): Flow<Map<DownloadIndexMangaKey, Long>>
    suspend fun getTotalChapterDirCount(): Long
    suspend fun insertChapterDirs(entries: List<DownloadIndexEntry>)
    suspend fun deleteChapterDirs(sourceId: Long, mangaDir: String, chapterDirs: List<String>)
    suspend fun renameMangaDir(sourceId: Long, mangaDir: String, newMangaDir: String)
    suspend fun deleteMangaDir(sourceId: Long, mangaDir: String)
    suspend fun deleteBySource(sourceId: Long)
    suspend fun replaceAll(entries: List<DownloadIndexEntry>)
}
