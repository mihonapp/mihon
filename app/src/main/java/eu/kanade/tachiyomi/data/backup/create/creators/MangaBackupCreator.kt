package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) {
    suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (mangas.isEmpty()) {
            // skip all other SQL queries
            return emptyList()
        }

        val excludedScanlatorsMap = handler.awaitList {
            excluded_scanlatorsQueries.listAll { mangaId, scanlator ->
                Pair(mangaId, scanlator)
            }
        }.groupBy({ it.first }, { it.second })

        logcat(LogPriority.DEBUG) { "Begin load chapters" }

        val chaptersMap = if (options.chapters) {
            handler.awaitList {
                chaptersQueries.listAll(backupChapterMapper)
            }.groupBy({ it.first }, { it.second })
        } else emptyMap()

        logcat(LogPriority.DEBUG) { "End load chapters, found ${chaptersMap.values.sumOf { it.size }}" }

        val categoriesMap = if (options.categories) {
            getCategories.awaitWithMangaId()
        } else emptyMap()

        val tracksMap = if (options.tracking) {
            handler.awaitList {
                manga_syncQueries.getTracks(backupTrackMapper)
            }.groupBy({ it.first }, { it.second })
        } else emptyMap()

        val historiesMap = if (options.history) {
            handler.awaitList {
                historyQueries.listHistoriesWithMangaId { mangaId, url, lastRead, timeRead ->
                    Pair(mangaId, BackupHistory(url, lastRead?.time ?: 0L, timeRead))
                }
            }.groupBy({ it.first }, { it.second })
        } else emptyMap()

        return mangas.map {
            backupManga(it, options,
                excludedScanlatorsMap,
                chaptersMap,
                categoriesMap,
                tracksMap,
                historiesMap)
        }
    }

    private fun backupManga(
        manga: Manga,
        options: BackupOptions,
        excludedScanlatorsMap: Map<Long, List<String>>,
        chaptersMap: Map<Long, List<BackupChapter>>,
        categoriesMap: Map<Long, List<Category>>,
        tracksMap: Map<Long, List<BackupTracking>>,
        historiesMap: Map<Long, List<BackupHistory>>
    ): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga()

        excludedScanlatorsMap[manga.id]?.let {
            mangaObject.excludedScanlators = it
        }

        if (options.chapters) {
            // Backup all the chapters
            chaptersMap[manga.id]
                ?.takeUnless(List<BackupChapter>::isNullOrEmpty)
                ?.let { mangaObject.chapters = it }
        }

        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = categoriesMap[manga.id]
            if (categoriesForManga?.isNotEmpty() == true) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = tracksMap[manga.id]
            if (tracks?.isNotEmpty() == true) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
            val history = historiesMap[manga.id]
            if (history?.isNotEmpty() == true) {
                mangaObject.history = history
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
        version = this.version,
    )
