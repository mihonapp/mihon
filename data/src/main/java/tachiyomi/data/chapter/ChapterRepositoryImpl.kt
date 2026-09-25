package tachiyomi.data.chapter

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ChapterRepositoryImpl(
    private val database: Database,
) : ChapterRepository {

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    override suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Chapter>,
        updated: List<ChapterRemoteUpdate>,
    ): List<Chapter> {
        return database.transactionWithResult {
            if (removedIds.isNotEmpty()) {
                database.chaptersQueries.removeChaptersWithIds(removedIds)
            }
            val existing = added.map { it.mangaId }
                .distinct()
                .flatMap { mangaId ->
                    database.chaptersQueries
                        .getChaptersByMangaId(
                            mangaId = mangaId,
                            applyScanlatorFilter = false.toLong(),
                            mapper = ::mapChapter,
                        )
                        .awaitAsList()
                        .map { mangaId to it.url }
                }
                .toMutableSet()
            val stored = added.filter { existing.add(it.mangaId to it.url) }.map { chapter ->
                val chapterId = database.chaptersQueries.insertReturningId(
                    mangaId = chapter.mangaId,
                    url = chapter.url,
                    name = chapter.name,
                    scanlator = chapter.scanlator,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = chapter.chapterNumber,
                    sourceOrder = chapter.sourceOrder,
                    dateFetch = chapter.dateFetch,
                    dateUpload = chapter.dateUpload,
                    memo = chapter.memo,
                )
                    .awaitAsOne()
                chapter.copy(id = chapterId)
            }
            updated.forEach { chapterUpdate ->
                database.chaptersQueries.updateRemote(
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                    memo = chapterUpdate.memo,
                )
            }
            stored
        }
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        database.transaction {
            chapterUpdates.forEach { chapterUpdate ->
                database.chaptersQueries.update(
                    read = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    lastPageRead = chapterUpdate.lastPageRead,
                    dateFetch = chapterUpdate.dateFetch,
                    chapterId = chapterUpdate.id,
                )
            }
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return database.chaptersQueries
            .getChaptersByMangaId(
                mangaId = mangaId,
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .awaitAsList()
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return database.chaptersQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .awaitAsList()
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chaptersQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .subscribeToList()
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return database.chaptersQueries
            .getBookmarkedChaptersByMangaId(mangaId, ::mapChapter)
            .awaitAsList()
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return database.chaptersQueries
            .getChapterById(id, ::mapChapter)
            .awaitAsOneOrNull()
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return database.chaptersQueries
            .getChaptersByMangaId(
                mangaId = mangaId,
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .subscribeToList()
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return database.chaptersQueries
            .getChapterByUrlAndMangaId(url, mangaId, ::mapChapter)
            .awaitAsOneOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapChapter(
        id: Long,
        mangaId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        memo: JsonObject,
    ): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        memo = memo,
    )
}
