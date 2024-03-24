package mihon.core.migration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val coroutineScope = CoroutineScope(Dispatchers.IO)

        val migrationsByVersion = migrations.groupBy { it.version.toInt() }
        val always = listOf(Migration.ALWAYS.toInt())

        if (old == 0) {
            onMigrationComplete()
            return with(coroutineScope) {
                migrationContext.migrate(always, migrationsByVersion, dryrun)
            }
        }

        if (old >= new) {
            return false
        }

        onMigrationComplete()
        val versions = migrationsByVersion.keys.filter { version -> version in (old + 1)..new }
        return with(coroutineScope) {
            migrationContext.migrate(always + versions, migrationsByVersion, dryrun)
        }
    }

    context (CoroutineScope)
    @SuppressWarnings("MaxLineLength")
    private fun MigrationContext.migrate(
        versions: List<Int>,
        migrationsByVersion: Map<Int, List<Migration>>,
        dryrun: Boolean
    ): Boolean {
        var aBoolean = false
        for (version in versions.sorted()) {
            val migrations = migrationsByVersion.getOrDefault(version, emptyList()).sortedBy(Migration::version)
            for (migration in migrations) {
                val success = runBlocking {
                    if (!dryrun) {
                        logcat { "Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                    } else {
                        logcat { "(Dry-run) Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                    }
                    migration.action(this@migrate)
                }
                aBoolean = success || aBoolean
            }
        }
        return aBoolean
    }
}
