package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val database: Database,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return database.categoriesQueries
            .getCategory(id, ::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoriesQueries
            .getCategories(::mapCategory)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoriesQueries
            .getCategories(::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoriesQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoriesQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override suspend fun insert(category: Category) {
        database.categoriesQueries.insert(
            name = category.name,
            order = category.order,
            flags = category.flags,
            version = category.version,
            uid = category.uid,
            lastModifiedAt = category.lastModifiedAt,
        )
    }

    override suspend fun updatePartial(update: CategoryUpdate) {
        database.categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            version = update.version,
            uid = update.uid,
            lastModifiedAt = update.lastModifiedAt,
            isSyncing = null,
            categoryId = update.id,
        )
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        database.transaction {
            updates.forEach { updatePartial(it) }
        }
    }

    override suspend fun updateAllFlags(flags: Long?) {
        database.categoriesQueries.updateAllFlags(flags)
    }

    override suspend fun delete(categoryId: Long) {
        database.categoriesQueries.delete(categoryId = categoryId)
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            version = version,
            uid = uid,
            lastModifiedAt = lastModifiedAt,
        )
    }
}
