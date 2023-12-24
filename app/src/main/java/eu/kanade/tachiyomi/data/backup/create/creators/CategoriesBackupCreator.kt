package eu.kanade.tachiyomi.data.backup.create.creators

import tachiyomi.domain.backup.model.BackupCategory
import tachiyomi.domain.backup.model.backupCategoryMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesBackupCreator(
    private val getCategories: GetCategories = Injekt.get(),
) {

    suspend fun backupCategories(): List<BackupCategory> {
        return getCategories.await()
            .filterNot(Category::isSystemCategory)
            .map(backupCategoryMapper)
    }
}
