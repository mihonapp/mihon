package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun getCategoriesByMangaIds(mangaIds: List<Long>): Map<Long, List<Category>>

    suspend fun insert(category: NewCategory)

    suspend fun restore(categories: List<NewCategory>, parentNames: Map<String, String?>): Set<Long>

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateNameAndParent(categoryId: Long, name: String, parentId: Long?)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
