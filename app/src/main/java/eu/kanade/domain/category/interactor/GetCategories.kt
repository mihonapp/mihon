package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class GetCategories(
    private val categoryRepository: CategoryRepository,
) {

    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAll()
    }
}
