package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.DatabaseHelper
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

abstract class AbstractBackupManager(protected val context: Context) {

    internal val db: DatabaseHelper = Injekt.get()
    internal val sourceManager: SourceManager = Injekt.get()
    internal val trackManager: TrackManager = Injekt.get()
    protected val preferences: PreferencesHelper = Injekt.get()

    abstract fun createBackup(uri: Uri, flags: Int, isAutoBackup: Boolean): String

    /**
     * Returns manga
     *
     * @return [Manga], null if not found
     */
    internal fun getMangaFromDatabase(manga: Manga): Manga? =
        db.getManga(manga.url, manga.source).executeAsBlocking()

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
        val syncedChapters = syncChaptersWithSource(db, fetchedChapters, manga, source)
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
    protected fun getFavoriteManga(): List<Manga> =
        db.getFavoriteMangas().executeAsBlocking()

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    internal fun insertManga(manga: Manga): Long? =
        db.insertManga(manga).executeAsBlocking().insertedId()

    /**
     * Inserts list of chapters
     */
    protected fun insertChapters(chapters: List<Chapter>) {
        db.insertChapters(chapters).executeAsBlocking()
    }

    /**
     * Updates a list of chapters
     */
    protected fun updateChapters(chapters: List<Chapter>) {
        db.updateChaptersBackup(chapters).executeAsBlocking()
    }

    /**
     * Updates a list of chapters with known database ids
     */
    protected fun updateKnownChapters(chapters: List<Chapter>) {
        db.updateKnownChaptersBackup(chapters).executeAsBlocking()
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    protected fun numberOfBackups(): Int = preferences.numberOfBackups().get()
}
