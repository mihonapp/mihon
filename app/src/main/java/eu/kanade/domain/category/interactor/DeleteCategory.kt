package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.repository.CategoryRepository

class DeleteCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long) {
        categoryRepository.delete(categoryId)
    }
}
