package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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

    suspend fun await(mangaIds: List<Long>, categoryIds: List<Long>) {
        try {
            mangaRepository.setMangasCategories(mangaIds, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun add(mangaIds: List<Long>, categoryIds: List<Long>) {
        try {
            mangaRepository.addMangasCategories(mangaIds, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun remove(mangaIds: List<Long>, categoryIds: List<Long>) {
        try {
            mangaRepository.removeMangasCategories(mangaIds, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
