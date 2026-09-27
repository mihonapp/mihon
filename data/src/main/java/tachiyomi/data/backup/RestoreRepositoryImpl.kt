package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Database
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import java.util.Date
import kotlin.math.max

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RestoreRepositoryImpl(
    private val database: Database,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val trackRepository: TrackRepository,
) : RestoreRepository {

    override suspend fun getMangaUrlsBySourceId(): Map<Long, List<String>> {
        return database.mangasQueries
            .getAllMangaSourceAndUrl()
            .awaitAsList()
            .groupBy({ it.source }, { it.url })
    }

    override suspend fun restoreManga(entries: List<RestoredManga>, update: suspend (Manga) -> MangaUpdate) {
        database.transaction {
            entries.forEach { entry ->
                // A cancelled restore stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                mangaRepository.update(update(restore(entry)))
            }
        }
    }

    private suspend fun restore(entry: RestoredManga): Manga {
        val dbManga = mangaRepository.getMangaByUrlAndSourceId(entry.manga.url, entry.manga.source)
        val manga = if (dbManga == null) {
            entry.manga.copy(id = insertManga(entry.manga))
        } else {
            updateManga(mergeManga(entry.manga, dbManga))
        }

        if (entry.categoryIds.isNotEmpty()) {
            mangaRepository.setMangaCategories(manga.id, entry.categoryIds)
        }
        restoreChapters(manga, entry.chapters)
        restoreTracking(manga, entry.tracks)
        restoreHistory(manga, entry.history)
        restoreExcludedScanlators(manga, entry.excludedScanlators)
        return manga
    }

    private fun mergeManga(manga: Manga, dbManga: Manga): Manga {
        val details = if (dbManga.initialized || !manga.initialized) dbManga else manga
        return dbManga.copy(
            favoriteAt = if (dbManga.favorite || manga.favorite) {
                listOfNotNull(dbManga.favoriteAt, manga.favoriteAt).filter { it > 0 }.minOrNull() ?: 0L
            } else {
                null
            },
            title = details.title,
            artist = details.artist,
            author = details.author,
            description = details.description,
            genre = details.genre,
            status = details.status,
            thumbnailUrl = details.thumbnailUrl,
            updateStrategy = details.updateStrategy,
            initialized = dbManga.initialized || manga.initialized,
            // Merge both backup and local data with local winning
            memo = JsonObject(manga.memo + dbManga.memo),
        )
    }

    private suspend fun updateManga(manga: Manga): Manga {
        database.mangasQueries.updateFromBackup(
            favoriteAt = manga.favoriteAt,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            title = manga.title,
            status = manga.status,
            thumbnailUrl = manga.thumbnailUrl,
            lastUpdate = manga.lastUpdate,
            initialized = manga.initialized,
            viewer = manga.viewerFlags,
            chapterFlags = manga.chapterFlags,
            coverLastModified = manga.coverLastModified,
            mangaId = manga.id,
            updateStrategy = manga.updateStrategy,
            notes = manga.notes,
            memo = manga.memo,
        )
        return manga
    }

    private suspend fun insertManga(manga: Manga): Long {
        return database.mangasQueries.insertReturningId(
            favoriteAt = manga.favoriteAt,
            source = manga.source,
            url = manga.url,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            title = manga.title,
            status = manga.status,
            thumbnailUrl = manga.thumbnailUrl,
            lastUpdate = manga.lastUpdate,
            nextUpdate = 0L,
            calculateInterval = 0L,
            initialized = manga.initialized,
            viewerFlags = manga.viewerFlags,
            chapterFlags = manga.chapterFlags,
            coverLastModified = manga.coverLastModified,
            updateStrategy = manga.updateStrategy,
            notes = manga.notes,
            memo = manga.memo,
        )
            .awaitAsOne()
    }

    private suspend fun restoreChapters(manga: Manga, restoredChapters: List<Chapter>) {
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = restoredChapters
            .groupBy { it.url }
            .map { (_, copies) ->
                copies.reduce { kept, other ->
                    kept.copy(
                        read = kept.read || other.read,
                        bookmark = kept.bookmark || other.bookmark,
                        lastPageRead = max(kept.lastPageRead, other.lastPageRead),
                    )
                }
            }
            .mapNotNull {
                val chapter = it.copy(mangaId = manga.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: // New chapter
                    return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        read = chapter.read || dbChapter.read,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                        lastPageRead = max(chapter.lastPageRead, dbChapter.lastPageRead),
                        dateFetch = dbChapter.dateFetch,
                        sourceOrder = dbChapter.sourceOrder,
                        // Merge both backup and local data with local winning
                        memo = JsonObject(chapter.memo + dbChapter.memo),
                    )
            }
            .partition { it.id > 0 }

        newChapters.forEach { chapter ->
            database.chaptersQueries.insert(
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
                chapter.memo,
            )
        }
        existingChapters.forEach { chapter ->
            database.chaptersQueries.updateFromBackup(
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.lastPageRead,
                chapterId = chapter.id,
                memo = chapter.memo,
            )
        }
    }

    private fun Chapter.forComparison() =
        this.copy(id = 0L, mangaId = 0L, dateFetch = 0L, dateUpload = 0L)

    private suspend fun restoreHistory(manga: Manga, restoredHistory: List<RestoredHistory>) {
        val toUpdate = restoredHistory
            .groupBy { it.chapterUrl }
            .mapNotNull { (chapterUrl, copies) ->
                val readAt = copies.maxOf { it.readAt?.time ?: 0L }
                val readDuration = copies.sumOf { it.readDuration }
                val dbHistory = database.historyQueries
                    .getHistoryByChapterUrlAndMangaId(chapterUrl, manga.id)
                    .awaitAsOneOrNull()

                if (dbHistory == null) {
                    val chapter = database.chaptersQueries
                        .getChapterByUrlAndMangaId(chapterUrl, manga.id)
                        .awaitAsOneOrNull()
                        // Chapter doesn't exist; skip
                        ?: return@mapNotNull null
                    // New history entry
                    return@mapNotNull Triple(chapter._id, Date(readAt), readDuration)
                }

                // Update history entry
                Triple(
                    dbHistory.chapter_id,
                    Date(max(readAt, dbHistory.last_read?.time ?: 0L)),
                    max(readDuration, dbHistory.time_read) - dbHistory.time_read,
                )
            }

        toUpdate.forEach { (chapterId, readAt, readDuration) ->
            database.historyQueries.upsert(chapterId, readAt, readDuration)
        }
    }

    private suspend fun restoreTracking(manga: Manga, restoredTracks: List<Track>) {
        val dbTrackByTrackerId = trackRepository.getTracksByMangaId(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = restoredTracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            trackRepository.upsertAll(newTracks)
        }

        existingTracks.forEach { track ->
            database.manga_syncQueries.update(
                track.mangaId,
                track.trackerId,
                track.remoteId,
                track.libraryId,
                track.title,
                track.lastChapterRead,
                track.totalChapters,
                track.status,
                track.score,
                track.remoteUrl,
                track.startDate,
                track.finishDate,
                track.private,
                track.id,
            )
        }
    }

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByMangaId(manga.id)
            .awaitAsList()
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        toInsert.forEach { database.excluded_scanlatorsQueries.insert(manga.id, it) }
    }
}
