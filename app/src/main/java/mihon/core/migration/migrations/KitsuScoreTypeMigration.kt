package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.service.TrackPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class KitsuScoreTypeMigration(
    private val trackPreferences: TrackPreferences,
) : Migration {
    override val version: Float = 30f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val oldScoreType = trackPreferences.kitsuScoreType.get()
        trackPreferences.kitsuScoreType.set(oldScoreType.lowercase())
        return true
    }
}
