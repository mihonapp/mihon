package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class MangaRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
) {

    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                compareBy<BackupManga> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restoreManga(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbManga = findExistingManga(backupManga)
            val manga = backupManga.getMangaImpl()
            val restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                restoreExistingManga(manga, dbManga)
            }

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history + backupManga.brokenHistory.map { it.toBackupHistory() },
                tracks = backupManga.tracking,
            )
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        return getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Manga {
        return if (manga.lastModifiedAt > dbManga.lastModifiedAt) {
            updateManga(dbManga.copyFrom(manga).copy(id = dbManga.id))
        } else {
            updateManga(manga.copyFrom(dbManga).copy(id = dbManga.id))
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            artist = newer.artist,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
        )
    }

    private suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(separator = ", "),
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                mangaId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
            )
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga.copy(
            initialized = manga.description != null,
            id = insertManga(manga),
        )
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull {
                val chapter = it.toChapterImpl().copy(mangaId = manga.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: // New chapter
                    return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing chapter
                var updatedChapter = chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                    )
                if (dbChapter.read && !updatedChapter.read) {
                    updatedChapter = updatedChapter.copy(
                        read = true,
                        lastPageRead = dbChapter.lastPageRead,
                    )
                } else if (updatedChapter.lastPageRead == 0L && dbChapter.lastPageRead != 0L) {
                    updatedChapter = updatedChapter.copy(
                        lastPageRead = dbChapter.lastPageRead,
                    )
                }
                updatedChapter
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun Chapter.forComparison() =
        this.copy(id = 0L, mangaId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L)

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
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
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id,
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(history)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        return manga
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val mangaCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (mangaCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(manga.id)
                mangaCategoriesToUpdate.forEach { (mangaId, categoryId) ->
                    mangas_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { historyQueries.getHistoryByChapterUrl(history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitOneOrNull { chaptersQueries.getChapterByUrl(history.url) }
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.chapter_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_read) - dbHistory.time_read,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
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
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    manga_syncQueries.update(
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
                        track.id,
                    )
                }
            }
        }
    }

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)
}
