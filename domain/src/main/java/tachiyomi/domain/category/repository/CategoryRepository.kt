package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryOrderUpdate
import tachiyomi.domain.category.model.CategoryUpdate

interface CategoryRepository {

    suspend fun insert(name: String, flags: Long)

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesOfManga(mangaId: Long): List<Category>

    fun getCategoriesOfMangaAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun updatePartial(update: CategoryUpdate)

    suspend fun updatePartial(updates: List<CategoryUpdate>)

    suspend fun updateChapterOrder(updates: List<CategoryOrderUpdate>)

    suspend fun updateFlagsForAll(flags: Long)

    suspend fun delete(id: Long)
}
