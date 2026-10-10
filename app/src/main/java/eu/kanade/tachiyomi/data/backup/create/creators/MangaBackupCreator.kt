package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flow
import mihon.core.common.extensions.toByteArray
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.repository.TrackRepository

@Inject
class MangaBackupCreator(
    private val mangaRepository: MangaRepository,
    private val categoryRepository: CategoryRepository,
    private val historyRepository: HistoryRepository,
    private val chapterRepository: ChapterRepository,
    private val trackRepository: TrackRepository,
) {

    operator fun invoke(mangas: List<Manga>, options: BackupOptions, chunkSize: Int): Flow<BackupManga> = flow {
        mangas.asFlow().chunked(chunkSize).collect { chunk ->
            for (manga in backupManga(chunk, options)) {
                emit(manga)
            }
        }
    }

    private suspend fun backupManga(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val excludedScanlatorsMap = mangaRepository.getExcludedScanlators(mangaIds)

        val chaptersMap = if (options.chapters || options.history) {
            chapterRepository.getChaptersMapByMangaIds(mangaIds, applyScanlatorFilter = false)
        } else {
            emptyMap()
        }

        val categoriesMap = if (options.categories) {
            categoryRepository.getCategoriesMapByMangaIds(mangaIds)
        } else {
            emptyMap()
        }

        val tracksMap = if (options.tracking) {
            trackRepository.getTracksMapByMangaIds(mangaIds)
        } else {
            emptyMap()
        }

        val historyMap = if (options.history) {
            historyRepository.getHistoryMapByMangaIds(mangaIds)
        } else {
            emptyMap()
        }

        val chaptersByIdMap = if (options.history) {
            chaptersMap.values.flatten().associateBy { it.id }
        } else {
            emptyMap()
        }

        return mangas.map { manga ->
            val mangaObject = manga.toBackupManga()

            excludedScanlatorsMap[manga.id]
                ?.takeUnless { it.isEmpty() }
                ?.let { mangaObject.excludedScanlators = it }

            if (options.chapters) {
                chaptersMap[manga.id]
                    ?.map { it.toBackupChapter() }
                    ?.takeUnless { it.isEmpty() }
                    ?.let { mangaObject.chapters = it }
            }

            if (options.categories) {
                categoriesMap[manga.id]
                    ?.map { it.order }
                    ?.takeUnless { it.isEmpty() }
                    ?.let { mangaObject.categories = it }
            }

            if (options.tracking) {
                tracksMap[manga.id]
                    ?.map { it.toBackupTracking() }
                    ?.takeUnless { it.isEmpty() }
                    ?.let { mangaObject.tracking = it }
            }

            if (options.history) {
                historyMap[manga.id]
                    ?.mapNotNull { history ->
                        chaptersByIdMap[history.chapterId]?.let { chapter ->
                            BackupHistory(
                                url = chapter.url,
                                lastRead = history.readAt?.time ?: 0L,
                                readDuration = history.readDuration,
                            )
                        }
                    }
                    ?.takeUnless { it.isEmpty() }
                    ?.let { mangaObject.history = it }
            }

            mangaObject
        }
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
        dateAdded = this.favoriteAt ?: 0L,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        notes = this.notes,
        initialized = this.initialized,
        memo = this.memo.toByteArray(),
    )
