package tachiyomi.data.download

import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.download.model.DownloadIndexEntry
import tachiyomi.domain.download.model.DownloadIndexMangaKey
import tachiyomi.domain.download.repository.DownloadIndexRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DownloadIndexRepositoryImpl(
    private val database: Database,
) : DownloadIndexRepository {

    private val indexQueries get() = database.download_indexQueries

    override fun getChapterDirsAsFlow(sourceId: Long, mangaDir: String): Flow<Set<String>> {
        return indexQueries.getChapterDirs(sourceId, mangaDir)
            .subscribeToList()
            .map { it.toSet() }
            .distinctUntilChanged()
    }

    override fun getAllChapterDirsAsFlow(): Flow<Map<DownloadIndexMangaKey, Set<String>>> {
        return indexQueries.getAllChapterDirs()
            .subscribeToList()
            .map { list ->
                list.groupBy(
                    keySelector = { DownloadIndexMangaKey(it.source_id, it.manga_dir) },
                    valueTransform = { it.chapter_dir },
                ).mapValues { it.value.toSet() }
            }.distinctUntilChanged()
    }

    override fun getAllMangaChapterDirCountsAsFlow(): Flow<Map<DownloadIndexMangaKey, Long>> {
        return indexQueries.getAllMangaChapterDirCounts()
            .subscribeToList()
            .map { list ->
                list.associate { DownloadIndexMangaKey(it.source_id, it.manga_dir) to it.total }
            }.distinctUntilChanged()
    }

    override suspend fun getTotalChapterDirCount(): Long {
        return indexQueries.getTotalChapterDirCount().awaitAsOne()
    }

    override suspend fun insertChapterDirs(entries: List<DownloadIndexEntry>) = withContext(Dispatchers.IO) {
        database.transaction {
            entries.forEach { entry ->
                indexQueries.insertChapterDir(
                    sourceId = entry.sourceId,
                    sourceDir = entry.sourceDir.lowercase(),
                    mangaDir = entry.mangaDir,
                    chapterDir = entry.chapterDir,
                )
            }
        }
    }

    override suspend fun deleteChapterDirs(sourceId: Long, mangaDir: String, chapterDirs: List<String>) = withContext(
        Dispatchers.IO,
    ) {
        database.transaction {
            chapterDirs.chunked(500).forEach { chunk ->
                indexQueries.deleteChapterDirs(sourceId, mangaDir, chunk)
            }
        }
    }

    override suspend fun renameMangaDir(sourceId: Long, mangaDir: String, newMangaDir: String) = withContext(
        Dispatchers.IO,
    ) {
        if (mangaDir == newMangaDir) return@withContext
        database.transaction {
            indexQueries.renameMangaDir(newMangaDir, sourceId, mangaDir)
            indexQueries.deleteMangaDir(sourceId, mangaDir)
        }
    }

    override suspend fun deleteMangaDir(sourceId: Long, mangaDir: String) = withContext(Dispatchers.IO) {
        database.transaction {
            indexQueries.deleteMangaDir(sourceId, mangaDir)
        }
    }

    override suspend fun deleteBySource(sourceId: Long) = withContext(Dispatchers.IO) {
        database.transaction {
            indexQueries.deleteBySource(sourceId)
        }
    }

    override suspend fun replaceAll(entries: List<DownloadIndexEntry>) = withContext(Dispatchers.IO) {
        database.transaction {
            indexQueries.deleteAll()
            entries.forEach { entry ->
                indexQueries.insertChapterDir(
                    sourceId = entry.sourceId,
                    sourceDir = entry.sourceDir.lowercase(),
                    mangaDir = entry.mangaDir,
                    chapterDir = entry.chapterDir,
                )
            }
        }
    }
}
