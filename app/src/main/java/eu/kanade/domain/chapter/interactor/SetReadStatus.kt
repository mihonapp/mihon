package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority

class SetReadStatus(
    private val preferences: PreferencesHelper,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
) {

    private val mapper = { chapter: Chapter, read: Boolean ->
        ChapterUpdate(
            read = read,
            lastPageRead = if (!read) 0 else null,
            id = chapter.id,
        )
    }

    suspend fun await(read: Boolean, vararg values: Chapter): Result = withContext(NonCancellable) {
        val chapters = values.filterNot { it.read == read }

        if (chapters.isEmpty()) {
            return@withContext Result.NoChapters
        }

        val manga = chapters.fold(mutableSetOf<Manga>()) { acc, chapter ->
            if (acc.all { it.id != chapter.mangaId }) {
                acc += mangaRepository.getMangaById(chapter.mangaId)
            }
            acc
        }

        try {
            chapterRepository.updateAll(
                chapters.map { chapter ->
                    mapper(chapter, read)
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withContext Result.InternalError(e)
        }

        if (read && preferences.removeAfterMarkedAsRead()) {
            manga.forEach {
                deleteDownload.awaitAll(
                    manga = it,
                    values = chapters
                        .filter { chapter -> it.id == chapter.mangaId }
                        .toTypedArray(),
                )
            }
        }

        Result.Success
    }

    suspend fun await(mangaId: Long, read: Boolean): Result = withContext(NonCancellable) {
        await(
            read = read,
            values = chapterRepository
                .getChapterByMangaId(mangaId)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, read: Boolean) =
        await(manga.id, read)

    sealed class Result {
        object Success : Result()
        object NoChapters : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
