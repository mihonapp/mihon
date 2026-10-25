package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class CategoryFilterCleanupMigration(
    private val getCategories: GetCategories,
    private val upcomingPreferences: UpcomingPreferences,
    private val updatesPreferences: UpdatesPreferences,
) : Migration {
    override val version: Float = 39f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val allCategoryIds = getCategories.await().map { it.id }.toSet()

        listOf(
            upcomingPreferences.filterIncludedCategories,
            upcomingPreferences.filterExcludedCategories,
            updatesPreferences.filterIncludedCategories,
            updatesPreferences.filterExcludedCategories,
        ).forEach { preference ->
            val filtered = preference.get()
            val gone = filtered.filterNotTo(HashSet()) { it in allCategoryIds }
            if (gone.isEmpty()) return@forEach
            preference.set(filtered.minus(gone))
        }

        return true
    }
}
