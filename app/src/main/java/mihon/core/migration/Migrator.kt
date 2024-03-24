package mihon.core.migration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import tachiyomi.core.common.util.system.logcat

object Migrator {

    @SuppressWarnings("ReturnCount")
    fun migrate(
        old: Int,
        new: Int,
        migrations: List<Migration>,
        dryrun: Boolean = false,
        onMigrationComplete: () -> Unit
    ): Array<Deferred<Boolean>> {
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
            return emptyArray()
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
    ): Array<Deferred<Boolean>> {
        return versions.sorted()
            .flatMap { version ->
                (migrationsByVersion[version] ?: emptyList())
                    .sortedBy(Migration::version)
                    .map { migration ->
                        if (!dryrun) {
                            logcat { "Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                            return@map async { migration.action(this@migrate) }
                        }
                        logcat { "(Dry-run) Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }" }
                        async { true }
                    }
            }
            .toTypedArray()
    }
}
