package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import eu.kanade.domain.source.service.SourcePreferences
import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class TrustExtensionRepositoryMigration : Migration {
    override val version: Float = 7f

    override suspend fun action(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val extensionRepositoryRepository = migrationContext.get<ExtensionRepoRepository>() ?: return@withIOContext false
        for ((index, source) in sourcePreferences.extensionRepos().get().withIndex()) {
            try {
                extensionRepositoryRepository.upsertRepo(
                    source,
                    "Repo #${index + 1}",
                    null,
                    source,
                    "NOFINGERPRINT-${index + 1}",
                )
            } catch (e: SaveExtensionRepoException) {
                logcat(LogPriority.ERROR, e) { "Error Migrating Extension Repo with baseUrl: $source" }
            }
        }
        sourcePreferences.extensionRepos().delete()
        return@withIOContext true
    }
}
