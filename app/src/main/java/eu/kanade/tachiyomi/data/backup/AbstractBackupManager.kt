package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.toLong
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import data.Mangas as DbManga

abstract class AbstractBackupManager(protected val context: Context) {

    protected val handler: DatabaseHandler = Injekt.get()

    internal val sourceManager: SourceManager = Injekt.get()
    internal val trackManager: TrackManager = Injekt.get()
    protected val preferences: PreferencesHelper = Injekt.get()

    abstract suspend fun createBackup(uri: Uri, flags: Int, isAutoBackup: Boolean): String

    /**
     * Returns manga
     *
     * @return [Manga], null if not found
     */
    internal suspend fun getMangaFromDatabase(url: String, source: Long): DbManga? {
        return handler.awaitOneOrNull { mangasQueries.getMangaByUrlAndSource(url, source) }
    }

    /**
     * Fetches chapter information.
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @param chapters list of chapters in the backup
     * @return Updated manga chapters.
     */
    internal suspend fun restoreChapters(source: Source, manga: Manga, chapters: List<Chapter>): Pair<List<Chapter>, List<Chapter>> {
        val fetchedChapters = source.getChapterList(manga.toMangaInfo())
            .map { it.toSChapter() }
        val syncedChapters = syncChaptersWithSource(fetchedChapters, manga, source)
        if (syncedChapters.first.isNotEmpty()) {
            chapters.forEach { it.manga_id = manga.id }
            updateChapters(chapters)
        }
        return syncedChapters
    }

    /**
     * Returns list containing manga from library
     *
     * @return [Manga] from library
     */
    protected suspend fun getFavoriteManga(): List<DbManga> {
        return handler.awaitList { mangasQueries.getFavorites() }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    internal suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOne(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.getGenres(),
                title = manga.title,
                status = manga.status.toLong(),
                thumbnail_url = manga.thumbnail_url,
                favorite = manga.favorite,
                last_update = manga.last_update,
                next_update = 0L,
                initialized = manga.initialized,
                viewer = manga.viewer_flags.toLong(),
                chapter_flags = manga.chapter_flags.toLong(),
                cover_last_modified = manga.cover_last_modified,
                date_added = manga.date_added,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    internal suspend fun updateManga(manga: Manga): Long {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite.toLong(),
                lastUpdate = manga.last_update,
                initialized = manga.initialized.toLong(),
                viewer = manga.viewer_flags.toLong(),
                chapterFlags = manga.chapter_flags.toLong(),
                coverLastModified = manga.cover_last_modified,
                dateAdded = manga.date_added,
                mangaId = manga.id!!,
            )
        }
        return manga.id!!
    }

    /**
     * Inserts list of chapters
     */
    protected suspend fun insertChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.manga_id!!,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.last_page_read.toLong(),
                    chapter.chapter_number,
                    chapter.source_order.toLong(),
                    chapter.date_fetch,
                    chapter.date_upload,
                )
            }
        }
    }

    /**
     * Updates a list of chapters
     */
    protected suspend fun updateChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    chapter.manga_id!!,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read.toLong(),
                    chapter.bookmark.toLong(),
                    chapter.last_page_read.toLong(),
                    chapter.chapter_number.toDouble(),
                    chapter.source_order.toLong(),
                    chapter.date_fetch,
                    chapter.date_upload,
                    chapter.id!!,
                )
            }
        }
    }

    /**
     * Updates a list of chapters with known database ids
     */
    protected suspend fun updateKnownChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read.toLong(),
                    bookmark = chapter.bookmark.toLong(),
                    lastPageRead = chapter.last_page_read.toLong(),
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id!!,
                )
            }
        }
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    protected fun numberOfBackups(): Int = preferences.numberOfBackups().get()
}
