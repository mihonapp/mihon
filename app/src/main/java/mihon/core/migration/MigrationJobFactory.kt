package mihon.core.migration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import logcat.LogPriority
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
                    async {
                        val prev = acc.await()
                        // A failure would cancel the rest of the chain and leave the app waiting on it for good
                        val ran = try {
                            migration(migrationContext)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) {
                                "Migration failed: { name = ${migration::class.simpleName}, version = ${migration.version} }"
                            }
                            false
                        }
                        ran || prev
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
