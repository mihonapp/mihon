package eu.kanade.domain.category.interactor

import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaCategories(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        try {
            mangaRepository.setMangaCategories(mangaId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
