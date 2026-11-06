package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class ChapterNameSuffixMigration(
    private val preference: LibraryPreferences,
) : Migration {
    override val version: Float = 34f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (migrationContext.previousVersion in 13..33) {
            preference.enableChapterNameHash.set(true)
        }
        return true
    }
}
