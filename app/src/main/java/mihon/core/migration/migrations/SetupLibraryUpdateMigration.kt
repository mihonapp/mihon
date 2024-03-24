package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob

class SetupLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun action(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<App>() ?: return false
        LibraryUpdateJob.setupTask(context)
        return true
    }
}
