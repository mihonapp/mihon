package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class RemoveNonReferredCategoryPreferencesMigration : Migration {
    override val version: Float = 10f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val handler = migrationContext.get<DatabaseHandler>() ?: return@withIOContext false
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val downloadPreferences = migrationContext.get<DownloadPreferences>() ?: return@withIOContext false

        val allCategoryIds = handler.awaitList { categoriesQueries.getCategories() }.map { it.id.toString() }

        val defaultCategory = libraryPreferences.defaultCategory().get()
        if (defaultCategory.toString() !in allCategoryIds) {
            libraryPreferences.defaultCategory().delete()
        }

        val categoriesPrefs = listOf(
            libraryPreferences.updateCategories(),
            libraryPreferences.updateCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
        )
        categoriesPrefs.forEach { pref ->
            val categoriesSet = pref.get()
            val removingList = emptySet<String>().toMutableSet()
            categoriesSet.forEach { setId ->
                if (setId !in allCategoryIds) {
                    removingList += setId
                }
            }
            pref.set(
                categoriesSet.minus(removingList),
            )
        }
        return@withIOContext true
    }
}
