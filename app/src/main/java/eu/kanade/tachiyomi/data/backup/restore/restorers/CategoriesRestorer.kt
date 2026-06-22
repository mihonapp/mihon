package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.Database
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val database: Database = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            val dbCategoriesByUid = dbCategories.associateBy { it.uid }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .map { backupCategory ->
                    var dbCategory = if (backupCategory.uid != 0L) {
                        dbCategoriesByUid[backupCategory.uid]
                    } else {
                        null
                    }

                    if (dbCategory == null) {
                        dbCategory = dbCategoriesByName[backupCategory.name]
                    }

                    if (dbCategory != null) {
                        database.categoriesQueries.update(
                            name = backupCategory.name,
                            order = backupCategory.order,
                            flags = backupCategory.flags,
                            version = backupCategory.version,
                            uid = if (backupCategory.uid != 0L) backupCategory.uid else dbCategory.uid,
                            lastModifiedAt = backupCategory.lastModifiedAt,
                            isSyncing = 1,
                            categoryId = dbCategory.id,
                        )
                        return@map dbCategory
                    }

                    val order = nextOrder++
                    database.categoriesQueries
                        .insert(
                            name = backupCategory.name,
                            order = order,
                            flags = backupCategory.flags,
                            version = backupCategory.version,
                            uid = backupCategory.uid,
                            lastModifiedAt = backupCategory.lastModifiedAt,
                        )
                        .let { id -> backupCategory.toCategory(id).copy(order = order) }
                }

            database.categoriesQueries.resetIsSyncing()

            libraryPreferences.categorizedDisplaySettings.set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
