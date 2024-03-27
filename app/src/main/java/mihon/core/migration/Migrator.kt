package mihon.core.migration

import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.util.system.logcat

object Migrator {

    @SuppressWarnings("ReturnCount")
    fun migrate(
        old: Int,
        new: Int,
        migrations: List<Migration>,
        dryrun: Boolean = false,
        onMigrationComplete: () -> Unit
    ): Boolean {
        val migrationContext = MigrationContext()

        if (old == 0) {
            return migrationContext.migrate(
                migrations = migrations.filter { it.isAlways() },
                dryrun = dryrun
            )
                .also { onMigrationComplete() }
        }

        if (old >= new) {
            return false
        }

        return migrationContext.migrate(
            migrations = migrations.filter { it.isAlways() || it.version.toInt() in (old + 1)..new },
            dryrun = dryrun
        )
            .also { onMigrationComplete() }
    }

    private fun Migration.isAlways() = version == Migration.ALWAYS

    @SuppressWarnings("MaxLineLength")
    private fun MigrationContext.migrate(migrations: List<Migration>, dryrun: Boolean): Boolean {
        return migrations.sortedBy { it.version }
            .map { migration ->
                if (!dryrun) {
                    logcat { "Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                    runBlocking { migration(this@migrate) }
                } else {
                    logcat { "(Dry-run) Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                    true
                }
            }
            .reduce { acc, b -> acc || b }
    }
}
