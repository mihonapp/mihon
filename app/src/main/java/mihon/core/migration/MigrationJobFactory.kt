package mihon.core.migration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import tachiyomi.core.common.util.system.logcat

class MigrationJobFactory(
    private val migrationContext: MigrationContext,
    private val scope: CoroutineScope,
) {

    fun create(migrations: List<Migration>): Deferred<Boolean> = with(scope) {
        return migrations.sortedBy { it.version }
            .fold(CompletableDeferred(true)) { acc: Deferred<Boolean>, migration: Migration ->
                if (!migrationContext.dryrun) {
                    logcat {
                        "Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }"
                    }
                    async(start = CoroutineStart.UNDISPATCHED) {
                        val prev = acc.await()
                        migration(migrationContext) || prev
                    }
                } else {
                    logcat {
                        "(Dry-run) Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }"
                    }
                    CompletableDeferred(true)
                }
            }
    }
}
