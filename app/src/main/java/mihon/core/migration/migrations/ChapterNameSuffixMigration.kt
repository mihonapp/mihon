package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@ContributesIntoSet(AppScope::class)
class ChapterNameSuffixMigration(
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 30f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (version > migrationContext.previousVersion) {
            val enableChapterNameHash = preferenceStore.getBoolean("pref_enable_chapter_name_hash", false)
            if (!enableChapterNameHash.isSet()) {
                enableChapterNameHash.set(true)
            }
        }
        return true
    }
}
