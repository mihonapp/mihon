package eu.kanade.domain.chapter.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga

@Inject
@SingleIn(AppScope::class)
class SetChapterHidden(
    private val chapterRepository: ChapterRepository,
    private val downloadManager: DownloadManager,
) {
    private val lock = Mutex()

    suspend fun await(chapters: List<Chapter>, manga: Manga, source: Source, hidden: Boolean) = lock.withLock {
        if (chapters.isEmpty()) return@withLock
        // Do not use UpdateChapter: it logs and swallows database errors. No files may be deleted on failure.
        chapterRepository.updateAll(chapters.map { ChapterUpdate(it.id) { this.hidden = hidden } })
        // Identify cleanup failures only after the visibility write has committed.
        if (hidden) {
            try {
                downloadManager.deleteHiddenChapters(chapters, manga, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw CleanupFailed(e)
            }
        }
    }

    /** A delayed Retry must respect any subsequent Unhide, and never write stale chapter state. */
    suspend fun retryCleanup(chapters: List<Chapter>, manga: Manga, source: Source) = lock.withLock {
        if (chapters.isEmpty()) return@withLock
        require(chapters.all { it.mangaId == manga.id })
        val targetIds = chapters.mapTo(HashSet()) { it.id }
        val stillHidden = chapterRepository.getChapterByMangaId(manga.id)
            .filter { it.id in targetIds && it.hidden }
        if (stillHidden.isNotEmpty()) downloadManager.deleteHiddenChapters(stillHidden, manga, source)
    }

    class CleanupFailed(cause: Exception) : Exception(cause)
}
