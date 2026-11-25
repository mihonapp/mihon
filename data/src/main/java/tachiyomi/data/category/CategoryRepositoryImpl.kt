package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CategoryRepositoryImpl(
    private val database: Database,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return database.categoryQueries
            .getCategory(id, ::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaIds(mangaIds: List<Long>): Map<Long, List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaIds(mangaIds) { mangaId, id, name, order, flags, parentId ->
                mangaId to mapCategory(id, name, order, flags, parentId)
            }
            .awaitAsList()
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun insert(category: NewCategory) {
        database.categoryQueries.insert(name = category.name, flags = category.flags, parentId = category.parentId)
    }

    override suspend fun restore(categories: List<NewCategory>, parentNames: Map<String, String?>): Set<Long> {
        return database.transactionWithResult {
            val existing = getAll().mapTo(mutableSetOf()) { it.name }
            categories.filter { existing.add(it.name) }.forEach { insert(it) }

            val byName = getAll().associateBy { it.name }
            val parents = byName.values.associate { it.id to it.parentId }.toMutableMap()
            val restored = parentNames.mapNotNull { (name, parentName) ->
                val category = byName[name]?.takeUnless { it.isSystemCategory } ?: return@mapNotNull null
                parents[category.id] = byName[parentName]?.takeUnless { it.isSystemCategory }?.id
                category
            }
            for (category in restored) {
                val visited = mutableSetOf(category.id)
                var ancestor = parents[category.id]
                while (ancestor != null) {
                    if (!visited.add(ancestor)) {
                        parents[category.id] = null
                        break
                    }
                    ancestor = parents[ancestor]
                }
                if (parents[category.id] != category.parentId) {
                    database.categoryQueries.updateParent(id = category.id, parentId = parents[category.id])
                }
            }
            byName.values.mapTo(mutableSetOf()) { it.flags }
        }
    }

    override suspend fun updateName(categoryId: Long, name: String) {
        database.categoryQueries.updateName(name = name, id = categoryId)
    }

    override suspend fun updateNameAndParent(categoryId: Long, name: String, parentId: Long?) {
        database.transaction {
            if (parentId != null) {
                val visited = mutableSetOf(categoryId)
                var ancestor: Long? = parentId
                while (ancestor != null) {
                    require(ancestor != Category.UNCATEGORIZED_ID && visited.add(ancestor))
                    ancestor = requireNotNull(get(ancestor)).parentId
                }
            }
            updateName(categoryId, name)
            database.categoryQueries.updateParent(id = categoryId, parentId = parentId)
        }
    }

    override suspend fun updateFlags(categoryId: Long, flags: Long) {
        database.categoryQueries.updateFlags(flags = flags, id = categoryId)
    }

    override suspend fun updateAllFlags(flags: Long) {
        database.categoryQueries.updateAllFlags(flags = flags)
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            val current = database.categoryQueries.getUserCategoryIds().awaitAsList()
            val ids = orderedIds.filter { it in current } + current.filterNot { it in orderedIds }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = -index - 2L, id = categoryId)
            }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = index.toLong(), id = categoryId)
            }
        }
    }

    override suspend fun delete(categoryId: Long) {
        database.categoryQueries.delete(id = categoryId)
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        parentId: Long?,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            parentId = parentId,
        )
    }
}
