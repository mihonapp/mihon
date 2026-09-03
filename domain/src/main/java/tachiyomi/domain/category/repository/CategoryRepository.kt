package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun insert(category: Category)

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long?)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
