package eu.kanade.domain.chapter.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetAvailableScanlators(
    private val repository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return repository.getScanlatorsByMangaId(mangaId).toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return repository.getScanlatorsByMangaIdAsFlow(mangaId)
            .map { it.toSet() }
    }
}
