package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

class VerticalNavigatorMigration : Migration {
    override val version: Float = 25f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        val readerPreferences = migrationContext.get<ReaderPreferences>() ?: return@withIOContext false

        if (migrationContext.previousVersion == 24) {
            val oldVerticalNavigator = preferenceStore.getBoolean("pref_webtoon_vertical_navigator", true)
            if (oldVerticalNavigator.get()) {
                readerPreferences.verticalNavigator.set(setOf(ReadingMode.WEBTOON, ReadingMode.CONTINUOUS_VERTICAL))
            }
            if (oldVerticalNavigator.isSet()) oldVerticalNavigator.delete()
        }

        val oldVerticalNavigatorOnLeft = preferenceStore.getBoolean("pref_webtoon_vertical_navigator_on_left", false)
        if (oldVerticalNavigatorOnLeft.isSet()) {
            readerPreferences.verticalNavigatorOnLeft.set(oldVerticalNavigatorOnLeft.get())
            oldVerticalNavigatorOnLeft.delete()
        }

        return@withIOContext true
    }
}
