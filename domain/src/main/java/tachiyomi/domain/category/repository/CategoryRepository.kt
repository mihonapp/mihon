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

    /**
     * Names of the user categories of each library manga from [sourceId], keyed by manga id
     */
    fun getUserCategoryNamesOfFavoritesAsFlow(sourceId: Long): Flow<Map<Long, List<String>>>

    suspend fun insert(category: NewCategory)

    suspend fun insertAll(categories: List<NewCategory>)

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
