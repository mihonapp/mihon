package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .map {
                    val dbCategory = dbCategoriesByName[it.name]
                    if (dbCategory != null) return@map dbCategory
                    val order = nextOrder++
                    handler.awaitOneExecutable {
                        categoriesQueries.insert(it.name, order, it.flags)
                        categoriesQueries.selectLastInsertedRowId()
                    }
                        .let { id -> it.toCategory(id).copy(order = order) }
                }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
