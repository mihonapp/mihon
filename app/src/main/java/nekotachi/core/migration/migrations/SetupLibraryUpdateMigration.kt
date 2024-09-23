package nekotachi.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import nekotachi.core.migration.Migration
import nekotachi.core.migration.MigrationContext

class SetupLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        LibraryUpdateJob.setupTask(context)
        return true
    }
}
