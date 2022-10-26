package eu.kanade.data.category

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.Database
import kotlinx.coroutines.flow.Flow

class CategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, categoryMapper) }
    }

    override suspend fun getAll(): List<Category> {
        return handler.awaitList { categoriesQueries.getCategories(categoryMapper) }
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(categoryMapper) }
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByMangaId(mangaId, categoryMapper)
        }
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByMangaId(mangaId, categoryMapper)
        }
    }

    override suspend fun insert(category: Category) {
        handler.await {
            categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
        }
    }

    override suspend fun updatePartial(update: CategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: CategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            categoryId = update.id,
        )
    }

    override suspend fun updateAllFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }
}
