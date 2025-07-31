package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.repository.MangaRepository

class GetCategoriesPerManga(
    private val categoryRepository: CategoryRepository,
    private val mangaRepository: MangaRepository,
) {

    fun subscribe(): Flow<Map<Long, List<Category>>> {
        return mangaRepository.getLibraryMangaAsFlow().flatMapLatest { mangaList ->
            if (mangaList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    mangaList.map { manga ->
                        categoryRepository.getCategoriesByMangaIdAsFlow(manga.id)
                            .map { categories -> manga.id to categories }
                    },
                ) { pairs ->
                    pairs.associate { it.first to it.second }
                }
            }
        }
    }
}
