package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CategoriesRestorer(
    private val categoryRepository: CategoryRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val backups = backupCategories.sortedBy { it.order }.distinctBy { it.name }
        val namesById = backups.associate { it.id to it.name }
        // A missing field in an older backup preserves the matched category's current parent.
        val parentNames = backups.filter { it.parentId != null }.associate { backup ->
            backup.name to backup.parentId?.takeUnless { it == Category.UNCATEGORIZED_ID }?.let(namesById::get)
        }
        val flags = categoryRepository.restore(
            categories = backups.map { NewCategory(name = it.name, flags = it.flags) },
            parentNames = parentNames,
        )
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)
    }
}
