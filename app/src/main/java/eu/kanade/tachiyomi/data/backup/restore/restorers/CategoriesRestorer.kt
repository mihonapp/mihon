package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CategoriesRestorer(
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>): Map<Long, Long> {
        if (backupCategories.isEmpty()) return emptyMap()

        val dbCategories = getCategories.await()
        val dbCategoryNames = dbCategories.mapTo(HashSet()) { it.name }

        val newCategories = backupCategories
            .filter { it.name !in dbCategoryNames }
            .distinctBy { it.name }
            .sortedBy { it.order }
        categoryRepository.insertAll(newCategories.map { NewCategory(name = it.name, flags = it.flags) })

        val restoredCategoriesByName = getCategories.await().groupBy { it.name }

        val flags = buildSet {
            dbCategories.mapTo(this) { it.flags }
            newCategories.mapTo(this) { it.flags }
        }
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)

        val unambiguousBackupCategories = backupCategories
            .groupBy { it.id }
            .values
            .filter { it.size == 1 }
            .flatten()
        return unambiguousBackupCategories.mapNotNull { backupCategory ->
            restoredCategoriesByName[backupCategory.name]
                ?.singleOrNull()
                ?.let { backupCategory.id to it.id }
        }.toMap()
    }
}
