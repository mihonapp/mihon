package eu.kanade.domain.category.repository

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    fun getAll(): Flow<List<Category>>

    @Throws(DuplicateNameException::class)
    suspend fun insert(name: String, order: Long)

    @Throws(DuplicateNameException::class)
    suspend fun update(payload: CategoryUpdate)

    suspend fun delete(categoryId: Long)

    suspend fun getCategoriesForManga(mangaId: Long): List<Category>

    suspend fun checkDuplicateName(name: String): Boolean
}

class DuplicateNameException(name: String) : Exception("There's a category which is named \"$name\" already")
