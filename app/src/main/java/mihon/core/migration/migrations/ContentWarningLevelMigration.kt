package mihon.core.migration.migrations

import eu.kanade.domain.source.model.ContentWarningLevel
import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

/**
 * The boolean `show_nsfw_source` preference was replaced by the tri-state
 * [SourcePreferences.contentWarningLevel]. Map each existing user's setting to the level that
 * reproduces their prior behavior exactly: the old toggle only ever gated discovery of NSFW
 * extensions, with Mixed always shown alongside Safe, so `false` becomes [ContentWarningLevel.SAFE_AND_MIXED]
 * and `true` becomes [ContentWarningLevel.ALL].
 */
class ContentWarningLevelMigration : Migration {
    override val version: Float = 26f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false

        val oldShowNsfwSource = preferenceStore.getBoolean("show_nsfw_source", true)
        if (oldShowNsfwSource.isSet()) {
            sourcePreferences.contentWarningLevel.set(
                if (oldShowNsfwSource.get()) ContentWarningLevel.ALL else ContentWarningLevel.SAFE_AND_MIXED,
            )
            oldShowNsfwSource.delete()
        }

        return@withIOContext true
    }
}
