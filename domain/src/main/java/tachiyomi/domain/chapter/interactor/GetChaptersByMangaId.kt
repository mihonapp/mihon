package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

@Inject
class GetChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter> {
        return try {
            chapterRepository.getChapterByMangaId(mangaId, applyScanlatorFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun await(mangaIds: List<Long>, applyScanlatorFilter: Boolean = false): Map<Long, List<Chapter>> {
        return try {
            chapterRepository.getChaptersMapByMangaIds(mangaIds, applyScanlatorFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }
}
