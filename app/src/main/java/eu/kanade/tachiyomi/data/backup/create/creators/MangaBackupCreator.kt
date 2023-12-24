package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.backup.model.BackupChapter
import tachiyomi.domain.backup.model.BackupHistory
import tachiyomi.domain.backup.model.BackupManga
import tachiyomi.domain.backup.model.backupChapterMapper
import tachiyomi.domain.backup.model.backupTrackMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
) {

    suspend fun backupMangas(mangas: List<Manga>, flags: Int): List<BackupManga> {
        return mangas.map {
            backupManga(it, flags)
        }
    }

    private suspend fun backupManga(manga: Manga, options: Int): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga()

        // Check if user wants chapter information in backup
        if (options and BackupCreateFlags.BACKUP_CHAPTER == BackupCreateFlags.BACKUP_CHAPTER) {
            // Backup all the chapters
            handler.awaitList {
                chaptersQueries.getChaptersByMangaId(
                    mangaId = manga.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        // Check if user wants category information in backup
        if (options and BackupCreateFlags.BACKUP_CATEGORY == BackupCreateFlags.BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        // Check if user wants track information in backup
        if (options and BackupCreateFlags.BACKUP_TRACK == BackupCreateFlags.BACKUP_TRACK) {
            val tracks = handler.awaitList { manga_syncQueries.getTracksByMangaId(manga.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        // Check if user wants history information in backup
        if (options and BackupCreateFlags.BACKUP_HISTORY == BackupCreateFlags.BACKUP_HISTORY) {
            val historyByMangaId = getHistory.await(manga.id)
            if (historyByMangaId.isNotEmpty()) {
                val history = historyByMangaId.map { history ->
                    val chapter = handler.awaitOne { chaptersQueries.getChapterById(history.chapterId) }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }
}

private fun Manga.toBackupManga() =
    BackupManga(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
    )
