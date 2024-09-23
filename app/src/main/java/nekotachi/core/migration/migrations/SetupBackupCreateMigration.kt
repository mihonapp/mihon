package nekotachi.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import nekotachi.core.migration.Migration
import nekotachi.core.migration.MigrationContext

class SetupBackupCreateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        BackupCreateJob.setupTask(context)
        return true
    }
}
