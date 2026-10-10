package mihon.data.sync

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
import mihon.domain.sync.SyncMergePolicy
import mihon.domain.sync.SyncMergePolicy.ChapterSet
import mihon.domain.sync.SyncMergePolicy.ChapterState
import mihon.domain.sync.model.SyncMangaState
import mihon.domain.sync.model.SyncedChapter
import mihon.domain.sync.model.SyncedManga
import mihon.domain.sync.repository.SyncRepository
import tachiyomi.data.Database
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import java.util.Date
import kotlin.math.max

/**
 * Merges what another device published into the library, by the sync's rules rather than a restore's.
 *
 * A restore replays a file that may be months old, so it only ever adds. Here both sides are live
 * devices, so the latest decision wins, removals included. Every rule must also give the same answer
 * on both devices, or each keeps its own value, publishes it, and the two never settle.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncRepositoryImpl(
    private val database: Database,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val trackRepository: TrackRepository,
) : SyncRepository {

    override suspend fun getStates(): Map<Long, SyncMangaState> {
        return database.sync_mangaQueries
            .getStates()
            .awaitAsList()
            .associate { it.manga_id to SyncMangaState(it.favorite_changed_at, it.change_count) }
    }

    override suspend fun getReadChangedAt(mangaId: Long): Map<String, Long> {
        return database.sync_chapterQueries
            .getReadChangedAtByMangaId(mangaId)
            .awaitAsList()
            .associate { it.remote_url to it.read_changed_at }
    }

    override suspend fun merge(entries: List<SyncedManga>, update: suspend (Manga) -> MangaUpdate) {
        database.transaction {
            entries.forEach { entry ->
                // A cancelled sync stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                val manga = merge(entry) ?: return@forEach
                mangaRepository.update(update(manga))
            }
        }
    }

    private suspend fun merge(entry: SyncedManga): Manga? {
        val dbManga = mangaRepository.getMangaByUrlAndSourceId(entry.manga.url, entry.manga.source)
        // Another device took the entry out of its library; this one never had it, so there is nothing to
        // remove, and adding it would only leave a stray entry behind.
        if (dbManga == null && !entry.manga.favorite) return null
        val chapterSet = SyncMergePolicy.resolveChapterSet(
            isSync = dbManga != null,
            localListAt = dbManga?.lastUpdate ?: 0,
            incomingListAt = entry.chapterListAt,
        )
        val manga = if (dbManga == null) insertManga(entry) else updateManga(entry, dbManga, chapterSet)

        // The incoming side owns the categories: it published because something changed there, and an
        // empty list is the only way leaving the last category can reach this device.
        entry.categoryIds?.let { mangaRepository.setMangaCategories(manga.id, it) }
        mergeChapters(manga, entry.chapters, chapterSet)
        mergeTracking(manga, entry.tracks)
        mergeHistory(manga, entry.history)
        entry.excludedScanlators.forEach { database.excluded_scanlatorQueries.insert(manga.id, it) }
        return manga
    }

    private suspend fun insertManga(entry: SyncedManga): Manga {
        // A new entry's chapter list is the incoming one, as of when that device refreshed it
        val manga = entry.manga.copy(lastUpdate = entry.chapterListAt)
        val id = database.mangaQueries.insertReturningId(
            userFavoriteAt = manga.favoriteAt,
            sourceId = manga.source,
            remoteUrl = manga.url,
            remoteArtist = manga.artist,
            remoteAuthor = manga.author,
            remoteDescription = manga.description,
            remoteGenre = manga.genre,
            remoteTitle = manga.title,
            remoteStatus = manga.status,
            remoteCover = manga.thumbnailUrl,
            stateChapterLastUpdate = manga.lastUpdate,
            stateChapterNextUpdate = 0L,
            stateChapterFetchInterval = 0L,
            stateInitialized = manga.initialized,
            userReaderFlags = manga.viewerFlags,
            userChapterFlags = manga.chapterFlags,
            stateCoverLastModified = manga.coverLastModified,
            remoteUpdateStrategy = manga.updateStrategy,
            userNotes = manga.notes,
            remoteMemo = manga.memo,
        )
            .awaitAsOne()
        // After the insert, whose trigger dated the entry by this device's clock
        database.sync_mangaQueries.setFavoriteChangedAt(id, entry.favoriteChangedAt)
        return manga.copy(id = id)
    }

    private suspend fun updateManga(entry: SyncedManga, dbManga: Manga, chapterSet: ChapterSet): Manga {
        val incoming = entry.manga
        val localChangedAt = database.sync_mangaQueries
            .getFavoriteChangedAt(dbManga.id)
            .awaitAsOneOrNull() ?: 0L
        val favorite = SyncMergePolicy.resolveFavorite(
            isSync = true,
            localFavorite = dbManga.favorite,
            localModifiedAt = localChangedAt,
            incomingFavorite = incoming.favorite,
            incomingModifiedAt = entry.favoriteChangedAt,
        )
        // Details come from the source, which each device asks itself; the incoming ones only stand in
        // for details this device never fetched.
        val details = if (dbManga.initialized || !incoming.initialized) dbManga else incoming
        val manga = dbManga.copy(
            favoriteAt = if (favorite) {
                listOfNotNull(dbManga.favoriteAt, incoming.favoriteAt).filter { it > 0 }.minOrNull() ?: 0L
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
            initialized = dbManga.initialized || incoming.initialized,
            // What the user chose for the entry follows the device that published: it published because
            // something changed there, and keeping the local choices sent the old ones straight back.
            viewerFlags = incoming.viewerFlags,
            chapterFlags = incoming.chapterFlags,
            notes = incoming.notes,
            updateStrategy = incoming.updateStrategy,
            // Only this device knows when its chapter list last changed, unless the incoming list replaces it
            lastUpdate = if (chapterSet == ChapterSet.Incoming) entry.chapterListAt else dbManga.lastUpdate,
            // Merge both sides' data with the local one winning
            memo = JsonObject(incoming.memo + dbManga.memo),
        )
        database.mangaQueries.updateFromBackup(
            userFavoriteAt = manga.favoriteAt,
            remoteArtist = manga.artist,
            remoteAuthor = manga.author,
            remoteDescription = manga.description,
            remoteGenre = manga.genre,
            remoteTitle = manga.title,
            remoteStatus = manga.status,
            remoteCover = manga.thumbnailUrl,
            stateChapterLastUpdate = manga.lastUpdate,
            stateInitialized = manga.initialized,
            userReaderFlags = manga.viewerFlags,
            userChapterFlags = manga.chapterFlags,
            stateCoverLastModified = manga.coverLastModified,
            id = manga.id,
            remoteUpdateStrategy = manga.updateStrategy,
            userNotes = manga.notes,
            remoteMemo = manga.memo,
        )
        // After the update, whose trigger dated any change by this device's clock: the time the merge
        // settled on is the one both devices have to agree on.
        database.sync_mangaQueries.setFavoriteChangedAt(
            mangaId = manga.id,
            favoriteChangedAt = SyncMergePolicy.newestTimestamp(localChangedAt, entry.favoriteChangedAt) ?: 0L,
        )
        return manga
    }

    private suspend fun mergeChapters(manga: Manga, incoming: List<SyncedChapter>, chapterSet: ChapterSet) {
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(manga.id).associateBy { it.url }
        val localDecidedAt = getReadChangedAt(manga.id)

        val newChapters = mutableListOf<SyncedChapter>()
        val changedChapters = mutableListOf<Pair<Chapter, Long>>()
        incoming.forEach { synced ->
            val chapter = synced.chapter.copy(mangaId = manga.id)
            val dbChapter = dbChaptersByUrl[chapter.url]
            if (dbChapter == null) {
                newChapters += synced.copy(chapter = chapter)
                return@forEach
            }
            val local = ChapterState(
                read = dbChapter.read,
                lastPageRead = dbChapter.lastPageRead,
                decidedAt = localDecidedAt[chapter.url] ?: 0L,
                bookmark = dbChapter.bookmark,
            )
            val resolved = SyncMergePolicy.resolveChapterState(
                isSync = true,
                local = local,
                incoming = ChapterState(
                    read = chapter.read,
                    lastPageRead = chapter.lastPageRead,
                    decidedAt = synced.readChangedAt,
                    bookmark = chapter.bookmark,
                ),
            )
            // The time counts as well: two devices that merged the same change at different moments hold
            // different times, and stopping at equal states left them disagreeing for good, each one
            // publishing the entry back to the other on every round.
            if (resolved == local) return@forEach
            changedChapters += dbChapter.copy(
                read = resolved.read,
                bookmark = resolved.bookmark,
                lastPageRead = resolved.lastPageRead,
                memo = JsonObject(chapter.memo + dbChapter.memo),
            ) to resolved.decidedAt
        }

        // The incoming device refreshed later, so what only this device still lists is gone from the
        // source. An empty incoming list proves nothing of the kind, so it never empties this one.
        if (chapterSet == ChapterSet.Incoming && incoming.isNotEmpty()) {
            val listed = incoming.mapTo(HashSet()) { it.chapter.url }
            val gone = dbChaptersByUrl.values.filter { it.url !in listed }.map { it.id }
            if (gone.isNotEmpty()) database.chapterQueries.removeChaptersWithIds(gone)
        }

        // This device refreshed later, so what only the incoming list has left the source since
        if (chapterSet != ChapterSet.Local) {
            newChapters.forEach { synced -> insertChapter(synced) }
        }

        changedChapters.forEach { (chapter, decidedAt) ->
            database.chapterQueries.updateFromBackup(
                userRead = chapter.read,
                userBookmark = chapter.bookmark,
                userLastPageRead = chapter.lastPageRead,
                id = chapter.id,
                remoteMemo = chapter.memo,
            )
            // After the update, whose trigger dated it by this device's clock
            database.sync_chapterQueries.setReadChangedAt(chapterId = chapter.id, readChangedAt = decidedAt)
        }
    }

    private suspend fun insertChapter(synced: SyncedChapter) {
        val chapter = synced.chapter
        val id = database.chapterQueries.insertReturningId(
            mangaId = chapter.mangaId,
            remoteUrl = chapter.url,
            remoteName = chapter.name,
            remoteScanlator = chapter.scanlator,
            userRead = chapter.read,
            userBookmark = chapter.bookmark,
            userLastPageRead = chapter.lastPageRead,
            remoteChapterNumber = chapter.chapterNumber,
            remoteOrder = chapter.sourceOrder,
            stateDateFetch = chapter.dateFetch,
            remoteDateUpload = chapter.dateUpload,
            remoteMemo = chapter.memo,
        )
            .awaitAsOneOrNull()
        if (id != null && synced.readChangedAt > 0) {
            database.sync_chapterQueries.setReadChangedAt(chapterId = id, readChangedAt = synced.readChangedAt)
        }
    }

    /**
     * The binding and the furthest chapter read are what the other device can tell this one about a
     * track. Its status, score and dates come from the tracker itself.
     */
    private suspend fun mergeTracking(manga: Manga, tracks: List<Track>) {
        val dbTrackByTrackerId = trackRepository.getTracksByMangaId(manga.id).associateBy { it.trackerId }
        val (existingTracks, newTracks) = tracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    // New track; let the database assign its id
                    ?: return@mapNotNull track.copy(id = 0, mangaId = manga.id)
                dbTrack
                    .copy(
                        remoteId = track.remoteId,
                        libraryId = track.libraryId,
                        lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                    )
                    .takeIf { it != dbTrack }
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            trackRepository.upsertAll(newTracks)
        }
        existingTracks.forEach { track ->
            database.manga_trackQueries.update(
                mangaId = track.mangaId,
                trackerId = track.trackerId,
                remoteId = track.remoteId,
                libraryId = track.libraryId,
                title = track.title,
                lastChapterRead = track.lastChapterRead,
                totalChapters = track.totalChapters,
                status = track.status,
                score = track.score,
                remoteUrl = track.remoteUrl,
                startDate = track.startDate,
                finishDate = track.finishDate,
                `private` = track.private,
                id = track.id,
            )
        }
    }

    /**
     * Reading history only ever grows, as in a restore: the latest read wins, and the time spent reading
     * is the longest either device recorded.
     */
    private suspend fun mergeHistory(manga: Manga, history: List<RestoredHistory>) {
        if (history.isEmpty()) return
        val chapterIdsByUrl = chapterRepository.getChapterByMangaId(manga.id).associate { it.url to it.id }
        val dbHistoryByChapterId = database.historyQueries
            .getHistoryByMangaId(manga.id)
            .awaitAsList()
            .associateBy { it.chapter_id }

        history
            .filter { it.chapterUrl in chapterIdsByUrl }
            .groupBy { chapterIdsByUrl.getValue(it.chapterUrl) }
            .forEach { (chapterId, copies) ->
                val readAt = copies.maxOf { it.readAt?.time ?: 0L }
                val readDuration = copies.maxOf { it.readDuration }
                val dbHistory = dbHistoryByChapterId[chapterId]
                if (dbHistory == null) {
                    database.historyQueries.upsert(
                        chapterId = chapterId,
                        readAt = Date(readAt),
                        readDuration = readDuration,
                    )
                    return@forEach
                }
                val dbReadAt = dbHistory.read_at?.time ?: 0L
                if (readAt <= dbReadAt && readDuration <= dbHistory.read_duration) return@forEach
                // The upsert adds the duration it is given, so only the difference goes in. 0 is kept rather
                // than written as NULL, since it marks history the user removed.
                database.historyQueries.upsert(
                    chapterId = chapterId,
                    readAt = Date(max(readAt, dbReadAt)),
                    readDuration = max(readDuration, dbHistory.read_duration) - dbHistory.read_duration,
                )
            }
    }
}
