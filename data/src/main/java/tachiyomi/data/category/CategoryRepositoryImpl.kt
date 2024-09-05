package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryOrderUpdate
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryRepository {

    override suspend fun insert(name: String, flags: Long) {
        handler.await { categoriesQueries.insert(name, flags) }
    }

    override suspend fun get(id: Long): Category? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getAll(): List<Category> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getCategoriesOfManga(mangaId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesOfManga(mangaId, ::mapCategory)
        }
    }

    override fun getCategoriesOfMangaAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesOfManga(mangaId, ::mapCategory)
        }
    }

    private fun mapCategory(id: Long, name: String, order: Long, flags: Long): Category {
        return Category(id = id, name = name, order = order, flags = flags)
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
        categoriesQueries.updatePartial(id = update.id, name = update.name, order = update.order, flags = update.flags)
    }

    override suspend fun updateChapterOrder(updates: List<CategoryOrderUpdate>) {

        val updates = mutableListOf<CategoryOrderUpdate>()

        updates.forEach(::println)
    }

    private suspend fun Database.internalUpdateChapterOrder(updates: List<CategoryOrderUpdate>) {
        val filtered = updates.filter { it.newOrder != it.oldOrder }.toMutableList()
        var newOrder: Long? = null
        while (filtered.isNotEmpty()) {
            val index = filtered.indexOfFirst { it.newOrder == newOrder }.takeIf { it >= 0 }
            val category = filtered.removeAt(index ?: 0)
            if (index == null) {
                categoriesQueries.updatePartial(id = category.id, order = -1, name = null, flags = null)
                updates.add(CategoryOrderUpdate(category.newOrder, -1))
            }
            val update = updates.find { it.oldOrder == category.oldOrder }
            updates.add(CategoryOrderUpdate(update?.newOrder ?: category.oldOrder, category.newOrder))
            newOrder = category.oldOrder
        }
    }

    override suspend fun updateFlagsForAll(flags: Long) {
        handler.await { categoriesQueries.updateFlagsForAll(flags) }
    }

    override suspend fun delete(id: Long) {
        handler.withTransaction {
            val categories = handler.awaitList { categoriesQueries.getCategories(::mapCategory) }
            categoriesQueries.delete(id = id)
            val categories = getAll()
        }
    }


}
